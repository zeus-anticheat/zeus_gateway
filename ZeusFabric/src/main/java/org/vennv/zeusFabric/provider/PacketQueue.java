package org.vennv.zeusFabric.provider;

import org.vennv.PacketEncode;

import java.util.concurrent.LinkedBlockingQueue;

public final class PacketQueue {

    private static final LinkedBlockingQueue<PacketEncode> QUEUE =
            new LinkedBlockingQueue<>();

    public static void push(PacketEncode packet) {
        QUEUE.add(packet);
    }

    public static PacketEncode poll() {
        return QUEUE.poll();
    }

    public static PacketEncode take() throws InterruptedException {
        return QUEUE.take();
    }

    public static boolean isEmpty() {
        return QUEUE.isEmpty();
    }

    public static int size() {
        return QUEUE.size();
    }

    public static void clear() {
        QUEUE.clear();
    }
}
