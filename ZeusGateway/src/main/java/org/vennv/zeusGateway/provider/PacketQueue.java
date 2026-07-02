package org.vennv.zeusGateway.provider;

import org.vennv.PacketBase;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketEncode;
import org.vennv.PacketId;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class PacketQueue {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final PriorityBlockingQueue<QueuedPacket> QUEUE =
            new PriorityBlockingQueue<>();

    public static void push(PacketEncode packet) {
        QUEUE.add(new QueuedPacket(priority(packet), timestamp(packet), SEQUENCE.getAndIncrement(), packet));
    }

    public static PacketEncode poll() {
        QueuedPacket queued = QUEUE.poll();
        return queued == null ? null : queued.packet;
    }

    public static PacketEncode take() throws InterruptedException {
        return QUEUE.take().packet;
    }

    public static boolean isEmpty() {
        return QUEUE.isEmpty();
    }

    private static int priority(PacketEncode packet) {
        if (!(packet instanceof PacketBase)) {
            return 2;
        }
        byte id = ((PacketBase) packet).packetId();
        switch (id) {
            case PacketId.PACKET_PLAYER_JOIN:
            case PacketId.PACKET_PLAYER_LEAVE:
            case PacketId.PACKET_PLAYER_TELEPORT:
            case PacketId.PACKET_PLAYER_POSITION:
            case PacketId.PACKET_PLAYER_VEHICLE_MOVE:
            case PacketId.PACKET_PLAYER_INPUT:
            case PacketId.PACKET_PLAYER_VELOCITY:
            case PacketId.PACKET_PLAYER_EXTERNAL_FORCE:
            case PacketId.PACKET_SERVER_BOUND_PLAYER_COMMAND:
                return 0;
            case PacketId.PACKET_CHUNK_DATA:
            case PacketId.PACKET_BLOCK_CHANGE_EVENT:
                return 0;
            default:
                return 2;
        }
    }

    private static long timestamp(PacketEncode packet) {
        return packet instanceof PacketBaseInfo ? ((PacketBaseInfo) packet).getTimestamp() : Long.MAX_VALUE;
    }

    private static final class QueuedPacket implements Comparable<QueuedPacket> {
        private final int priority;
        private final long timestamp;
        private final long sequence;
        private final PacketEncode packet;

        private QueuedPacket(int priority, long timestamp, long sequence, PacketEncode packet) {
            this.priority = priority;
            this.timestamp = timestamp;
            this.sequence = sequence;
            this.packet = packet;
        }

        @Override
        public int compareTo(QueuedPacket other) {
            int priorityCompare = Integer.compare(priority, other.priority);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            int timestampCompare = Long.compare(timestamp, other.timestamp);
            if (timestampCompare != 0) {
                return timestampCompare;
            }
            return Long.compare(sequence, other.sequence);
        }
    }
}
