package org.vennv.zeusGateway.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.vennv.PacketEncode;
import org.vennv.packets.PacketChunkData;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketCollisionWindow.Cell;
import org.vennv.zeusGateway.network.ProxyClient;
import org.vennv.zeusGateway.provider.PacketQueue;

class ChunkSyncTaskTest {

    @AfterEach
    void clearState() {
        ChunkSyncTask.clearAll();
        PacketQueue.clear();
    }

    @Test
    void usesCanonicalIndexesAndFloorCenters() {
        assertEquals(0, PacketCollisionWindow.collisionWindowIndex(-4, -4, -4));
        assertEquals(364, PacketCollisionWindow.collisionWindowIndex(0, 0, 0));
        assertEquals(728, PacketCollisionWindow.collisionWindowIndex(4, 4, 4));

        ChunkSyncTask.Center center = ChunkSyncTask.Center.floor(-0.01, -1.01, 15.99);
        assertEquals(-1, center.x);
        assertEquals(-2, center.y);
        assertEquals(15, center.z);
        assertEquals(
                Arrays.toString(new int[] {-5, -6, 11}),
                Arrays.toString(PacketCollisionWindow.collisionWindowPosition(
                        center.x, center.y, center.z, 0)));
        assertEquals(
                Arrays.toString(new int[] {3, 2, 19}),
                Arrays.toString(PacketCollisionWindow.collisionWindowPosition(
                        center.x, center.y, center.z, 728)));
    }

    @Test
    void splitsWindowAcrossOneTwoAndFourChunks() {
        assertRegionLayout(new ChunkSyncTask.Center(8, 64, 8), 1, 729);
        assertRegionLayout(new ChunkSyncTask.Center(15, 64, 8), 2, 729);
        assertRegionLayout(new ChunkSyncTask.Center(15, 64, 15), 4, 729);
        assertRegionLayout(new ChunkSyncTask.Center(-1, 64, -1), 4, 729);
    }

    @Test
    void samplesOnlyEnteringCellsForAxisAndDiagonalShifts() {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        ChunkSyncTask.Center origin = new ChunkSyncTask.Center(0, 64, 0);
        commitKnownAir(playerId, worldId, origin);

        ChunkSyncTask.PreparedUpdate axis = ChunkSyncTask.prepareUpdate(
                playerId, worldId, new ChunkSyncTask.Center(1, 64, 0), false);
        assertNotNull(axis);
        assertFalse(axis.isFull());
        assertEquals(81, axis.sampleIndices().length);
        assertEquals(648, countCells(axis.cells(), PacketCollisionWindow.CellType.KNOWN_AIR));
        assertEquals(81, countCells(axis.cells(), PacketCollisionWindow.CellType.UNKNOWN));

        ChunkSyncTask.clearAll();
        PacketQueue.clear();
        commitKnownAir(playerId, worldId, origin);
        ChunkSyncTask.PreparedUpdate diagonal = ChunkSyncTask.prepareUpdate(
                playerId, worldId, new ChunkSyncTask.Center(1, 64, 1), false);
        assertNotNull(diagonal);
        assertFalse(diagonal.isFull());
        assertEquals(153, diagonal.sampleIndices().length);
        assertEquals(576, countCells(diagonal.cells(), PacketCollisionWindow.CellType.KNOWN_AIR));
        assertEquals(153, countCells(diagonal.cells(), PacketCollisionWindow.CellType.UNKNOWN));
    }

    @Test
    void staleUpdateRemainsCurrentWhenGenerationUnchanged() {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        ChunkSyncTask.PreparedUpdate stale = ChunkSyncTask.prepareUpdate(
                playerId, worldId, new ChunkSyncTask.Center(0, 64, 0), false);
        ChunkSyncTask.PreparedUpdate current = ChunkSyncTask.prepareUpdate(
                playerId, worldId, new ChunkSyncTask.Center(1, 64, 0), false);

        assertNotNull(stale);
        assertNotNull(current);
        assertTrue(ChunkSyncTask.isCurrent(stale));
        assertTrue(ChunkSyncTask.isCurrent(current));
        assertTrue(current.isFull());
        assertTrue(current.generation() > 0);
        assertTrue(current.sequence() > stale.sequence());
    }

