package net.lega.protocol.translation;

/**
 * Direction of packet travel relative to the server.
 *
 * @author maatsuh
 * @since  1.0.0
 */
public enum TranslationDirection {
    /**
     * Packet is travelling from the <em>client</em> to the server
     * (C→S / serverbound). Translate from the client's format to the
     * server's internal format.
     */
    SERVERBOUND,

    /**
     * Packet is travelling from the server to the <em>client</em>
     * (S→C / clientbound). Translate from the server's internal format
     * to the format expected by the connecting client version.
     */
    CLIENTBOUND
}
