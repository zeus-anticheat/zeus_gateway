package org.vennv.zeusFabric.provider;

import org.vennv.PacketEncode;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketMovementStateSnapshot;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class PacketQueue {

    public record Batch(long generation, List<PacketEncode> packets) {}

    private record CollisionMetadata(
            String uid,
            long generation,
            long sequence,
            PacketCollisionWindow.Kind kind,
            long baseSequence) {}

    private record PacketGroup(
            long generation,
            CollisionMetadata collision,
            List<PacketEncode> packets) {
        private PacketGroup {
            packets = List.copyOf(packets);
        }

        private int size() {
            return packets.size();
        }
    }

    private static final int CAPACITY = Math.max(1, Math.min(1_000_000, Integer.getInteger("zeus.queue.capacity", 8192)));
    private static final Deque<PacketGroup> QUEUE = new ArrayDeque<>();
    private static final AtomicLong DROPPED = new AtomicLong();
    private static final Object STATE_LOCK = new Object();
    private static long generation;
    private static int queuedPackets;
    private static int sendsInFlight;
    private static boolean discontinuityRequired;
    private static final Map<String, CollisionMetadata> COLLISION_HIGH_WATER = new HashMap<>();
    private static final Map<String, Long> COLLISION_RECOVERY_FLOORS = new HashMap<>();
    private static Thread resyncProducer;
    private static List<PacketGroup> resyncGroups;
    private static int resyncPacketCount;

    public static boolean push(PacketEncode packet) {
        return pushAll(List.of(packet));
    }

    public static boolean pushAll(List<? extends PacketEncode> packets) {
        Objects.requireNonNull(packets, "packets");
        if (packets.isEmpty()) {
            return true;
        }
        packets.forEach(packet -> Objects.requireNonNull(packet, "packet"));
        synchronized (STATE_LOCK) {
            if (resyncProducer != null && resyncProducer != Thread.currentThread()) {
                markDiscontinuityLocked(packets.size());
                return false;
            }
            if (discontinuityRequired) {
                DROPPED.addAndGet(packets.size());
                return false;
            }
            PacketGroup group = new PacketGroup(generation, null, new ArrayList<>(packets));
            if (resyncProducer == Thread.currentThread()) {
                if (resyncPacketCount + group.size() > CAPACITY) {
                    markDiscontinuityLocked(group.size());
                    return false;
                }
                resyncGroups.add(group);
                resyncPacketCount += group.size();
                return true;
            }
            if (queuedPackets + group.size() <= CAPACITY) {
                QUEUE.addLast(group);
                queuedPackets += group.size();
                STATE_LOCK.notifyAll();
                return true;
            }
            markDiscontinuityLocked(group.size());
            return false;
        }
    }

    public static boolean pushCollisionWindow(
            String uid,
            long generation,
            long sequence,
            List<PacketCollisionWindow> fragments) {
        if (uid == null || fragments == null || fragments.isEmpty()) {
            return false;
        }
        List<PacketCollisionWindow> packets;
        CollisionMetadata metadata;
        try {
            packets = new ArrayList<>(fragments);
            for (int index = 0; index < packets.size(); index++) {
                PacketCollisionWindow packet = packets.get(index);
                if (packet == null
                        || !uid.equals(packet.getUid())
                        || generation != packet.getGeneration()
                        || sequence != packet.getSequence()
                        || packets.size() != packet.getFragmentCount()
                        || index != packet.getFragmentIndex()) {
                    return false;
                }
            }
            PacketCollisionWindow.CollisionWindowUpdate update = PacketCollisionWindow.reassemble(packets);
            metadata = new CollisionMetadata(
                    uid,
                    update.getGeneration(),
                    update.getSequence(),
                    update.getKind(),
                    update.getBaseSequence());
        } catch (IOException | RuntimeException failure) {
            return false;
        }
        synchronized (STATE_LOCK) {
            if (resyncProducer != null && resyncProducer != Thread.currentThread()) {
                markDiscontinuityLocked(packets.size());
                return false;
            }
            if (discontinuityRequired) {
                DROPPED.addAndGet(packets.size());
                return false;
            }
            if (COLLISION_RECOVERY_FLOORS.containsKey(uid)) {
                if (resyncProducer == Thread.currentThread()) {
                    markDiscontinuityLocked(packets.size());
                } else {
                    DROPPED.addAndGet(packets.size());
                }
                return false;
            }
            if (!acceptCollisionLocked(metadata)) {
                if (resyncProducer == Thread.currentThread()
                        && COLLISION_RECOVERY_FLOORS.containsKey(uid)) {
                    markDiscontinuityLocked(packets.size());
                } else {
                    DROPPED.addAndGet(packets.size());
                }
                return false;
            }
            List<PacketEncode> encoded = new ArrayList<>(packets);
            PacketGroup group = new PacketGroup(PacketQueue.generation, metadata, encoded);
            Collection<PacketGroup> target = resyncProducer == Thread.currentThread() ? resyncGroups : QUEUE;
            int packetCount = resyncProducer == Thread.currentThread() ? resyncPacketCount : queuedPackets;
            int replaceable = metadata.kind() == PacketCollisionWindow.Kind.FULL
                    ? countCollisionPackets(target, uid)
                    : 0;
            if (packetCount - replaceable + group.size() > CAPACITY) {
                markDiscontinuityLocked(group.size());
                return false;
            }
            if (replaceable > 0) {
                removeCollisionGroups(target, uid);
                packetCount -= replaceable;
            }
            target.add(group);
            packetCount += group.size();
            COLLISION_HIGH_WATER.put(uid, metadata);
            if (metadata.kind() == PacketCollisionWindow.Kind.FULL) {
                COLLISION_RECOVERY_FLOORS.remove(uid);
            }
            if (resyncProducer == Thread.currentThread()) {
                resyncPacketCount = packetCount;
            } else {
                queuedPackets = packetCount;
                STATE_LOCK.notifyAll();
            }
            return true;
        }
    }

    public static boolean pushRecovery(
            String uid,
            long generation,
            long sequence,
            List<PacketCollisionWindow> collisionFragments,
            List<PacketMovementStateSnapshot> stateFragments) {
        if (uid == null || collisionFragments == null || collisionFragments.isEmpty()
                || !validStateSnapshot(uid, generation, sequence, stateFragments)) {
            return false;
        }
        List<PacketCollisionWindow> collision;
        CollisionMetadata metadata;
        try {
            collision = new ArrayList<>(collisionFragments);
            for (int index = 0; index < collision.size(); index++) {
                PacketCollisionWindow packet = collision.get(index);
                if (packet == null
                        || !uid.equals(packet.getUid())
                        || generation != packet.getGeneration()
                        || sequence != packet.getSequence()
                        || collision.size() != packet.getFragmentCount()
                        || index != packet.getFragmentIndex()) {
                    return false;
                }
            }
            PacketCollisionWindow.CollisionWindowUpdate update =
                    PacketCollisionWindow.reassemble(collision);
            if (update.getKind() != PacketCollisionWindow.Kind.FULL) return false;
            metadata = new CollisionMetadata(
                    uid,
                    update.getGeneration(),
                    update.getSequence(),
                    update.getKind(),
                    update.getBaseSequence());
        } catch (IOException | RuntimeException failure) {
            return false;
        }

        List<PacketEncode> packets = new ArrayList<>(collision);
        packets.addAll(stateFragments);
        synchronized (STATE_LOCK) {
            if (resyncProducer != null && resyncProducer != Thread.currentThread()) {
                markDiscontinuityLocked(packets.size());
                return false;
            }
            if (discontinuityRequired) {
                DROPPED.addAndGet(packets.size());
                return false;
            }
            if (!acceptCollisionLocked(metadata)) {
                if (resyncProducer == Thread.currentThread()
                        && COLLISION_RECOVERY_FLOORS.containsKey(uid)) {
                    markDiscontinuityLocked(packets.size());
                } else {
                    DROPPED.addAndGet(packets.size());
                }
                return false;
            }
            PacketGroup group = new PacketGroup(PacketQueue.generation, metadata, packets);
            Collection<PacketGroup> target =
                    resyncProducer == Thread.currentThread() ? resyncGroups : QUEUE;
            int packetCount =
                    resyncProducer == Thread.currentThread() ? resyncPacketCount : queuedPackets;
            int replaceable = countCollisionPackets(target, uid);
            if (packetCount - replaceable + group.size() > CAPACITY) {
                markDiscontinuityLocked(group.size());
                return false;
            }
            if (replaceable > 0) {
                removeCollisionGroups(target, uid);
                packetCount -= replaceable;
            }
            target.add(group);
            packetCount += group.size();
            COLLISION_HIGH_WATER.put(uid, metadata);
            COLLISION_RECOVERY_FLOORS.remove(uid);
            if (resyncProducer == Thread.currentThread()) {
                resyncPacketCount = packetCount;
            } else {
                queuedPackets = packetCount;
                STATE_LOCK.notifyAll();
            }
            return true;
        }
    }

    public static void removeCollisionWindows(String uid) {
        Objects.requireNonNull(uid, "uid");
        synchronized (STATE_LOCK) {
            int removed = countCollisionPackets(QUEUE, uid);
            removeCollisionGroups(QUEUE, uid);
            queuedPackets -= removed;
            if (resyncGroups != null) {
                removed = countCollisionPackets(resyncGroups, uid);
                removeCollisionGroups(resyncGroups, uid);
                resyncPacketCount -= removed;
            }
        }
    }

    public static void markDiscontinuity(long additionalDroppedPackets) {
        synchronized (STATE_LOCK) {
            markDiscontinuityLocked(Math.max(0L, additionalDroppedPackets));
        }
    }

    public static boolean recoverFromDiscontinuity(Runnable producer) {
        Objects.requireNonNull(producer, "producer");
        synchronized (STATE_LOCK) {
            if (!discontinuityRequired || resyncProducer != null || sendsInFlight != 0) {
                return false;
            }
            QUEUE.clear();
            queuedPackets = 0;
            discontinuityRequired = false;
            resyncProducer = Thread.currentThread();
            resyncGroups = new ArrayList<>();
            resyncPacketCount = 0;
        }
        try {
            producer.run();
            synchronized (STATE_LOCK) {
                if (discontinuityRequired) {
                    return false;
                }
                QUEUE.addAll(resyncGroups);
                queuedPackets = resyncPacketCount;
                resyncProducer = null;
                resyncGroups = null;
                resyncPacketCount = 0;
                STATE_LOCK.notifyAll();
                return true;
            }
        } catch (RuntimeException | Error failure) {
            synchronized (STATE_LOCK) {
                if (!discontinuityRequired) {
                    markDiscontinuityLocked(0L);
                }
            }
            throw failure;
        } finally {
            synchronized (STATE_LOCK) {
                resyncProducer = null;
                resyncGroups = null;
                resyncPacketCount = 0;
            }
        }
    }

    public static Batch pollBatch(int maxElements, long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit, "unit");
        int limit = Math.max(1, maxElements);
        long remainingNanos = unit.toNanos(timeout);
        synchronized (STATE_LOCK) {
            long deadline = System.nanoTime() + remainingNanos;
            while (QUEUE.isEmpty()) {
                if (remainingNanos <= 0L) {
                    return null;
                }
                TimeUnit.NANOSECONDS.timedWait(STATE_LOCK, remainingNanos);
                remainingNanos = deadline - System.nanoTime();
            }
            long batchGeneration = QUEUE.getFirst().generation();
            List<PacketEncode> packets = new ArrayList<>(limit);
            while (!QUEUE.isEmpty() && QUEUE.getFirst().generation() == batchGeneration) {
                PacketGroup group = QUEUE.getFirst();
                int remaining = limit - packets.size();
                if (!packets.isEmpty() && group.size() > remaining) {
                    break;
                }
                QUEUE.removeFirst();
                queuedPackets -= group.size();
                packets.addAll(group.packets());
                if (packets.size() >= limit) {
                    break;
                }
            }
            return new Batch(batchGeneration, List.copyOf(packets));
        }
    }

    public static boolean beginSend(long batchGeneration) {
        synchronized (STATE_LOCK) {
            if (batchGeneration != generation || discontinuityRequired || resyncProducer != null) {
                return false;
            }
            sendsInFlight++;
            return true;
        }
    }

    public static void endSend() {
        synchronized (STATE_LOCK) {
            if (sendsInFlight <= 0) {
                throw new IllegalStateException("no packet send in flight");
            }
            sendsInFlight--;
        }
    }

    private static boolean validStateSnapshot(
            String uid,
            long generation,
            long sequence,
            List<PacketMovementStateSnapshot> fragments) {
        if (fragments == null || fragments.isEmpty()) return false;
        try {
            for (int index = 0; index < fragments.size(); index++) {
                PacketMovementStateSnapshot fragment = fragments.get(index);
                if (fragment == null
                        || !uid.equals(fragment.getUid())
                        || generation != fragment.getGeneration()
                        || sequence != fragment.getSequence()
                        || fragments.size() != fragment.getFragmentCount()
                        || index != fragment.getFragmentIndex()) {
                    return false;
                }
            }
            PacketMovementStateSnapshot.reassemble(fragments);
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static boolean acceptCollisionLocked(CollisionMetadata metadata) {
        Long recoveryFloor = COLLISION_RECOVERY_FLOORS.get(metadata.uid());
        if (recoveryFloor != null) {
            return metadata.kind() == PacketCollisionWindow.Kind.FULL
                    && metadata.generation() > recoveryFloor;
        }
        CollisionMetadata previous = COLLISION_HIGH_WATER.get(metadata.uid());
        if (previous == null) {
            return metadata.kind() == PacketCollisionWindow.Kind.FULL;
        }
        int generationOrder = Long.compare(metadata.generation(), previous.generation());
        if (generationOrder < 0
                || generationOrder == 0 && metadata.sequence() <= previous.sequence()) {
            return false;
        }
        if (metadata.kind() == PacketCollisionWindow.Kind.FULL) {
            return true;
        }
        return generationOrder == 0
                && previous.sequence() != Long.MAX_VALUE
                && metadata.baseSequence() == previous.sequence()
                && metadata.sequence() == previous.sequence() + 1;
    }

    private static int countCollisionPackets(Collection<PacketGroup> groups, String uid) {
        int count = 0;
        for (PacketGroup group : groups) {
            if (group.collision() != null && uid.equals(group.collision().uid())) {
                count += group.size();
            }
        }
        return count;
    }

    private static void removeCollisionGroups(Collection<PacketGroup> groups, String uid) {
        Iterator<PacketGroup> iterator = groups.iterator();
        while (iterator.hasNext()) {
            PacketGroup group = iterator.next();
            if (group.collision() != null && uid.equals(group.collision().uid())) {
                iterator.remove();
            }
        }
    }

    private static void markDiscontinuityLocked(long additionalDroppedPackets) {
        int queued = queuedPackets + resyncPacketCount;
        for (Map.Entry<String, CollisionMetadata> entry : COLLISION_HIGH_WATER.entrySet()) {
            COLLISION_RECOVERY_FLOORS.merge(
                    entry.getKey(), entry.getValue().generation(), Math::max);
        }
        QUEUE.clear();
        queuedPackets = 0;
        if (resyncGroups != null) {
            resyncGroups.clear();
            resyncPacketCount = 0;
        }
        DROPPED.addAndGet(queued + additionalDroppedPackets);
        generation++;
        discontinuityRequired = true;
    }

    public static boolean isEmpty() {
        synchronized (STATE_LOCK) {
            return queuedPackets == 0;
        }
    }

    public static int size() {
        synchronized (STATE_LOCK) {
            return queuedPackets;
        }
    }

    public static int capacity() {
        return CAPACITY;
    }

    public static long droppedCount() {
        return DROPPED.get();
    }

    public static boolean discontinuityRequired() {
        synchronized (STATE_LOCK) {
            return discontinuityRequired;
        }
    }

    public static void clear() {
        synchronized (STATE_LOCK) {
            QUEUE.clear();
            queuedPackets = 0;
            DROPPED.set(0L);
            generation++;
            sendsInFlight = 0;
            discontinuityRequired = false;
            COLLISION_HIGH_WATER.clear();
            COLLISION_RECOVERY_FLOORS.clear();
            resyncProducer = null;
            resyncGroups = null;
            resyncPacketCount = 0;
        }
    }
}
