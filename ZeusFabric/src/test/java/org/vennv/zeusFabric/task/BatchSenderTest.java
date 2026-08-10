package org.vennv.zeusFabric.task;

import org.vennv.PacketEncode;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketCollisionWindow.Cell;
import org.vennv.packets.PacketMovementStateSnapshot;
import org.vennv.zeusFabric.network.ProxyClient;
import org.vennv.zeusFabric.provider.PacketQueue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class BatchSenderTest {
    public static void main(String[] args) throws Exception {
        stopDrainsAndExits();
        sendFailureRequiresResync();
        claimedCollisionSendsBeforeReplacement();
        collisionFailureRecoversWithFreshGroup();
        collisionDatagramSizeBound();
        legacyDatagramSizeBound();
        PacketQueue.clear();
    }

    private static void stopDrainsAndExits() throws Exception {
        PacketQueue.clear();
        PacketEncode first = new EmptyPacket();
        PacketEncode second = new EmptyPacket();
        require(PacketQueue.push(first), "first packet rejected");
        require(PacketQueue.push(second), "second packet rejected");
        List<PacketEncode> sent = new ArrayList<>();
        BatchSender sender = new BatchSender(packet -> {
            sent.add(packet);
            return true;
        }, 2);
        sender.stop();
        Thread thread = new Thread(sender, "BatchSenderTest-drain");
        thread.start();
        thread.join(2_000L);
        require(!thread.isAlive(), "stopped sender did not exit");
        require(!sender.isRunning(), "sender still marked running");
        require(sent.equals(List.of(first, second)), "sender did not drain in FIFO order");
        require(PacketQueue.isEmpty(), "queue not empty after sender drain");
    }

    private static void sendFailureRequiresResync() throws Exception {
        PacketQueue.clear();
        require(PacketQueue.push(new EmptyPacket()), "failure packet rejected");
        require(PacketQueue.push(new EmptyPacket()), "queued failure packet rejected");
        BatchSender sender = new BatchSender(packet -> false, 2);
        sender.stop();
        Thread thread = new Thread(sender, "BatchSenderTest-failure");
        thread.start();
        thread.join(2_000L);
        require(!thread.isAlive(), "failed sender did not exit");
        require(PacketQueue.discontinuityRequired(), "send failure did not mark discontinuity");
        require(PacketQueue.isEmpty(), "failed batch remained trusted");
    }

    private static void claimedCollisionSendsBeforeReplacement() throws Exception {
        PacketQueue.clear();
        List<PacketCollisionWindow> old = collisionFragments(1L, 1L, "minecraft:sender_old_");
        List<PacketCollisionWindow> current = collisionFragments(1L, 2L, "minecraft:sender_current_");
        require(PacketQueue.pushCollisionWindow("u", 1L, 1L, old), "sender old group rejected");
        List<PacketEncode> sent = new ArrayList<>();
        BatchSender sender = new BatchSender(packet -> {
            sent.add(packet);
            if (packet == old.get(0)) {
                require(PacketQueue.pushCollisionWindow("u", 1L, 2L, current),
                        "replacement during claimed send rejected");
            }
            return true;
        }, 1);
        sender.stop();
        Thread thread = new Thread(sender, "BatchSenderTest-claimed-replacement");
        thread.start();
        thread.join(2_000L);
        require(!thread.isAlive(), "claimed replacement sender did not exit");
        List<PacketEncode> expected = new ArrayList<>(old);
        expected.addAll(current);
        require(sent.equals(expected), "replacement interrupted claimed collision group");
    }

    private static void collisionFailureRecoversWithFreshGroup() throws Exception {
        PacketQueue.clear();
        List<PacketCollisionWindow> old = collisionFragments(2L, 1L, "minecraft:sender_failed_");
        List<PacketCollisionWindow> dependent = collisionDeltaFragments(2L, 2L, 1L);
        require(PacketQueue.pushCollisionWindow("u", 2L, 1L, old), "failed collision group rejected");
        require(PacketQueue.pushCollisionWindow("u", 2L, 2L, dependent), "dependent collision group rejected");
        List<PacketEncode> attempted = new ArrayList<>();
        BatchSender failedSender = new BatchSender(packet -> {
            attempted.add(packet);
            return attempted.size() != 2;
        }, 1);
        failedSender.stop();
        Thread failedThread = new Thread(failedSender, "BatchSenderTest-collision-failure");
        failedThread.start();
        failedThread.join(2_000L);
        require(!failedThread.isAlive(), "failed collision sender did not exit");
        require(attempted.equals(new ArrayList<PacketEncode>(old.subList(0, 2))),
                "collision sender continued after fragment failure");
        require(PacketQueue.discontinuityRequired(), "mid-group failure did not require resync");

        List<PacketCollisionWindow> fresh = collisionFragments(3L, 1L, "minecraft:sender_fresh_");
        List<PacketMovementStateSnapshot> state = movementStateFragments(3L, 1L);
        require(PacketQueue.recoverFromDiscontinuity(() -> require(
                PacketQueue.pushRecovery("u", 3L, 1L, fresh, state), "fresh recovery group rejected")),
                "fresh collision resync failed");
        List<PacketEncode> sent = new ArrayList<>();
        BatchSender recoveredSender = new BatchSender(packet -> {
            sent.add(packet);
            return true;
        }, 1);
        recoveredSender.stop();
        Thread recoveredThread = new Thread(recoveredSender, "BatchSenderTest-collision-recovery");
        recoveredThread.start();
        recoveredThread.join(2_000L);
        require(!recoveredThread.isAlive(), "recovered collision sender did not exit");
        List<PacketEncode> expected = new ArrayList<>(fresh);
        expected.addAll(state);
        require(sent.equals(expected), "fresh collision/state group was not fully sent");
    }

    private static List<PacketCollisionWindow> collisionFragments(
            long generation,
            long sequence,
            String statePrefix) {
        List<Cell> cells = new ArrayList<>(PacketCollisionWindow.COLLISION_WINDOW_CELLS);
        for (int index = 0; index < PacketCollisionWindow.COLLISION_WINDOW_CELLS; index++) {
            cells.add(Cell.knownBlock(statePrefix + index));
        }
        List<PacketCollisionWindow> fragments = PacketCollisionWindow.CollisionWindowUpdate.full(
                generation, sequence, 0, 64, 0, cells).toFragments(1L, "u", "n");
        require(fragments.size() > 2, "sender collision fixture did not fragment");
        return fragments;
    }

    private static List<PacketCollisionWindow> collisionDeltaFragments(
            long generation,
            long sequence,
            long baseSequence) {
        List<PacketCollisionWindow.CellUpdate> updates = new ArrayList<>();
        for (int index : PacketCollisionWindow.enteringCellIndices(0, 64, 0, 1, 64, 0)) {
            updates.add(new PacketCollisionWindow.CellUpdate(index, Cell.knownBlock("minecraft:sender_delta_" + index)));
        }
        return PacketCollisionWindow.CollisionWindowUpdate.delta(
                generation, sequence, baseSequence, 0, 64, 0, 1, 64, 0, updates)
                .toFragments(1L, "u", "n");
    }

    private static List<PacketMovementStateSnapshot> movementStateFragments(
            long generation,
            long sequence) {
        return PacketMovementStateSnapshot.createFragments(
                1L,
                "u",
                "n",
                generation,
                sequence,
                PacketMovementStateSnapshot.Snapshot.vanilla(true));
    }

    private static void collisionDatagramSizeBound() throws Exception {
        List<Cell> cells = new ArrayList<>(PacketCollisionWindow.COLLISION_WINDOW_CELLS);
        for (int index = 0; index < PacketCollisionWindow.COLLISION_WINDOW_CELLS; index++) {
            cells.add(Cell.knownBlock("minecraft:collision_size_" + index));
        }
        List<PacketCollisionWindow> fragments = PacketCollisionWindow.CollisionWindowUpdate.full(
                1L, 1L, 0, 64, 0, cells).toFragments(1L, "u", "n");
        require(fragments.size() > 1, "collision size fixture did not fragment");
        boolean reachedLimit = false;
        ProxyClient client = new ProxyClient("127.0.0.1", 9);
        try {
            for (PacketCollisionWindow fragment : fragments) {
                int length = fragment.encodedDatagramLength();
                require(length <= PacketCollisionWindow.MAX_DATAGRAM_LENGTH,
                        "collision datagram exceeded 1200 bytes");
                reachedLimit |= length == PacketCollisionWindow.MAX_DATAGRAM_LENGTH;
                require(client.send(fragment), "valid collision datagram rejected");
            }
        } finally {
            client.close();
        }
        require(reachedLimit, "collision fixture did not exercise 1200-byte boundary");
    }

    private static void legacyDatagramSizeBound() throws Exception {
        ProxyClient client = new ProxyClient("127.0.0.1", 9);
        try {
            require(client.send(new SizedPacket(1_201)), "legacy packet incorrectly used collision bound");
            require(!client.send(new SizedPacket(65_508)), "oversized legacy datagram accepted");
        } finally {
            client.close();
        }
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

    private static final class SizedPacket implements PacketEncode {
        private final int size;

        private SizedPacket(int size) {
            this.size = size;
        }

        @Override
        public void encode(ByteArrayOutputStream out) {
            out.writeBytes(new byte[size]);
        }
    }
}
