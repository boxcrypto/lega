package net.lega.security.packet;

/**
 * @author maatsuh
 * @since  1.0.0
 */

import net.lega.security.LegaSecurityManager;
import net.lega.security.SecurityDecision;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;


public final class PacketFirewall {

    private static final Logger LOGGER = LogManager.getLogger("LEGA/Firewall");

    
    public PacketFirewall(LegaSecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    public void initialize() {
        LOGGER.info("[LEGA/Firewall] Packet firewall ready. Monitoring {} packet types with size limits.",
                PACKET_SIZE_LIMITS.size());
    }

    
    public SecurityDecision inspect(String ip, int packetId, int size) {
        // Global size limit
        if (size > MAX_PACKET_SIZE_BYTES) {
            return SecurityDecision.block(
                    "Oversized packet: ID=" + packetId + " size=" + size + " bytes");
        }

        // Per-packet type size check
        Integer sizeLimit = PACKET_SIZE_LIMITS.get(packetId);
        if (sizeLimit != null && size > sizeLimit) {
            return SecurityDecision.block(
                    "Packet 0x" + Integer.toHexString(packetId)
                            + " exceeds size limit: " + size + " > " + sizeLimit);
        }

        return SecurityDecision.allow();
    }

    
    public boolean isPositionValid(double x, double y, double z) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) return false;
        if (Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) return false;
        // World border check
        if (Math.abs(x) > 3.0E7 || Math.abs(z) > 3.0E7) return false;
        if (y < -2048 || y > 2048) return false;
        return true;
    }
}
