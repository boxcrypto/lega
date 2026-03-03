package net.lega.server.bootstrap;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.server.LegaServer;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;


public final class LegaBootstrap {

    // ANSI color codes
    private static final String RESET  = "\u001B[0m";
    private static final String PURPLE = "\u001B[35m";
    private static final String BLUE   = "\u001B[34m";
    private static final String CYAN   = "\u001B[36m";
    private static final String BOLD   = "\u001B[1m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";

    private LegaBootstrap() {}

    public static void main(String[] args) throws Exception {
        // Configure log4j2 before any logger is created
        System.setProperty("log4j2.configurationFile", "log4j2.xml");

        // Record precise start time
        final long startNanos = System.nanoTime();

        // Print LEGA ASCII banner
        printBanner();

        // Java version guard
        int javaVersion = Runtime.version().feature();
        if (javaVersion < 21) {
            System.err.println(RED + "[LEGA] FATAL: Java 21+ is required! Found: Java " + javaVersion + RESET);
            System.exit(1);
        }

        // Print JVM flags summary
        printJvmInfo();

        // Recommend GC flags if not already set
        warnMissingGcFlags();

        // Change working directory to server root if --server-dir argument provided
        String serverDir = parseArg(args, "--server-dir", ".");
        System.setProperty("user.dir", new File(serverDir).getAbsolutePath());

        System.out.println(PURPLE + "[LEGA] " + RESET + "Bootstrapping LEGA Server Engine...");

        // Create and start the LEGA server
        LegaServer server = new LegaServer(args);
        server.start();

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        System.out.println(GREEN + "[LEGA] Server started in " + elapsedMs + "ms" + RESET);
    }

    private static void printBanner() {
        System.out.println();
        System.out.println(PURPLE + BOLD +
                "  ██╗     ███████╗ ██████╗  █████╗ " + RESET);
        System.out.println(PURPLE + BOLD +
                "  ██║     ██╔════╝██╔════╝ ██╔══██╗" + RESET);
        System.out.println(BLUE + BOLD +
                "  ██║     █████╗  ██║  ███╗███████║" + RESET);
        System.out.println(BLUE + BOLD +
                "  ██║     ██╔══╝  ██║   ██║██╔══██║" + RESET);
        System.out.println(CYAN + BOLD +
                "  ███████╗███████╗╚██████╔╝██║  ██║" + RESET);
        System.out.println(CYAN + BOLD +
                "  ╚══════╝╚══════╝ ╚═════╝ ╚═╝  ╚═╝" + RESET);
        System.out.println();
        System.out.println(PURPLE + "  High-Performance Minecraft Server Engine" + RESET);
        System.out.println(CYAN + "  Supporting Minecraft 1.7.10 → 1.21.x" + RESET);
        System.out.println(BLUE + "  github.com/boxcrypto/lega" + RESET);
        System.out.println();
        System.out.println(PURPLE + "  [LEGA] " + RESET + "Performance Engine Initialized.");
        System.out.println();
    }

    private static void printJvmInfo() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        long maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        System.out.println(CYAN + "[LEGA] " + RESET + "Java " + Runtime.version()
                + " | Max Heap: " + maxHeapMb + "MB"
                + " | Processors: " + Runtime.getRuntime().availableProcessors());
    }

    private static void warnMissingGcFlags() {
        List<String> flags = ManagementFactory.getRuntimeMXBean().getInputArguments();
        String flagStr = String.join(" ", flags);

        boolean hasGc = flagStr.contains("UseG1GC")
                || flagStr.contains("UseZGC")
                || flagStr.contains("UseShenandoahGC");

        if (!hasGc) {
            System.out.println(YELLOW + "[LEGA] WARN: No GC flags detected. For best performance, add:"  + RESET);
            System.out.println(YELLOW + "  -XX:+UseG1GC -XX:G1HeapRegionSize=16M -XX:MaxGCPauseMillis=100 \\" + RESET);
            System.out.println(YELLOW + "  -XX:+ParallelRefProcEnabled -XX:+UnlockExperimentalVMOptions \\" + RESET);
            System.out.println(YELLOW + "  -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 \\" + RESET);
            System.out.println(YELLOW + "  -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \\" + RESET);
            System.out.println(YELLOW + "  -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90" + RESET);
        }
    }

    private static String parseArg(String[] args, String key, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
}
