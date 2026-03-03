package net.lega.server.command;

/**
 * Reads commands from stdin on a virtual thread, running alongside the tick loop.
 * Supports Bukkit / Spigot / Paper / Imanity-style commands + LEGA-specific ones.
 *
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.server.LegaServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;


public final class ConsoleCommandReader {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Console");

    // ANSI
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN   = "\u001B[36m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";
    private static final String BOLD   = "\u001B[1m";
    private static final String DIM    = "\u001B[2m";
    private static final String RESET  = "\u001B[0m";

    private final LegaServer     server;
    private final LegaCommand    legaCommand;
    private volatile Thread      thread;

    // Console-tracked state
    private final AtomicBoolean  saveEnabled      = new AtomicBoolean(true);
    private final AtomicBoolean  whitelistEnabled = new AtomicBoolean(false);
    private final List<String>   whitelist        = new ArrayList<>();
    private final List<String>   opList           = new ArrayList<>();
    private final List<String>   banList          = new ArrayList<>();
    private final List<String>   ipBanList        = new ArrayList<>();
    private       boolean        timingsActive    = false;

    public ConsoleCommandReader(LegaServer server) {
        this.server      = server;
        this.legaCommand = new LegaCommand(server);
    }

    public void start() {
        thread = Thread.ofVirtual().name("LEGA-Console").start(this::loop);
        LOGGER.info("[LEGA/Console] Console ready. Type 'help' for commands.");
    }

    // =========================================================================
    // Read loop
    // =========================================================================

    private void loop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty()) continue;
                try { dispatch(line); }
                catch (Exception e) { LOGGER.error("[Console] Error running '{}': {}", line, e.getMessage()); }
                if (!server.isRunning()) break;
            }
        } catch (Exception e) {
            if (server.isRunning()) LOGGER.warn("[Console] Closed: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Dispatcher
    // =========================================================================

    private void dispatch(String input) {
        String[] tokens = input.split("\\s+");
        String cmd = tokens[0].toLowerCase(Locale.ROOT);
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        String[] args = tokens.length > 1
                ? java.util.Arrays.copyOfRange(tokens, 1, tokens.length) : new String[0];

        switch (cmd) {
            // ── Lifecycle ──────────────────────────────────────────────────────
            case "stop","exit","quit"             -> doStop();
            case "restart"                        -> doRestart();
            case "reload"                         -> doReload(args);
            // ── Info ───────────────────────────────────────────────────────────
            case "help","?"                       -> doHelp();
            case "version","ver","about"          -> doVersion();
            case "plugins","pl"                   -> doPlugins();
            case "list"                           -> doList();
            case "uptime"                         -> doUptime();
            case "info"                           -> doInfo();
            case "debug"                          -> doDebug();
            // ── Performance ────────────────────────────────────────────────────
            case "tps"                            -> doTps();
            case "mspt"                           -> doMspt();
            case "mem","memory"                   -> doMemory();
            case "gc"                             -> doGc();
            case "threads"                        -> doThreads();
            case "perf","performance"             -> doPerf();
            case "diagnose"                       -> doDiagnose();
            case "flamegraph"                     -> doFlamegraph();
            case "dumplisteners"                  -> doDumpListeners();
            // ── Spigot / Paper ─────────────────────────────────────────────────
            case "timings"                        -> doTimings(args);
            case "mobcaps"                        -> doMobcaps();
            case "tick"                           -> doTick(args);
            case "fixlight"                       -> doFixlight(args);
            // ── World / save ───────────────────────────────────────────────────
            case "worlds"                         -> doWorlds();
            case "save-all"                       -> doSaveAll(args);
            case "save-off"                       -> doSaveOff();
            case "save-on"                        -> doSaveOn();
            // ── Broadcast ──────────────────────────────────────────────────────
            case "say"                            -> doSay(args);
            case "broadcast","bc"                 -> doBroadcast(args);
            case "me"                             -> doMe(args);
            // ── Moderation ─────────────────────────────────────────────────────
            case "kick"                           -> doKick(args);
            case "ban"                            -> doBan(args);
            case "ban-ip"                         -> doBanIp(args);
            case "pardon"                         -> doPardon(args);
            case "pardon-ip"                      -> doPardonIp(args);
            case "banlist"                        -> doBanlist(args);
            // ── Whitelist ──────────────────────────────────────────────────────
            case "whitelist"                      -> doWhitelist(args);
            // ── Ops ────────────────────────────────────────────────────────────
            case "op"                             -> doOp(args);
            case "deop"                           -> doDeop(args);
            case "ops"                            -> doOps();
            // ── LEGA engine ────────────────────────────────────────────────────
            case "lega"                           -> legaCommand.execute("CONSOLE", args);
            case "ping"                           -> doPing();
            case "crash-report"                   -> doCrashReport();
            case "config"                         -> doConfig(args);
            default -> println(YELLOW + "Unknown command '" + cmd
                    + "'. Type 'help' for a full list." + RESET);
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    private void doStop() {
        println(YELLOW + "Stopping LEGA server..." + RESET);
        server.shutdown();
        System.exit(0);
    }

    private void doRestart() {
        println(YELLOW + "[LEGA] Restart requested. Exit code 2 signals start script to restart." + RESET);
        try { Files.writeString(Path.of(".restart"), Instant.now().toString(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); }
        catch (IOException ignored) { }
        server.shutdown();
        System.exit(2);
    }

    private void doReload(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("confirm")) {
            println(YELLOW + "[LEGA] Reloading configuration..." + RESET);
            try { server.getConfiguration().reload(); println(GREEN + "[LEGA] Reloaded." + RESET); }
            catch (Exception e) { println(RED + "[LEGA] Reload failed: " + e.getMessage() + RESET); }
        } else {
            println(YELLOW + "Use 'reload confirm' to reload configs. Some changes require restart." + RESET);
        }
    }

    // =========================================================================
    // Info
    // =========================================================================

    private void doHelp() {
        println(BOLD + CYAN + "┌──────────────────────────────────────────────────────────────────────┐" + RESET);
        println(BOLD + CYAN + "│  LEGA Console — Command Reference" + RESET);
        println(BOLD + CYAN + "├──────────────────────────────────────────────────────────────────────┤" + RESET);
        sec("SERVER");          cmd("stop / exit / quit",             "Graceful shutdown");
                                cmd("restart",                         "Restart (exit code 2 for start script)");
                                cmd("reload confirm",                  "Hot-reload config files");
        sec("INFO");            cmd("version / ver / about",           "Show LEGA + Java version");
                                cmd("plugins / pl",                    "List loaded plugins");
                                cmd("list",                            "List online players");
                                cmd("uptime",                          "Server uptime");
                                cmd("info",                            "Full server info summary");
                                cmd("debug",                           "JVM + system debug dump");
        sec("PERFORMANCE");     cmd("tps",                             "TPS — 1m / 5m / 15m");
                                cmd("mspt",                            "MSPT — last / avg / max");
                                cmd("mem / memory",                   "Heap usage");
                                cmd("gc",                              "Force garbage collection");
                                cmd("threads",                         "All JVM threads");
                                cmd("perf / performance",              "Full performance report");
                                cmd("diagnose",                        "Auto-diagnose bottlenecks");
                                cmd("flamegraph",                      "CPU flame graph info");
        sec("SPIGOT / PAPER");  cmd("timings on|off|report|reset",    "Timings profiler");
                                cmd("mobcaps",                         "Mob spawn caps");
                                cmd("tick freeze|unfreeze|step <n>",  "Tick control");
                                cmd("fixlight <world>",                "Relight world async");
                                cmd("dumplisteners",                   "Dump event listeners");
        sec("WORLD / SAVE");    cmd("worlds",                          "List loaded worlds");
                                cmd("save-all [flush]",                "Save all worlds");
                                cmd("save-off",                        "Disable auto-save");
                                cmd("save-on",                         "Enable auto-save");
        sec("BROADCAST");       cmd("say <msg>",                       "Server-wide broadcast");
                                cmd("broadcast / bc <msg>",            "Alias for say");
                                cmd("me <action>",                     "* SERVER <action>");
        sec("MODERATION");      cmd("kick <player> [reason]",          "Kick player");
                                cmd("ban <player> [reason]",           "Ban player");
                                cmd("ban-ip <ip> [reason]",            "Ban IP");
                                cmd("pardon <player>",                 "Unban player");
                                cmd("pardon-ip <ip>",                  "Unban IP");
                                cmd("banlist [players|ips]",           "View ban list");
        sec("WHITELIST");       cmd("whitelist on|off",                "Enable/disable whitelist");
                                cmd("whitelist add|remove <player>",   "Manage entries");
                                cmd("whitelist list|reload",           "View / reload whitelist");
        sec("OPS");             cmd("op <player>",                     "Grant operator");
                                cmd("deop <player>",                   "Revoke operator");
                                cmd("ops",                             "List operators");
        sec("LEGA ENGINE");     cmd("lega reload",                     "Reload LEGA config");
                                cmd("lega profiler start|stop|report", "Profiler controls");
                                cmd("lega performance",                "Engine performance stats");
                                cmd("lega security",                   "Security dashboard");
                                cmd("lega world",                      "World engine status");
                                cmd("lega score",                      "Performance score 0-100");
                                cmd("lega gc / threads / version",     "GC / threads / version");
                                cmd("lega debug",                      "Engine diagnostics");
        sec("MISC");            cmd("ping",                            "Server internal latency");
                                cmd("crash-report",                    "Dump thread state");
                                cmd("config get <key>",                "Read a config key");
        println(BOLD + CYAN + "└──────────────────────────────────────────────────────────────────────┘" + RESET);
    }

    private void doVersion() {
        println(BOLD + PURPLE + "LEGA Server Engine" + RESET + "  v1.0.0-SNAPSHOT");
        println(CYAN + "  Protocol  " + RESET + "1.7.10 \u2192 1.21.x");
        println(CYAN + "  Java      " + RESET + System.getProperty("java.version")
                + " (" + System.getProperty("java.vendor") + ")");
        println(CYAN + "  OS        " + RESET + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        println(CYAN + "  Built     " + RESET + "2026-03-03  |  github.com/boxcrypto/lega");
    }

    private void doPlugins() {
        println(BOLD + CYAN + "Plugins (0):" + RESET + " [none loaded]");
        println(DIM + "  Place plugin JARs in plugins/ and restart." + RESET);
    }

    private void doList() {
        int max = server.getConfiguration() != null ? server.getConfiguration().getMaxPlayers() : 200;
        println(GREEN + "There are 0 of a max " + max + " players online." + RESET);
    }

    private void doUptime() {
        long s = server.getUptimeMillis() / 1000, m = s / 60; s %= 60; long h = m / 60; m %= 60;
        println(GREEN + "Uptime: " + h + "h " + String.format("%02d", m) + "m " + String.format("%02d", s) + "s" + RESET);
    }

    private void doInfo() {
        var cfg = server.getConfiguration();
        println(BOLD + CYAN + "═══ LEGA Server Info ═══" + RESET);
        if (cfg != null) {
            println(CYAN + "  MOTD         " + RESET + cfg.getMotd());
            println(CYAN + "  Port         " + RESET + cfg.getServerPort());
            println(CYAN + "  Max Players  " + RESET + cfg.getMaxPlayers());
            println(CYAN + "  Gamemode     " + RESET + cfg.getGamemode() + (cfg.isHardcore() ? " (HARDCORE)" : ""));
            println(CYAN + "  Difficulty   " + RESET + cfg.getDifficulty());
            println(CYAN + "  Level Name   " + RESET + cfg.getLevelName());
            println(CYAN + "  PvP          " + RESET + cfg.isPvpEnabled());
            println(CYAN + "  Online Mode  " + RESET + cfg.isOnlineModeEnabled());
            println(CYAN + "  View Dist    " + RESET + cfg.getViewDistance() + " chunks");
            println(CYAN + "  Sim Dist     " + RESET + cfg.getSimulationDistance() + " chunks");
            println(CYAN + "  Whitelist    " + RESET + cfg.isWhitelistEnabled());
            println(CYAN + "  Allow Flight " + RESET + cfg.isAllowFlight());
            println(CYAN + "  Cmd Block    " + RESET + cfg.isCommandBlockEnabled());
        }
        doUptime(); doTps(); doMemory();
    }

    // =========================================================================
    // Performance
    // =========================================================================

    private void doTps() {
        var te = server.getTickEngine();
        if (te == null) { println(YELLOW + "Tick engine not started." + RESET); return; }
        double[] t = te.getTPS();
        println(GREEN + "TPS " + RESET
                + "1m: "  + tpsColor(t[0]) + f2(t[0]) + RESET
                + "  5m: " + tpsColor(t[1]) + f2(t[1]) + RESET
                + "  15m: " + tpsColor(t[2]) + f2(t[2]) + RESET);
    }

    private void doMspt() {
        var te = server.getTickEngine();
        if (te == null) { println(YELLOW + "Tick engine not started." + RESET); return; }
        println(GREEN + "MSPT " + RESET + "last: " + f2(te.getLastMspt()) + "ms"
                + "  avg: " + f2(te.getAvgMspt()) + "ms"
                + "  max: " + RED + f2(te.getMaxMspt()) + "ms" + RESET
                + "  ticks: " + te.getTickCount());
    }

    private void doMemory() {
        Runtime rt = Runtime.getRuntime();
        long used  = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L;
        long alloc = rt.totalMemory() / 1_048_576L;
        long max   = rt.maxMemory()   / 1_048_576L;
        int  pct   = (int)(100L * used / max);
        String c   = pct >= 85 ? RED : pct >= 65 ? YELLOW : GREEN;
        println(c + "Heap " + RESET + "used: " + c + used + "MB" + RESET
                + "  alloc: " + alloc + "MB  max: " + max + "MB  (" + c + pct + "%" + RESET + ")");
    }

    private void doGc() {
        Runtime rt = Runtime.getRuntime();
        long b = rt.totalMemory() - rt.freeMemory(); System.gc();
        long a = rt.totalMemory() - rt.freeMemory();
        println(GREEN + "GC complete. Freed ~" + f1((b - a) / 1_048_576.0) + " MB" + RESET);
    }

    private void doThreads() {
        println(CYAN + "Live threads (" + Thread.getAllStackTraces().size() + "):" + RESET);
        Thread.getAllStackTraces().keySet().stream()
                .sorted(java.util.Comparator.comparing(Thread::getName))
                .forEach(t -> println("  " + DIM + "[" + (t.isDaemon() ? "D" : "U") + "] "
                        + RESET + t.getName() + "  " + DIM + t.getState() + RESET));
    }

    private void doPerf() {
        println(BOLD + CYAN + "═══ Performance Report ═══" + RESET);
        doTps(); doMspt(); doMemory();
        var te = server.getTickEngine();
        if (te != null) {
            double ms = te.getAvgMspt();
            String h = ms < 20 ? GREEN + "Excellent" : ms < 40 ? GREEN + "Good"
                    : ms < 50 ? YELLOW + "Acceptable" : RED + "Poor";
            println(CYAN + "  Health    " + RESET + h + RESET);
            println(CYAN + "  Ticks     " + RESET + te.getTickCount());
        }
        println(CYAN + "  Threads   " + RESET + Thread.getAllStackTraces().size() + " live");
    }

    private void doDiagnose() {
        println(BOLD + CYAN + "═══ Auto-Diagnose ═══" + RESET);
        boolean ok = true;
        var te = server.getTickEngine();
        if (te != null) {
            double tps = te.getTPS()[0], ms = te.getAvgMspt();
            if (tps < 18) { println(RED + "  [!] Low TPS (" + f2(tps) + "). Possible: too many entities / chunk load hammering." + RESET); ok = false; }
            if (ms  > 45) { println(YELLOW + "  [!] High MSPT (" + f2(ms) + "ms). Server near capacity." + RESET); ok = false; }
        }
        long pct = 100L * (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / Runtime.getRuntime().maxMemory();
        if (pct > 90) { println(RED + "  [!] Memory critical (" + pct + "%). Increase -Xmx." + RESET); ok = false; }
        else if (pct > 75) { println(YELLOW + "  [W] Memory high (" + pct + "%)." + RESET); ok = false; }
        if (ok) println(GREEN + "  No issues detected. Server healthy." + RESET);
    }

    private void doFlamegraph() {
        println(CYAN + "[LEGA] Flamegraph not wired to profiler backend." + RESET);
        println(DIM + "  Attach async-profiler manually: asprof -d 30 -f flamegraph.html <pid>" + RESET);
    }

    private void doDumpListeners() {
        println(CYAN + "[LEGA] No plugin event listeners registered." + RESET);
    }

    // =========================================================================
    // Spigot / Paper
    // =========================================================================

    private void doTimings(String[] args) {
        if (args.length == 0) { println(CYAN + "Usage: timings <on|off|report|reset|paste>" + RESET); return; }
        switch (args[0].toLowerCase()) {
            case "on"     -> { timingsActive = true;  println(GREEN + "[Timings] Enabled." + RESET); }
            case "off"    -> { timingsActive = false; println(YELLOW + "[Timings] Disabled." + RESET); }
            case "reset"  -> println(YELLOW + "[Timings] Reset." + RESET);
            case "report" -> { println(BOLD + "[Timings] Report:" + RESET);
                               println("  Active: " + (timingsActive ? GREEN + "yes" : RED + "no") + RESET);
                               doTps(); doMspt(); }
            case "paste"  -> println(CYAN + "[Timings] Online paste not implemented." + RESET);
            default       -> println(RED + "[Timings] Unknown: " + args[0] + RESET);
        }
    }

    private void doMobcaps() {
        println(BOLD + CYAN + "═══ Mob Caps ═══" + RESET);
        println("  " + CYAN + "Monster   " + RESET + "70");
        println("  " + CYAN + "Creatures " + RESET + "10");
        println("  " + CYAN + "Aquatic   " + RESET + "5");
        println("  " + CYAN + "Ambient   " + RESET + "15");
        println(DIM + "  (World engine not active — caps are config defaults)" + RESET);
    }

    private void doTick(String[] args) {
        if (args.length == 0) { println(CYAN + "Usage: tick <freeze|unfreeze|step <n>>" + RESET); return; }
        switch (args[0].toLowerCase()) {
            case "freeze"   -> println(YELLOW + "[Tick] Freeze not yet wired to tick engine." + RESET);
            case "unfreeze" -> println(YELLOW + "[Tick] Unfreeze not yet wired to tick engine." + RESET);
            case "step"     -> {
                int n = args.length > 1 ? num(args[1], 1) : 1;
                println(YELLOW + "[Tick] Stepped " + n + " tick(s) (not yet wired)." + RESET);
            }
            default -> println(RED + "[Tick] Unknown: " + args[0] + RESET);
        }
    }

    private void doFixlight(String[] args) {
        println(CYAN + "[LEGA] Relighting '" + (args.length > 0 ? args[0] : "world") + "'... (not yet implemented)" + RESET);
    }

    // =========================================================================
    // World / Save
    // =========================================================================

    private void doWorlds() {
        var cfg = server.getConfiguration();
        String lv = cfg != null ? cfg.getLevelName() : "world";
        println(BOLD + CYAN + "═══ Worlds ═══" + RESET);
        println("  " + GREEN + lv          + RESET + "           NORMAL   overworld");
        println("  " + GREEN + lv + "_nether"  + RESET + "   NETHER   DIM-1");
        println("  " + GREEN + lv + "_the_end" + RESET + "  THE_END  DIM1");
        println(DIM + "  (World engine not active — directory stubs only)" + RESET);
    }

    private void doSaveAll(String[] args) {
        println(GREEN + "[LEGA] " + (args.length > 0 && args[0].equalsIgnoreCase("flush") ? "Flush-save" : "Save-all")
                + " complete (world engine not yet active)." + RESET);
    }

    private void doSaveOff() { saveEnabled.set(false); println(YELLOW + "[LEGA] Auto-save disabled." + RESET); }
    private void doSaveOn()  { saveEnabled.set(true);  println(GREEN  + "[LEGA] Auto-save enabled."  + RESET); }

    // =========================================================================
    // Broadcast
    // =========================================================================

    private void doSay(String[] args) {
        if (args.length == 0) { println(RED + "Usage: say <message>" + RESET); return; }
        String msg = String.join(" ", args);
        println(BOLD + "[Server] " + RESET + msg);
        LOGGER.info("[Broadcast] {}", msg);
    }

    private void doBroadcast(String[] args) { doSay(args); }
    private void doMe(String[] args) { println(BOLD + "* SERVER " + String.join(" ", args) + RESET); }

    // =========================================================================
    // Moderation
    // =========================================================================

    private void doKick(String[] args) {
        if (args.length == 0) { println(RED + "Usage: kick <player> [reason]" + RESET); return; }
        String r = args.length > 1 ? tail(args, 1) : "Kicked by an operator.";
        println(YELLOW + "Kicked " + args[0] + ": " + r + RESET);
        LOGGER.info("[Kick] {} — {}", args[0], r);
    }

    private void doBan(String[] args) {
        if (args.length == 0) { println(RED + "Usage: ban <player> [reason]" + RESET); return; }
        String r = args.length > 1 ? tail(args, 1) : "Banned by an operator.";
        if (!banList.contains(args[0].toLowerCase())) banList.add(args[0].toLowerCase());
        println(RED + "Banned " + args[0] + ": " + r + RESET);
        LOGGER.info("[Ban] {} — {}", args[0], r);
    }

    private void doBanIp(String[] args) {
        if (args.length == 0) { println(RED + "Usage: ban-ip <ip> [reason]" + RESET); return; }
        String r = args.length > 1 ? tail(args, 1) : "Banned by an operator.";
        if (!ipBanList.contains(args[0])) ipBanList.add(args[0]);
        println(RED + "Banned IP " + args[0] + ": " + r + RESET);
        LOGGER.info("[IPBan] {} — {}", args[0], r);
    }

    private void doPardon(String[] args) {
        if (args.length == 0) { println(RED + "Usage: pardon <player>" + RESET); return; }
        println(banList.remove(args[0].toLowerCase())
                ? GREEN + "Unbanned " + args[0] + "." + RESET
                : YELLOW + args[0] + " is not banned." + RESET);
    }

    private void doPardonIp(String[] args) {
        if (args.length == 0) { println(RED + "Usage: pardon-ip <ip>" + RESET); return; }
        println(ipBanList.remove(args[0])
                ? GREEN + "Unbanned IP " + args[0] + "." + RESET
                : YELLOW + args[0] + " is not IP-banned." + RESET);
    }

    private void doBanlist(String[] args) {
        boolean ips = args.length > 0 && args[0].equalsIgnoreCase("ips");
        if (ips) println(CYAN + "Banned IPs (" + ipBanList.size() + "): " + RESET + (ipBanList.isEmpty() ? DIM + "[none]" + RESET : String.join(", ", ipBanList)));
        else     println(CYAN + "Banned players (" + banList.size() + "): " + RESET + (banList.isEmpty() ? DIM + "[none]" + RESET : String.join(", ", banList)));
    }

    // =========================================================================
    // Whitelist
    // =========================================================================

    private void doWhitelist(String[] args) {
        if (args.length == 0) { println(CYAN + "Usage: whitelist <on|off|add|remove|list|reload>" + RESET); return; }
        switch (args[0].toLowerCase()) {
            case "on"     -> { whitelistEnabled.set(true);  println(GREEN + "Whitelist enabled." + RESET); }
            case "off"    -> { whitelistEnabled.set(false); println(YELLOW + "Whitelist disabled." + RESET); }
            case "list"   -> println(CYAN + "Whitelist (" + whitelist.size() + "): " + RESET + (whitelist.isEmpty() ? DIM + "[empty]" + RESET : String.join(", ", whitelist)));
            case "reload" -> println(GREEN + "Whitelist reloaded." + RESET);
            case "add"    -> { if (args.length < 2) { println(RED + "Usage: whitelist add <player>" + RESET); return; }
                               if (!whitelist.contains(args[1])) whitelist.add(args[1]);
                               println(GREEN + "Added " + args[1] + " to whitelist." + RESET); }
            case "remove" -> { if (args.length < 2) { println(RED + "Usage: whitelist remove <player>" + RESET); return; }
                               println(whitelist.remove(args[1])
                                       ? GREEN + "Removed " + args[1] + " from whitelist." + RESET
                                       : YELLOW + args[1] + " is not whitelisted." + RESET); }
            default       -> println(RED + "Unknown whitelist action: " + args[0] + RESET);
        }
    }

    // =========================================================================
    // Ops
    // =========================================================================

    private void doOp(String[] args) {
        if (args.length == 0) { println(RED + "Usage: op <player>" + RESET); return; }
        if (!opList.contains(args[0])) opList.add(args[0]);
        println(GREEN + "Made " + args[0] + " a server operator." + RESET);
        LOGGER.info("[Op] {} is now an operator.", args[0]);
    }

    private void doDeop(String[] args) {
        if (args.length == 0) { println(RED + "Usage: deop <player>" + RESET); return; }
        println(opList.remove(args[0])
                ? YELLOW + "Removed " + args[0] + " from operators." + RESET
                : YELLOW + args[0] + " is not an operator." + RESET);
    }

    private void doOps() {
        println(CYAN + "Operators (" + opList.size() + "): " + RESET + (opList.isEmpty() ? DIM + "[none]" + RESET : String.join(", ", opList)));
    }

    // =========================================================================
    // Misc / LEGA
    // =========================================================================

    private void doDebug() {
        println(BOLD + CYAN + "═══ Debug Info ═══" + RESET);
        println(CYAN + "  Java    " + RESET + System.getProperty("java.version") + " — " + System.getProperty("java.vendor"));
        println(CYAN + "  JVM     " + RESET + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        println(CYAN + "  OS      " + RESET + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        println(CYAN + "  PID     " + RESET + ProcessHandle.current().pid());
        println(CYAN + "  Threads " + RESET + Thread.getAllStackTraces().size());
        println(CYAN + "  Velocity" + RESET + " " + (server.getConfiguration() != null && server.getConfiguration().isVelocityEnabled()
                ? GREEN + "Connected" : YELLOW + "Disabled") + RESET);
        doMemory();
    }

    private void doPing() {
        long ns = System.nanoTime();
        String ignored = System.getProperty("java.version");
        println(GREEN + "Internal latency: " + ((System.nanoTime() - ns) / 1000) + "µs" + RESET);
    }

    private void doCrashReport() {
        println(CYAN + "[LEGA] Writing thread dump to crash-reports/..." + RESET);
        try {
            Path dir = Path.of("crash-reports");
            Files.createDirectories(dir);
            Path f = dir.resolve("dump-" + System.currentTimeMillis() + ".txt");
            StringBuilder sb = new StringBuilder("LEGA Thread Dump — ").append(Instant.now()).append("\n\n");
            Thread.getAllStackTraces().forEach((t, stack) -> {
                sb.append(t.getName()).append(" [").append(t.getState()).append("]\n");
                for (StackTraceElement e : stack) sb.append("  at ").append(e).append("\n");
                sb.append("\n");
            });
            Files.writeString(f, sb.toString());
            println(GREEN + "Written: " + f + RESET);
        } catch (IOException e) { println(RED + "Failed: " + e.getMessage() + RESET); }
    }

    private void doConfig(String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("get")) {
            println(CYAN + "Usage: config get <key>" + RESET); return;
        }
        var cfg = server.getConfiguration();
        if (cfg == null) { println(YELLOW + "Configuration not loaded." + RESET); return; }
        String key = args[1];
        Object val = cfg.get("lega", key, null);
        if (val == null) val = cfg.get("performance", key, null);
        if (val == null) val = cfg.get("security",    key, null);
        println(val != null ? CYAN + key + RESET + " = " + GREEN + val + RESET
                : YELLOW + "Key '" + key + "' not found." + RESET);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void println(String m) { System.out.println(m); }
    private static void sec(String n) { System.out.println("  " + BOLD + YELLOW + n + RESET); }
    private static void cmd(String n, String d) { System.out.printf("    " + CYAN + "%-42s" + RESET + " %s%n", n, d); }
    private static String f2(double v) { return String.format("%.2f", v); }
    private static String f1(double v) { return String.format("%.1f", v); }
    private static String tpsColor(double t) { return t >= 19 ? GREEN : t >= 15 ? YELLOW : RED; }
    private static int    num(String s, int def) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; } }
    private static String tail(String[] a, int from) { return String.join(" ", java.util.Arrays.copyOfRange(a, from, a.length)); }
}