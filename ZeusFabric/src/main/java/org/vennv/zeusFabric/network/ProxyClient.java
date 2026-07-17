package org.vennv.zeusFabric.network;

import org.vennv.PacketEncode;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.zeusFabric.ZeusFabricMod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public final class ProxyClient {
    private static final int MAX_UDP_PAYLOAD = 65_507;

    private final DatagramSocket socket;
    private final InetSocketAddress proxyAddress;
    private volatile boolean closed = false;

    public ProxyClient(String host, int port) throws IOException {
        if (host == null || host.isEmpty()) {
            throw new IOException("[ZeusFabric] Proxy host is null or empty. Check config/zeusfabric.properties.");
        }
        if ("0.0.0.0".equals(host)) {
            ZeusFabricMod.LOGGER.warn("[ZeusFabric] proxy-host=0.0.0.0 is not a send destination; using 127.0.0.1 instead.");
            host = "127.0.0.1";
        }
        this.proxyAddress = new InetSocketAddress(host, port);
        if (this.proxyAddress.isUnresolved()) {
            throw new IOException("[ZeusFabric] Cannot resolve proxy host: " + host);
        }
        this.socket = new DatagramSocket();
    }

    public boolean send(PacketEncode packet) {
        if (closed) {
            return false;
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            packet.encode(out);

            byte[] payload = out.toByteArray();
            int maxPayload = packet instanceof PacketCollisionWindow
                    ? PacketCollisionWindow.MAX_DATAGRAM_LENGTH
                    : MAX_UDP_PAYLOAD;
            if (payload.length > maxPayload) {
                ZeusFabricMod.LOGGER.warn(
                        "[ZeusFabric] Refusing oversized UDP payload: {} bytes", payload.length);
                return false;
            }

            DatagramPacket udp = new DatagramPacket(
                    payload,
                    payload.length,
                    proxyAddress
            );

            socket.send(udp);
            return true;
        } catch (IOException e) {
            ZeusFabricMod.LOGGER.error("[ZeusFabric] Failed to send UDP payload", e);
            return false;
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public void close() {
        closed = true;
        if (!socket.isClosed()) {
            socket.close();
        }
    }
}
