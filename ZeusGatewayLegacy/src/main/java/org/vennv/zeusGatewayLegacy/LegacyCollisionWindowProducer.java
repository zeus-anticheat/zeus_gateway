package org.vennv.zeusGatewayLegacy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketCollisionWindow.Cell;
import org.vennv.packets.PacketCollisionWindow.CellUpdate;
import org.vennv.packets.PacketCollisionWindow.CollisionWindowUpdate;

final class LegacyCollisionWindowProducer implements AutoCloseable {
    private static final int CELL_COUNT = PacketCollisionWindow.COLLISION_WINDOW_CELLS;
    private static final AtomicLong GENERATIONS = new AtomicLong(Math.max(1L, System.currentTimeMillis()));
    private final JavaPlugin plugin;
    private final RecoveryHandler recoveryHandler;
    private final Map<UUID, State> states = new HashMap<UUID, State>();
    private final Set<UUID> refreshScheduled = new HashSet<UUID>();
    private boolean closed;

    LegacyCollisionWindowProducer(JavaPlugin plugin, RecoveryHandler recoveryHandler) {
        this.plugin = plugin;
        this.recoveryHandler = recoveryHandler;
    }

    static LegacyCollisionWindowProducer start(JavaPlugin plugin, RecoveryHandler recoveryHandler) {
        return new LegacyCollisionWindowProducer(plugin, recoveryHandler);
    }

    void forceFull(Player player) {
        if (player != null) capture(player, player.getLocation(), true);
    }

    void forceFull(Player player, Location location) {
        if (player != null && location != null) capture(player, location, true);
    }

    void onMovement(Player player, double x, double y, double z) {
        if (player == null) return;
        Location location = new Location(player.getWorld(), x, y, z);
        capture(player, location, false);
    }

    void lifecycle(Player player, Location location) {
        if (player == null) return;
        invalidate(player.getUniqueId());
        forceFull(player, location == null ? player.getLocation() : location);
    }

    void invalidate(UUID playerId) {
        if (playerId == null) return;
        states.put(playerId, State.empty(nextGeneration(System.currentTimeMillis())));
        LegacyPacketQueue.dropCollision(playerId);
    }

    void remove(UUID playerId) {
        if (playerId == null) return;
        states.remove(playerId);
        refreshScheduled.remove(playerId);
        LegacyPacketQueue.dropCollision(playerId);
    }

    void blockChanged(World world, int x, int y, int z) {
        if (closed || world == null) return;
        UUID worldId = world.getUID();
        for (Map.Entry<UUID, State> entry : new ArrayList<Map.Entry<UUID, State>>(states.entrySet())) {
            State state = entry.getValue();
            if (state.committed() && worldId.equals(state.worldId) && contains(state.center, x, y, z)) {
                requestFull(entry.getKey());
            }
        }
    }

