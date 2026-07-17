package org.vennv.zeusGateway.provider;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketEncode;
import org.vennv.packets.PacketChunkData;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketCollisionWindow.CollisionWindowUpdate;
import org.vennv.packets.PacketCollisionWindow.Kind;

public final class PacketQueue {

    private static final int CAPACITY = Math.max(
            1, Math.min(1_000_000, Integer.getInteger("zeus.queue.capacity", 8192)));
    private static final Deque<PacketGroup> QUEUE = new ArrayDeque<>();
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Condition STATE_CHANGED = LOCK.newCondition();
    private static final Set<String> BLOCKED_UIDS = new HashSet<>();
    private static final Set<String> DISCONTINUITIES = new HashSet<>();
    private static final Map<String, CollisionKey> LATEST_COLLISION_KEYS = new HashMap<>();
    private static final Map<String, CollisionKey> SENT_COLLISION_KEYS = new HashMap<>();
    private static PacketGroup compatibilityGroup;
    private static Thread compatibilityOwner;
    private static int compatibilityIndex;
    private static int queuedPackets;
    private static long droppedPackets;

    public static boolean push(PacketEncode packet) {
        return pushAll(Collections.singletonList(Objects.requireNonNull(packet, "packet")));
    }

    public static boolean pushAll(List<? extends PacketEncode> packets) {
        Objects.requireNonNull(packets, "packets");
        if (packets.isEmpty()) return true;
        List<PacketEncode> copy = new ArrayList<>(packets.size());
        for (PacketEncode packet : packets) {
            copy.add(Objects.requireNonNull(packet, "packet"));
        }
        PacketGroup group = new PacketGroup(commonUid(copy), null, copy);
        LOCK.lock();
        try {
            if (group.uid != null && BLOCKED_UIDS.contains(group.uid)) {
                droppedPackets += group.size();
                return false;
            }
            if (queuedPackets + group.size() > CAPACITY) {
                droppedPackets += group.size();
                if (group.uid != null) markDiscontinuityLocked(group.uid);
                return false;
            }
            enqueueLocked(group);
            return true;
        } finally {
            LOCK.unlock();
        }
    }

    public static boolean pushRecoveryChunk(PacketChunkData packet) {
        return push(packet);
    }

    public static boolean pushCollisionWindow(
            String uid,
            long generation,
            long sequence,
            List<PacketCollisionWindow> fragments) {
        if (uid == null || fragments == null || fragments.isEmpty()) return false;
        List<PacketCollisionWindow> packets;
        CollisionWindowUpdate update;
        try {
            packets = new ArrayList<>(fragments);
            update = PacketCollisionWindow.reassemble(packets);
            for (int index = 0; index < packets.size(); index++) {
                PacketCollisionWindow packet = packets.get(index);
                if (!uid.equals(packet.getUid())
                        || generation != packet.getGeneration()
                        || sequence != packet.getSequence()
                        || packets.size() != packet.getFragmentCount()
                        || index != packet.getFragmentIndex()) {
                    return false;
                }
            }
            if (generation != update.getGeneration() || sequence != update.getSequence()) return false;
        } catch (IOException | RuntimeException failure) {
            return false;
        }

        CollisionKey key = new CollisionKey(
                update.getGeneration(), update.getSequence(), update.getKind(), update.getBaseSequence());
        List<PacketEncode> encoded = new ArrayList<PacketEncode>(packets);
        PacketGroup group = new PacketGroup(uid, key, encoded);
        LOCK.lock();
        try {
            CollisionKey latest = LATEST_COLLISION_KEYS.get(uid);
            if (latest != null && key.compareVersion(latest) <= 0) return false;
            if (key.kind == Kind.DELTA) {
                if (BLOCKED_UIDS.contains(uid)
                        || latest == null
                        || latest.generation != key.generation
                        || latest.sequence != key.baseSequence
                        || !isQueuedCollisionLocked(uid, latest)
                                && !latest.equals(SENT_COLLISION_KEYS.get(uid))) {
                    return false;
                }
            }

            int replaceable = key.kind == Kind.FULL ? countCollisionPacketsLocked(uid) : 0;
            int removable = BLOCKED_UIDS.contains(uid) && key.kind == Kind.FULL
                    ? countUidPacketsLocked(uid)
                    : replaceable;
            if (queuedPackets - removable + group.size() > CAPACITY) {
                droppedPackets += group.size();
                markDiscontinuityLocked(uid);
                return false;
            }
            if (BLOCKED_UIDS.contains(uid) && key.kind == Kind.FULL) {
                removeUidGroupsLocked(uid);
            } else if (key.kind == Kind.FULL) {
                removeCollisionGroupsLocked(uid);
            }
            enqueueLocked(group);
            LATEST_COLLISION_KEYS.put(uid, key);
            if (key.kind == Kind.FULL) {
                BLOCKED_UIDS.remove(uid);
                DISCONTINUITIES.remove(uid);
            }
            return true;
        } finally {
            LOCK.unlock();
        }
    }

