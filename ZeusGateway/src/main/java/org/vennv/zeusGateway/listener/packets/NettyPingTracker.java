package org.vennv.zeusGateway.listener.packets;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class NettyPingTracker {

    private static final int MAX_PENDING_PER_PLAYER = 50;
    private final AtomicInteger counter = new AtomicInteger();
    private final Map<UUID, LinkedHashMap<Integer, Long>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMeasuredRtt = new ConcurrentHashMap<>();

    public int stage(UUID playerId, boolean modern) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId is required");
        }
        int id = nextId(counter, modern);
        LinkedHashMap<Integer, Long> playerPending = pending.computeIfAbsent(
                playerId, ignored -> new LinkedHashMap<>());
        synchronized (playerPending) {
            if (playerPending.size() >= MAX_PENDING_PER_PLAYER) {
                Iterator<Integer> it = playerPending.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            playerPending.put(id, System.nanoTime());
        }
        return id;
    }

    public long complete(UUID playerId, int id) {
        if (playerId == null) {
            return -1;
        }
        LinkedHashMap<Integer, Long> playerPending = pending.get(playerId);
        if (playerPending == null) {
            return -1;
        }
        Long sendNanos = null;
        synchronized (playerPending) {
            if (!playerPending.containsKey(id)) {
                return -1;
            }
            Iterator<Map.Entry<Integer, Long>> iterator = playerPending.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, Long> entry = iterator.next();
                iterator.remove();
                if (entry.getKey() == id) {
                    sendNanos = entry.getValue();
                    break;
                }
            }
        }
        if (sendNanos == null) {
            return -1;
        }
        long elapsedNanos = System.nanoTime() - sendNanos;
        long rttMs = Math.max(0L, elapsedNanos / 1_000_000L);
        lastMeasuredRtt.put(playerId, rttMs);
        return rttMs;
    }

    public Long getLastRtt(UUID playerId) {
        return playerId != null ? lastMeasuredRtt.get(playerId) : null;
    }

    public void clearPlayer(UUID playerId) {
        if (playerId != null) {
            pending.remove(playerId);
            lastMeasuredRtt.remove(playerId);
        }
    }

    public void clear() {
        pending.clear();
        lastMeasuredRtt.clear();
    }

    static int nextId(AtomicInteger counter, boolean modern) {
        if (modern) {
            // Positive integer with high bit 0 and bit 30 set (>= 1,073,741,824)
            // Distinct from ClientAcknowledgementTracker which uses negative integers.
            return 0x40000000 | (counter.incrementAndGet() & 0x3fffffff);
        }
        // Negative short between -16384 and -32767
        int val = -(0x4000 + (counter.incrementAndGet() & 0x3fff));
        return (short) val;
    }
}
