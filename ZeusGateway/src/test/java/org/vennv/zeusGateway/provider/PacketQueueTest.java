package org.vennv.zeusGateway.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketEncode;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketCollisionWindow.Cell;
import org.vennv.packets.PacketCollisionWindow.CellUpdate;
import org.vennv.packets.PacketCollisionWindow.CollisionWindowUpdate;
import org.vennv.packets.PacketMovementStateSnapshot;
import org.vennv.packets.PacketCollisionWindow.Kind;

class PacketQueueTest {

    @AfterEach
    void clearQueue() {
        PacketQueue.clear();
    }

    @Test
    void pushAllIsImmutableAtomicAndUnsplit() throws Exception {
        PacketEncode first = new EmptyPacket();
        PacketEncode second = new EmptyPacket();
        List<PacketEncode> source = new ArrayList<>(Arrays.asList(first, second));

        assertTrue(PacketQueue.pushAll(source));
        source.clear();
        PacketQueue.PacketGroup group = PacketQueue.takeGroup();

        assertEquals(Arrays.asList(first, second), group.packets());
        assertThrows(UnsupportedOperationException.class,
                () -> group.packets().add(new EmptyPacket()));
        assertTrue(PacketQueue.isEmpty());
    }

    @Test
    void capacityIsCountedInPacketsAndRejectsWholeGroup() throws Exception {
        for (int index = 0; index < PacketQueue.capacity() - 1; index++) {
            assertTrue(PacketQueue.push(new EmptyPacket()));
        }
        PlayerPacket first = new PlayerPacket("u");
        PlayerPacket second = new PlayerPacket("u");

        assertFalse(PacketQueue.pushAll(Arrays.asList(first, second)));
        assertEquals(PacketQueue.capacity() - 1, PacketQueue.size());
        assertTrue(PacketQueue.consumeDiscontinuity("u"));
        assertFalse(PacketQueue.push(first));
    }

    @Test
    void collisionAdmissionRejectsMixedMetadata() {
        List<PacketCollisionWindow> fragments = fullFragments(
                "u", 1L, 1L, 1L, "minecraft:metadata_");
        List<PacketCollisionWindow> other = fullFragments(
                "u", 1L, 1L, 2L, "minecraft:metadata_");
        assertEquals(fragments.size(), other.size());
        List<PacketCollisionWindow> mixed = new ArrayList<>(fragments);
        mixed.set(0, other.get(0));

        assertFalse(PacketQueue.pushCollisionWindow("u", 1L, 1L, mixed));
        assertTrue(PacketQueue.isEmpty());
        assertFalse(PacketQueue.consumeDiscontinuity("u"));
    }

    @Test
    void claimedCollisionGroupIsUnaffectedByNewerFull() throws Exception {
        List<PacketCollisionWindow> old = new ArrayList<>(fullFragments(
                "u", 2L, 1L, 1L, "minecraft:claimed_old_"));
        List<PacketCollisionWindow> current = fullFragments(
                "u", 2L, 2L, 1L, "minecraft:claimed_current_");
        assertTrue(PacketQueue.pushCollisionWindow("u", 2L, 1L, old));

        PacketQueue.PacketGroup claimed = PacketQueue.takeGroup();
        assertTrue(PacketQueue.pushCollisionWindow("u", 2L, 2L, current));
        old.clear();

        assertEquals(1L, claimed.collisionSequence());
        assertEquals(current, PacketQueue.takeGroup().packets());
        assertFalse(claimed.packets().isEmpty());
    }

    @Test
    void fullDeltaChainIsRetainedWithTrackedKeys() throws Exception {
        List<PacketCollisionWindow> full = fullFragments(
                "u", 3L, 1L, 1L, "minecraft:chain_");
        List<PacketCollisionWindow> delta = deltaFragments("u", 3L, 2L, 1L);

        assertTrue(PacketQueue.pushCollisionWindow("u", 3L, 1L, full));
        assertTrue(PacketQueue.pushCollisionWindow("u", 3L, 2L, delta));

        PacketQueue.PacketGroup predecessor = PacketQueue.takeGroup();
        PacketQueue.PacketGroup successor = PacketQueue.takeGroup();
        assertEquals(full, predecessor.packets());
        assertEquals(delta, successor.packets());
        assertEquals(3L, successor.collisionGeneration());
        assertEquals(2L, successor.collisionSequence());
        assertEquals(Kind.DELTA, successor.collisionKind());
        assertEquals(1L, successor.collisionBaseSequence());
    }

    @Test
    void olderAndEqualCollisionKeysAreRejected() {
        List<PacketCollisionWindow> current = fullFragments(
                "u", 4L, 2L, 1L, "minecraft:current_");
        List<PacketCollisionWindow> equal = fullFragments(
                "u", 4L, 2L, 2L, "minecraft:equal_");
        List<PacketCollisionWindow> older = fullFragments(
                "u", 4L, 1L, 1L, "minecraft:older_");

        assertTrue(PacketQueue.pushCollisionWindow("u", 4L, 2L, current));
        assertFalse(PacketQueue.pushCollisionWindow("u", 4L, 2L, equal));
        assertFalse(PacketQueue.pushCollisionWindow("u", 4L, 1L, older));
        assertEquals(current.size(), PacketQueue.size());
    }

    @Test
    void groupsRemainFifoAcrossPacketTypes() throws Exception {
        PacketEncode first = new EmptyPacket();
        List<PacketCollisionWindow> collision = fullFragments(
                "u", 5L, 1L, 1L, "minecraft:fifo_");
        PacketEncode last = new EmptyPacket();

        assertTrue(PacketQueue.push(first));
        assertTrue(PacketQueue.pushCollisionWindow("u", 5L, 1L, collision));
        assertTrue(PacketQueue.push(last));

        assertEquals(Collections.singletonList(first), PacketQueue.takeGroup().packets());
        assertEquals(collision, PacketQueue.takeGroup().packets());
        assertEquals(Collections.singletonList(last), PacketQueue.takeGroup().packets());
    }