    public static PacketEncode poll() {
        LOCK.lock();
        try {
            if (compatibilityGroup != null) {
                return compatibilityOwner == Thread.currentThread() ? pollCompatibilityLocked() : null;
            }
            PacketGroup group = pollSendableGroupLocked();
            if (group == null) return null;
            compatibilityGroup = group;
            compatibilityOwner = Thread.currentThread();
            compatibilityIndex = 0;
            return pollCompatibilityLocked();
        } finally {
            LOCK.unlock();
        }
    }

    public static PacketEncode take() throws InterruptedException {
        LOCK.lockInterruptibly();
        try {
            while (true) {
                if (compatibilityGroup != null) {
                    if (compatibilityOwner == Thread.currentThread()) return pollCompatibilityLocked();
                    STATE_CHANGED.await();
                    continue;
                }
                PacketGroup group = pollSendableGroupLocked();
                if (group != null) {
                    compatibilityGroup = group;
                    compatibilityOwner = Thread.currentThread();
                    compatibilityIndex = 0;
                    return pollCompatibilityLocked();
                }
                STATE_CHANGED.await();
            }
        } finally {
            LOCK.unlock();
        }
    }

    public static PacketGroup takeGroup() throws InterruptedException {
        LOCK.lockInterruptibly();
        try {
            while (true) {
                while (compatibilityGroup != null) STATE_CHANGED.await();
                PacketGroup group = pollSendableGroupLocked();
                if (group != null) {
                    java.util.logging.Logger.getLogger("ZeusGateway").severe("[TRACE] takeGroup: uid=" + group.uid() + " packets=" + group.packets().size() + " hasCollision=" + group.isCollision() + " kind=" + (group.isCollision() ? group.collisionKind() : "none"));
                    return group;
                }
                STATE_CHANGED.await();
            }
        } finally {
            LOCK.unlock();
        }
    }

    public static void markSent(PacketGroup group) {
        Objects.requireNonNull(group, "group");
        if (group.collisionKey == null) return;
        LOCK.lock();
        try {
            CollisionKey sent = SENT_COLLISION_KEYS.get(group.uid);
            if (sent == null || group.collisionKey.compareVersion(sent) > 0) {
                SENT_COLLISION_KEYS.put(group.uid, group.collisionKey);
            }
        } finally {
            LOCK.unlock();
        }
    }

    public static void markDiscontinuity(String uid) {
        Objects.requireNonNull(uid, "uid");
        LOCK.lock();
        try {
            markDiscontinuityLocked(uid);
        } finally {
            LOCK.unlock();
        }
    }

    public static boolean consumeDiscontinuity(String uid) {
        Objects.requireNonNull(uid, "uid");
        LOCK.lock();
        try {
            return DISCONTINUITIES.remove(uid);
        } finally {
            LOCK.unlock();
        }
    }

    public static boolean isEmpty() {
        LOCK.lock();
        try {
            return QUEUE.isEmpty() && compatibilityGroup == null;
        } finally {
            LOCK.unlock();
        }
    }

    public static int size() {
        LOCK.lock();
        try {
            return queuedPackets + (compatibilityGroup == null
                    ? 0 : compatibilityGroup.size() - compatibilityIndex);
        } finally {
            LOCK.unlock();
        }
    }

    public static int capacity() {
        return CAPACITY;
    }

    public static long droppedCount() {
        LOCK.lock();
        try {
            return droppedPackets;
        } finally {
            LOCK.unlock();
        }
    }

    public static void removeChunks(String uid) {
        Objects.requireNonNull(uid, "uid");
        LOCK.lock();
        try {
            Iterator<PacketGroup> iterator = QUEUE.iterator();
            while (iterator.hasNext()) {
                PacketGroup group = iterator.next();
                if (group.packets.size() == 1
                        && group.packets.get(0) instanceof PacketChunkData
                        && uid.equals(((PacketChunkData) group.packets.get(0)).getUid())) {
                    queuedPackets -= group.size();
                    iterator.remove();
                }
            }
        } finally {
            LOCK.unlock();
        }
    }

    public static void removeCollisionWindows(String uid) {
        Objects.requireNonNull(uid, "uid");
        LOCK.lock();
        try {
            removeCollisionGroupsLocked(uid);
        } finally {
            LOCK.unlock();
        }
    }

    public static void clear() {
        LOCK.lock();
        try {
            QUEUE.clear();
            BLOCKED_UIDS.clear();
            DISCONTINUITIES.clear();
            LATEST_COLLISION_KEYS.clear();
            SENT_COLLISION_KEYS.clear();
            compatibilityGroup = null;
            compatibilityOwner = null;
            compatibilityIndex = 0;
            queuedPackets = 0;
            droppedPackets = 0;
            STATE_CHANGED.signalAll();
        } finally {
            LOCK.unlock();
        }
    }

    private static void enqueueLocked(PacketGroup group) {
        QUEUE.addLast(group);
        queuedPackets += group.size();
        STATE_CHANGED.signalAll();
    }

