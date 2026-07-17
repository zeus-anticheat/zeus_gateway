package org.vennv.zeusGatewayLegacy;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.vennv.PacketEncode;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketCollisionWindow.Cell;
import org.vennv.packets.PacketCollisionWindow.CellType;
import org.vennv.packets.PacketCollisionWindow.Kind;

final class LegacyCollisionWindowProducerTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @AfterEach
    void clearQueue() {
        LegacyPacketQueue.setOverflowHandler(null);
        LegacyPacketQueue.clear();
    }

    @Test
    void samplesExactFloorCenteredNineCubedGeometry() {
        LegacyCollisionWindowProducer.Center center =
                LegacyCollisionWindowProducer.Center.floor(-0.01, -1.01, 15.99);
        assertEquals(-1, center.x);
        assertEquals(-2, center.y);
        assertEquals(15, center.z);
        final Set<String> positions = new HashSet<String>();

        LegacyCollisionWindowProducer.Prepared prepared = LegacyCollisionWindowProducer.prepare(
                null, WORLD, center, true, 10L, new LegacyCollisionWindowProducer.CellSource() {
                    @Override
                    public Cell sample(int x, int y, int z) {
                        positions.add(x + ":" + y + ":" + z);
                        return Cell.knownAir();
                    }
                });

        assertTrue(prepared.full);
        assertEquals(729, prepared.sampledIndices.length);
        assertEquals(729, positions.size());
        assertTrue(positions.contains("-5:-6:11"));
        assertTrue(positions.contains("3:2:19"));
        assertEquals(0, PacketCollisionWindow.collisionWindowIndex(-4, -4, -4));
        assertEquals(364, PacketCollisionWindow.collisionWindowIndex(0, 0, 0));
        assertEquals(728, PacketCollisionWindow.collisionWindowIndex(4, 4, 4));
    }

    @Test
    void fullSnapshotPreservesUnknownAirAndLegacyBlockNames() throws Exception {
        LegacyCollisionWindowProducer.Prepared prepared = LegacyCollisionWindowProducer.prepare(
                null, WORLD, new LegacyCollisionWindowProducer.Center(0, 64, 0), true, 20L,
                new LegacyCollisionWindowProducer.CellSource() {
                    @Override
                    public Cell sample(int x, int y, int z) {
                        int index = PacketCollisionWindow.collisionWindowIndex(x, y - 64, z);
                        if (index == 0) return Cell.unknown();
                        if (index == 1) return Cell.knownAir();
                        return Cell.knownBlock("minecraft:stained_clay");
                    }
                });
        List<PacketCollisionWindow> fragments = prepared.update.toFragments(
                20L, PLAYER.toString(), "legacy", 47);
        PacketCollisionWindow.CollisionWindowUpdate decoded = PacketCollisionWindow.reassemble(fragments);

        assertEquals(Kind.FULL, decoded.getKind());
        assertEquals(729, decoded.getCells().size());
        assertEquals(CellType.UNKNOWN, decoded.getCells().get(0).getCell().getType());
        assertEquals(CellType.KNOWN_AIR, decoded.getCells().get(1).getCell().getType());
        assertEquals("minecraft:stained_clay", decoded.getCells().get(2).getCell().getBlockState());
        for (PacketCollisionWindow fragment : fragments) {
            assertTrue(fragment.encodedDatagramLength() <= 1200);
            assertEquals(Long.valueOf(47L), fragment.getOptionalProtocolVersion());
        }
    }

    @Test
    void integerCenterMovementEmitsValidDeltaAndSameCenterEmitsNothing() throws Exception {
        LegacyCollisionWindowProducer.Prepared full = knownAir(
                null, WORLD, new LegacyCollisionWindowProducer.Center(0, 64, 0), true, 30L);
        assertNull(LegacyCollisionWindowProducer.prepare(
                full.state, WORLD, new LegacyCollisionWindowProducer.Center(0, 64, 0), false, 31L,
                knownAirSource()));

        LegacyCollisionWindowProducer.Prepared delta = knownAir(
                full.state, WORLD, new LegacyCollisionWindowProducer.Center(1, 64, 0), false, 32L);
        PacketCollisionWindow.CollisionWindowUpdate decoded = PacketCollisionWindow.reassemble(
                delta.update.toFragments(32L, PLAYER.toString(), "legacy"));

        assertFalse(delta.full);
        assertEquals(81, delta.sampledIndices.length);
        assertEquals(Kind.DELTA, decoded.getKind());
        assertEquals(full.state.generation, decoded.getGeneration());
        assertEquals(full.state.sequence, decoded.getBaseSequence());
        assertEquals(full.state.sequence + 1L, decoded.getSequence());
        assertEquals(81, decoded.getCells().size());
    }

    @Test
    void lifecycleWorldChangeCreatesNewStrictlyIncreasingGenerationAndFull() {
        LegacyCollisionWindowProducer.Prepared first = knownAir(
                null, WORLD, new LegacyCollisionWindowProducer.Center(0, 64, 0), true, 40L);
        UUID otherWorld = UUID.fromString("00000000-0000-0000-0000-000000000003");
        LegacyCollisionWindowProducer.Prepared changed = knownAir(
                first.state, otherWorld, new LegacyCollisionWindowProducer.Center(0, 64, 0), false, 40L);
        long next = LegacyCollisionWindowProducer.nextGeneration(1L);

        assertTrue(changed.full);
        assertEquals(1L, changed.state.sequence);
        assertTrue(changed.state.generation > first.state.generation);
        assertTrue(next > changed.state.generation);
        assertEquals(otherWorld, changed.state.worldId);
    }

    @Test
    void nonOverlappingMovementFallsBackToFull() {
        LegacyCollisionWindowProducer.Prepared first = knownAir(
                null, WORLD, new LegacyCollisionWindowProducer.Center(0, 64, 0), true, 50L);
        LegacyCollisionWindowProducer.Prepared moved = knownAir(
                first.state, WORLD, new LegacyCollisionWindowProducer.Center(9, 64, 0), false, 51L);

        assertTrue(moved.full);
        assertEquals(729, moved.sampledIndices.length);
        assertEquals(first.state.generation, moved.state.generation);
        assertEquals(first.state.sequence + 1L, moved.state.sequence);
    }

    @Test
    void collisionFragmentsQueueAsImmutableFifoGroup() throws Exception {
        PacketEncode before = new EmptyPacket();
        PacketEncode after = new EmptyPacket();
        List<PacketCollisionWindow> fragments = fragmentedFull(60L, 1L);

        assertTrue(LegacyPacketQueue.push(PLAYER, before));
        assertTrue(LegacyPacketQueue.pushCollision(PLAYER, fragments));
        assertTrue(LegacyPacketQueue.push(PLAYER, after));

        LegacyPacketQueue.PacketGroup first = LegacyPacketQueue.pollGroup();
        LegacyPacketQueue.PacketGroup collision = LegacyPacketQueue.pollGroup();
        LegacyPacketQueue.PacketGroup last = LegacyPacketQueue.pollGroup();
        assertEquals(Arrays.asList(before), first.packets());
        assertEquals(fragments, collision.packets());
        assertTrue(collision.isCollision());
        assertThrows(UnsupportedOperationException.class,
                () -> collision.packets().add(new EmptyPacket()));
        assertEquals(Arrays.asList(after), last.packets());
        assertNull(LegacyPacketQueue.pollGroup());
    }

    @Test
    void collisionAdmissionRejectsIncompleteGroupWithoutPartialQueue() {
        List<PacketCollisionWindow> fragments = fragmentedFull(70L, 1L);
        List<PacketCollisionWindow> incomplete = new ArrayList<PacketCollisionWindow>(fragments);
        incomplete.remove(incomplete.size() - 1);

        assertFalse(LegacyPacketQueue.pushCollision(PLAYER, incomplete));
        assertEquals(0, LegacyPacketQueue.size());
    }

    @Test
    void senderDoesNotSplitOrInterleaveCollisionGroupAtBatchLimit() throws Exception {
        final PacketEncode before = new EmptyPacket();
        final PacketEncode after = new EmptyPacket();
        final List<PacketCollisionWindow> fragments = fragmentedFull(80L, 1L);
        final List<PacketEncode> sent = new ArrayList<PacketEncode>();
        assertTrue(LegacyPacketQueue.push(PLAYER, before));
        assertTrue(LegacyPacketQueue.pushCollision(PLAYER, fragments));
        assertTrue(LegacyPacketQueue.push(PLAYER, after));
        LegacyBatchSender sender = new LegacyBatchSender(new LegacyBatchSender.PacketSender() {
            @Override
            public boolean send(PacketEncode packet) {
                sent.add(packet);
                if (packet == after) Thread.currentThread().interrupt();
                return true;
            }
        }, 1);
        Thread thread = new Thread(sender);

        thread.start();
        thread.join(2000L);

        assertFalse(thread.isAlive());
        List<PacketEncode> expected = new ArrayList<PacketEncode>();
        expected.add(before);
        expected.addAll(fragments);
        expected.add(after);
        assertEquals(expected, sent);
    }

    private static LegacyCollisionWindowProducer.Prepared knownAir(
            LegacyCollisionWindowProducer.State previous,
            UUID world,
            LegacyCollisionWindowProducer.Center center,
            boolean forceFull,
            long timestamp) {
        return LegacyCollisionWindowProducer.prepare(
                previous, world, center, forceFull, timestamp, knownAirSource());
    }

    private static LegacyCollisionWindowProducer.CellSource knownAirSource() {
        return new LegacyCollisionWindowProducer.CellSource() {
            @Override
            public Cell sample(int x, int y, int z) {
                return Cell.knownAir();
            }
        };
    }

    private static List<PacketCollisionWindow> fragmentedFull(long generation, long sequence) {
        List<Cell> cells = new ArrayList<Cell>(PacketCollisionWindow.COLLISION_WINDOW_CELLS);
        for (int index = 0; index < PacketCollisionWindow.COLLISION_WINDOW_CELLS; index++) {
            cells.add(Cell.knownBlock("minecraft:legacy_state_" + index));
        }
        List<PacketCollisionWindow> fragments = PacketCollisionWindow.CollisionWindowUpdate.full(
                generation, sequence, 0, 64, 0, cells).toFragments(
                1L, PLAYER.toString(), "legacy", 47);
        assertTrue(fragments.size() > 1);
        return fragments;
    }

    private static final class EmptyPacket implements PacketEncode {
        @Override
        public void encode(ByteArrayOutputStream out) throws IOException {
        }
    }
}