    @Test
    void compatibilityConsumerClaimsWholeGroup() throws Exception {
        PacketEncode first = new EmptyPacket();
        PacketEncode second = new EmptyPacket();
        assertTrue(PacketQueue.pushAll(Arrays.asList(first, second)));

        assertSame(first, PacketQueue.poll());
        Thread competing = new Thread(() -> assertNull(PacketQueue.poll()));
        competing.start();
        competing.join(1_000L);
        assertFalse(competing.isAlive());
        assertSame(second, PacketQueue.poll());
        assertTrue(PacketQueue.isEmpty());
    }

    @Test
    void discontinuityBlocksUidUntilCollisionAndStateRecoveryAreQueuedTogether() throws Exception {
        List<PacketCollisionWindow> full = fullFragments(
                "u", 6L, 1L, 1L, "minecraft:blocked_");
        assertTrue(PacketQueue.pushCollisionWindow("u", 6L, 1L, full));
        PacketQueue.markDiscontinuity("u");

        assertFalse(PacketQueue.push(new PlayerPacket("u")));
        assertFalse(PacketQueue.pushCollisionWindow(
                "u", 6L, 2L, deltaFragments("u", 6L, 2L, 1L)));
        assertTrue(PacketQueue.consumeDiscontinuity("u"));
        assertFalse(PacketQueue.push(new PlayerPacket("u")));

        List<PacketCollisionWindow> recovery = fullFragments(
                "u", 7L, 1L, 1L, "minecraft:recovery_");
        List<PacketMovementStateSnapshot> state = movementStateFragments("u", 7L, 1L);
        assertFalse(PacketQueue.pushCollisionWindow("u", 7L, 1L, recovery));
        assertFalse(PacketQueue.push(state.get(0)));
        assertTrue(PacketQueue.pushRecovery(
                "u", 7L, 1L, recovery, state));
        assertFalse(PacketQueue.consumeDiscontinuity("u"));
        assertTrue(PacketQueue.push(new PlayerPacket("u")));

        List<PacketEncode> expected = new ArrayList<>(recovery);
        expected.addAll(state);
        assertEquals(expected, PacketQueue.takeGroup().packets());
    }

    @Test
    void metricsTrackDepthHighWaterAndRejectedPackets() {
        for (int index = 0; index < PacketQueue.capacity(); index++) {
            assertTrue(PacketQueue.push(new EmptyPacket()));
        }
        assertFalse(PacketQueue.pushAll(Arrays.asList(
                new PlayerPacket("overloaded"), new PlayerPacket("overloaded"))));

        PacketQueue.QueueMetrics metrics = PacketQueue.metricsSnapshot();
        assertEquals(PacketQueue.capacity(), metrics.currentDepth());
        assertEquals(PacketQueue.capacity(), metrics.highWaterMark());
        assertEquals(1L, metrics.rejectedGroups());
        assertEquals(2L, metrics.rejectedPackets());
        assertEquals(1, metrics.blockedUidCount());
        assertEquals(0L, metrics.recoveryCount());
    }

    @Test
    void metricsTrackCompletedFullRecovery() {
        PacketQueue.markDiscontinuity("u");
        assertEquals(1, PacketQueue.metricsSnapshot().blockedUidCount());

        List<PacketCollisionWindow> recovery = fullFragments(
                "u", 10L, 1L, 1L, "minecraft:metrics_recovery_");
        assertTrue(PacketQueue.pushRecovery(
                "u", 10L, 1L, recovery, movementStateFragments("u", 10L, 1L)));

        PacketQueue.QueueMetrics metrics = PacketQueue.metricsSnapshot();
        assertEquals(0, metrics.blockedUidCount());
        assertEquals(1L, metrics.recoveryCount());
    }

    private static List<PacketCollisionWindow> fullFragments(
            String uid,
            long generation,
            long sequence,
            long timestamp,
            String statePrefix) {
        List<Cell> cells = new ArrayList<>(PacketCollisionWindow.COLLISION_WINDOW_CELLS);
        for (int index = 0; index < PacketCollisionWindow.COLLISION_WINDOW_CELLS; index++) {
            cells.add(Cell.knownBlock(statePrefix + index));
        }
        List<PacketCollisionWindow> fragments = CollisionWindowUpdate.full(
                generation, sequence, 0, 64, 0, cells).toFragments(timestamp, uid, "n");
        assertTrue(fragments.size() > 1);
        return fragments;
    }

    private static List<PacketCollisionWindow> deltaFragments(
            String uid,
            long generation,
            long sequence,
            long baseSequence) {
        List<Integer> indices = PacketCollisionWindow.enteringCellIndices(
                0, 64, 0, 1, 64, 0);
        List<CellUpdate> cells = new ArrayList<>(indices.size());
        for (Integer index : indices) cells.add(new CellUpdate(index, Cell.knownAir()));
        return CollisionWindowUpdate.delta(
                generation, sequence, baseSequence,
                0, 64, 0, 1, 64, 0, cells).toFragments(1L, uid, "n");
    }

    private static List<PacketMovementStateSnapshot> movementStateFragments(
            String uid,
            long generation,
            long sequence) {
        return PacketMovementStateSnapshot.createFragments(
                1L,
                uid,
                "n",
                generation,
                sequence,
                PacketMovementStateSnapshot.Snapshot.vanilla(true));
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
