package org.vennv.zeusGateway.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import org.vennv.PacketEncode;
import org.vennv.packets.PacketCollisionWindow;

public final class ProxyClient {

    static final int MAX_UDP_PAYLOAD = 65_507;
    private static final Logger LOGGER = Logger.getLogger("ZeusGateway");

    private final DatagramSocket socket;
    private final InetSocketAddress proxyAddress;
    private final PacketTransmitObserver observer;
    private boolean suppressErrors = false;

    public ProxyClient(String host, int port) throws IOException {
        this(host, port, PacketTransmitObserver.NO_OP);
    }

    public ProxyClient(String host, int port, PacketTransmitObserver observer) throws IOException {
        if (host == null || host.isEmpty()) {
            throw new IOException(
                "[ZeusGateway] Proxy host is null or empty. Check your config.yml."
            );
        }

        // 0.0.0.0 is a bind/listen address, not a valid send destination.
        // Auto-correct to 127.0.0.1 so packets actually reach the local proxy.
        if ("0.0.0.0".equals(host)) {
            LOGGER.warning(
                "[ZeusGateway] Proxy host is set to 0.0.0.0, which is not a valid send destination. " +
                    "Automatically using 127.0.0.1 instead. Please update your config.yml to set host: 127.0.0.1"
            );
            host = "127.0.0.1";
        }

        this.proxyAddress = new InetSocketAddress(host, port);

        if (this.proxyAddress.isUnresolved()) {
            throw new IOException(
                "[ZeusGateway] Cannot resolve proxy host: " +
                    host +
                    ". Check your config.yml proxy-ac.host value."
            );
        }

        this.socket = new DatagramSocket(); // random local port
        this.observer = observer == null ? PacketTransmitObserver.NO_OP : observer;
        LOGGER.info(
            "[ZeusGateway] ProxyClient initialized, sending to " +
                host +
                ":" +
                port
        );
    }

    ProxyClient(
        DatagramSocket socket,
        InetSocketAddress proxyAddress,
        PacketTransmitObserver observer
    ) {
        this.socket = socket;
        this.proxyAddress = proxyAddress;
        this.observer = observer == null ? PacketTransmitObserver.NO_OP : observer;
    }

    public boolean send(PacketEncode packet) {
        java.util.logging.Logger.getLogger("ZeusGateway").severe("[TRACE] ProxyClient.send: packetClass=" + packet.getClass().getSimpleName());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            packet.encode(out);

            byte[] payload = out.toByteArray();
            int maxPayload = packet instanceof PacketCollisionWindow
                    ? PacketCollisionWindow.MAX_DATAGRAM_LENGTH
                    : MAX_UDP_PAYLOAD;
            if (payload.length > maxPayload) {
                if (!suppressErrors) {
                    LOGGER.warning("[ZeusGateway] Refusing oversized UDP payload: " + payload.length + " bytes");
                }
                return false;
            }

            DatagramPacket udp = new DatagramPacket(
                payload,
                payload.length,
                proxyAddress
            );

            socket.send(udp);

            try {
                observer.onPacketTransmitted(packet);
            } catch (RuntimeException e) {
                LOGGER.warning(
                    "[ZeusGateway] Packet transmit observer failed: " + e.getMessage()
                );
            }

            // Reset error suppression on successful send
            if (suppressErrors) {
                LOGGER.info(
                    "[ZeusGateway] ProxyClient connection restored to " +
                        proxyAddress
                );
                suppressErrors = false;
            }
            return true;
        } catch (BindException e) {
            if (!suppressErrors) {
                LOGGER.severe(
                    "[ZeusGateway] Cannot send to " +
                        proxyAddress +
                        " — BindException: " +
                        e.getMessage() +
                        ". Ensure the proxy host in config.yml is a valid destination (e.g. 127.0.0.1), not a bind address (0.0.0.0)."
                );
                LOGGER.severe(
                    "[ZeusGateway] Suppressing further send errors until connection is restored."
                );
                suppressErrors = true;
            }
            return false;
        } catch (IOException e) {
            if (!suppressErrors) {
                LOGGER.warning(
                    "[ZeusGateway] Failed to send packet to " +
                        proxyAddress +
                        ": " +
                        e.getMessage()
                );
                LOGGER.warning(
                    "[ZeusGateway] Suppressing further send errors until connection is restored."
                );
                suppressErrors = true;
            }
            return false;
        }
    }

    public void close() {
        if (!socket.isClosed()) {
            socket.close();
            LOGGER.info("[ZeusGateway] ProxyClient socket closed.");
        }
    }
}
