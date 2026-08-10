package org.vennv.zeusFabric.provider;

import org.vennv.PacketEncode;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketCollisionWindow.Cell;
import org.vennv.packets.PacketMovementStateSnapshot;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class PacketQueueTest {

    public static void main(String[] args) throws Exception {
        fifoAndDrain();
        overflowRequiresResync();
        atomicPairAdmission();
        genericGroupDoesNotSplitAtBatchLimit();
        collisionWindowCoalescing();
        collisionOrderingAndDeltaChain();
        fullReplacementRemovesQueuedDeltaChain();
        claimedCollisionWindowSurvivesReplacement();
        collisionWindowDoesNotSplitAtBatchLimit();
        collisionWindowRequiresCompleteMetadataGroup();
        collisionWindowReplacementRespectsCapacity();
        collisionWindowOverflowRequiresResync();
        collisionWindowRecoveryPreservesGeneration();
        collisionAndMovementStateRecoverAsOneGroup();
        removeCollisionWindowsPreservesOtherPackets();
        dequeuedBatchInvalidatedByOverflow();
        oversizedResyncStaysDiscontinuous();
        PacketQueue.clear();
    }

    private static void fifoAndDrain() throws Exception {
        PacketQueue.clear();
        PacketEncode first = out -> out.write(1);
        PacketEncode second = out -> out.write(2);
        require(PacketQueue.push(first), "first offer failed");
        require(PacketQueue.push(second), "second offer failed");

        PacketQueue.Batch drained = PacketQueue.pollBatch(2, 1, TimeUnit.MILLISECONDS);
        require(drained != null, "batch missing");
        require(drained.packets().equals(List.of(first, second)), "FIFO order mismatch");
        require(PacketQueue.isEmpty(), "queue not empty after drain");
        require(PacketQueue.beginSend(drained.generation()), "current batch rejected");
        PacketQueue.endSend();
    }

    private static void overflowRequiresResync() throws Exception {
        PacketQueue.clear();
        for (int i = 0; i < PacketQueue.capacity(); i++) {
            require(PacketQueue.push(new EmptyPacket()), "bounded queue rejected early");
        }
        require(!PacketQueue.push(new EmptyPacket()), "bounded queue accepted overflow");
        require(PacketQueue.discontinuityRequired(), "overflow did not mark discontinuity");
        require(PacketQueue.isEmpty(), "untrusted backlog survived overflow");
        require(PacketQueue.droppedCount() == PacketQueue.capacity() + 1L, "overflow drop count mismatch");
        require(!PacketQueue.push(new EmptyPacket()), "ordinary packet passed discontinuity gate");

        PacketEncode firstResync = out -> out.write(3);
        PacketEncode secondResync = out -> out.write(4);
        require(PacketQueue.recoverFromDiscontinuity(() -> {
            require(PacketQueue.push(firstResync), "first resync packet rejected");
            require(PacketQueue.push(secondResync), "second resync packet rejected");
        }), "bounded resync failed");
        require(!PacketQueue.discontinuityRequired(), "resync did not restore continuity");

        PacketQueue.Batch recovered = PacketQueue.pollBatch(PacketQueue.capacity(), 1, TimeUnit.MILLISECONDS);
        require(recovered != null && recovered.packets().equals(List.of(firstResync, secondResync)), "resync order mismatch");
    }

    private static void atomicPairAdmission() throws Exception {
        PacketQueue.clear();
        for (int i = 0; i < PacketQueue.capacity() - 1; i++) {
            require(PacketQueue.push(new EmptyPacket()), "pair capacity seed rejected");
        }
        require(!PacketQueue.pushAll(List.of(new EmptyPacket(), new EmptyPacket())),
                "partial movement/capture pair admitted");
        require(PacketQueue.discontinuityRequired(), "rejected pair did not require resync");
        require(PacketQueue.isEmpty(), "partial pair backlog survived");
        PacketQueue.clear();

        PacketEncode movement = out -> out.write(3);
        PacketEncode capture = out -> out.write(47);
        require(PacketQueue.pushAll(List.of(movement, capture)), "movement/capture pair rejected");
        PacketQueue.Batch pair = PacketQueue.pollBatch(2, 1, TimeUnit.MILLISECONDS);
        require(pair != null && pair.packets().equals(List.of(movement, capture)),
                "movement/capture pair order mismatch");
    }

    private static void genericGroupDoesNotSplitAtBatchLimit() throws Exception {
        PacketQueue.clear();
        PacketEncode prefix = new EmptyPacket();
        PacketEncode first = out -> out.write(1);
        PacketEncode second = out -> out.write(2);
        require(PacketQueue.push(prefix), "generic prefix rejected");
        require(PacketQueue.pushAll(List.of(first, second)), "generic group rejected");
        PacketQueue.Batch prefixBatch = PacketQueue.pollBatch(2, 1, TimeUnit.MILLISECONDS);
        require(prefixBatch != null && prefixBatch.packets().equals(List.of(prefix)),
                "generic group split behind prefix");
        PacketQueue.Batch oversized = PacketQueue.pollBatch(1, 1, TimeUnit.MILLISECONDS);
        require(oversized != null && oversized.packets().equals(List.of(first, second)),
                "first oversized generic group split");
    }

    private static void collisionWindowCoalescing() throws Exception {
        PacketQueue.clear();
        List<PacketCollisionWindow> stale = collisionFragments("u", 1L, 1L, "minecraft:stale_");
        List<PacketCollisionWindow> current = collisionFragments("u", 1L, 2L, "minecraft:current_");
        PacketEncode ordinary = out -> out.write(7);
        require(PacketQueue.pushCollisionWindow("u", 1L, 1L, stale), "stale group rejected");
        require(PacketQueue.push(ordinary), "ordinary packet rejected");
        require(PacketQueue.pushCollisionWindow("u", 1L, 2L, current), "replacement group rejected");

        PacketQueue.Batch batch = PacketQueue.pollBatch(PacketQueue.capacity(), 1, TimeUnit.MILLISECONDS);
        require(batch != null, "coalesced batch missing");
        require(batch.packets().get(0) == ordinary, "ordinary packet order changed");
        require(batch.packets().size() == current.size() + 1, "stale collision group survived");
        for (int index = 0; index < current.size(); index++) {
            require(batch.packets().get(index + 1) == current.get(index), "current fragment order mismatch");
        }
    }

    private static void collisionOrderingAndDeltaChain() throws Exception {
        PacketQueue.clear();
        List<PacketCollisionWindow> full = collisionFragments("u", 10L, 1L, "minecraft:chain_full_");
        List<PacketCollisionWindow> delta = collisionDeltaFragments("u", 10L, 2L, 1L, 0, 1);
        require(PacketQueue.pushCollisionWindow("u", 10L, 1L, full), "chain full rejected");
        require(!PacketQueue.pushCollisionWindow("u", 10L, 1L, full), "equal collision accepted");
        require(!PacketQueue.pushCollisionWindow("u", 9L, 2L,
                collisionFragments("u", 9L, 2L, "minecraft:older_")), "older generation accepted");
        require(!PacketQueue.pushCollisionWindow("u", 10L, 3L,
                collisionDeltaFragments("u", 10L, 3L, 1L, 0, 2)), "delta gap accepted");
        require(PacketQueue.pushCollisionWindow("u", 10L, 2L, delta), "valid delta rejected");
        PacketQueue.Batch batch = PacketQueue.pollBatch(PacketQueue.capacity(), 1, TimeUnit.MILLISECONDS);
        List<PacketEncode> expected = new ArrayList<>(full);
        expected.addAll(delta);
        require(batch != null && batch.packets().equals(expected), "delta predecessor chain changed");
    }

    private static void fullReplacementRemovesQueuedDeltaChain() throws Exception {
        PacketQueue.clear();
        List<PacketCollisionWindow> full = collisionFragments("u", 20L, 1L, "minecraft:replace_full_");
        List<PacketCollisionWindow> delta = collisionDeltaFragments("u", 20L, 2L, 1L, 0, 1);
        List<PacketCollisionWindow> replacement = collisionFragments("u", 21L, 1L, "minecraft:replace_new_");
        require(PacketQueue.pushCollisionWindow("u", 20L, 1L, full), "replacement base rejected");
        require(PacketQueue.pushCollisionWindow("u", 20L, 2L, delta), "replacement delta rejected");
        require(PacketQueue.pushCollisionWindow("u", 21L, 1L, replacement), "newer full rejected");
        PacketQueue.Batch batch = PacketQueue.pollBatch(PacketQueue.capacity(), 1, TimeUnit.MILLISECONDS);
        require(batch != null && batch.packets().equals(new ArrayList<PacketEncode>(replacement)),
                "newer full retained unclaimed predecessor chain");
    }

    private static void claimedCollisionWindowSurvivesReplacement() throws Exception {
        PacketQueue.clear();
        List<PacketCollisionWindow> old = collisionFragments("u", 2L, 1L, "minecraft:claimed_old_");
        List<PacketCollisionWindow> current = collisionFragments("u", 2L, 2L, "minecraft:claimed_current_");
        require(PacketQueue.pushCollisionWindow("u", 2L, 1L, old), "claimed old group rejected");

        PacketQueue.Batch claimed = PacketQueue.pollBatch(1, 1, TimeUnit.MILLISECONDS);
        require(claimed != null && claimed.packets().equals(new ArrayList<PacketEncode>(old)),
                "claimed old group was partial");
        require(PacketQueue.pushCollisionWindow("u", 2L, 2L, current), "claimed replacement rejected");

        PacketQueue.Batch replacement = PacketQueue.pollBatch(PacketQueue.capacity(), 1, TimeUnit.MILLISECONDS);
        require(replacement != null && replacement.packets().equals(new ArrayList<PacketEncode>(current)),
                "replacement did not follow full claimed group");
    }

    private static void collisionWindowDoesNotSplitAtBatchLimit() throws Exception {
        PacketQueue.clear();
        PacketEncode ordinary = new EmptyPacket();
        List<PacketCollisionWindow> collision = collisionFragments("u", 3L, 1L, "minecraft:batch_limit_");
        require(PacketQueue.push(ordinary), "batch limit prefix rejected");
        require(PacketQueue.pushCollisionWindow("u", 3L, 1L, collision), "batch limit collision rejected");

        PacketQueue.Batch prefix = PacketQueue.pollBatch(collision.size(), 1, TimeUnit.MILLISECONDS);
        require(prefix != null && prefix.packets().equals(List.of(ordinary)),
                "collision group split to fill max batch");
        PacketQueue.Batch claimed = PacketQueue.pollBatch(1, 1, TimeUnit.MILLISECONDS);
        require(claimed != null && claimed.packets().equals(new ArrayList<PacketEncode>(collision)),
                "oversized collision group was split");
    }

    private static void collisionWindowRequiresCompleteMetadataGroup() {
        PacketQueue.clear();
        List<PacketCollisionWindow> current = collisionFragments("u", 2L, 3L, "minecraft:complete_");
        List<PacketCollisionWindow> incomplete = new ArrayList<>(current);
        incomplete.remove(incomplete.size() - 1);
        require(!PacketQueue.pushCollisionWindow("u", 2L, 3L, incomplete), "incomplete group accepted");
        require(!PacketQueue.discontinuityRequired(), "invalid input marked discontinuity");
        require(PacketQueue.isEmpty(), "incomplete group partially admitted");

        List<PacketCollisionWindow> mismatched = new ArrayList<>(current);
        List<PacketCollisionWindow> otherMetadata = collisionFragments(
                "u", 2L, 3L, "minecraft:complete_", 2L);
        require(otherMetadata.size() == mismatched.size(), "metadata fixture size mismatch");
        mismatched.set(0, otherMetadata.get(0));
        require(!PacketQueue.pushCollisionWindow("u", 2L, 3L, mismatched), "mixed metadata group accepted");

        List<PacketCollisionWindow> reversed = new ArrayList<>(current);
        Collections.reverse(reversed);
        require(!PacketQueue.pushCollisionWindow("u", 2L, 3L, reversed), "unordered group accepted");
        require(!PacketQueue.pushCollisionWindow("wrong", 2L, 3L, current), "uid mismatch accepted");
        require(!PacketQueue.pushCollisionWindow("u", 2L, 4L, current), "sequence mismatch accepted");
        require(PacketQueue.isEmpty(), "invalid metadata group partially admitted");
    }

    private static void collisionWindowReplacementRespectsCapacity() throws Exception {
        PacketQueue.clear();
        List<PacketCollisionWindow> stale = collisionFragments("u", 3L, 1L, "minecraft:before_");
        List<PacketCollisionWindow> current = collisionFragments("u", 3L, 2L, "minecraft:afterx_");
        require(stale.size() == current.size() && stale.size() > 1, "replacement fixture size mismatch");
        require(PacketQueue.pushCollisionWindow("u", 3L, 1L, stale), "capacity stale group rejected");
        for (int index = stale.size(); index < PacketQueue.capacity(); index++) {
            require(PacketQueue.push(new EmptyPacket()), "capacity filler rejected");
        }
        require(PacketQueue.pushCollisionWindow("u", 3L, 2L, current), "same-size replacement rejected at capacity");
        require(!PacketQueue.discontinuityRequired(), "valid replacement marked discontinuity");
        require(PacketQueue.size() == PacketQueue.capacity(), "replacement changed queue size");

        PacketQueue.Batch batch = PacketQueue.pollBatch(PacketQueue.capacity(), 1, TimeUnit.MILLISECONDS);
        require(batch != null, "replacement batch missing");
        require(batch.packets().subList(batch.packets().size() - current.size(), batch.packets().size()).equals(current),
                "replacement fragments missing or unordered");
    }

    private static void collisionWindowOverflowRequiresResync() {
        PacketQueue.clear();
        List<PacketCollisionWindow> fragments = collisionFragments("u", 4L, 1L, "minecraft:overflow_");
        for (int index = 0; index <= PacketQueue.capacity() - fragments.size(); index++) {
            require(PacketQueue.push(new EmptyPacket()), "collision overflow filler rejected");
        }
        require(!PacketQueue.pushCollisionWindow("u", 4L, 1L, fragments), "oversized replacement admitted");
        require(PacketQueue.discontinuityRequired(), "collision overflow did not require resync");
        require(PacketQueue.isEmpty(), "collision overflow left partial backlog");
    }

    private static void collisionWindowRecoveryPreservesGeneration() throws Exception {
        PacketQueue.clear();
        List<PacketCollisionWindow> current = collisionFragments("u", 5L, 1L, "minecraft:recovery_current_");
        require(PacketQueue.pushCollisionWindow("u", 5L, 1L, current), "recovery current group rejected");
        PacketQueue.markDiscontinuity(current.size());
        require(!PacketQueue.recoverFromDiscontinuity(() -> require(
                !PacketQueue.pushCollisionWindow("u", 5L, 2L,
                        collisionFragments("u", 5L, 2L, "minecraft:recovery_equal_")),
                "equal recovery generation accepted")), "equal generation restored continuity");
        require(PacketQueue.discontinuityRequired(), "equal recovery generation cleared discontinuity");
        List<PacketCollisionWindow> fresh = collisionFragments("u", 6L, 1L, "minecraft:recovery_fresh_");
        List<PacketMovementStateSnapshot> state = movementStateFragments("u", 6L, 1L);
        require(PacketQueue.recoverFromDiscontinuity(() -> require(
                PacketQueue.pushRecovery("u", 6L, 1L, fresh, state), "fresh recovery group rejected")),
                "fresh collision recovery failed");
        PacketQueue.Batch recovered = PacketQueue.pollBatch(PacketQueue.capacity(), 1, TimeUnit.MILLISECONDS);
        List<PacketEncode> expected = new ArrayList<>(fresh);
        expected.addAll(state);
        require(recovered != null && recovered.packets().equals(expected),
                "collision recovery did not publish fresh full");
        require(PacketQueue.beginSend(recovered.generation()), "recovered collision generation invalid");
        PacketQueue.endSend();
    }

    private static void collisionAndMovementStateRecoverAsOneGroup() throws Exception {
        PacketQueue.clear();
        List<PacketCollisionWindow> collision = collisionFragments(
                "u", 30L, 1L, "minecraft:atomic_recovery_");
        List<PacketMovementStateSnapshot> state = movementStateFragments("u", 30L, 1L);

        require(PacketQueue.pushRecovery("u", 30L, 1L, collision, state),
                "atomic collision/state recovery rejected");
        PacketQueue.Batch batch = PacketQueue.pollBatch(1, 1, TimeUnit.MILLISECONDS);
        List<PacketEncode> expected = new ArrayList<>(collision);
        expected.addAll(state);
        require(batch != null && batch.packets().equals(expected),
                "atomic collision/state recovery split or reordered");

        PacketQueue.clear();
        require(!PacketQueue.pushRecovery(
                "u", 30L, 1L, collision, movementStateFragments("u", 30L, 2L)),
                "mismatched movement-state key accepted");
        require(PacketQueue.isEmpty(), "invalid recovery partially queued");
    }

    private static void removeCollisionWindowsPreservesOtherPackets() throws Exception {
        PacketQueue.clear();
        List<PacketCollisionWindow> first = collisionFragments("first", 6L, 1L, "minecraft:first_");
        List<PacketCollisionWindow> second = collisionFragments("second", 6L, 1L, "minecraft:second_");
        PacketEncode ordinary = new EmptyPacket();
        require(PacketQueue.pushCollisionWindow("first", 6L, 1L, first), "first collision group rejected");
        require(PacketQueue.push(ordinary), "ordinary removal fixture rejected");
        require(PacketQueue.pushCollisionWindow("second", 6L, 1L, second), "second collision group rejected");
        PacketQueue.removeCollisionWindows("first");

        PacketQueue.Batch batch = PacketQueue.pollBatch(PacketQueue.capacity(), 1, TimeUnit.MILLISECONDS);
        require(batch != null && batch.packets().size() == second.size() + 1, "collision removal size mismatch");
        require(batch.packets().get(0) == ordinary, "collision removal removed ordinary packet");
        require(batch.packets().subList(1, batch.packets().size()).equals(new ArrayList<PacketEncode>(second)),
                "collision removal removed wrong player");
    }

    private static List<PacketCollisionWindow> collisionFragments(
            String uid,
            long generation,
            long sequence,
            String statePrefix) {
        return collisionFragments(uid, generation, sequence, statePrefix, 1L);
    }

    private static List<PacketCollisionWindow> collisionFragments(
            String uid,
            long generation,
            long sequence,
            String statePrefix,
            long timestamp) {
        List<Cell> cells = new ArrayList<>(PacketCollisionWindow.COLLISION_WINDOW_CELLS);
        for (int index = 0; index < PacketCollisionWindow.COLLISION_WINDOW_CELLS; index++) {
            cells.add(Cell.knownBlock(statePrefix + index));
        }
        List<PacketCollisionWindow> fragments = PacketCollisionWindow.CollisionWindowUpdate.full(
                generation, sequence, 0, 64, 0, cells).toFragments(timestamp, uid, "n");
        require(fragments.size() > 1, "collision fixture did not fragment");
        return fragments;
    }

    private static List<PacketCollisionWindow> collisionDeltaFragments(
            String uid,
            long generation,
            long sequence,
            long baseSequence,
            int baseX,
            int centerX) {
        List<PacketCollisionWindow.CellUpdate> updates = new ArrayList<>();
        for (int index : PacketCollisionWindow.enteringCellIndices(baseX, 64, 0, centerX, 64, 0)) {
            updates.add(new PacketCollisionWindow.CellUpdate(index, Cell.knownBlock("minecraft:delta_" + index)));
        }
        return PacketCollisionWindow.CollisionWindowUpdate.delta(
                generation, sequence, baseSequence, baseX, 64, 0, centerX, 64, 0, updates)
                .toFragments(1L, uid, "n");
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

    private static void dequeuedBatchInvalidatedByOverflow() throws Exception {
        PacketQueue.clear();
        require(PacketQueue.push(new EmptyPacket()), "batch seed rejected");
        PacketQueue.Batch stale = PacketQueue.pollBatch(1, 1, TimeUnit.MILLISECONDS);
        require(stale != null, "stale batch missing");
        PacketQueue.markDiscontinuity(stale.packets().size());
        require(!PacketQueue.beginSend(stale.generation()), "stale batch remained sendable");
        require(PacketQueue.recoverFromDiscontinuity(() -> PacketQueue.push(new EmptyPacket())),
                "recovery after stale batch failed");
        PacketQueue.clear();
    }

    private static void oversizedResyncStaysDiscontinuous() {
        PacketQueue.clear();
        PacketQueue.markDiscontinuity(1L);
        require(!PacketQueue.recoverFromDiscontinuity(() -> {
            for (int i = 0; i <= PacketQueue.capacity(); i++) {
                PacketQueue.push(new EmptyPacket());
            }
        }), "oversized resync unexpectedly succeeded");
        require(PacketQueue.discontinuityRequired(), "oversized resync restored trust");
        require(PacketQueue.isEmpty(), "partial oversized resync escaped");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class EmptyPacket implements PacketEncode {
        @Override
        public void encode(ByteArrayOutputStream out) {
        }
    }
}
