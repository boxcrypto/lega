package net.lega.server.command;

/**
 * Reads commands from stdin (the server console) on a daemon virtual thread.
 * Runs concurrently with the tick engine's main-thread loop.
 *
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.server.LegaServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;


public final class ConsoleCommandReader {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Console");

    // ANSI helpers
    private static final String CYAN   = "\u001B[36m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String BOLD   = "\u001B[1m";
    private static final String RESET  = "\u001B[0m";

    private final LegaServer  server;
    private final LegaCommand legaCommand;
    private volatile Thread   thread;

    public ConsoleCommandReader(LegaServer server) {
        this.server      = server;
        this.legaCommand = new LegaCommand(server);
    }

    /** Starts reading stdin on a daemon virtual thread. Returns immediately. */
    public void start() {
        thread = Thread.ofVirtual()
                .name("LEGA-Console")
                .start(this::loop);
        LOGGER.info("[LEGA/Console] Console command reader started. Type 'help' for commands.");
    }

    // =========================================================================
    // Main read loop
    // =========================================================================

    private void loop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty()) continue;

                try {
                    dispatch(line);
                } catch (Exception e) {
                    LOGGER.error("[LEGA/Console] Error executing command '{}': {}", line, e.getMessage());
                }

                if (!server.isRunning()) break;
            }
        } catch (Exception e) {
            if (server.isRunning()) {
                LOGGER.warn("[LEGA/Console] Console reader closed unexpectedly: {}", e.getMessage());
            }
        }
    }

    // =========================================================================
    // Dispatcher
    // =========================================================================

    private void dispatch(String input) {
        String[] tokens = input.split("\\s+");
        String cmd = tokens[0].toLowerCase(Locale.ROOT);

        // Strip leading slash if present (e.g. /stop, /tps)
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }

        switch (cmd) {
            case "stop", "exit", "quit" -> handleStop();
            case "help",  "?"           -> handleHelp();
            case "tps"                  -> handleTps();
            case "mspt"                 -> handleMspt();
            case "uptime"               -> handleUptime();
            case "threads"              -> handleThreads();
            case "gc"                   -> handleGc();
            case "mem",  "memory"       -> handleMemory();
            case "lega"                 -> {
                // Forward sub-commands: lega reload / lega profiler / etc.
                String[] subArgs = java.util.Arrays.copyOfRange(tokens, 1, tokens.length);
                legaCommand.execute("CONSOLE", subArgs);
            }
            default -> System.out.println(YELLOW + "Unknown command: " + cmd
                    + ". Type 'help' for a list of commands." + RESET);
        }
    }

    // =========================================================================
    // Command handlers
    // =========================================================================

    private void handleStop() {
        System.out.println(YELLOW + "Stopping server…" + RESET);
        // Shutdown hook already registered; just trigger it.
        server.shutdown();
        System.exit(0);
    }

    private void handleHelp() {
        System.out.println(BOLD + CYAN
                + "┌─────────────────────────────────────────────────────────┐" + RESET);
        System.out.println(BOLD + CYAN + "│  LEGA Console Commands" + RESET);
        System.out.println(BOLD + CYAN
                + "├─────────────────────────────────────────────────────────┤" + RESET);
        printCmd("stop / exit / quit",   "Gracefully shut down the server");
        printCmd("tps",                  "Show current TPS (1m / 5m / 15m)");
        printCmd("mspt",                 "Show ms-per-tick metrics");
        printCmd("uptime",               "Show server uptime");
        printCmd("threads",              "List all live threads");
        printCmd("gc",                   "Run a full garbage collection cycle");
        printCmd("mem / memory",         "Show JVM heap usage");
        printCmd("lega <sub>",           "LEGA engine commands (reload, profiler, …)");
        printCmd("help / ?",             "Show this help");
        System.out.println(BOLD + CYAN
                + "└─────────────────────────────────────────────────────────┘" + RESET);
    }

    private void handleTps() {
        if (server.getTickEngine() == null) {
            System.out.println(YELLOW + "Tick engine not yet started." + RESET);
            return;
        }
        double[] tps = server.getTickEngine().getTPS();
        System.out.printf(GREEN + "TPS  1m: %.2f  |  5m: %.2f  |  15m: %.2f%n" + RESET,
                tps[0], tps[1], tps[2]);
    }

    private void handleMspt() {
        if (server.getTickEngine() == null) {
            System.out.println(YELLOW + "Tick engine not yet started." + RESET);
            return;
        }
        var te = server.getTickEngine();
        System.out.printf(GREEN + "MSPT  last: %.2f ms  |  avg: %.2f ms  |  max: %.2f ms  |  ticks: %d%n" + RESET,
                te.getLastMspt(), te.getAvgMspt(), te.getMaxMspt(), te.getTickCount());
    }

    private void handleUptime() {
        long millis = server.getUptimeMillis();
        long s = millis / 1000;
        long m = s / 60; s %= 60;
        long h = m / 60; m %= 60;
        System.out.printf(GREEN + "Uptime: %dh %02dm %02ds%n" + RESET, h, m, s);
    }

    private void handleThreads() {
        System.out.println(CYAN + "Live threads:" + RESET);
        Thread.getAllStackTraces().keySet().stream()
                .sorted(java.util.Comparator.comparing(Thread::getName))
                .forEach(t -> System.out.printf("  [%s] %s  state=%s%n",
                        t.isDaemon() ? "D" : "U", t.getName(), t.getState()));
    }

    private void handleGc() {
        long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.gc();
        long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.printf(GREEN + "GC complete. Freed ~%.1f MB%n" + RESET,
                (before - after) / 1_048_576.0);
    }

    private void handleMemory() {
        Runtime rt = Runtime.getRuntime();
        long used  = rt.totalMemory() - rt.freeMemory();
        long total = rt.totalMemory();
        long max   = rt.maxMemory();
        System.out.printf(GREEN + "Heap  used: %.1f MB  /  allocated: %.1f MB  /  max: %.1f MB%n" + RESET,
                used / 1_048_576.0, total / 1_048_576.0, max / 1_048_576.0);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void printCmd(String name, String desc) {
        System.out.printf("  " + BOLD + "%-22s" + RESET + " %s%n", name, desc);
    }
}
