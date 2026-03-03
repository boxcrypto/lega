# LEGA Wiki

> Comprehensive technical reference for LEGA — the high-performance Minecraft server engine.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Module Reference](#2-module-reference)
   - [lega-api](#21-lega-api)
   - [lega-server](#22-lega-server)
   - [lega-async-engine](#23-lega-async-engine)
   - [lega-performance-engine](#24-lega-performance-engine)
   - [lega-security](#25-lega-security)
   - [lega-profiler](#26-lega-profiler)
   - [lega-world-engine](#27-lega-world-engine)
   - [lega-protocol](#28-lega-protocol)
   - [lega-bungeecord-bridge](#29-lega-bungeecord-bridge)
   - [lega-velocity-bridge](#210-lega-velocity-bridge)
3. [Event System](#3-event-system)
4. [Scheduler](#4-scheduler)
5. [World Engine](#5-world-engine)
6. [Performance Engine](#6-performance-engine)
7. [Security System](#7-security-system)
8. [Profiler](#8-profiler)
9. [Velocity Bridge](#9-velocity-bridge)
10. [Configuration](#10-configuration)
11. [Admin Commands](#11-admin-commands)
12. [Performance Tuning Guide](#12-performance-tuning-guide)
13. [Developer Guide](#13-developer-guide)
14. [Startup Scripts & JVM Flags](#14-startup-scripts--jvm-flags)

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         LEGA SERVER                             │
│                                                                 │
│  ┌──────────────┐  ┌────────────────┐  ┌─────────────────────┐ │
│  │  lega-server │  │  lega-api      │  │  lega-async-engine  │ │
│  │  Bootstrap   │  │  Public API    │  │  ForkJoin + VT + Sched│ │
│  │  LegaServer  │  │  EventBus      │  │  LegaEventBusImpl   │ │
│  │  TickEngine  │  │  Scheduler     │  │  LegaTaskHandle     │ │
│  │  Command     │  │  WorldManager  │  └─────────────────────┘ │
│  └──────────────┘  └────────────────┘                           │
│                                                                 │
│  ┌──────────────────┐  ┌───────────────┐  ┌─────────────────┐  │
│  │ lega-performance │  │ lega-security │  │  lega-profiler  │  │
│  │ EntityOptimizer  │  │ PacketFirewall│  │  CPU Tracking   │  │
│  │ ChunkOptimizer   │  │ RateLimiter   │  │  Chunk Heatmap  │  │
│  │ TickOptimizer    │  │ AntiBot       │  │  JSON Export    │  │
│  │ GcMonitor        │  │ EventLog      │  └─────────────────┘  │
│  │ AIOptimizer      │  └───────────────┘                        │
│  └──────────────────┘                                           │
│                                                                 │
│  ┌────────────────────┤  ┌───────────────┤  ┌────────────────┤    │
│  │  lega-protocol    │  │ lega-world-engine│  │lega-bungeecord │    │
│  │  46 MC Versions   │  │ RAM Templates    │  │BungeeCord/WF   │    │
│  │  VersionNegotiator│  │ Instant Reset    │  │Travertine      │    │
│  │  Packet Translation│ │ Async I/O        │  │Velocity v4     │    │
│  └────────────────────┘  └───────────────┘  └────────────────┘    │
│                                                                 │
│  ┌──────────────────────────────────┤                           │
│  │       lega-velocity-bridge                │                           │
│  │  HMAC-SHA256 Forwarding Validation        │                           │
│  │  Cross-Server Teleport                    │                           │
│  │  Plugin Channels                          │                           │
│  └──────────────────────────────────┘                           │
└─────────────────────────────────────────────────────────────────┘
```

### Thread Model

| Thread Pool | Purpose | Size |
|---|---|---|
| Main Thread | World state, tick logic, sync tasks | 1 |
| ForkJoinPool | CPU-bound async work | `nCPU - 1` |
| Virtual Thread Executor | I/O-bound work (DB, disk, network) | Unlimited (JDK Virtual Threads) |
| ScheduledThreadPoolExecutor | Timed/repeating tasks | `nCPU / 2` |

---

## 2. Module Reference

### 2.1 lega-api

The **public plugin API**. Your plugin depends only on this module.

```groovy
dependencies {
    compileOnly("net.lega:lega-api:1.0.0-SNAPSHOT")
}
```

**Key classes:**

| Class | Description |
|---|---|
| `LegaAPI` | Singleton entry point. Use `LegaAPI.getInstance()`. |
| `LegaEventBus` | Register/fire events |
| `LegaEvent` | Base class for all events |
| `LegaScheduler` | Sync and async task scheduling |
| `LegaWorldManager` | Create/reset/destroy world instances |
| `LegaServerInfo` | Real-time server metrics |

---

### 2.2 lega-server

The core server bootstrap and management layer.

**Startup sequence:**
1. `LegaBootstrap.main()` — Validates Java version, prints banner
2. `LegaServer.start()` — Loads config, initialises all subsystems in dependency order
3. `LegaTickEngine.start()` — Enters main tick loop (50ms target)

---

### 2.3 lega-async-engine

Implements `LegaScheduler` and `LegaEventBus` with full concurrency support.

**Async Plugin Guard:** Any task submitted to the main thread that blocks for >5ms emits a warning log identifying the plugin responsible.

**virtual thread usage:**
```java
// All I/O tasks automatically run on Virtual Threads
scheduler.runAsync(() -> {
    // This runs on a Java 21 Virtual Thread
    Files.readAllBytes(somePath); // No OS thread blocked
});
```

---

### 2.4 lega-performance-engine

Five cooperating subsystems controlled by `LegaPerformanceEngine`:

| Subsystem | Responsibility |
|---|---|
| `EntityOptimizer` | Activation ranges, merge, predictive despawn |
| `ChunkOptimizer` | Async load/gen, lighting, throttle levels |
| `TickOptimizer` | Dynamic tick skipping, regionized tick |
| `GcMonitor` | JMX GC notifications, heap pressure alerts |
| `AIServerOptimizer` | Pattern learning — peak hours, chunk heatmap, plugin lag detection |

**Auto-Performance Mode** activates when TPS < 17.0 and suppresses it when TPS > 19.0 for 30s.

---

### 2.5 lega-security

Multi-layer protection stack:

```
Incoming Connection
        │
        ▼
  AntiBotSystem ──── connection flood detection
        │
        ▼
   PacketFirewall ── size + position validation
        │
        ▼
   RateLimiter ───── token bucket per IP
        │
        ▼
  LegaSecurityManager (central decision log)
```

---

### 2.6 lega-profiler

Lightweight in-process profiler. Does **not** use async sampling profilers (no agent required).

**What it tracks:**
- Plugin CPU time per tick phase (via `System.nanoTime()` bookends)
- Tick phase durations (entity tick, chunk tick, redstone, lighting, etc.)
- Chunk access heatmap (hotspot detection)
- Per-tick samples for percentile analysis (p50, p95, p99)

**Export:** JSON to `lega-profiler-output/<timestamp>.json`

---

### 2.7 lega-world-engine

**RAM-based instanced worlds** with near-instant reset:

```
Template Registration
     world-files ──read──► byte[] in RAM (WorldTemplate)

Instance Creation
     WorldTemplate ──Arrays.copyOf──► LegaWorldInstanceImpl (< 5ms)

Instance Reset
     WorldTemplate ──Arrays.copyOf──► existing instance (< 50ms)
```

All disk I/O uses Virtual Threads and Java NIO.

---

### 2.8 lega-protocol

The **multi-version protocol layer**. Handles version negotiation and in-flight
packet translation so that 46 Minecraft versions can connect without any
external compat proxy (no ViaVersion / ViaBackwards / ViaRewind).

**Key classes:**

| Class | Description |
|---|---|
| `MinecraftVersion` | Enum of all 46 supported versions (1.7.10 → 1.21.4) with protocol IDs |
| `VersionAdapterRegistry` | Registry mapping each version to its `VersionAdapter` |
| `VersionNegotiator` | Checks if a client protocol is in the enabled set; resolves fallback |
| `NegotiationResult` | Immutable record stored as a Netty channel attribute after handshake |
| `HandshakePacketDecoder` | Netty `ByteToMessageDecoder`; reads Handshake 0x00; disconnects unsupported protocols; injects `PacketTranslationPipeline` when client ≠ server version |
| `PacketTranslationPipeline` | Netty `ChannelDuplexHandler`; remaps packet IDs and applies translators in chain |
| `TranslationRegistry` | Singleton holding all `PacketTranslator` instances |

**Built-in translators:**

| Translator | Boundary | What it handles |
|---|---|---|
| `ChatPacketTranslator` | 1.19 (protocol 759) | Old unified chat ↔ signed system/overlay chat |
| `ChunkDataTranslator` | 1.18 | 16 sections (y:0–255) ↔ 24 sections (y:−64–319); biome format |
| `EntitySpawnTranslator` | 1.9, 1.14, 1.19 | UUID added, entity spawn merged, head-yaw field |
| `TitlePacketTranslator` | 1.17 | Unified action-based packet ↔ separate title packets |

**Version selection config (`versions.yml`)** — auto-generated on first run:

```yaml
# Generated by LEGA on first startup
accept-eula: false           # set to true to agree to the Minecraft EULA
default-version: "1.21.4"   # the version your server actually runs

enabled-versions:
  1.21.4: true   # enable which client versions may connect
  1.21.0: true
  1.20.4: true
  1.19.4: false  # disabled — server will refuse 1.19.4 clients
  ...
```

The server halts until `accept-eula: true` is set.

---

### 2.9 lega-bungeecord-bridge

Proxy forwarding bridge supporting **all four** major proxy platforms via a
single `proxy.yml` configuration file.

| Platform | Forwarding method | BungeeGuard |
|---|---|---|
| `BUNGEECORD` | NUL-delimited Handshake server-address field | Optional |
| `WATERFALL` | NUL-delimited Handshake server-address field | ✅ Supported |
| `TRAVERTINE` | NUL-delimited Handshake server-address field | ✅ Supported |
| `VELOCITY` | Login Plugin Request/Response (HMAC-SHA256 modern forwarding v4) | N/A |

**Key classes:**

| Class | Description |
|---|---|
| `LegaBungeeCordBridge` | Entry-point; loads `proxy.yml`, validates config, exposes handler factories |
| `BungeeCordBridgeConfig` | Parses `proxy.yml`; holds all settings including `velocity-support.secret` |
| `ProxyPlatform` | Enum: `BUNGEECORD`, `WATERFALL`, `TRAVERTINE`, `VELOCITY` |
| `BungeeCordHandshakeInterceptor` | Netty handler (pre-frame); parses NUL-delimited server-address; verifies BungeeGuard; rewrites handshake |
| `BungeeGuardVerifier` | Constant-time HMAC token verification via `MessageDigest.isEqual()` |
| `VelocityForwardingHandler` | Netty handler (post-frame); sends Login Plugin Request; verifies HMAC-SHA256; parses forwarding payload |
| `VelocityPlayerInfo` | Immutable record: `realIp`, `playerId (UUID)`, `playerName`, `propertiesJson` |

**Pipeline placement:**

```
BungeeCord / Waterfall / Travertine:
  [proxy-interceptor]  ← must be BEFORE frame-decoder (raw stream)
  [frame-decoder]
  [frame-encoder]
  [handshake]

Velocity:
  [frame-decoder]
  [frame-encoder]
  [velocity-forwarding]  ← after framing, before handshake
  [handshake]
```

---

### 2.10 lega-velocity-bridge

Secure proxy integration:

- **Modern Forwarding**: HMAC-SHA256 signed handshakes — no IP spoofing possible
- **Plugin Channels**: `lega:main`, `lega:teleport`, `lega:perms`, `lega:staff`
- **Cross-Server Teleport**: UUID-tracked requests with CompletableFuture result
- **Verified Player Cache**: Caffeine cache (30 min TTL) for validated connections

---

## 3. Event System

### Registering with a Lambda (Recommended)

```java
LegaEventBus bus = LegaAPI.getInstance().getEventBus();

EventRegistration reg = bus.register(PlayerJoinEvent.class, event -> {
    System.out.println(event.getPlayerName() + " joined!");
});

// Later, to remove just this handler:
reg.unregister();
```

### Registering with @Subscribe (Class Listener)

```java
public class MyListener {

    @LegaEventBus.Subscribe
    public void onJoin(PlayerJoinEvent event) {
        System.out.println("join: " + event.getPlayerName());
    }

    @LegaEventBus.Subscribe(priority = EventPriority.HIGHEST)
    public void onJoinHigh(PlayerJoinEvent event) {
        // This runs before the default-priority handler above
    }
}

// Register all @Subscribe methods at once:
bus.registerAll(new MyListener());
```

### Creating a Custom Event

```java
public class ArenaStartEvent extends LegaEvent {

    private final String arenaId;
    private int maxPlayers;

    public ArenaStartEvent(String arenaId, int maxPlayers) {
        this.arenaId = arenaId;
        this.maxPlayers = maxPlayers;
    }

    @Override
    public boolean isCancellable() { return true; }

    public String getArenaId() { return arenaId; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int n) { this.maxPlayers = n; }
}

// Fire it:
ArenaStartEvent event = new ArenaStartEvent("arena-5", 16);
bus.fire(event);
if (!event.isCancelled()) {
    startArena(event.getArenaId(), event.getMaxPlayers());
}
```

### Event Priority Order

Events are dispatched in ascending priority order:

```
LOWEST → LOW → NORMAL → HIGH → HIGHEST → MONITOR
```

`MONITOR` handlers receive the final (possibly cancelled) event and should **never** modify it — use it for logging/statistics only.

### Async Events

```java
bus.fireAsync(event).thenRun(() -> {
    System.out.println("All async handlers completed");
});
```

---

## 4. Scheduler

### One-shot Tasks

```java
LegaScheduler s = LegaAPI.getInstance().getScheduler();

// Async (Virtual Thread)
s.runAsync(() -> {/* ... */});

// Main thread
s.runSync(() -> {/* ... */});

// Delayed (time-based)
s.runSyncLater(() -> {/* ... */}, Duration.ofSeconds(10));
s.runAsyncLater(() -> {/* ... */}, Duration.ofMillis(500));

// Delayed (tick-based)
s.runSyncLaterTicks(() -> {/* ... */}, 40L); // 40 ticks = 2 seconds at 20 TPS
```

### Repeating Tasks

```java
TaskHandle task = s.runAsyncTimer(
    () -> { /* work */ },
    Duration.ofSeconds(5),   // initial delay
    Duration.ofSeconds(30)   // period
);

// Cancel when done
task.cancel();

// Check stats
System.out.println("Ran " + task.getExecutionCount() + " times");
System.out.println("Avg time: " + task.getAverageExecutionTimeNanos() + "ns");
```

### Chaining Async → Main Thread

```java
s.supplyAsync(() -> loadDataFromDatabase())
 .thenCompose(data -> s.runOnMainThread(() -> applyToWorld(data)))
 .exceptionally(e -> { logger.severe(e.getMessage()); return null; });
```

### Checking Current Thread

```java
if (s.isMainThread()) {
    // Safe to call Bukkit/LEGA world APIs
} else {
    s.runSync(() -> { /* bounce to main */ });
}
```

---

## 5. World Engine

### Registering a Template

```java
LegaWorldManager wm = LegaAPI.getInstance().getWorldManager();

// Load a world folder into RAM as a template
wm.registerTemplate("bedwars-island", "worlds/templates/bedwars-island")
  .thenRun(() -> System.out.println("Template ready"))
  .exceptionally(e -> { System.err.println("Failed: " + e); return null; });
```

The template directory is read recursively and stored as a `byte[]` in heap memory.

### Creating Instances

```java
wm.createInstance("bedwars-island", "game-42").thenAccept(instance -> {
    UUID worldUUID = instance.getWorldUUID();
    String name    = instance.getInstanceName();
    // Use worldUUID to operate on the Minecraft world
});
```

### Resetting an Instance (< 50ms)

```java
wm.resetInstance("game-42").thenRun(() -> {
    System.out.println("game-42 reset from template");
});
```

The reset copies the template's `byte[]` using `Arrays.copyOf` — no disk I/O.

### Destroying an Instance

```java
wm.destroyInstance("game-42");
// Internal byte[] is nulled; eligible for GC
```

### Querying Statistics

```java
LegaWorldInstance inst = wm.getInstance("game-42");
LegaWorldInstance.InstanceStats stats = inst.getStats();

System.out.println("Players: "    + stats.playerCount());
System.out.println("Entities: "   + stats.entityCount());
System.out.println("Chunks: "     + stats.loadedChunks());
System.out.println("Uptime ms: "  + stats.uptimeMillis());
System.out.println("Resets: "     + stats.resetCount());
```

---

## 6. Performance Engine

### Auto-Performance Mode

When TPS drops below **17.0**, LEGA automatically:

1. Activates aggressive entity culling (halved activation range)
2. Enables dynamic tick skipping for non-critical systems
3. Sets chunk throttle level to `AGGRESSIVE`
4. Suppresses redstone and lighting updates in low-activity chunks

Auto mode deactivates when TPS exceeds **19.0** for 30 consecutive seconds.

Force toggle via command:
```
/lega performance auto on
/lega performance auto off
```

### Entity Optimizer Tuning

In `performance.yml`:

```yaml
entity-optimizer:
  activation-range: 32        # Blocks from nearest player to activate entity
  merge-range: 2.0            # Merge distance for same-type entities (items, drops)
  predictive-despawn: true    # Despawn entities unlikely to be interacted with
  max-entities-per-chunk: 50  # Hard cap per chunk
```

### Chunk Optimizer Tuning

```yaml
chunk-optimizer:
  async-loading: true         # Load chunks off main thread
  async-lighting: true        # Recalculate lighting off main thread
  async-generation: true      # Generate new chunks off main thread
  view-distance-auto: true    # Reduce view distance under load
  min-view-distance: 4
  max-view-distance: 12
```

### AI Server Optimizer

Learns usage patterns over time:

- **Peak Hour Detection**: Tracks `hourlyPlayerCounts[24]` — during detected peak hours, resources are pre-allocated.
- **Chunk Heatmap**: `ConcurrentHashMap<Long, AtomicInteger>` of chunk access frequency — hot chunks are kept loaded longer, cold chunks unloaded sooner.
- **Plugin Lag Detection**: If a plugin's contribution to tick time exceeds 5ms average over 60 ticks, it appears in `/lega profiler` output as a suspect.

---

## 7. Security System

### Risk Score

`LegaSecurityManager` maintains a rolling risk score (0–100) based on recent security events:

| Event | Score Increase |
|---|---|
| Connection flood detected | +30 |
| Packet exploit attempt | +20 |
| Bot detected | +15 |
| IP blocked | +10 |
| Failed login | +5 |

Score decays by 1 every 10 seconds with no events.

### IP Blocking

```
/lega security block <ip>
/lega security unblock <ip>
/lega security list
```

Blocked IPs are kept in `ConcurrentHashMap<String, String>` (IP → reason).

### Packet Firewall Rules

| Packet Type | Max Size |
|---|---|
| Chat | 256 bytes |
| Book | 8,192 bytes |
| Commands | 512 bytes |
| Custom Payload | 32,767 bytes |

Any packet exceeding its limit is dropped and logged as a `PACKET_EXPLOIT` event.

Position packets with `NaN`, `Infinity`, or coordinates outside world bounds are also rejected.

### Anti-Bot Settings

In `security.yml`:

```yaml
anti-bot:
  enabled: true
  connections-per-second: 5     # Triggers "suspicious" flag
  connections-for-lockdown: 25  # Triggers full lockdown
  lockdown-duration-seconds: 30
  failed-logins-per-minute: 10  # Per IP before block
```

### Book-Ban Protection

LEGA scans book content before it reaches the server for:
- Null bytes
- Control characters
- Pages exceeding 256 characters
- Books exceeding 50 pages

---

## 8. Profiler

### Starting a Profile

```
/lega profiler start
```

Profiling begins capturing tick samples, plugin CPU allocation, and chunk access patterns.

### Stopping and Viewing

```
/lega profiler stop
```

Results summary is printed to console and chat. Full JSON report saved to `lega-profiler-output/`.

### Reading the JSON Report

```json
{
  "durationMillis": 30000,
  "tickCount": 600,
  "averageTickMs": 3.2,
  "p50TickMs": 2.8,
  "p95TickMs": 7.1,
  "p99TickMs": 14.3,
  "pluginCpuPercent": {
    "MyPlugin": 12.4,
    "AnotherPlugin": 3.1
  },
  "topChunkHotspots": [
    { "x": 10, "z": -5, "accesses": 4821 },
    { "x": 11, "z": -5, "accesses": 3204 }
  ]
}
```

### Programmatic Access

```java
LegaProfiler profiler = LegaProfiler.getInstance();

// Record plugin CPU time
long start = System.nanoTime();
// ... your work ...
profiler.recordPluginCpu("MyPlugin", System.nanoTime() - start);

// Generate report
profiler.generateReport().thenAccept(report -> {
    System.out.println("Avg tick: " + report.averageTickMs() + "ms");
});
```

---

## 9. Velocity Bridge

### Enabling Modern Forwarding

All proxy settings live in **`proxy.yml`** in the server root.

#### Velocity

```yaml
# proxy.yml
enabled: true
platform: VELOCITY
reject-direct-connections: true

# Must match 'forwarding-secret' in velocity.toml
velocity-support:
  secret: "your-strong-secret-here"
```

Set `forwarding-mode = modern` in `velocity.toml` (Velocity side).

Incoming connections are validated with HMAC-SHA256 (Login Plugin Request/Response v4):

```java
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
byte[] expected = mac.doFinal(data);
// Constant-time comparison with MessageDigest.isEqual
```

#### BungeeCord / Waterfall / Travertine

```yaml
# proxy.yml
enabled: true
platform: WATERFALL          # BUNGEECORD | WATERFALL | TRAVERTINE
ip-forwarding: true
reject-direct-connections: true

bungee-guard:
  enabled: true              # Waterfall/Travertine only
  tokens:
    - "your-bungee-guard-token"
```

### Cross-Server Teleport

```java
LegaVelocityBridge bridge = LegaVelocityBridge.getInstance();

bridge.requestCrossServerTeleport(playerUUID, "target-server", destination)
      .thenAccept(result -> {
          if (result.success()) {
              player.sendMessage("Teleporting...");
          } else {
              player.sendMessage("Failed: " + result.reason());
          }
      });
```

### Plugin Channels

| Channel | Direction | Purpose |
|---|---|---|
| `lega:main` | Proxy → Server | General control messages |
| `lega:teleport` | Bidirectional | Cross-server teleport requests/results |
| `lega:perms` | Proxy → Server | Proxy permission sync |
| `lega:staff` | Bidirectional | Staff notifications |

---

## 10. Configuration

### File Reference

| File | Purpose |
|---|---|
| `lega.yml` | Core server settings (max players, MOTD, threads) |
| `performance.yml` | All performance tuning knobs |
| `security.yml` | Security system configuration |
| `proxy.yml` | Proxy bridge — platform (BC/WF/TV/Velocity), BungeeGuard tokens, Velocity secret |
| `versions.yml` | Enabled MC client versions + EULA acceptance + default-version |
| `eula.txt` | Minecraft EULA flag (synced with versions.yml on first run) |

### Hot Reload

All four config files support hot reload without restart:

```
/lega reload
```

Changes take effect on the next tick.

### Important lega.yml Options

```yaml
server:
  max-players: 200
  view-distance: 10
  simulation-distance: 8

performance:
  async-plugin-guard: true     # Warn when plugins block the main thread
  async-pathfinding: true      # Move entity AI off main thread

threads:
  event-bus-threads: 4
  async-task-threads: 8        # Override ForkJoinPool size (0 = auto)
```

---

## 11. Admin Commands

All commands require `lega.admin` permission.

| Command | Description |
|---|---|
| `/lega reload` | Hot-reload all config files |
| `/lega performance` | Show performance stats (TPS, MSPT, score) |
| `/lega performance auto on/off` | Toggle auto-performance mode |
| `/lega profiler start` | Begin profiling session |
| `/lega profiler stop` | Stop + display profiling results |
| `/lega security status` | Show security stats and risk score |
| `/lega security block <ip>` | Block an IP address |
| `/lega security unblock <ip>` | Unblock an IP address |
| `/lega world list` | List active world instances |
| `/lega world reset <name>` | Reset a world instance |
| `/lega world destroy <name>` | Destroy a world instance |
| `/lega score` | Display full performance score breakdown |
| `/lega debug` | Toggle debug logging |
| `/lega gc` | Suggest JVM GC run (informational) |
| `/lega threads` | Show thread pool occupancy |
| `/lega version` | Show LEGA version and build info |

---

## 12. Performance Tuning Guide

### Low TPS (< 18)

1. Run `/lega profiler start` for 30 seconds, then `/lega profiler stop`.
2. Check `pluginCpuPercent` in the JSON report — identify the heaviest plugin.
3. If `averageTickMs > 45`, check `/lega score` for bottleneck category.
4. Try reducing `view-distance` in `lega.yml` (each step saves ~10–15% entity/chunk tick overhead).
5. Enable `async-pathfinding: true` in `performance.yml` if entities are the bottleneck.

### Memory Pressure / Lag Spikes

1. Run `/lega threads` to verify GC isn't pausing all threads.
2. Increase `-Xms` to equal `-Xmx` in `start.sh` to avoid GC caused by heap growth.
3. Add `-XX:G1HeapRegionSize=16M` for servers with 16GB+ RAM.
4. Enable `print-gc-stats: true` in `performance.yml` to log GC events.

### Anti-Bot / DDoS

1. Set `connections-per-second` to a realistic peak (e.g. 10 for events).
2. Keep `connections-for-lockdown: 25` unless you expect bursts (e.g. YouTube video release).
3. Monitor `/lega security status` during attacks.

### World Instance Throughput

- Smaller templates = faster resets. Keep templates trimmed to just the arena boundary.
- For high-frequency resets (< 1s), pre-create multiple instances and rotate.
- If heap pressure is high, reduce `max-instances` in `lega.yml`.

---

## 13. Developer Guide

### Adding a Dependency on lega-api

**Gradle (Kotlin DSL):**
```kotlin
repositories {
    maven("https://nexus.lega.net/repository/releases/")
}

dependencies {
    compileOnly("net.lega:lega-api:1.0.0-SNAPSHOT")
}
```

**Maven:**
```xml
<dependency>
    <groupId>net.lega</groupId>
    <artifactId>lega-api</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

### Plugin Entry Point Pattern

LEGA does not use a `plugin.yml` loader by default. You manage plugin lifecycle:

```java
public class MyPlugin {
    private EventRegistration loginHandler;

    public void onEnable() {
        LegaAPI api = LegaAPI.getInstance();
        loginHandler = api.getEventBus().register(PlayerLoginEvent.class, this::handleLogin);
    }

    public void onDisable() {
        loginHandler.unregister();
    }

    private void handleLogin(PlayerLoginEvent event) {
        // ...
    }
}
```

### Thread Safety Rules

| Rule | Details |
|---|---|
| World state | Only modify from main thread or via `scheduler.runSync()` |
| Event handlers | Default priority handlers run on the thread that fired the event |
| Async events | Fired on a Virtual Thread; do not touch world state |
| `ConcurrentHashMap` | Safe to read/write from any thread |
| Bukkit/API objects | Main thread only unless documented otherwise |

### Extending the Event Bus

To add a new built-in event, extend `LegaEvent`:

```java
public final class ChunkPreLoadEvent extends LegaEvent {
    private final int chunkX, chunkZ;
    private boolean forceLoad;

    public ChunkPreLoadEvent(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    @Override public boolean isCancellable() { return true; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public boolean isForceLoad() { return forceLoad; }
    public void setForceLoad(boolean v) { this.forceLoad = v; }
}
```

Then fire it from inside the engine:

```java
ChunkPreLoadEvent event = new ChunkPreLoadEvent(cx, cz);
eventBus.fire(event);
if (!event.isCancelled()) {
    actuallyLoadChunk(cx, cz, event.isForceLoad());
}
```

---

## 14. Startup Scripts & JVM Flags

### Recommended JVM Flags (copied from `start.sh`)

```bash
-Xms4G -Xmx4G
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+ParallelRefProcEnabled
-XX:+UnlockExperimentalVMOptions
-XX:+UseStringDeduplication
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1HeapRegionSize=8M
-XX:G1ReservePercent=20
-XX:G1HeapWastePercent=5
-XX:G1MixedGCCountTarget=4
-XX:InitiatingHeapOccupancyPercent=15
-XX:G1MixedGCLiveThresholdPercent=90
-XX:G1RSetUpdatingPauseTimePercent=5
-XX:SurvivorRatio=32
-XX:+PerfDisableSharedMem
-XX:MaxTenuringThreshold=1
-Dfile.encoding=UTF-8
--enable-preview
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
```

### Flag Explanations

| Flag | Reason |
|---|---|
| `-Xms` = `-Xmx` | Prevents GC heap-growth pauses |
| `G1NewSizePercent=30` | Larger young gen → fewer minor GC pauses |
| `MaxGCPauseMillis=200` | G1 pause target — don't set < 100ms |
| `InitiatingHeapOccupancyPercent=15` | Start mixed GC earlier to avoid emergency collections |
| `--enable-preview` | Required for Java 21 preview features (Virtual Threads fully stable in 21, but some APIs still use preview) |

### Running with Gradle

```bash
cd c:/Users/0fxff/OneDrive/Pictures/test

# Generate Gradle wrapper (first time only)
gradle wrapper --gradle-version 8.6

# Build the fat JAR
./gradlew :lega-server:shadowJar

# Run
java @jvm-flags.txt -jar lega-server/build/libs/lega-server-1.0.0-SNAPSHOT-all.jar
```

---

*LEGA Wiki — last updated 2026. For issues or contributions, open a PR on the repository.*