    @Test
    void fallsBackToFullWhenCentersDoNotOverlap() {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        commitKnownAir(playerId, worldId, new ChunkSyncTask.Center(0, 64, 0));

        ChunkSyncTask.PreparedUpdate update = ChunkSyncTask.prepareUpdate(
                playerId, worldId, new ChunkSyncTask.Center(9, 64, 0), false);

        assertNotNull(update);
        assertTrue(update.isFull());
        assertEquals(729, update.sampleIndices().length);
        assertEquals(update.center(), update.baseCenter());
    }

    @Test
    void removesStateAndPurgesPendingWork() {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        ChunkSyncTask.PreparedUpdate update = ChunkSyncTask.prepareUpdate(
                playerId, worldId, new ChunkSyncTask.Center(0, 64, 0), false);

        assertTrue(ChunkSyncTask.hasState(playerId));
        ChunkSyncTask.remove(playerId);

        assertFalse(ChunkSyncTask.hasState(playerId));
        assertFalse(ChunkSyncTask.isCurrent(update));
    }

    @Test
    void commitsBoundedCacheOnlyAfterWholeFragmentGroupQueues() throws IOException {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        ChunkSyncTask.Center center = new ChunkSyncTask.Center(15, 64, 15);
        ChunkSyncTask.PreparedUpdate update = ChunkSyncTask.prepareUpdate(
                playerId, worldId, center, true);
        assertNotNull(update);
        for (int index = 0; index < update.cells().length; index++) {
            update.cells()[index] = index % 2 == 0
                    ? Cell.knownAir()
                    : Cell.knownBlock("minecraft:stone");
        }

        assertTrue(ChunkSyncTask.publishPrepared(update, 1L, playerId.toString(), "VennDev"));
        assertTrue(ChunkSyncTask.contains(playerId, worldId, 11, 60, 11));
        assertTrue(ChunkSyncTask.contains(playerId, worldId, 19, 68, 19));
        assertFalse(ChunkSyncTask.contains(playerId, worldId, 20, 68, 19));

        int fragmentCount = 0;
        PacketEncode packet;
        while ((packet = PacketQueue.poll()) != null) {
            PacketCollisionWindow fragment = (PacketCollisionWindow) packet;
            assertTrue(fragment.encodedDatagramLength() <= 1200);
            fragmentCount++;
        }
        assertTrue(fragmentCount >= 1);
        assertEquals(729, update.cells().length);
    }

    @Test
    void collisionQueueCoalescesCompleteUpdatesAndPreservesPriority() {
        List<PacketCollisionWindow> stale = collisionFragments("u", 1, 1);
        List<PacketCollisionWindow> current = collisionFragments("u", 1, 2);
        PacketEncode movement = out -> { };
        PacketChunkData chunk = new PacketChunkData(
                1, "u", "n", 0, 0, true, new ArrayList<>());

        assertTrue(PacketQueue.pushCollisionWindow("u", 1, 1, stale));
        PacketQueue.push(chunk);
        assertTrue(PacketQueue.pushCollisionWindow("u", 1, 2, current));
        PacketQueue.push(movement);

        assertSame(chunk, PacketQueue.poll());
        for (PacketCollisionWindow fragment : current) {
            assertSame(fragment, PacketQueue.poll());
        }
        assertSame(movement, PacketQueue.poll());
        assertNull(PacketQueue.poll());

        List<PacketCollisionWindow> incomplete = new ArrayList<>(current);
        incomplete.remove(incomplete.size() - 1);
        assertFalse(PacketQueue.pushCollisionWindow("u", 1, 2, incomplete));
        assertTrue(PacketQueue.isEmpty());
    }

    @Test
    void collisionQueuePublishesWholeOrderedSetBeforeConsumerRuns() throws Exception {
        List<PacketCollisionWindow> fragments = collisionFragments("u", 2, 3);
        List<PacketEncode> consumed = new ArrayList<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger prematureEmpty = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread consumer = new Thread(() -> {
            started.countDown();
            try {
                for (int index = 0; index < fragments.size(); index++) {
                    consumed.add(PacketQueue.take());
                    if (index + 1 < fragments.size() && PacketQueue.isEmpty()) {
                        prematureEmpty.incrementAndGet();
                    }
                }
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                finished.countDown();
            }
        });
        consumer.start();

        assertTrue(started.await(1, TimeUnit.SECONDS));
        assertTrue(PacketQueue.pushCollisionWindow("u", 2, 3, fragments));
        assertTrue(finished.await(2, TimeUnit.SECONDS));
        assertNull(failure.get());
        assertEquals(0, prematureEmpty.get());
        assertEquals(fragments.size(), consumed.size());
        for (int index = 0; index < fragments.size(); index++) {
            assertSame(fragments.get(index), consumed.get(index));
        }
    }

