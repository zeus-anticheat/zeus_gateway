package org.vennv.zeusGateway.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.vennv.packets.PacketPlayerJoin;

class ProxyClientTest {
    @Test
    void observerRunsOnlyAfterSuccessfulDatagramSend() throws Exception {
        AtomicInteger transmitted = new AtomicInteger();
        TestSocket socket = new TestSocket();
        ProxyClient client = new ProxyClient(socket,
                new InetSocketAddress("127.0.0.1", 25555),
                ignored -> transmitted.incrementAndGet());
        PacketPlayerJoin packet = new PacketPlayerJoin(1L,
                "b4abb7c5-345a-42c1-8b9d-7a46b1b24432", "Venn");

        socket.fail = true;
        assertFalse(client.send(packet));
        assertEquals(0, transmitted.get());

        socket.fail = false;
        assertTrue(client.send(packet));
        assertEquals(1, transmitted.get());
        client.close();
    }

    private static final class TestSocket extends DatagramSocket {
        private boolean fail;

        private TestSocket() throws SocketException {
            super((SocketAddress) null);
        }

        @Override
        public void send(DatagramPacket packet) throws IOException {
            if (fail) {
                throw new IOException("simulated send failure");
            }
        }
    }
}
