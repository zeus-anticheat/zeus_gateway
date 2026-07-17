package org.vennv.zeusGateway.task;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketCollisionWindow.Cell;
import org.vennv.packets.PacketCollisionWindow.CellUpdate;
import org.vennv.packets.PacketCollisionWindow.CollisionWindowUpdate;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.compat.BlockCompat;
import org.vennv.zeusGateway.platform.ServerVersion;
import org.vennv.zeusGateway.provider.PacketQueue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

public final class ChunkSyncTask {

    private static final int RADIUS = PacketCollisionWindow.COLLISION_WINDOW_RADIUS;
    private static final int CELL_COUNT = PacketCollisionWindow.COLLISION_WINDOW_CELLS;
    private static final String GENERATION_PROPERTY = "org.vennv.zeusGateway.collisionGenerationHighWater";
    private static final String GENERATION_FILE = "collision-generation.high-water";
    private static final Object GENERATION_LOCK = GENERATION_PROPERTY.intern();
    private static final Object STATE_LOCK = new Object();
    private static final Map<UUID, ProducerState> STATES = new HashMap<>();
    private static final AtomicLong GENERATIONS = new AtomicLong(initialGenerationSeed());
    private static final AtomicLong TOKENS = new AtomicLong();
    private static volatile File dataFolder;

    private final ZeusGateway plugin;

    public ChunkSyncTask(ZeusGateway plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        configureGenerationPersistence(plugin.getDataFolder());
    }

    void syncPlayer(Player player) {
        Location location = player.getLocation();
        schedule(player, location.getX(), location.getY(), location.getZ(), true);
    }

    public void forceFull(Player player) {
        Location location = player.getLocation();
        schedule(player, location.getX(), location.getY(), location.getZ(), true);
    }

    public void onMovement(Player player, double x, double y, double z) {
        schedule(player, x, y, z, false);
    }

    public static void invalidateAndRequestFullResync(ZeusGateway plugin, Player player) {
        if (plugin == null || player == null) return;
        invalidate(player.getUniqueId());
        requestFullResync(plugin, player);
    }

