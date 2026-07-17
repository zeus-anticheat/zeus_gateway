package org.vennv.zeusGateway.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.vennv.PacketEncode;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketPlayerJoin;

class ProxyClientTest {
    @Test
    void legacyPayloadAcceptsExactLimitAndRejectsOneByteOver() throws Exception {
        AtomicInteger transmitted = new AtomicInteger();
        TestSocket socket = new TestSocket();
        ProxyClient client = new ProxyClient(socket,
                new InetSocketAddress("127.0.0.1", 25555),
                ignored -> transmitted.incrementAndGet());
        PacketEncode exact = out -> out.write(new byte[ProxyClient.MAX_UDP_PAYLOAD]);
        PacketEncode oversized = out -> out.write(new byte[ProxyClient.MAX_UDP_PAYLOAD + 1]);

        assertTrue(client.send(exact));
        assertFalse(client.send(oversized));
        assertEquals(1, socket.sends);
        assertEquals(1, transmitted.get());
        client.close();
    }

    @Test
    void collisionPayloadUsesStrictDatagramLimitWithoutChangingLegacyLimit() throws Exception {
        TestSocket socket = new TestSocket();
        ProxyClient client = new ProxyClient(socket,
                new InetSocketAddress("127.0.0.1", 25555),
                PacketTransmitObserver.NO_OP);
        PacketCollisionWindow exact = mock(PacketCollisionWindow.class);
        PacketCollisionWindow oversized = mock(PacketCollisionWindow.class);
        doAnswer(invocation -> {
            invocation.<ByteArrayOutputStream>getArgument(0)
                    .write(new byte[PacketCollisionWindow.MAX_DATAGRAM_LENGTH]);
            return null;
        }).when(exact).encode(any(ByteArrayOutputStream.class));
        doAnswer(invocation -> {
            invocation.<ByteArrayOutputStream>getArgument(0)
                    .write(new byte[PacketCollisionWindow.MAX_DATAGRAM_LENGTH + 1]);
            return null;
        }).when(oversized).encode(any(ByteArrayOutputStream.class));
        PacketEncode legacy = out -> out.write(
                new byte[PacketCollisionWindow.MAX_DATAGRAM_LENGTH + 1]);

        assertTrue(client.send(exact));
        assertFalse(client.send(oversized));
        assertTrue(client.send(legacy));
        assertEquals(2, socket.sends);
        client.close();
    }

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
        private int sends;

        private TestSocket() throws SocketException {
            super((SocketAddress) null);
        }

        @Override
        public void send(DatagramPacket packet) throws IOException {
            sends++;
            if (fail) {
                throw new IOException("simulated send failure");
            }
        }
    }
}
