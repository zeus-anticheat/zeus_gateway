package org.vennv.zeusGateway.listener.packets;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

final class ClientAcknowledgementTracker {
    private final AtomicInteger counter = new AtomicInteger();
    private final Map<UUID, LinkedHashMap<Integer, Runnable>> pending = new LinkedHashMap<>();

    int stage(UUID playerId, boolean modern, Runnable task) {
        if (playerId == null || task == null) {
            throw new IllegalArgumentException("playerId and task are required");
        }
        synchronized (pending) {
            int id = nextId(counter, modern);
            pending.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>()).put(id, task);
            return id;
        }
    }

    boolean acknowledge(UUID playerId, int id) {
        List<Runnable> ready = new ArrayList<>();
        synchronized (pending) {
            LinkedHashMap<Integer, Runnable> playerPending = pending.get(playerId);
            if (playerPending == null || !playerPending.containsKey(id)) {
                return false;
            }
            Iterator<Map.Entry<Integer, Runnable>> iterator = playerPending.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, Runnable> entry = iterator.next();
                ready.add(entry.getValue());
                iterator.remove();
                if (entry.getKey() == id) {
                    break;
                }
            }
            if (playerPending.isEmpty()) {
                pending.remove(playerId);
            }
        }
        for (Runnable task : ready) {
            task.run();
        }
        return true;
    }

    void clearPlayer(UUID playerId) {
        synchronized (pending) {
            pending.remove(playerId);
        }
    }

    void clear() {
        synchronized (pending) {
            pending.clear();
        }
    }

    static int nextId(AtomicInteger counter, boolean modern) {
        if (modern) {
            int id;
            do {
                id = Integer.MIN_VALUE | (counter.incrementAndGet() & Integer.MAX_VALUE);
            } while (id == (short) id);
            return id;
        }
        int id;
        do {
            id = -(counter.incrementAndGet() & 0x7fff);
        } while (id == 0);
        return id;
    }
}