    public static void invalidateAndRequestFullResync(ZeusGateway plugin, String uid) {
        if (plugin == null || uid == null) return;
        UUID playerId;
        try {
            playerId = UUID.fromString(uid);
        } catch (IllegalArgumentException exception) {
            return;
        }
        invalidate(playerId);
        try {
            plugin.getSchedulerAdapter().runTask(plugin, () -> {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) requestFullResync(plugin, player);
            });
        } catch (RuntimeException | LinkageError exception) {
            return;
        }
    }

    private static void requestFullResync(ZeusGateway plugin, Player player) {
        if (plugin.getSchedulerAdapter() == null) return;
        try {
            plugin.getSchedulerAdapter().runEntityTask(plugin, player, () -> {
                if (player.isOnline()) new ChunkSyncTask(plugin).forceFull(player);
            });
        } catch (RuntimeException | LinkageError exception) {
            return;
        }
    }

    private void schedule(Player player, double x, double y, double z, boolean forceFull) {
        UUID playerId = Objects.requireNonNull(player.getUniqueId(), "player id");
        World world = Objects.requireNonNull(player.getWorld(), "player world");
        UUID worldId = Objects.requireNonNull(world.getUID(), "world id");
        Center center = Center.floor(x, y, z);
        PreparedUpdate update = prepareUpdate(playerId, worldId, center, forceFull);
        if (update == null) return;

        String uid = playerId.toString();
        String username = player.getName();
        long timestamp = System.currentTimeMillis();
        int minHeight = ServerVersion.isAtLeast(1, 17) ? world.getMinHeight() : 0;
        int maxHeight = world.getMaxHeight();
        List<RegionSlice> regions = splitRegions(center, update.sampleIndices);
        Assembly assembly = new Assembly(
                regions.size(),
                () -> {
                    if (!publishPrepared(update, timestamp, uid, username)
                            && update.recoveryRequired) {
                        requestFullResync(plugin, player);
                    }
                });

        for (int index = 0; index < regions.size(); index++) {
            RegionSlice region = regions.get(index);
            RegionCapture capture = new RegionCapture(
                    world, minHeight, maxHeight, update, region, assembly, index);
            try {
                capture.run();
            } catch (RuntimeException exception) {
                capture.fail();
            }
        }
    }

    public static void invalidate(Player player) {
        invalidate(player.getUniqueId());
    }

    static void invalidate(UUID playerId) {
        Objects.requireNonNull(playerId, "player id");
        synchronized (STATE_LOCK) {
            ProducerState state = STATES.get(playerId);
            if (state == null) {
                state = new ProducerState(nextGeneration());
                STATES.put(playerId, state);
            } else {
                state.reset(nextGeneration());
            }
            PacketQueue.removeCollisionWindows(playerId.toString());
        }
    }

    public static void remove(Player player) {
        remove(player.getUniqueId());
    }

    static void remove(UUID playerId) {
        Objects.requireNonNull(playerId, "player id");
        synchronized (STATE_LOCK) {
            ProducerState state = STATES.remove(playerId);
            if (state != null) state.discard();
            PacketQueue.removeCollisionWindows(playerId.toString());
        }
    }

    public static void clearAll() {
        synchronized (STATE_LOCK) {
            for (Map.Entry<UUID, ProducerState> entry : STATES.entrySet()) {
                entry.getValue().discard();
                PacketQueue.removeCollisionWindows(entry.getKey().toString());
            }
            STATES.clear();
        }
    }

    public static boolean contains(
            UUID playerId,
            UUID worldId,
            int x,
            int y,
            int z) {
        synchronized (STATE_LOCK) {
            ProducerState state = STATES.get(playerId);
            if (state == null) return false;
            boolean committed = state.hasSnapshot
                    && worldId.equals(state.worldId)
                    && contains(state.center, x, y, z);
            PendingToken pending = state.pending;
            return committed
                    || pending != null
                    && worldId.equals(pending.worldId)
                    && contains(pending.center, x, y, z);
        }
    }

    public static boolean recordBlockChange(
            UUID playerId,
            UUID worldId,
            int x,
            int y,
            int z,
            String blockType,
            long timestamp) {
        if (playerId == null || worldId == null || blockType == null) return false;
        Cell cell = "minecraft:air".equals(blockType)
                ? Cell.knownAir()
                : Cell.knownBlock(blockType);
        synchronized (STATE_LOCK) {
            ProducerState state = STATES.get(playerId);
            if (state == null) return false;
            long ordinal = nextToken();
            boolean recorded = false;
            if (state.hasSnapshot
                    && worldId.equals(state.worldId)
                    && contains(state.center, x, y, z)) {
                state.cells[cellIndex(state.center, x, y, z)] = cell;
                recorded = true;
            }
            PreparedUpdate pending = state.pendingUpdate;
            if (pending != null
                    && worldId.equals(pending.pending.worldId)
                    && contains(pending.center, x, y, z)) {
                if (timestamp > pending.captureTimestamp
                        || timestamp == pending.captureTimestamp && ordinal > pending.captureOrdinal) {
                    int index = cellIndex(pending.center, x, y, z);
                    BlockOverride previous = state.pendingOverrides.get(index);
                    if (previous == null
                            || timestamp > previous.timestamp
                            || timestamp == previous.timestamp && ordinal > previous.ordinal) {
                        state.pendingOverrides.remove(index);
                        state.pendingOverrides.put(index, new BlockOverride(cell, timestamp, ordinal));
                    }
                }
                recorded = true;
            }
            return recorded;
        }
    }

    static PreparedUpdate prepareUpdate(
            UUID playerId,
            UUID worldId,
            Center center,
            boolean forceFull) {
        Objects.requireNonNull(playerId, "player id");
        Objects.requireNonNull(worldId, "world id");
        Objects.requireNonNull(center, "center");
        synchronized (STATE_LOCK) {
            ProducerState state = STATES.get(playerId);
            if (state == null) {
                state = new ProducerState(nextGeneration());
                STATES.put(playerId, state);
            } else if (state.hasSnapshot && !worldId.equals(state.worldId)
                    || state.pending != null && !worldId.equals(state.pending.worldId)) {
                state.reset(nextGeneration());
                PacketQueue.removeCollisionWindows(playerId.toString());
            }

            if (!forceFull) {
                if (state.pending != null
                        && worldId.equals(state.pending.worldId)
                        && center.equals(state.pending.center)) return null;
                if (state.pending == null
                        && state.hasSnapshot
                        && worldId.equals(state.worldId)
                        && center.equals(state.center)) return null;
            }

            long sequence = state.nextSequence;
            boolean full = forceFull
                    || state.pending != null
                    || !state.hasSnapshot
                    || !worldId.equals(state.worldId)
                    || !overlaps(state.center, center)
                    || sequence != state.lastEmittedSequence + 1;
            state.nextSequence = Math.incrementExact(sequence);
            long token = nextToken();
            long captureTimestamp = System.currentTimeMillis();
            PendingToken pending = new PendingToken(token, state.generation, worldId, center);
            state.pending = pending;
            state.pendingUpdate = null;
            state.pendingOverrides.clear();

            Cell[] cells = new Cell[CELL_COUNT];
            Arrays.fill(cells, Cell.unknown());
            int[] sampleIndices;
            Center baseCenter;
            long baseSequence;
            if (full) {
                sampleIndices = allCellIndices();
                baseCenter = center;
                baseSequence = 0;
            } else {
                baseCenter = state.center;
                baseSequence = state.lastEmittedSequence;
                reuseOverlap(state.cells, baseCenter, cells, center);
                sampleIndices = enteringIndices(baseCenter, center);
            }
            PreparedUpdate update = new PreparedUpdate(
                    playerId,
                    state,
                    pending,
                    sequence,
                    baseSequence,
                    baseCenter,
                    center,
                    full,
                    cells,
                    sampleIndices,
                    captureTimestamp,
                    token);
            state.pendingUpdate = update;
            return update;
        }
    }

    static boolean publishPrepared(
            PreparedUpdate update,
            long timestamp,
            String uid,
            String username) {
        synchronized (STATE_LOCK) {
            if (!isCurrentLocked(update)) return false;
            for (Map.Entry<Integer, BlockOverride> entry : update.state.pendingOverrides.entrySet()) {
                update.cells[entry.getKey()] = entry.getValue().cell;
            }

            List<PacketCollisionWindow> fragments;
            try {
                CollisionWindowUpdate packetUpdate = update.toPacketUpdate();
                fragments = packetUpdate.toFragments(timestamp, uid, username);
                for (PacketCollisionWindow fragment : fragments) {
                    if (fragment.encodedDatagramLength()
                            > PacketCollisionWindow.MAX_DATAGRAM_LENGTH) {
                        abandonLocked(update);
                        return false;
                    }
                }
            } catch (IllegalArgumentException | IOException exception) {
                abandonLocked(update);
                return false;
            }

            boolean queued;
            try {
                queued = PacketQueue.pushCollisionWindow(
                        uid, update.pending.generation, update.sequence, fragments);
            } catch (RuntimeException exception) {
                abandonLocked(update);
                throw exception;
            }
            if (!queued) {
                PacketQueue.consumeDiscontinuity(uid);
                PacketQueue.markDiscontinuity(uid);
                update.recoveryRequired = true;
                update.state.reset(nextGeneration());
                PacketQueue.removeCollisionWindows(uid);
                return false;
            }
            if (update.sequence > update.state.lastEmittedSequence) {
                System.arraycopy(update.cells, 0, update.state.cells, 0, CELL_COUNT);
                update.state.hasSnapshot = true;
                update.state.worldId = update.pending.worldId;
                update.state.center = update.center;
                update.state.lastEmittedSequence = update.sequence;
            }
            if (update.state.pending == update.pending) {
                update.state.pending = null;
            }
            if (update.state.pendingUpdate == update) {
                update.state.pendingUpdate = null;
            }
            update.state.pendingOverrides.clear();
            return true;
        }
    }

    static boolean isCurrent(PreparedUpdate update) {
        synchronized (STATE_LOCK) {
            return isCurrentLocked(update);
        }
    }

    static boolean hasState(UUID playerId) {
        synchronized (STATE_LOCK) {
            return STATES.containsKey(playerId);
        }
    }

    static int[] allCellIndices() {
        int[] indices = new int[CELL_COUNT];
        for (int index = 0; index < CELL_COUNT; index++) indices[index] = index;
        return indices;
    }

    static int[] enteringIndices(Center baseCenter, Center center) {
        List<Integer> entering = PacketCollisionWindow.enteringCellIndices(
                baseCenter.x,
                baseCenter.y,
                baseCenter.z,
                center.x,
                center.y,
                center.z);
        int[] indices = new int[entering.size()];
        for (int index = 0; index < entering.size(); index++) {
            indices[index] = entering.get(index);
        }
        return indices;
    }

    static List<RegionSlice> splitRegions(Center center, int[] sampleIndices) {
        Map<ChunkCoordinate, List<Integer>> grouped = new LinkedHashMap<>();
        for (int index : sampleIndices) {
            int worldX = Math.addExact(center.x, offsetX(index));
            int worldZ = Math.addExact(center.z, offsetZ(index));
            ChunkCoordinate coordinate = new ChunkCoordinate(worldX >> 4, worldZ >> 4);
            List<Integer> indices = grouped.get(coordinate);
            if (indices == null) {
                indices = new ArrayList<>();
                grouped.put(coordinate, indices);
            }
            indices.add(index);
        }
        if (grouped.isEmpty() || grouped.size() > 4) {
            throw new IllegalArgumentException("collision window must intersect one to four chunks");
        }
        List<RegionSlice> regions = new ArrayList<>(grouped.size());
        for (Map.Entry<ChunkCoordinate, List<Integer>> entry : grouped.entrySet()) {
            int[] indices = new int[entry.getValue().size()];
            for (int index = 0; index < indices.length; index++) {
                indices[index] = entry.getValue().get(index);
            }
            regions.add(new RegionSlice(entry.getKey().x, entry.getKey().z, indices));
        }
        return Collections.unmodifiableList(regions);
    }

    private static void captureRegion(
            World world,
            int minHeight,
            int maxHeight,
            PreparedUpdate update,
            RegionSlice region) {
        if (!world.isChunkLoaded(region.chunkX, region.chunkZ)) return;
        Chunk chunk = world.getChunkAt(region.chunkX, region.chunkZ);
        if (chunk == null || !chunk.isLoaded()) return;

        for (int index : region.indices) {
            int blockY = Math.addExact(update.center.y, offsetY(index));
            if (blockY < minHeight || blockY >= maxHeight) continue;
            int blockX = Math.addExact(update.center.x, offsetX(index));
            int blockZ = Math.addExact(update.center.z, offsetZ(index));
            try {
                Block block = chunk.getBlock(blockX & 15, blockY, blockZ & 15);
                update.cells[index] = BlockCompat.isAir(block)
                        ? Cell.knownAir()
                        : Cell.knownBlock(BlockCompat.getBlockDataString(block));
            } catch (RuntimeException exception) {
                update.cells[index] = Cell.unknown();
            }
        }
    }

    private static void reuseOverlap(
            Cell[] oldCells,
            Center oldCenter,
            Cell[] newCells,
            Center newCenter) {
        for (int index = 0; index < CELL_COUNT; index++) {
            long oldDx = (long) newCenter.x + offsetX(index) - oldCenter.x;
            long oldDy = (long) newCenter.y + offsetY(index) - oldCenter.y;
            long oldDz = (long) newCenter.z + offsetZ(index) - oldCenter.z;
            if (Math.abs(oldDx) <= RADIUS
                    && Math.abs(oldDy) <= RADIUS
                    && Math.abs(oldDz) <= RADIUS) {
                int oldIndex = PacketCollisionWindow.collisionWindowIndex(
                        (int) oldDx, (int) oldDy, (int) oldDz);
                newCells[index] = oldCells[oldIndex];
            }
        }
    }

    private static void abandon(PreparedUpdate update) {
        synchronized (STATE_LOCK) {
            abandonLocked(update);
        }
    }

    private static void abandonLocked(PreparedUpdate update) {
        if (!isCurrentLocked(update)) return;
        if (update.state.pending == update.pending) {
            update.state.pending = null;
        }
        if (update.state.pendingUpdate == update) {
            update.state.pendingUpdate = null;
        }
        update.state.pendingOverrides.clear();
    }

    private static boolean isCurrentLocked(PreparedUpdate update) {
        ProducerState current = STATES.get(update.playerId);
        if (current != update.state) return false;
        if (update.state.generation != update.pending.generation) return false;
        return true;
    }

    private static boolean overlaps(Center left, Center right) {
        return Math.abs((long) left.x - right.x) < PacketCollisionWindow.COLLISION_WINDOW_EDGE
                && Math.abs((long) left.y - right.y) < PacketCollisionWindow.COLLISION_WINDOW_EDGE
                && Math.abs((long) left.z - right.z) < PacketCollisionWindow.COLLISION_WINDOW_EDGE;
    }

    private static boolean contains(Center center, int x, int y, int z) {
        return within(x, center.x) && within(y, center.y) && within(z, center.z);
    }

    private static boolean within(int coordinate, int center) {
        return Math.abs((long) coordinate - center) <= RADIUS;
    }

    private static int offsetX(int index) {
        return index % PacketCollisionWindow.COLLISION_WINDOW_EDGE - RADIUS;
    }

    private static int offsetY(int index) {
        return index
                / (PacketCollisionWindow.COLLISION_WINDOW_EDGE
                        * PacketCollisionWindow.COLLISION_WINDOW_EDGE)
                - RADIUS;
    }

    private static int offsetZ(int index) {
        return index / PacketCollisionWindow.COLLISION_WINDOW_EDGE
                % PacketCollisionWindow.COLLISION_WINDOW_EDGE
                - RADIUS;
    }

    private static int cellIndex(Center center, int x, int y, int z) {
        return PacketCollisionWindow.collisionWindowIndex(
                Math.subtractExact(x, center.x),
                Math.subtractExact(y, center.y),
                Math.subtractExact(z, center.z));
    }

    private static long initialGenerationSeed() {
        synchronized (GENERATION_LOCK) {
            long seed = Math.max(1L, System.currentTimeMillis());
            seed = Math.max(seed, propertyGeneration());
            System.setProperty(GENERATION_PROPERTY, Long.toString(seed));
            return seed;
        }
    }

    private static void configureGenerationPersistence(File folder) {
        if (folder == null || !folder.isDirectory()) return;
        synchronized (GENERATION_LOCK) {
            dataFolder = folder;
            long highWater = Math.max(GENERATIONS.get(), propertyGeneration());
            highWater = Math.max(highWater, persistedGeneration(folder));
            GENERATIONS.set(highWater);
            System.setProperty(GENERATION_PROPERTY, Long.toString(highWater));
            persistGeneration(folder, highWater);
        }
    }

    private static long nextGeneration() {
        synchronized (GENERATION_LOCK) {
            long highWater = Math.max(GENERATIONS.get(), propertyGeneration());
            File folder = dataFolder;
            if (folder != null) highWater = Math.max(highWater, persistedGeneration(folder));
            long generation = Math.incrementExact(highWater);
            if (generation <= 0) throw new IllegalStateException("collision generation overflow");
            GENERATIONS.set(generation);
            System.setProperty(GENERATION_PROPERTY, Long.toString(generation));
            if (folder != null) persistGeneration(folder, generation);
            return generation;
        }
    }

    private static long propertyGeneration() {
        return parseGeneration(System.getProperty(GENERATION_PROPERTY));
    }

    private static long persistedGeneration(File folder) {
        Path path = new File(folder, GENERATION_FILE).toPath();
        try {
            if (!Files.isRegularFile(path)) return 0L;
            return parseGeneration(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        } catch (IOException | SecurityException exception) {
            return 0L;
        }
    }

    private static long parseGeneration(String value) {
        if (value == null) return 0L;
        try {
            long generation = Long.parseLong(value.trim());
            return generation > 0 ? generation : 0L;
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static void persistGeneration(File folder, long generation) {
        Path target = new File(folder, GENERATION_FILE).toPath();
        Path temporary = new File(folder, GENERATION_FILE + ".tmp").toPath();
        try {
            Files.write(temporary, Long.toString(generation).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | SecurityException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException | SecurityException ignored) {
                return;
            }
        }
    }

    private static long nextToken() {
        long token = TOKENS.incrementAndGet();
        if (token <= 0) throw new IllegalStateException("collision token overflow");
        return token;
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
            return Objects.hash(x, y, z);
        }
    }

    static final class PreparedUpdate {
        private final UUID playerId;
        private final ProducerState state;
        private final PendingToken pending;
        private final long sequence;
        private final long baseSequence;
        private final Center baseCenter;
        private final Center center;
        private final boolean full;
        private final Cell[] cells;
        private final int[] sampleIndices;
        private final long captureTimestamp;
        private final long captureOrdinal;
        private boolean recoveryRequired;

        private PreparedUpdate(
                UUID playerId,
                ProducerState state,
                PendingToken pending,
                long sequence,
                long baseSequence,
                Center baseCenter,
                Center center,
                boolean full,
                Cell[] cells,
                int[] sampleIndices,
                long captureTimestamp,
                long captureOrdinal) {
            this.playerId = playerId;
            this.state = state;
            this.pending = pending;
            this.sequence = sequence;
            this.baseSequence = baseSequence;
            this.baseCenter = baseCenter;
            this.center = center;
            this.full = full;
            this.cells = cells;
            this.sampleIndices = sampleIndices;
            this.captureTimestamp = captureTimestamp;
            this.captureOrdinal = captureOrdinal;
        }

        boolean isFull() {
            return full;
        }

        long generation() {
            return pending.generation;
        }

        long sequence() {
            return sequence;
        }

        Center baseCenter() {
            return baseCenter;
        }

        Center center() {
            return center;
        }

        Cell[] cells() {
            return cells;
        }

        int[] sampleIndices() {
            return sampleIndices.clone();
        }

        private CollisionWindowUpdate toPacketUpdate() {
            if (full) {
                Cell[] snapshot = cells.clone();
                return CollisionWindowUpdate.full(
                        pending.generation,
                        sequence,
                        center.x,
                        center.y,
                        center.z,
                        Arrays.asList(snapshot));
            }
            List<CellUpdate> updates = new ArrayList<>(sampleIndices.length);
            for (int index : sampleIndices) {
                updates.add(new CellUpdate(index, cells[index]));
            }
            return CollisionWindowUpdate.delta(
                    pending.generation,
                    sequence,
                    baseSequence,
                    baseCenter.x,
                    baseCenter.y,
                    baseCenter.z,
                    center.x,
                    center.y,
                    center.z,
                    updates);
        }
    }

    static final class RegionSlice {
        final int chunkX;
        final int chunkZ;
        final int[] indices;

        private RegionSlice(int chunkX, int chunkZ, int[] indices) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.indices = indices;
        }

        int size() {
            return indices.length;
        }
    }

    static final class Assembly {
        private final AtomicInteger remaining;
        private final AtomicIntegerArray completed;
        private final Runnable completion;

        Assembly(int regionCount, Runnable completion) {
            if (regionCount < 1 || regionCount > 4) {
                throw new IllegalArgumentException("collision assembly region count is out of range");
            }
            this.remaining = new AtomicInteger(regionCount);
            this.completed = new AtomicIntegerArray(regionCount);
            this.completion = Objects.requireNonNull(completion, "completion");
        }

        void complete(int regionIndex) {
            if (!completed.compareAndSet(regionIndex, 0, 1)) return;
            if (remaining.decrementAndGet() == 0) completion.run();
        }
    }

    private static final class ProducerState {
        private long generation;
        private long nextSequence;
        private long lastEmittedSequence;
        private UUID worldId;
        private Center center;
        private final Cell[] cells = new Cell[CELL_COUNT];
        private final LinkedHashMap<Integer, BlockOverride> pendingOverrides = new LinkedHashMap<>();
        private boolean hasSnapshot;
        private PendingToken pending;
        private PreparedUpdate pendingUpdate;

        private ProducerState(long generation) {
            reset(generation);
        }

        private void reset(long generation) {
            this.generation = generation;
            this.nextSequence = 1;
            this.lastEmittedSequence = 0;
            this.worldId = null;
            this.center = null;
            Arrays.fill(cells, Cell.unknown());
            this.hasSnapshot = false;
            this.pending = null;
            this.pendingUpdate = null;
            this.pendingOverrides.clear();
        }

        private void discard() {
            Arrays.fill(cells, Cell.unknown());
            hasSnapshot = false;
            pending = null;
            pendingUpdate = null;
            pendingOverrides.clear();
            worldId = null;
            center = null;
        }
    }

    private static final class BlockOverride {
        private final Cell cell;
        private final long timestamp;
        private final long ordinal;

        private BlockOverride(Cell cell, long timestamp, long ordinal) {
            this.cell = cell;
            this.timestamp = timestamp;
            this.ordinal = ordinal;
        }
    }

    private static final class PendingToken {
        private final long token;
        private final long generation;
        private final UUID worldId;
        private final Center center;

        private PendingToken(long token, long generation, UUID worldId, Center center) {
            this.token = token;
            this.generation = generation;
            this.worldId = worldId;
            this.center = center;
        }
    }

    private static final class ChunkCoordinate {
        private final int x;
        private final int z;

        private ChunkCoordinate(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ChunkCoordinate)) return false;
            ChunkCoordinate coordinate = (ChunkCoordinate) other;
            return x == coordinate.x && z == coordinate.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }

    private static final class RegionCapture implements Runnable {
        private final World world;
        private final int minHeight;
        private final int maxHeight;
        private final PreparedUpdate update;
        private final RegionSlice region;
        private final Assembly assembly;
        private final int regionIndex;

        private RegionCapture(
                World world,
                int minHeight,
                int maxHeight,
                PreparedUpdate update,
                RegionSlice region,
                Assembly assembly,
                int regionIndex) {
            this.world = world;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.update = update;
            this.region = region;
            this.assembly = assembly;
            this.regionIndex = regionIndex;
        }

        @Override
        public void run() {
            try {
                captureRegion(world, minHeight, maxHeight, update, region);
            } catch (RuntimeException exception) {
                for (int index : region.indices) update.cells[index] = Cell.unknown();
            } finally {
                assembly.complete(regionIndex);
            }
        }

        private void fail() {
            assembly.complete(regionIndex);
        }
    }
}
