package org.vennv.zeusGatewayLegacy;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.vennv.PacketEncode;

final class LegacyPacketQueue {
    private static final Queue<PacketEncode> QUEUE = new ConcurrentLinkedQueue<PacketEncode>();

    private LegacyPacketQueue() {
    }

    static void push(PacketEncode packet) {
        if (packet != null) {
            QUEUE.add(packet);
        }
    }

    static PacketEncode poll() {
        return QUEUE.poll();
    }
}
