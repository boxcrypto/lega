package net.lega.security;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.security.antibot.AntiBotSystem;
import net.lega.security.packet.PacketFirewall;
import net.lega.security.ratelimit.RateLimiter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public final class LegaSecurityManager {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Security");

    private final PacketFirewall packetFirewall;
    private final RateLimiter rateLimiter;
    private final AntiBotSystem antiBotSystem;

    // Statistics
    private final AtomicLong blockedPackets        = new AtomicLong(0);
    private final AtomicLong exploitAttempts       = new AtomicLong(0);
    private final AtomicLong botAttemptsBlocked     = new AtomicLong(0);
    private final AtomicLong floodAttemptsBlocked   = new AtomicLong(0);

    // Suspicious IP tracking (ip -> reason)
    private final ConcurrentHashMap<String, String> suspiciousIPs = new ConcurrentHashMap<>();
    // Blocked IPs (permanent ban)
    private final Set<String> blockedIPs = ConcurrentHashMap.newKeySet();

    // Security events log (circular buffer)
    private final SecurityEventLog eventLog = new SecurityEventLog(1000);

    // Dynamic risk score
    private volatile int riskScore = 0;

    public LegaSecurityManager() {
        this.packetFirewall = new PacketFirewall(this);
        this.rateLimiter    = new RateLimiter();
        this.antiBotSystem  = new AntiBotSystem(this);
    }

    
    public void initialize() {
        packetFirewall.initialize();
        rateLimiter.initialize();
        antiBotSystem.initialize();

        LOGGER.info("[LEGA/Security] Security manager initialized.");
        LOGGER.info("[LEGA/Security]   Packet Firewall  : ACTIVE");
        LOGGER.info("[LEGA/Security]   Rate Limiter     : ACTIVE (300 pkt/s per connection)");
        LOGGER.info("[LEGA/Security]   Anti-Bot         : ACTIVE");
        LOGGER.info("[LEGA/Security]   Anti-Crash       : ACTIVE");
        LOGGER.info("[LEGA/Security]   Book-Ban Shield  : ACTIVE");
        LOGGER.info("[LEGA/Security]   Chunk Exploit    : ACTIVE");
        LOGGER.info("[LEGA/Security]   Flood Detection  : ACTIVE");
    }

    // =========================================================================
    // Connection handling
    // =========================================================================

    
    public SecurityDecision onConnectionAttempt(String ip) {
        // Check permanent block list
        if (blockedIPs.contains(ip)) {
            return SecurityDecision.block("IP is permanently blocked");
        }

        // Anti-bot check
        if (antiBotSystem.isFloodDetected(ip)) {
            botAttemptsBlocked.incrementAndGet();
            logEvent(SecurityEvent.Type.BOT_DETECTED, ip, "Bot flood detected");
            return SecurityDecision.block("Bot flood detected");
        }

        return SecurityDecision.allow();
    }

    
    public SecurityDecision onPacket(String ip, int packetId, int size) {
        // Rate limit check
        if (!rateLimiter.checkAndConsume(ip)) {
            blockedPackets.incrementAndGet();
            logEvent(SecurityEvent.Type.RATE_LIMIT, ip, "Rate limit exceeded");
            return SecurityDecision.block("Rate limit exceeded");
        }

        // Firewall check
        SecurityDecision firewallDecision = packetFirewall.inspect(ip, packetId, size);
        if (firewallDecision.isBlocked()) {
            blockedPackets.incrementAndGet();
            exploitAttempts.incrementAndGet();
            logEvent(SecurityEvent.Type.PACKET_EXPLOIT, ip, firewallDecision.getReason());
            suspiciousIPs.put(ip, firewallDecision.getReason());
            return firewallDecision;
        }

        return SecurityDecision.allow();
    }

    
    public void blockIP(String ip, String reason) {
        blockedIPs.add(ip);
        logEvent(SecurityEvent.Type.IP_BLOCKED, ip, reason);
        LOGGER.warn("[LEGA/Security] Blocked IP: {} (reason: {})", ip, reason);
    }

    
    public void unblockIP(String ip) {
        blockedIPs.remove(ip);
        suspiciousIPs.remove(ip);
        LOGGER.info("[LEGA/Security] Unblocked IP: {}", ip);
    }

    // =========================================================================
    // Anti-exploit checks
    // =========================================================================

    
    public boolean isBookSafe(int pageCount, int totalLength) {
        if (pageCount > 100) {
            LOGGER.warn("[LEGA/Security] Book-ban attempt blocked: {} pages", pageCount);
            exploitAttempts.incrementAndGet();
            return false;
        }
        if (totalLength > 12800) { // 100 pages * 128 chars
            LOGGER.warn("[LEGA/Security] Book-ban attempt blocked: {} chars", totalLength);
            exploitAttempts.incrementAndGet();
            return false;
        }
        return true;
    }

    
    public boolean isChunkRequestSafe(int chunkX, int chunkZ) {
        // Minecraft world border is ±30,000,000 blocks = ±1,875,000 chunks
        if (Math.abs(chunkX) > 1_875_000 || Math.abs(chunkZ) > 1_875_000) {
            exploitAttempts.incrementAndGet();
            return false;
        }
        return true;
    }

    // =========================================================================
    // Risk score
    // =========================================================================

    
    public void updateRiskScore() {
        int score = 0;

        long recentExploits = exploitAttempts.get();
        if (recentExploits > 0)   score += Math.min(40, (int)(recentExploits * 2));

        long recentBots = botAttemptsBlocked.get();
        if (recentBots > 0)       score += Math.min(30, (int)(recentBots * 3));

        int suspiciousCount = suspiciousIPs.size();
        if (suspiciousCount > 0)  score += Math.min(20, suspiciousCount * 2);

        riskScore = Math.min(100, score);
    }

    // =========================================================================
    // Internal logging
    // =========================================================================

    private void logEvent(SecurityEvent.Type type, String ip, String detail) {
        SecurityEvent event = new SecurityEvent(type, ip, detail, System.currentTimeMillis());
        eventLog.add(event);
        if (type.isCritical()) {
            LOGGER.warn("[LEGA/Security] {} from {} – {}", type, ip, detail);
        }
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public int getRiskScore()             { return riskScore; }
    public long getBlockedPackets()       { return blockedPackets.get(); }
    public long getExploitAttempts()      { return exploitAttempts.get(); }
    public long getBotAttemptsBlocked()   { return botAttemptsBlocked.get(); }
    public long getFloodAttemptsBlocked() { return floodAttemptsBlocked.get(); }
    public int getSuspiciousIPCount()     { return suspiciousIPs.size(); }
    public Set<String> getBlockedIPs()    { return Collections.unmodifiableSet(blockedIPs); }
    public Map<String, String> getSuspiciousIPs() { return Collections.unmodifiableMap(suspiciousIPs); }
    public List<SecurityEvent> getRecentEvents(int count) { return eventLog.getLast(count); }

    public PacketFirewall getPacketFirewall() { return packetFirewall; }
    public RateLimiter getRateLimiter()       { return rateLimiter; }
    public AntiBotSystem getAntiBotSystem()   { return antiBotSystem; }
}
