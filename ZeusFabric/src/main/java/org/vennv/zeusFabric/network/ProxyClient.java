package org.vennv.zeusFabric.network;

import org.vennv.PacketEncode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public final class ProxyClient {

    private final DatagramSocket socket;
    private final InetSocketAddress proxyAddress;
    private volatile boolean closed = false;

    public ProxyClient(String host, int port) throws IOException {
        this.socket = new DatagramSocket();
        this.proxyAddress = new InetSocketAddress(host, port);
    }

    public void send(PacketEncode packet) {
        if (closed) {
            return;
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            packet.encode(out);

            byte[] payload = out.toByteArray();

            DatagramPacket udp = new DatagramPacket(
                    payload,
                    payload.length,
                    proxyAddress
            );

            socket.send(udp);
        } catch (IOException e) {
            e.printStackTrace();
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