    synchronized void requestFull(final UUID playerId) {
        if (closed || playerId == null || !refreshScheduled.add(playerId)) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                synchronized (LegacyCollisionWindowProducer.this) {
                    refreshScheduled.remove(playerId);
                    if (closed) return;
                }
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) forceFull(player);
            }
        }, 1L);
    }

    boolean hasState(UUID playerId) {
        return states.containsKey(playerId);
    }

    private void capture(final Player player, final Location location, boolean forceFull) {
        if (closed || location.getWorld() == null) return;
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("collision sampling requires Bukkit main thread");
        final World world = location.getWorld();
        final UUID playerId = player.getUniqueId();
        final long timestamp = System.currentTimeMillis();
        Prepared prepared = prepare(states.get(playerId), world.getUID(), Center.floor(
                location.getX(), location.getY(), location.getZ()), forceFull, timestamp, new CellSource() {
            @Override
            public Cell sample(int x, int y, int z) {
                return sampleBlock(world, x, y, z);
            }
        });
        if (prepared == null) return;
        List<PacketCollisionWindow> fragments;
        try {
            fragments = prepared.update.toFragments(
                    timestamp, playerId.toString(), player.getName(),
                    LegacyPhysicsCaptureManager.clientProtocol(player));
            for (PacketCollisionWindow fragment : fragments) {
                if (fragment.encodedDatagramLength() > PacketCollisionWindow.MAX_DATAGRAM_LENGTH) {
                    invalidate(playerId);
                    return;
                }
            }
        } catch (IllegalArgumentException | IOException error) {
            invalidate(playerId);
            return;
        }
        if (LegacyPacketQueue.pushCollision(playerId, fragments)) {
            states.put(playerId, prepared.state);
            if (prepared.full && recoveryHandler != null) recoveryHandler.onFullQueued(playerId);
        } else {
            invalidate(playerId);
        }
    }

    static Prepared prepare(
            State previous,
            UUID worldId,
            Center center,
            boolean forceFull,
            long timestamp,
            CellSource source) {
        if (worldId == null || center == null || source == null) throw new IllegalArgumentException("collision input is missing");
        State base = previous;
        if (base == null || base.committed() && !worldId.equals(base.worldId)) {
            base = State.empty(nextGeneration(timestamp));
        }
        if (!forceFull && base.committed() && worldId.equals(base.worldId) && center.equals(base.center)) {
            return null;
        }
        boolean full = forceFull || !base.committed() || !overlaps(base.center, center);
        Cell[] cells = new Cell[CELL_COUNT];
        Arrays.fill(cells, Cell.unknown());
        int[] indices;
        Center baseCenter;
        long baseSequence;
        if (full) {
            indices = allIndices();
            baseCenter = center;
            baseSequence = 0L;
        } else {
            reuse(base, center, cells);
            indices = enteringIndices(base.center, center);
            baseCenter = base.center;
            baseSequence = base.sequence;
        }
        for (int index : indices) {
            int[] position = PacketCollisionWindow.collisionWindowPosition(
                    center.x, center.y, center.z, index);
            Cell cell;
            try {
                cell = source.sample(position[0], position[1], position[2]);
            } catch (RuntimeException error) {
                cell = Cell.unknown();
            }
            cells[index] = cell == null ? Cell.unknown() : cell;
        }
        long sequence = Math.incrementExact(base.sequence);
        CollisionWindowUpdate update;
        if (full) {
            update = CollisionWindowUpdate.full(
                    base.generation, sequence, center.x, center.y, center.z,
                    Arrays.asList(cells.clone()));
        } else {
            List<CellUpdate> updates = new ArrayList<CellUpdate>(indices.length);
            for (int index : indices) updates.add(new CellUpdate(index, cells[index]));
            update = CollisionWindowUpdate.delta(
                    base.generation, sequence, baseSequence,
                    baseCenter.x, baseCenter.y, baseCenter.z,
                    center.x, center.y, center.z, updates);
        }
        return new Prepared(full, indices, update,
                new State(base.generation, sequence, worldId, center, cells));
    }

    static Cell sampleBlock(World world, int x, int y, int z) {
        if (world == null || y < 0 || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return Cell.unknown();
        }
        try {
            Block block = world.getBlockAt(x, y, z);
            if (block == null || block.getType() == null) return Cell.unknown();
            String name = block.getType().name().toLowerCase(Locale.ROOT);
            return "air".equals(name) || name.endsWith("_air")
                    ? Cell.knownAir()
                    : Cell.knownBlock("minecraft:" + name);
        } catch (RuntimeException | LinkageError error) {
            return Cell.unknown();
        }
    }

    static long nextGeneration(long timestamp) {
        while (true) {
            long current = GENERATIONS.get();
            if (current == Long.MAX_VALUE) throw new IllegalStateException("collision generation overflow");
            long candidate = Math.max(Math.max(1L, timestamp), current + 1L);
            if (GENERATIONS.compareAndSet(current, candidate)) return candidate;
        }
    }

    static int[] allIndices() {
        int[] indices = new int[CELL_COUNT];
        for (int index = 0; index < CELL_COUNT; index++) indices[index] = index;
        return indices;
    }

    static int[] enteringIndices(Center base, Center center) {
        List<Integer> entering = PacketCollisionWindow.enteringCellIndices(
                base.x, base.y, base.z, center.x, center.y, center.z);
        int[] indices = new int[entering.size()];
        for (int index = 0; index < indices.length; index++) indices[index] = entering.get(index).intValue();
        return indices;
    }

    static boolean overlaps(Center left, Center right) {
        return Math.abs((long) left.x - right.x) < PacketCollisionWindow.COLLISION_WINDOW_EDGE
                && Math.abs((long) left.y - right.y) < PacketCollisionWindow.COLLISION_WINDOW_EDGE
                && Math.abs((long) left.z - right.z) < PacketCollisionWindow.COLLISION_WINDOW_EDGE;
    }

    static boolean contains(Center center, int x, int y, int z) {
        int radius = PacketCollisionWindow.COLLISION_WINDOW_RADIUS;
        return Math.abs((long) x - center.x) <= radius
                && Math.abs((long) y - center.y) <= radius
                && Math.abs((long) z - center.z) <= radius;
    }

    private static void reuse(State previous, Center center, Cell[] cells) {
        int radius = PacketCollisionWindow.COLLISION_WINDOW_RADIUS;
        for (int index = 0; index < CELL_COUNT; index++) {
            int[] position = PacketCollisionWindow.collisionWindowPosition(
                    center.x, center.y, center.z, index);
            long dx = (long) position[0] - previous.center.x;
            long dy = (long) position[1] - previous.center.y;
            long dz = (long) position[2] - previous.center.z;
            if (Math.abs(dx) <= radius && Math.abs(dy) <= radius && Math.abs(dz) <= radius) {
                cells[index] = previous.cells[PacketCollisionWindow.collisionWindowIndex(
                        (int) dx, (int) dy, (int) dz)];
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (UUID playerId : new ArrayList<UUID>(states.keySet())) LegacyPacketQueue.dropCollision(playerId);
        states.clear();
        refreshScheduled.clear();
    }

    interface CellSource {
        Cell sample(int x, int y, int z);
    }

    interface RecoveryHandler {
        void onFullQueued(UUID playerId);
    }

    static final class Center {
        final int x;
        final int y;
        final int z;

        Center(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static Center floor(double x, double y, double z) {
            int[] center = PacketCollisionWindow.collisionWindowCenter(x, y, z);
            return new Center(center[0], center[1], center[2]);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Center)) return false;
            Center center = (Center) other;
            return x == center.x && y == center.y && z == center.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            return 31 * result + z;
        }
    }

    static final class State {
        final long generation;
        final long sequence;
        final UUID worldId;
        final Center center;
        final Cell[] cells;

        State(long generation, long sequence, UUID worldId, Center center, Cell[] cells) {
            if (generation <= 0L || sequence < 0L || (sequence == 0L) != (center == null)) {
                throw new IllegalArgumentException("invalid collision state");
            }
            if ((worldId == null) != (center == null) || cells == null || cells.length != CELL_COUNT) {
                throw new IllegalArgumentException("collision state must contain exactly 729 cells");
            }
            this.generation = generation;
            this.sequence = sequence;
            this.worldId = worldId;
            this.center = center;
            this.cells = cells.clone();
        }

        static State empty(long generation) {
            Cell[] cells = new Cell[CELL_COUNT];
            Arrays.fill(cells, Cell.unknown());
            return new State(generation, 0L, null, null, cells);
        }

        boolean committed() {
            return sequence > 0L;
        }
    }

    static final class Prepared {
        final boolean full;
        final int[] sampledIndices;
        final CollisionWindowUpdate update;
        final State state;

        Prepared(boolean full, int[] sampledIndices, CollisionWindowUpdate update, State state) {
            this.full = full;
            this.sampledIndices = sampledIndices.clone();
            this.update = update;
            this.state = state;
        }
    }
}
