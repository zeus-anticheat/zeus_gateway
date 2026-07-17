package org.vennv.zeusFabric.listener;

import java.util.HashMap;
import java.util.Map;

final class AuthoritativeTeleportDedupe {
    enum Source {
        OUTBOUND,
        WORLD_CHANGE
    }

    private record State(
            String world,
            long x,
            long y,
            long z,
            long tick,
            long lifecycleKey,
            int sources) {}

    private static final int OUTBOUND = 1;
    private static final int WORLD_CHANGE = 2;
    private static final long PAIR_TICKS = 2L;
    private final Map<String, State> states = new HashMap<>();

    synchronized boolean shouldEmit(
            String uid,
            String world,
            double x,
            double y,
            double z,
            long tick,
            Source source,
            long lifecycleKey) {
        long xBits = Double.doubleToLongBits(x);
        long yBits = Double.doubleToLongBits(y);
        long zBits = Double.doubleToLongBits(z);
        State previous = states.get(uid);
        int sourceBit = source == Source.OUTBOUND ? OUTBOUND : WORLD_CHANGE;
        if (previous != null
                && previous.world().equals(world)
                && previous.x() == xBits
                && previous.y() == yBits
                && previous.z() == zBits) {
            if ((previous.sources() & sourceBit) != 0
                    && (previous.lifecycleKey() == lifecycleKey
                    || source == Source.WORLD_CHANGE && Math.abs(tick - previous.tick()) <= PAIR_TICKS)) {
                return false;
            }
            if ((previous.sources() & sourceBit) == 0
                    && Math.abs(tick - previous.tick()) <= PAIR_TICKS) {
                states.put(uid, new State(
                        world,
                        xBits,
                        yBits,
                        zBits,
                        tick,
                        source == Source.OUTBOUND ? lifecycleKey : previous.lifecycleKey(),
                        previous.sources() | sourceBit));
                return false;
            }
        }
        states.put(uid, new State(world, xBits, yBits, zBits, tick, lifecycleKey, sourceBit));
        return true;
    }

    synchronized void remove(String uid) {
        states.remove(uid);
    }
}
