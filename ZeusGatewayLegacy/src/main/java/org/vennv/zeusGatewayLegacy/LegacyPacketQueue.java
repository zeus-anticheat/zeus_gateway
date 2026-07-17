package org.vennv.zeusGatewayLegacy;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.vennv.PacketEncode;
import org.vennv.packets.PacketCollisionWindow;

final class LegacyPacketQueue {
    static final int CAPACITY = 8192;
    private static final Deque<PacketGroup> QUEUE = new ArrayDeque<PacketGroup>();
    private static volatile OverflowHandler overflowHandler;
    private static PacketGroup compatibilityGroup;
    private static int compatibilityIndex;
    private static int queuedPackets;

    private LegacyPacketQueue() {
    }

    static boolean push(UUID owner, PacketEncode packet) {
        return offer(owner, Collections.singletonList(packet), false);
    }

    static boolean pushControl(UUID owner, PacketEncode packet) {
        return offer(owner, Collections.singletonList(packet), true);
    }

    static boolean pushAll(UUID owner, List<? extends PacketEncode> packets) {
        return offer(owner, packets, false);
    }

    static boolean pushCollision(UUID owner, List<PacketCollisionWindow> fragments) {
        if (owner == null || fragments == null || fragments.isEmpty()) return false;
        try {
            PacketCollisionWindow.CollisionWindowUpdate update = PacketCollisionWindow.reassemble(fragments);
            for (int index = 0; index < fragments.size(); index++) {
                PacketCollisionWindow fragment = fragments.get(index);
                if (fragment == null
                        || !owner.toString().equals(fragment.getUid())
                        || fragment.getGeneration() != update.getGeneration()
                        || fragment.getSequence() != update.getSequence()
                        || fragment.getFragmentCount() != fragments.size()
                        || fragment.getFragmentIndex() != index) return false;
            }
        } catch (IOException | RuntimeException error) {
            return false;
        }
        return offer(owner, fragments, false);
    }

    static boolean pushCoalescing(UUID owner, String key, PacketEncode packet) {
        if (key == null) return false;
        return push(owner, packet);
    }

    private static boolean offer(UUID owner, List<? extends PacketEncode> packets, boolean control) {
        if (packets == null || packets.isEmpty()) return false;
        List<PacketEncode> copy = new ArrayList<PacketEncode>(packets.size());
        for (PacketEncode packet : packets) {
            if (packet == null) return false;
            copy.add(packet);
        }
        PacketGroup group = new PacketGroup(owner, control, copy);
        OverflowHandler handler;
        boolean global;
        synchronized (LegacyPacketQueue.class) {
            if (queuedPackets + group.size() <= CAPACITY) {
                enqueue(group);
                return true;
            }
            int removed = remove(owner, false);
            global = owner == null || control && removed == 0;
            if (global) removeTelemetry();
            if (control && queuedPackets + group.size() <= CAPACITY) enqueue(group);
            handler = overflowHandler;
        }
        if (handler != null) handler.onOverflow(owner, global);
        return false;
    }

    static synchronized PacketGroup pollGroup() {
        if (compatibilityGroup != null) return null;
        PacketGroup group = QUEUE.pollFirst();
        if (group != null) queuedPackets -= group.size();
        return group;
    }

    static synchronized PacketEncode poll() {
        if (compatibilityGroup == null) {
            compatibilityGroup = QUEUE.pollFirst();
            compatibilityIndex = 0;
            if (compatibilityGroup == null) return null;
            queuedPackets -= compatibilityGroup.size();
        }
        PacketEncode packet = compatibilityGroup.packets.get(compatibilityIndex++);
        if (compatibilityIndex == compatibilityGroup.size()) {
            compatibilityGroup = null;
            compatibilityIndex = 0;
        }
        return packet;
    }

    static synchronized void drop(UUID owner) {
        remove(owner, false);
    }

    static synchronized void dropCollision(UUID owner) {
        if (owner == null) return;
        Iterator<PacketGroup> iterator = QUEUE.iterator();
        while (iterator.hasNext()) {
            PacketGroup group = iterator.next();
            if (owner.equals(group.owner) && group.isCollision()) {
                queuedPackets -= group.size();
                iterator.remove();
            }
        }
    }

    private static int remove(UUID owner, boolean includeControl) {
        if (owner == null) return 0;
        int removed = 0;
        Iterator<PacketGroup> iterator = QUEUE.iterator();
        while (iterator.hasNext()) {
            PacketGroup group = iterator.next();
            if (owner.equals(group.owner) && (includeControl || !group.control)) {
                removed += group.size();
                queuedPackets -= group.size();
                iterator.remove();
            }
        }
        return removed;
    }

    private static void removeTelemetry() {
        Iterator<PacketGroup> iterator = QUEUE.iterator();
        while (iterator.hasNext()) {
            PacketGroup group = iterator.next();
            if (!group.control) {
                queuedPackets -= group.size();
                iterator.remove();
            }
        }
    }

    private static void enqueue(PacketGroup group) {
        QUEUE.addLast(group);
        queuedPackets += group.size();
    }

    static void sendFailed(PacketGroup group) {
        if (group == null) return;
        OverflowHandler handler;
        synchronized (LegacyPacketQueue.class) {
            if (group.owner == null) removeTelemetry();
            else remove(group.owner, false);
            handler = overflowHandler;
        }
        if (handler != null) handler.onOverflow(group.owner, group.owner == null);
    }

    static void setOverflowHandler(OverflowHandler handler) {
        overflowHandler = handler;
    }

    static synchronized int size() {
        return queuedPackets + (compatibilityGroup == null
                ? 0 : compatibilityGroup.size() - compatibilityIndex);
    }

    static synchronized void clear() {
        QUEUE.clear();
        compatibilityGroup = null;
        compatibilityIndex = 0;
        queuedPackets = 0;
    }

    interface OverflowHandler {
        void onOverflow(UUID owner, boolean global);
    }

    static final class PacketGroup {
        private final UUID owner;
        private final boolean control;
        private final List<PacketEncode> packets;

        private PacketGroup(UUID owner, boolean control, List<PacketEncode> packets) {
            this.owner = owner;
            this.control = control;
            this.packets = Collections.unmodifiableList(new ArrayList<PacketEncode>(packets));
        }

        UUID owner() {
            return owner;
        }

        List<PacketEncode> packets() {
            return packets;
        }

        int size() {
            return packets.size();
        }

        boolean isCollision() {
            return !packets.isEmpty() && packets.get(0) instanceof PacketCollisionWindow;
        }
    }
}