    @Test
    void batchSenderCountsFailedSendWithoutRetry() throws Exception {
        ProxyClient client = mock(ProxyClient.class);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch attempted = new CountDownLatch(1);
        doAnswer(invocation -> {
            attempts.incrementAndGet();
            attempted.countDown();
            return false;
        }).when(client).send(any(PacketEncode.class));
        long failuresBefore = BatchSender.getSendFailureCount();
        PacketQueue.push(out -> { });
        Thread sender = new Thread(new BatchSender(client, 1));

        sender.start();
        assertTrue(attempted.await(1, TimeUnit.SECONDS));
        sender.interrupt();
        sender.join(1_000);

        assertFalse(sender.isAlive());
        assertEquals(1, attempts.get());
        assertEquals(failuresBefore + 1, BatchSender.getSendFailureCount());
    }

    @Test
    void assemblyCompletesExactlyOnce() {
        AtomicInteger published = new AtomicInteger();
        ChunkSyncTask.Assembly assembly = new ChunkSyncTask.Assembly(4, published::incrementAndGet);

        assembly.complete(2);
        assembly.complete(0);
        assembly.complete(2);
        assembly.complete(3);
        assertEquals(0, published.get());
        assembly.complete(1);
        assembly.complete(1);
        assertEquals(1, published.get());
    }

    @Test
    void sameCenterMovementDoesNotCreateUpdate() {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        commitKnownAir(playerId, worldId, new ChunkSyncTask.Center(-1, 64, 15));

        assertNull(ChunkSyncTask.prepareUpdate(
                playerId, worldId, new ChunkSyncTask.Center(-1, 64, 15), false));
        assertNotNull(ChunkSyncTask.prepareUpdate(
                playerId, worldId, new ChunkSyncTask.Center(0, 64, 15), false));
    }

    private static List<PacketCollisionWindow> collisionFragments(
            String uid,
            long generation,
            long sequence) {
        List<Cell> cells = new ArrayList<>(PacketCollisionWindow.COLLISION_WINDOW_CELLS);
        for (int index = 0; index < PacketCollisionWindow.COLLISION_WINDOW_CELLS; index++) {
            cells.add(Cell.knownBlock("minecraft:test_block_" + index));
        }
        List<PacketCollisionWindow> fragments = PacketCollisionWindow.CollisionWindowUpdate.full(
                generation, sequence, 0, 64, 0, cells).toFragments(1, uid, "n");
        assertTrue(fragments.size() > 1);
        return fragments;
    }

    private static void assertRegionLayout(
            ChunkSyncTask.Center center,
            int expectedRegions,
            int expectedCells) {
        List<ChunkSyncTask.RegionSlice> regions = ChunkSyncTask.splitRegions(
                center, ChunkSyncTask.allCellIndices());
        assertEquals(expectedRegions, regions.size());
        int cells = 0;
        for (ChunkSyncTask.RegionSlice region : regions) cells += region.size();
        assertEquals(expectedCells, cells);
    }

    private static void commitKnownAir(
            UUID playerId,
            UUID worldId,
            ChunkSyncTask.Center center) {
        ChunkSyncTask.PreparedUpdate update = ChunkSyncTask.prepareUpdate(
                playerId, worldId, center, true);
        assertNotNull(update);
        Arrays.fill(update.cells(), Cell.knownAir());
        assertTrue(ChunkSyncTask.publishPrepared(
                update, 1L, playerId.toString(), "VennDev"));
        PacketQueue.clear();
    }

    private static int countCells(Cell[] cells, PacketCollisionWindow.CellType type) {
        int count = 0;
        for (Cell cell : cells) {
            if (cell.getType() == type) count++;
        }
        return count;
    }
}
