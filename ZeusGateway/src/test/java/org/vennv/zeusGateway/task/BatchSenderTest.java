package org.vennv.zeusGateway.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketEncode;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketCollisionWindow.Cell;
import org.vennv.packets.PacketCollisionWindow.CellUpdate;
import org.vennv.packets.PacketCollisionWindow.CollisionWindowUpdate;
import org.vennv.zeusGateway.provider.PacketQueue;

class BatchSenderTest {

    @AfterEach
    void clearQueue() {
        PacketQueue.clear();
    }

    @Test
    void sendsClaimedGroupsContiguously() throws Exception {
        List<PacketCollisionWindow> old = fullFragments(
                1L, 1L, "minecraft:sender_old_");
        List<PacketCollisionWindow> current = fullFragments(
                1L, 2L, "minecraft:sender_current_");
        assertTrue(PacketQueue.pushCollisionWindow("u", 1L, 1L, old));
        List<PacketEncode> sent = new ArrayList<>();
        BatchSender sender = new BatchSender(packet -> {
            sent.add(packet);
            if (packet == old.get(0)) {
                assertTrue(PacketQueue.pushCollisionWindow("u", 1L, 2L, current));
            }
            if (packet == current.get(current.size() - 1)) Thread.currentThread().interrupt();
            return true;
        }, 1);

        Thread thread = new Thread(sender, "BatchSenderTest-contiguous");
        thread.start();
        thread.join(2_000L);

        assertFalse(thread.isAlive());
        List<PacketEncode> expected = new ArrayList<>(old);
        expected.addAll(current);
        assertEquals(expected, sent);
    }

    @Test
    void genericPushAllRemainsUnsplitAtBatchLimit() throws Exception {
        PacketEncode first = new EmptyPacket();
        PacketEncode second = new EmptyPacket();
        PacketEncode third = new EmptyPacket();
        assertTrue(PacketQueue.pushAll(Arrays.asList(first, second)));
        assertTrue(PacketQueue.push(third));
        List<PacketEncode> sent = new ArrayList<>();
        BatchSender sender = new BatchSender(packet -> {
            sent.add(packet);
            if (packet == third) Thread.currentThread().interrupt();
            return true;
        }, 1);

        Thread thread = new Thread(sender, "BatchSenderTest-generic-group");
        thread.start();
        thread.join(2_000L);

        assertFalse(thread.isAlive());
        assertEquals(Arrays.asList(first, second, third), sent);
    }

    @Test
    void falseMidGroupStopsFragmentsAndRequiresFullRecovery() throws Exception {
        assertMidGroupFailure(false);
    }

    @Test
    void exceptionMidGroupStopsFragmentsAndRequiresFullRecovery() throws Exception {
        assertMidGroupFailure(true);
    }

    private static void assertMidGroupFailure(boolean throwFailure) throws Exception {
        List<PacketCollisionWindow> full = fullFragments(
                2L, 1L, "minecraft:sender_failed_");
        List<PacketCollisionWindow> delta = deltaFragments(2L, 2L, 1L);
        assertTrue(PacketQueue.pushCollisionWindow("u", 2L, 1L, full));
        assertTrue(PacketQueue.pushCollisionWindow("u", 2L, 2L, delta));
        AtomicInteger attempts = new AtomicInteger();
        long failuresBefore = BatchSender.getSendFailureCount();
        BatchSender sender = new BatchSender(packet -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 2) {
                Thread.currentThread().interrupt();
                if (throwFailure) throw new IllegalStateException("simulated send failure");
                return false;
            }
            return true;
        }, 1);

        Thread thread = new Thread(sender, "BatchSenderTest-mid-group-failure");
        thread.start();
        thread.join(2_000L);

        assertFalse(thread.isAlive());
        assertEquals(2, attempts.get());
        assertEquals(failuresBefore + 1, BatchSender.getSendFailureCount());
        assertTrue(PacketQueue.consumeDiscontinuity("u"));
        assertFalse(PacketQueue.push(new PlayerPacket("u")));
        assertFalse(PacketQueue.pushCollisionWindow("u", 2L, 3L, deltaFragments(2L, 3L, 2L)));
        assertTrue(PacketQueue.isEmpty());

        List<PacketCollisionWindow> recovery = fullFragments(
                3L, 1L, "minecraft:sender_recovery_");
        assertTrue(PacketQueue.pushCollisionWindow("u", 3L, 1L, recovery));
        assertTrue(PacketQueue.push(new PlayerPacket("u")));
        List<PacketEncode> sent = new ArrayList<>();
        BatchSender recovered = new BatchSender(packet -> {
            sent.add(packet);
            if (sent.size() == recovery.size() + 1) Thread.currentThread().interrupt();
            return true;
        }, 1);
        Thread recoveredThread = new Thread(recovered, "BatchSenderTest-recovery");
        recoveredThread.start();
        recoveredThread.join(2_000L);

        assertFalse(recoveredThread.isAlive());
        List<PacketEncode> expected = new ArrayList<>(recovery);
        assertEquals(recovery, sent.subList(0, recovery.size()));
        assertEquals(expected.size() + 1, sent.size());
    }

    private static List<PacketCollisionWindow> fullFragments(
            long generation,
            long sequence,
            String statePrefix) {
        List<Cell> cells = new ArrayList<>(PacketCollisionWindow.COLLISION_WINDOW_CELLS);
        for (int index = 0; index < PacketCollisionWindow.COLLISION_WINDOW_CELLS; index++) {
            cells.add(Cell.knownBlock(statePrefix + index));
        }
        List<PacketCollisionWindow> fragments = CollisionWindowUpdate.full(
                generation, sequence, 0, 64, 0, cells).toFragments(1L, "u", "n");
        assertTrue(fragments.size() > 2);
        return fragments;
    }

    private static List<PacketCollisionWindow> deltaFragments(
            long generation,
            long sequence,
            long baseSequence) {
        List<Integer> indices = PacketCollisionWindow.enteringCellIndices(
                0, 64, 0, 1, 64, 0);
        List<CellUpdate> cells = new ArrayList<>(indices.size());
        for (Integer index : indices) cells.add(new CellUpdate(index, Cell.knownAir()));
        return CollisionWindowUpdate.delta(
                generation, sequence, baseSequence,
                0, 64, 0, 1, 64, 0, cells).toFragments(1L, "u", "n");
    }

    private static final class EmptyPacket implements PacketEncode {
        @Override
        public void encode(ByteArrayOutputStream out) throws IOException {
        }
    }

    private static final class PlayerPacket extends PacketBaseInfo {
        private PlayerPacket(String uid) {
            super(1L, uid, "n");
        }

        @Override
        public byte packetId() {
            return 0;
        }

        @Override
        public void encode(ByteArrayOutputStream out) throws IOException {
        }
    }
}
