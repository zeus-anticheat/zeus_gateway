package org.vennv.zeusGatewayLegacy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import org.vennv.PacketEncode;

final class LegacyProxyClient {
    private static final Logger LOGGER = Logger.getLogger("ZeusGatewayLegacy");

    private final DatagramSocket socket;
    private final InetSocketAddress proxyAddress;
    private boolean suppressErrors;

    LegacyProxyClient(String host, int port) throws IOException {
        if (host == null || host.trim().isEmpty()) {
            throw new IOException("proxy host is empty");
        }
        if ("0.0.0.0".equals(host)) {
            host = "127.0.0.1";
        }
        proxyAddress = new InetSocketAddress(host, port);
        if (proxyAddress.isUnresolved()) {
            throw new IOException("cannot resolve proxy host: " + host);
        }
        socket = new DatagramSocket();
        LOGGER.info("[ZeusGatewayLegacy] ProxyClient initialized, sending to " + host + ":" + port);
    }

    boolean send(PacketEncode packet) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            packet.encode(out);
            byte[] payload = out.toByteArray();
            DatagramPacket datagram = new DatagramPacket(payload, payload.length, proxyAddress);
            socket.send(datagram);
            if (suppressErrors) {
                LOGGER.info("[ZeusGatewayLegacy] UDP transmission restored to " + proxyAddress);
                suppressErrors = false;
            }
            return true;
        } catch (IOException e) {
            if (!suppressErrors) {
                LOGGER.warning("[ZeusGatewayLegacy] Failed to send packet to " + proxyAddress + ": " + e.getMessage());
                suppressErrors = true;
            }
            return false;
        }
    }

    void close() {
        if (!socket.isClosed()) {
            socket.close();
        }
    }
}