    private static PacketGroup pollSendableGroupLocked() {
        while (!QUEUE.isEmpty()) {
            PacketGroup group = QUEUE.removeFirst();
            queuedPackets -= group.size();
            if (group.uid != null && BLOCKED_UIDS.contains(group.uid)) {
                droppedPackets += group.size();
                continue;
            }
            return group;
        }
        return null;
    }

    private static PacketEncode pollCompatibilityLocked() {
        PacketEncode packet = compatibilityGroup.packets.get(compatibilityIndex++);
        if (compatibilityIndex == compatibilityGroup.size()) {
            PacketGroup completed = compatibilityGroup;
            compatibilityGroup = null;
            compatibilityOwner = null;
            compatibilityIndex = 0;
            if (completed.collisionKey != null) {
                CollisionKey sent = SENT_COLLISION_KEYS.get(completed.uid);
                if (sent == null || completed.collisionKey.compareVersion(sent) > 0) {
                    SENT_COLLISION_KEYS.put(completed.uid, completed.collisionKey);
                }
            }
            STATE_CHANGED.signalAll();
        }
        return packet;
    }

    private static boolean isQueuedCollisionLocked(String uid, CollisionKey key) {
        for (PacketGroup group : QUEUE) {
            if (uid.equals(group.uid) && key.equals(group.collisionKey)) return true;
        }
        return false;
    }

    private static int countCollisionPacketsLocked(String uid) {
        int count = 0;
        for (PacketGroup group : QUEUE) {
            if (uid.equals(group.uid) && group.collisionKey != null) count += group.size();
        }
        return count;
    }

    private static int countUidPacketsLocked(String uid) {
        int count = 0;
        for (PacketGroup group : QUEUE) {
            if (uid.equals(group.uid)) count += group.size();
        }
        return count;
    }

    private static void removeCollisionGroupsLocked(String uid) {
        Iterator<PacketGroup> iterator = QUEUE.iterator();
        while (iterator.hasNext()) {
            PacketGroup group = iterator.next();
            if (uid.equals(group.uid) && group.collisionKey != null) {
                queuedPackets -= group.size();
                iterator.remove();
            }
        }
    }

    private static void removeUidGroupsLocked(String uid) {
        Iterator<PacketGroup> iterator = QUEUE.iterator();
        while (iterator.hasNext()) {
            PacketGroup group = iterator.next();
            if (uid.equals(group.uid)) {
                queuedPackets -= group.size();
                droppedPackets += group.size();
                iterator.remove();
            }
        }
    }

    private static void markDiscontinuityLocked(String uid) {
        BLOCKED_UIDS.add(uid);
        DISCONTINUITIES.add(uid);
        removeCollisionGroupsLocked(uid);
        STATE_CHANGED.signalAll();
    }

    private static String commonUid(List<PacketEncode> packets) {
        String uid = null;
        for (PacketEncode packet : packets) {
            if (!(packet instanceof PacketBaseInfo)) return null;
            String packetUid = ((PacketBaseInfo) packet).getUid();
            if (packetUid == null) return null;
            if (uid == null) {
                uid = packetUid;
            } else if (!uid.equals(packetUid)) {
                return null;
            }
        }
        return uid;
    }

    public static final class PacketGroup {
        private final String uid;
        private final CollisionKey collisionKey;
        private final List<PacketEncode> packets;

        private PacketGroup(String uid, CollisionKey collisionKey, List<PacketEncode> packets) {
            this.uid = uid;
            this.collisionKey = collisionKey;
            this.packets = Collections.unmodifiableList(new ArrayList<>(packets));
        }

        public String uid() {
            return uid;
        }

        public List<PacketEncode> packets() {
            return packets;
        }

        public boolean isCollision() {
            return collisionKey != null;
        }

        public long collisionGeneration() {
            return requireCollisionKey().generation;
        }

        public long collisionSequence() {
            return requireCollisionKey().sequence;
        }

        public Kind collisionKind() {
            return requireCollisionKey().kind;
        }

        public long collisionBaseSequence() {
            return requireCollisionKey().baseSequence;
        }

        private int size() {
            return packets.size();
        }

        private CollisionKey requireCollisionKey() {
            if (collisionKey == null) throw new IllegalStateException("packet group is not a collision update");
            return collisionKey;
        }
    }

    private static final class CollisionKey {
        private final long generation;
        private final long sequence;
        private final Kind kind;
        private final long baseSequence;

        private CollisionKey(long generation, long sequence, Kind kind, long baseSequence) {
            this.generation = generation;
            this.sequence = sequence;
            this.kind = Objects.requireNonNull(kind, "kind");
            this.baseSequence = baseSequence;
        }

        private int compareVersion(CollisionKey other) {
            int generationOrder = Long.compare(generation, other.generation);
            return generationOrder != 0 ? generationOrder : Long.compare(sequence, other.sequence);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CollisionKey)) return false;
            CollisionKey key = (CollisionKey) other;
            return generation == key.generation
                    && sequence == key.sequence
                    && baseSequence == key.baseSequence
                    && kind == key.kind;
        }

        @Override
        public int hashCode() {
            return Objects.hash(generation, sequence, kind, baseSequence);
        }
    }
}
