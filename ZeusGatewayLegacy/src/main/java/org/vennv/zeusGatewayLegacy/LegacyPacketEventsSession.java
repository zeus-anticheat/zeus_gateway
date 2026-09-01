package org.vennv.zeusGatewayLegacy;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRespawn;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketBlockChangeEvent;
import org.vennv.packets.PacketChunkData;
import org.vennv.packets.PacketPlayerAbilities;
import org.vennv.packets.PacketPlayerChangeMode;
import org.vennv.packets.PacketPlayerBlockFace;
import org.vennv.packets.PacketPlayerBlockRayTrace;
import org.vennv.packets.PacketPlayerClickWindow;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketPlayerSwingHand;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.packets.PacketShulkerBoxAction;
import org.vennv.utils.ServerBoundPlayerCommandActions;

final class LegacyPacketEventsSession implements AutoCloseable {
    static final int MAX_PENDING = 4096;
    private static final int MAX_TASKS_PER_DRAIN = 128;
    private static final Set<String> VANILLA_SHULKER_PATHS = new HashSet<String>(Arrays.asList(
            "shulker_box", "white_shulker_box", "orange_shulker_box",
            "magenta_shulker_box", "light_blue_shulker_box", "yellow_shulker_box",
            "lime_shulker_box", "pink_shulker_box", "gray_shulker_box",
            "light_gray_shulker_box", "cyan_shulker_box", "purple_shulker_box",
            "blue_shulker_box", "brown_shulker_box", "green_shulker_box",
            "red_shulker_box", "black_shulker_box"));
    static final int MAX_WORLD_PENDING = 16;
    private static final long WARNING_INTERVAL_MS = 5000L;

    private final EventManager manager;
    private final List<PacketListenerCommon> handles = new ArrayList<PacketListenerCommon>();
    private final ConcurrentHashMap<UUID, MovementState> movement = new ConcurrentHashMap<UUID, MovementState>();
    private final ConcurrentHashMap<UUID, AtomicLong> movementSequences = new ConcurrentHashMap<UUID, AtomicLong>();
    private final ConcurrentHashMap<UUID, AtomicLong> serverAbilitySequences = new ConcurrentHashMap<UUID, AtomicLong>();
    private final ConcurrentHashMap<UUID, AtomicLong> clientAbilitySequences = new ConcurrentHashMap<UUID, AtomicLong>();
    private final StateAcknowledgements acknowledgements = new StateAcknowledgements();
    private final PendingTasks orderedTasks = new PendingTasks(MAX_PENDING);
    private final Set<UUID> discontinuities = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> worldReady = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private final AtomicLong nextWarningAt = new AtomicLong();
    private final ThreadPoolExecutor worldExecutor;
    private final ConcurrentHashMap<UUID, AtomicLong> worldSequences = new ConcurrentHashMap<UUID, AtomicLong>();
    private final ConcurrentHashMap<UUID, AtomicLong> worldGenerations = new ConcurrentHashMap<UUID, AtomicLong>();
    private final ConcurrentHashMap<UUID, Long> failedWorldAt = new ConcurrentHashMap<UUID, Long>();
    private final ThreadLocal<Long> currentWorldGeneration = new ThreadLocal<Long>();
    private final LegacyGatewaySession plugin;
    private volatile boolean closed;

    private LegacyPacketEventsSession(LegacyGatewaySession plugin) {
        this.plugin = plugin;
        this.manager = PacketEvents.getAPI().getEventManager();
        this.worldExecutor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(MAX_WORLD_PENDING),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable task) {
                        Thread thread = new Thread(task, "ZeusGatewayLegacy-WorldPackets");
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    static LegacyPacketEventsSession register(LegacyGatewaySession plugin) {
        LegacyPacketEventsSession session = new LegacyPacketEventsSession(plugin);
        try {
            session.add(new MovementListener(session));
            session.add(new SwingListener(session));
            session.add(new StateListener(session));
            session.add(new EntityActionListener(session));
            session.add(new DiggingListener(session));
            session.add(new AttackListener(session));
            session.add(new HeldItemListener(session));
            session.add(new ClickListener(session));
            session.add(new WorldListener(session));
            return session;
        } catch (RuntimeException error) {
            session.close();
            throw error;
        }
    }

    synchronized void awaitWorldResync(UUID uuid) {
        if (uuid != null && !worldReady.contains(uuid)) fail(uuid);
    }

    synchronized void collisionRecovered(UUID uuid) {
        if (uuid == null) return;
        failedWorldAt.remove(uuid);
        worldReady.add(uuid);
        recover(uuid);
    }

    synchronized void resetLifecycle(UUID uuid, boolean requireFullChunk) {
        if (uuid == null) return;
        removePending(uuid);
        for (Runnable task : new ArrayList<Runnable>(worldExecutor.getQueue())) {
            if (task instanceof WorldTask && uuid.equals(((WorldTask) task).uuid)
                    && worldExecutor.remove(task)) {
                ((WorldTask) task).cancel();
            }
        }
        movement.remove(uuid);
        discontinuities.add(uuid);
        worldGeneration(uuid).incrementAndGet();
        LegacyPacketQueue.drop(uuid);
        if (requireFullChunk) {
            worldReady.remove(uuid);
            AtomicLong counter = worldCounter(uuid);
            long barrier = counter.incrementAndGet();
            mergeFailedWorldSequence(uuid, barrier);
            fail(uuid);
        }
    }

    synchronized void outputOverflow(UUID owner, boolean global) {
        if (global) {
            Set<UUID> affected = new HashSet<UUID>();
            affected.addAll(movement.keySet());
            affected.addAll(worldSequences.keySet());
            for (Player player : Bukkit.getOnlinePlayers()) affected.add(player.getUniqueId());
            for (UUID uuid : affected) markOutputLoss(uuid);
            warn("Output queue full; all tracked players paused until full chunk resync");
        } else {
            markOutputLoss(owner);
            warn("Output queue full for " + owner + "; player paused until full chunk resync");
        }
    }

    private void markOutputLoss(UUID uuid) {
        if (uuid == null) return;
        long barrier = worldCounter(uuid).incrementAndGet();
        mergeFailedWorldSequence(uuid, barrier);
        worldReady.remove(uuid);
        fail(uuid);
    }

    boolean dispatchOrdered(UUID uuid, Runnable task) {
        return dispatchInput(uuid, task);
    }

    synchronized void leave(final Player player, final long timestamp) {
        UUID uuid = player.getUniqueId();
        removePending(uuid);
        for (Runnable task : new ArrayList<Runnable>(worldExecutor.getQueue())) {
            if (task instanceof WorldTask && uuid.equals(((WorldTask) task).uuid)
                    && worldExecutor.remove(task)) {
                ((WorldTask) task).cancel();
            }
        }
        movement.remove(uuid);
        movementSequences.remove(uuid);
        serverAbilitySequences.remove(uuid);
        clientAbilitySequences.remove(uuid);
        acknowledgements.clearPlayer(uuid);
        orderedTasks.remove(uuid);
        discontinuities.remove(uuid);
        worldReady.remove(uuid);
        worldSequences.remove(uuid);
        worldGenerations.remove(uuid);
        failedWorldAt.remove(uuid);
        LegacyPacketQueue.pushControl(uuid, new org.vennv.packets.PacketPlayerLeave(
                timestamp, uuid.toString(), player.getName()));
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        for (int i = handles.size() - 1; i >= 0; i--) {
            try {
                manager.unregisterListener(handles.get(i));
            } catch (RuntimeException | LinkageError error) {
                plugin.plugin().getLogger().warning("[ZeusGatewayLegacy] Failed to unregister listener: " + error.getMessage());
            }
        }
        handles.clear();
        List<Runnable> worldTasks = worldExecutor.shutdownNow();
        for (Runnable task : worldTasks) {
            if (task instanceof WorldTask) ((WorldTask) task).cancel();
        }
        try {
            worldExecutor.awaitTermination(1L, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        movement.clear();
        movementSequences.clear();
        serverAbilitySequences.clear();
        clientAbilitySequences.clear();
        acknowledgements.clear();
        orderedTasks.clear();
        discontinuities.clear();
        worldReady.clear();
        worldSequences.clear();
        worldGenerations.clear();
        failedWorldAt.clear();
        currentWorldGeneration.remove();
    }

    void emitBukkitState(final Player player) {
        emitBukkitState(player, player == null ? null : player.getGameMode());
    }

    void emitBukkitState(final Player player, final org.bukkit.GameMode modeOverride) {
        if (player == null || player.getUniqueId() == null || player.getName() == null) return;
        final UUID uuid = player.getUniqueId();
        dispatchInput(uuid, new Runnable() {
            @Override
            public void run() {
                emitBukkitStateNow(player, modeOverride);
            }
        });
    }

    void emitBukkitStateControl(Player player) {
        if (player == null || player.getUniqueId() == null || player.getName() == null) return;
        emitBukkitStateNow(player, player.getGameMode());
    }

    private void emitBukkitStateNow(Player player, org.bukkit.GameMode modeOverride) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        int mode = gameModeId(modeOverride == null ? player.getGameMode() : modeOverride);
        boolean canFly = player.getAllowFlight();
        boolean flying = canFly && player.isFlying();
        float flySpeed = validFlySpeed(player.getFlySpeed());
        emitMode(uuid, name, mode);
        emitServerAbilities(uuid, name, canFly, flying, flySpeed);
    }

    private void emitMode(UUID uuid, String name, int mode) {
        LegacyPacketQueue.push(uuid, new PacketPlayerChangeMode(
                System.currentTimeMillis(), uuid.toString(), name, mode));
    }

    private void emitServerAbilities(
            UUID uuid, String name, boolean canFly, boolean flying, float flySpeed) {
        LegacyPacketQueue.push(uuid, PacketPlayerAbilities.server(
                System.currentTimeMillis(), uuid.toString(), name,
                next(serverAbilitySequences, uuid), canFly, flying, flySpeed));
    }

    private static long next(ConcurrentHashMap<UUID, AtomicLong> counters, UUID uuid) {
        return counters.computeIfAbsent(uuid, ignored -> new AtomicLong()).incrementAndGet();
    }

    private static int gameModeId(org.bukkit.GameMode mode) {
        if (mode == null) return 0;
        switch (mode) {
            case CREATIVE: return 1;
            case ADVENTURE: return 2;
            case SPECTATOR: return 3;
            default: return 0;
        }
    }

    private static float validFlySpeed(float speed) {
        return Float.isFinite(speed) && speed >= 0.0f && speed <= 1.0f ? speed : 0.05f;
    }

    private void add(PacketListenerCommon listener) {
        PacketListenerCommon handle = manager.registerListener(listener);
        if (handle == null) throw new IllegalStateException("PacketEvents returned a null listener handle");
        handles.add(handle);
    }

    private boolean dispatch(UUID uuid, Runnable task) {
        return dispatch(uuid, task, true);
    }

    private boolean dispatchInput(UUID uuid, Runnable task) {
        return dispatch(uuid, task, false);
    }

    private boolean dispatch(UUID uuid, Runnable task, boolean worldDependent) {
        if (closed || uuid == null || task == null) return false;
        if (!orderedTasks.offer(uuid, task, worldDependent)) {
            if (worldDependent && orderedTasks.isFailed(uuid)) return false;
            fail(uuid);
            warn("Dropped raw packet task for " + uuid + ": dispatcher queue is full; simulation paused until full chunk resync");
            return false;
        }
        scheduleDrain();
        return true;
    }

    private void fail(UUID uuid) {
        if (uuid == null) return;
        orderedTasks.fail(uuid);
        discontinuities.add(uuid);
    }

    private void recover(UUID uuid) {
        if (uuid == null) return;
        orderedTasks.recover(uuid);
        scheduleDrain();
    }

    private void block(UUID uuid) {
        if (!closed) orderedTasks.block(uuid);
    }

    private void unblock(UUID uuid) {
        orderedTasks.unblock(uuid);
        scheduleDrain();
    }

    private void removePending(UUID uuid) {
        orderedTasks.removePending(uuid);
    }

    private void scheduleDrain() {
        if (closed || !orderedTasks.hasRunnable() || !drainScheduled.compareAndSet(false, true)) return;
        try {
            plugin.plugin().getServer().getScheduler().runTask(plugin.plugin(), new Runnable() {
                @Override
                public void run() {
                    drain();
                }
            });
        } catch (RuntimeException error) {
            drainScheduled.set(false);
            for (UUID uuid : orderedTasks.failAllAndClear()) {
                discontinuities.add(uuid);
            }
            warn("Failed to schedule packet drain: " + error.getMessage());
        }
    }

    private void drain() {
        try {
            int processed = 0;
            int examined = 0;
            int available = orderedTasks.size();
            Set<UUID> deferred = new HashSet<UUID>();
            while (!closed && processed < MAX_TASKS_PER_DRAIN && examined < available) {
                PendingTask task = orderedTasks.poll();
                if (task == null) break;
                examined++;
                if (orderedTasks.shouldDrop(task)) continue;
                if (orderedTasks.shouldDefer(task, deferred)) {
                    deferred.add(task.uuid);
                    orderedTasks.offer(task);
                    continue;
                }
                try {
                    task.task.run();
                } catch (RuntimeException | LinkageError error) {
                    fail(task.uuid);
                    warn("Ordered packet task failed for " + task.uuid + ": " + error.getMessage());
                }
                processed++;
            }
        } finally {
            drainScheduled.set(false);
            scheduleDrain();
        }
    }

    private void submitWorld(PacketSendEvent event) {
        if (closed || event == null || event.getUser() == null) return;
        UUID uuid = event.getUser().getUUID();
        if (uuid == null) return;
        long sequence = worldCounter(uuid).incrementAndGet();
        long generation = worldGeneration(uuid).get();
        PacketSendEvent cloned;
        try {
            cloned = event.clone();
        } catch (RuntimeException | LinkageError error) {
            mergeFailedWorldSequence(uuid, sequence);
            fail(uuid);
            warn("World packet clone failed for " + uuid + ": " + error.getMessage());
            return;
        }
        WorldTask task = new WorldTask(this, cloned, uuid, sequence, generation);
        block(uuid);
        try {
            worldExecutor.execute(task);
        } catch (RejectedExecutionException error) {
            mergeFailedWorldSequence(uuid, sequence);
            task.reject();
            warn("World capture saturated for " + uuid + "; raw movement paused until full chunk resync");
        }
    }

    private AtomicLong worldCounter(UUID uuid) {
        AtomicLong counter = worldSequences.get(uuid);
        if (counter == null) {
            AtomicLong created = new AtomicLong();
            AtomicLong existing = worldSequences.putIfAbsent(uuid, created);
            counter = existing == null ? created : existing;
        }
        return counter;
    }

    private AtomicLong worldGeneration(UUID uuid) {
        AtomicLong generation = worldGenerations.get(uuid);
        if (generation == null) {
            AtomicLong created = new AtomicLong();
            AtomicLong existing = worldGenerations.putIfAbsent(uuid, created);
            generation = existing == null ? created : existing;
        }
        return generation;
    }

    private boolean currentWorld(UUID uuid) {
        Long generation = currentWorldGeneration.get();
        return generation != null && generation.longValue() == worldGeneration(uuid).get();
    }

    private void mergeFailedWorldSequence(UUID uuid, long sequence) {
        while (true) {
            Long current = failedWorldAt.get(uuid);
            if (current != null && current >= sequence) return;
            if (current == null) {
                if (failedWorldAt.putIfAbsent(uuid, sequence) == null) return;
            } else if (failedWorldAt.replace(uuid, current, sequence)) {
                return;
            }
        }
    }

    private boolean processWorld(PacketSendEvent event, UUID uuid) {
        User user = event.getUser();
        String name = user == null ? null : user.getName();
        if (user == null || name == null) throw new IllegalStateException("World packet has no player identity");
        PacketTypeCommon type = event.getPacketType();
        long timestamp = System.currentTimeMillis();
        if (type == PacketType.Play.Server.BLOCK_CHANGE) {
            WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(event);
            Vector3i position = packet.getBlockPosition();
            if (position == null) throw new IllegalStateException("PacketEvents returned an empty block position");
            emitBlock(timestamp, uuid, name, position.getX(), position.getY(), position.getZ(), packet.getBlockState());
            return false;
        }
        if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(event);
            WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks = packet.getBlocks();
            if (blocks == null) throw new IllegalStateException("PacketEvents returned empty multi-block data");
            ClientVersion version = event.getClientVersion();
            for (WrapperPlayServerMultiBlockChange.EncodedBlock block : blocks) {
                if (block == null) throw new IllegalStateException("PacketEvents returned a null changed block");
                emitBlock(timestamp, uuid, name, block.getX(), block.getY(), block.getZ(), block.getBlockState(version));
            }
            return false;
        }
        if (type == PacketType.Play.Server.BLOCK_ACTION) {
            WrapperPlayServerBlockAction action = new WrapperPlayServerBlockAction(event);
            WrappedBlockState block = action.getBlockType();
            String blockType = block == null || block.getType() == null
                    ? null
                    : block.getType().getName();
            if (!isVanillaShulkerAction(blockType, action.getActionId())) return false;
            Vector3i position = action.getBlockPosition();
            if (position == null) throw new IllegalStateException("PacketEvents returned an empty block action position");
            emitShulkerAction(
                    timestamp, uuid, name,
                    position.getX(), position.getY(), position.getZ(),
                    action.getActionId(), action.getActionData());
        }
        return false;
    }

    static List<ChunkBatch> partitionChunkBlocks(
            String uid, String name, boolean fullChunk, List<PacketChunkData.BlockData> blocks) {
        int baseSize = PacketChunkData.encodedBaseSize(uid, name);
        int encodedSize = baseSize;
        boolean reset = fullChunk;
        List<ChunkBatch> batches = new ArrayList<ChunkBatch>();
        List<PacketChunkData.BlockData> batch = new ArrayList<PacketChunkData.BlockData>();
        for (PacketChunkData.BlockData block : blocks) {
            if (block == null || block.blockType == null) {
                throw new IllegalArgumentException("Chunk block state is missing");
            }
            int blockSize = PacketChunkData.encodedBlockSize(block.blockType);
            if (baseSize + blockSize > PacketChunkData.MAX_UDP_PAYLOAD) {
                throw new IllegalArgumentException("Chunk block state exceeds UDP payload");
            }
            if (encodedSize + blockSize > PacketChunkData.MAX_UDP_PAYLOAD) {
                batches.add(new ChunkBatch(reset, batch));
                reset = false;
                batch = new ArrayList<PacketChunkData.BlockData>();
                encodedSize = baseSize;
            }
            batch.add(block);
            encodedSize += blockSize;
        }
        if (!batch.isEmpty() || reset) batches.add(new ChunkBatch(reset, batch));
        return batches;
    }

    static long nextMovementSequence(AtomicLong counter, boolean discontinuity) {
        long sequence = counter.incrementAndGet();
        return discontinuity ? counter.incrementAndGet() : sequence;
    }

    static short transactionId(Integer actionNumber) {
        return actionNumber == null ? (short) 0 : actionNumber.shortValue();
    }

    static ServerBoundPlayerCommandActions playerAction(String actionName) {
        if (actionName == null) return null;
        if ("START_SNEAKING".equals(actionName)) return ServerBoundPlayerCommandActions.START_SNEAKING;
        if ("STOP_SNEAKING".equals(actionName)) return ServerBoundPlayerCommandActions.STOP_SNEAKING;
        if ("START_SPRINTING".equals(actionName)) return ServerBoundPlayerCommandActions.START_SPRINTING;
        if ("STOP_SPRINTING".equals(actionName)) return ServerBoundPlayerCommandActions.STOP_SPRINTING;
        if ("START_JUMPING_WITH_HORSE".equals(actionName)) return ServerBoundPlayerCommandActions.START_RIDING_JUMP;
        if ("STOP_JUMPING_WITH_HORSE".equals(actionName)) return ServerBoundPlayerCommandActions.STOP_RIDING_JUMP;
        if ("OPEN_HORSE_INVENTORY".equals(actionName)) return ServerBoundPlayerCommandActions.OPEN_INVENTORY;
        return null;
    }

    private synchronized void emitBlock(
            long timestamp, UUID uuid, String name, int x, int y, int z, WrappedBlockState state) {
        if (!currentWorld(uuid)) throw new IllegalStateException("stale world packet generation");
        String blockType = state == null ? "minecraft:air" : state.toString();
        if (!LegacyPacketQueue.push(uuid, new PacketBlockChangeEvent(
                timestamp, uuid.toString(), name, x, y, z, blockType, (byte) 0))) {
            throw new IllegalStateException("output queue rejected block change");
        }
        plugin.collisionBlockChanged(uuid);
    }

    private static boolean isVanillaShulkerAction(PacketSendEvent event) {
        PacketSendEvent probe = null;
        try {
            probe = event.clone();
            WrapperPlayServerBlockAction action = new WrapperPlayServerBlockAction(probe);
            WrappedBlockState block = action.getBlockType();
            String blockType = block == null || block.getType() == null
                    ? null
                    : block.getType().getName();
            return isVanillaShulkerAction(blockType, action.getActionId());
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        } finally {
            if (probe != null) probe.cleanUp();
        }
    }

    static boolean isVanillaShulkerAction(String blockType, int actionId) {
        if (actionId != 1 || blockType == null) return false;
        String normalized = blockType.toLowerCase(java.util.Locale.ROOT);
        int separator = normalized.indexOf(':');
        if (separator >= 0 && !normalized.startsWith("minecraft:")) return false;
        String path = separator < 0 ? normalized : normalized.substring(separator + 1);
        return VANILLA_SHULKER_PATHS.contains(path);
    }

    private synchronized void emitShulkerAction(
            long timestamp, UUID uuid, String name,
            int x, int y, int z, int actionId, int viewerCount) {
        if (!currentWorld(uuid)) throw new IllegalStateException("stale world packet generation");
        if (!LegacyPacketQueue.push(uuid, new PacketShulkerBoxAction(
                timestamp, uuid.toString(), name, x, y, z,
                (byte) actionId, (byte) viewerCount))) {
            throw new IllegalStateException("output queue rejected shulker action");
        }
    }

    private synchronized void completeWorld(UUID uuid, long sequence, boolean fullChunk) {
        Long failure = failedWorldAt.get(uuid);
        if (canRecoverWorld(failure, sequence, fullChunk)) {
            failedWorldAt.remove(uuid);
            worldReady.add(uuid);
            recover(uuid);
        }
    }

    static boolean canRecoverWorld(Long failedAt, long sequence, boolean fullChunk) {
        return fullChunk && (failedAt == null || sequence > failedAt.longValue());
    }

    private void warn(String message) {
        long now = System.currentTimeMillis();
        long next = nextWarningAt.get();
        if (now >= next && nextWarningAt.compareAndSet(next, now + WARNING_INTERVAL_MS)) {
            plugin.plugin().getLogger().warning("[ZeusGatewayLegacy] " + message);
        }
    }

    static final class ChunkBatch {
        final boolean reset;
        final List<PacketChunkData.BlockData> blocks;

        private ChunkBatch(boolean reset, List<PacketChunkData.BlockData> blocks) {
            this.reset = reset;
            this.blocks = Collections.unmodifiableList(new ArrayList<PacketChunkData.BlockData>(blocks));
        }
    }

    private static final class WorldTask implements Runnable {
        private final LegacyPacketEventsSession session;
        private final PacketSendEvent event;
        private final UUID uuid;
        private final long sequence;
        private final long generation;
        private final AtomicBoolean cleaned = new AtomicBoolean();

        private WorldTask(
                LegacyPacketEventsSession session, PacketSendEvent event,
                UUID uuid, long sequence, long generation) {
            this.session = session;
            this.event = event;
            this.uuid = uuid;
            this.sequence = sequence;
            this.generation = generation;
        }

        @Override
        public void run() {
            boolean success = false;
            session.currentWorldGeneration.set(generation);
            try {
                if (!session.currentWorld(uuid)) throw new IllegalStateException("stale world packet generation");
                boolean fullChunk = session.processWorld(event, uuid);
                if (!session.currentWorld(uuid)) throw new IllegalStateException("stale world packet generation");
                session.completeWorld(uuid, sequence, fullChunk);
                success = true;
            } catch (RuntimeException | LinkageError error) {
                session.warn("World packet capture failed for " + uuid + ": " + error.getMessage());
            } finally {
                session.currentWorldGeneration.remove();
                cleanUp();
                session.unblock(uuid);
                if (!success) session.fail(uuid);
            }
        }

        private void reject() {
            cleanUp();
            session.unblock(uuid);
            session.fail(uuid);
        }

        private void cancel() {
            cleanUp();
            session.unblock(uuid);
        }

        private void cleanUp() {
            if (cleaned.compareAndSet(false, true)) event.cleanUp();
        }
    }

    private static final class WorldListener extends PacketListenerAbstract {
        private final LegacyPacketEventsSession session;

        private WorldListener(LegacyPacketEventsSession session) {
            super(PacketListenerPriority.MONITOR);
            this.session = session;
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (session.closed || event.isCancelled()) return;
            PacketTypeCommon type = event.getPacketType();
            if (type == PacketType.Play.Server.BLOCK_ACTION) {
                if (!isVanillaShulkerAction(event)) return;
            } else if (type != PacketType.Play.Server.BLOCK_CHANGE
                    && type != PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                return;
            }
            session.submitWorld(event);
        }
    }

    private static final class MovementListener extends PacketListenerAbstract {
        private final LegacyPacketEventsSession session;

        private MovementListener(LegacyPacketEventsSession session) {
            super(PacketListenerPriority.MONITOR);
            this.session = session;
        }

        @Override
        public void onPacketReceive(final PacketReceiveEvent event) {
            if (session.closed || !WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
            User user = event.getUser();
            final UUID uuid = user == null ? null : user.getUUID();
            final String name = user == null ? null : user.getName();
            if (uuid == null || name == null) return;

            WrapperPlayClientPlayerFlying packet = new WrapperPlayClientPlayerFlying(event);
            Location location = packet.getLocation();
            final boolean hasPosition = packet.hasPositionChanged();
            final boolean hasLook = packet.hasRotationChanged();
            final double x = location.getX();
            final double y = location.getY();
            final double z = location.getZ();
            final float yaw = location.getYaw();
            final float pitch = location.getPitch();
            final boolean onGround = packet.isOnGround();
            final boolean cancelled = event.isCancelled();
            final long timestamp = System.currentTimeMillis();
            if (hasPosition && (!finite(x) || !finite(y) || !finite(z))
                    || hasLook && (!finite(yaw) || !finite(pitch))) return;

            session.dispatch(uuid, new Runnable() {
                @Override
                public void run() {
                    MovementState state = session.movement.get(uuid);
                    if (state == null) {
                        state = new MovementState();
                        session.movement.put(uuid, state);
                    }
                    if (!hasPosition && !state.hasPosition) return;
                    if (hasPosition) {
                        state.x = x;
                        state.y = y;
                        state.z = z;
                        state.hasPosition = true;
                    }
                    if (hasLook) {
                        state.yaw = yaw;
                        state.pitch = pitch;
                    }
                    AtomicLong sequenceCounter = session.movementSequences.get(uuid);
                    if (sequenceCounter == null) {
                        AtomicLong created = new AtomicLong();
                        AtomicLong existing = session.movementSequences.putIfAbsent(uuid, created);
                        sequenceCounter = existing == null ? created : existing;
                    }
                    long sequence = nextMovementSequence(
                            sequenceCounter, session.discontinuities.remove(uuid));
                    Player player = Bukkit.getPlayer(uuid);
                    float height = player != null && player.isSneaking() ? 1.5f : 1.8f;
                    double eyeHeight = player == null ? 1.62 : player.getEyeHeight();
                    if (!LegacyPacketQueue.push(uuid, new PacketPlayerPosition(
                            timestamp, uuid.toString(), name, cancelled,
                            state.x, state.y, state.z, state.x, state.y + eyeHeight, state.z,
                            state.yaw, state.pitch, height, onGround,
                            PacketPlayerPosition.SOURCE_RAW_CLIENT, sequence, hasPosition, hasLook))) {
                        return;
                    }
                    if (player != null) {
                        session.plugin.captureCollisionMovement(player, state.x, state.y, state.z);
                    }
                }
            });
        }

        private static boolean finite(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value);
        }
    }

    private static final class StateAcknowledgements {
        private final AtomicInteger counter = new AtomicInteger();
        private final ConcurrentHashMap<UUID, java.util.LinkedHashMap<Integer, Runnable>> pending =
                new ConcurrentHashMap<UUID, java.util.LinkedHashMap<Integer, Runnable>>();

        int stage(UUID uuid, boolean modern, Runnable task) {
            int id = nextId(modern);
            java.util.LinkedHashMap<Integer, Runnable> queue = pending.computeIfAbsent(
                    uuid, ignored -> new java.util.LinkedHashMap<Integer, Runnable>());
            synchronized (queue) {
                queue.put(id, task);
            }
            return id;
        }

        void acknowledge(UUID uuid, int id) {
            java.util.LinkedHashMap<Integer, Runnable> queue = pending.get(uuid);
            if (queue == null) return;
            java.util.List<Runnable> ready = new ArrayList<Runnable>();
            synchronized (queue) {
                if (!queue.containsKey(id)) return;
                Iterator<java.util.Map.Entry<Integer, Runnable>> iterator = queue.entrySet().iterator();
                while (iterator.hasNext()) {
                    java.util.Map.Entry<Integer, Runnable> entry = iterator.next();
                    ready.add(entry.getValue());
                    iterator.remove();
                    if (entry.getKey().intValue() == id) break;
                }
                if (queue.isEmpty()) pending.remove(uuid, queue);
            }
            for (Runnable task : ready) task.run();
        }

        void clearPlayer(UUID uuid) {
            pending.remove(uuid);
        }

        void clear() {
            pending.clear();
        }

        private int nextId(boolean modern) {
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

    /** Client-visible mode and ability state, normalized by PacketEvents for every protocol. */
    private static final class StateListener extends PacketListenerAbstract {
        private final LegacyPacketEventsSession session;

        private StateListener(LegacyPacketEventsSession session) {
            super(PacketListenerPriority.HIGH);
            this.session = session;
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (session.closed || event.isCancelled()) return;
            User user = event.getUser();
            if (user == null || user.getUUID() == null || user.getName() == null) return;
            final UUID uuid = user.getUUID();
            final String name = user.getName();
            final long timestamp = System.currentTimeMillis();
            if (event.getPacketType() == PacketType.Play.Server.PLAYER_ABILITIES) {
                WrapperPlayServerPlayerAbilities abilities = new WrapperPlayServerPlayerAbilities(event);
                final long sequence = next(session.serverAbilitySequences, uuid);
                final PacketPlayerAbilities packet = PacketPlayerAbilities.server(
                        timestamp, uuid.toString(), name, sequence,
                        abilities.isFlightAllowed(),
                        abilities.isFlying() && abilities.isFlightAllowed(),
                        validFlySpeed(abilities.getFlySpeed()));
                boolean modern = event.getClientVersion() != null
                        && event.getClientVersion().isNewerThanOrEquals(
                                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_17);
                int id = session.acknowledgements.stage(uuid, modern,
                        () -> session.dispatchInput(uuid, () -> LegacyPacketQueue.push(uuid, packet)));
                if (modern) {
                    event.getTasksAfterSend().add(() -> user.writePacket(new WrapperPlayServerPing(id)));
                } else {
                    event.getTasksAfterSend().add(() -> user.writePacket(
                            new WrapperPlayServerWindowConfirmation(0, (short) id, false)));
                }
                return;
            }
            final Integer mode;
            if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
                mode = modeId(new WrapperPlayServerJoinGame(event).getGameMode());
            } else if (event.getPacketType() == PacketType.Play.Server.RESPAWN) {
                mode = modeId(new WrapperPlayServerRespawn(event).getGameMode());
            } else if (event.getPacketType() == PacketType.Play.Server.CHANGE_GAME_STATE) {
                WrapperPlayServerChangeGameState state = new WrapperPlayServerChangeGameState(event);
                if (state.getReason() != WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE) return;
                mode = modeId(com.github.retrooper.packetevents.protocol.player.GameMode.getById(
                        (int) state.getValue()));
            } else {
                return;
            }
            final int gamemode = mode == null ? 0 : mode;
            Runnable emit = () -> session.dispatchInput(uuid, () -> session.emitMode(uuid, name, gamemode));
            if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
                emit.run();
            } else {
                boolean modern = event.getClientVersion() != null
                        && event.getClientVersion().isNewerThanOrEquals(
                                com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_17);
                int id = session.acknowledgements.stage(uuid, modern, emit);
                if (modern) {
                    event.getTasksAfterSend().add(() -> user.writePacket(new WrapperPlayServerPing(id)));
                } else {
                    event.getTasksAfterSend().add(() -> user.writePacket(
                            new WrapperPlayServerWindowConfirmation(0, (short) id, false)));
                }
            }
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (session.closed) return;
            User user = event.getUser();
            if (user == null || user.getUUID() == null) return;
            if (event.getPacketType() == PacketType.Play.Client.PONG) {
                session.acknowledgements.acknowledge(
                        user.getUUID(), new WrapperPlayClientPong(event).getId());
                return;
            }
            if (event.getPacketType() == PacketType.Play.Client.WINDOW_CONFIRMATION) {
                session.acknowledgements.acknowledge(
                        user.getUUID(), new WrapperPlayClientWindowConfirmation(event).getActionId());
                return;
            }
            if (event.isCancelled()
                    || event.getPacketType() != PacketType.Play.Client.PLAYER_ABILITIES) return;
            if (user.getName() == null) return;
            WrapperPlayClientPlayerAbilities abilities = new WrapperPlayClientPlayerAbilities(event);
            UUID uuid = user.getUUID();
            long sequence = next(session.clientAbilitySequences, uuid);
            final String name = user.getName();
            final boolean flying = abilities.isFlying();
            session.dispatchInput(uuid, () -> LegacyPacketQueue.push(uuid, PacketPlayerAbilities.client(
                    System.currentTimeMillis(), uuid.toString(), name, sequence, flying)));
        }

        private static long next(ConcurrentHashMap<UUID, AtomicLong> counters, UUID uuid) {
            return counters.computeIfAbsent(uuid, ignored -> new AtomicLong()).incrementAndGet();
        }

        private static int modeId(com.github.retrooper.packetevents.protocol.player.GameMode mode) {
            return mode == null ? 0 : mode.getId();
        }

        private static float validFlySpeed(float speed) {
            return Float.isFinite(speed) && speed >= 0.0f && speed <= 1.0f ? speed : 0.05f;
        }
    }

    /**
     * 1.8 clients have no PLAYER_INPUT packet. Entity actions remain authoritative
     * for sprint/sneak state and must stay ordered with following movement packets.
     */
    private static final class EntityActionListener extends PacketListenerAbstract {
        private final LegacyPacketEventsSession session;

        private EntityActionListener(LegacyPacketEventsSession session) {
            super(PacketListenerPriority.LOWEST);
            this.session = session;
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (session.closed || event.getPacketType() != PacketType.Play.Client.ENTITY_ACTION) return;
            User user = event.getUser();
            if (user == null || user.getUUID() == null || user.getName() == null) return;
            WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction(event);
            ServerBoundPlayerCommandActions action = playerAction(
                    wrapper.getAction() == null ? null : wrapper.getAction().name());
            if (action == null) return;
            final UUID uuid = user.getUUID();
            final String name = user.getName();
            final long timestamp = System.currentTimeMillis();
            final Integer horseJumpCharge = action == ServerBoundPlayerCommandActions.START_RIDING_JUMP
                    && wrapper.getJumpBoost() >= 0 && wrapper.getJumpBoost() <= 100
                    ? Integer.valueOf(wrapper.getJumpBoost()) : null;
            session.dispatchInput(uuid, new Runnable() {
                @Override
                public void run() {
                    LegacyPacketQueue.push(uuid, new PacketServerBoundPlayerCommand(
                            timestamp, uuid.toString(), name, action, horseJumpCharge));
                }
            });
        }
    }

    static boolean isBlockDigAction(DiggingAction action) {
        return action == DiggingAction.START_DIGGING
                || action == DiggingAction.FINISHED_DIGGING
                || action == DiggingAction.CANCELLED_DIGGING;
    }

    static byte digPhase(DiggingAction action) {
        if (action == DiggingAction.START_DIGGING) return PacketPlayerBlockRayTrace.DIG_PHASE_START;
        if (action == DiggingAction.FINISHED_DIGGING) return PacketPlayerBlockRayTrace.DIG_PHASE_FINISH;
        if (action == DiggingAction.CANCELLED_DIGGING) return PacketPlayerBlockRayTrace.DIG_PHASE_CANCEL;
        return PacketPlayerBlockRayTrace.DIG_PHASE_UNKNOWN;
    }

    private static final class DiggingListener extends PacketListenerAbstract {
        private final LegacyPacketEventsSession session;

        private DiggingListener(LegacyPacketEventsSession session) {
            super(PacketListenerPriority.LOWEST);
            this.session = session;
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (session.closed || event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
            User user = event.getUser();
            if (user == null || user.getUUID() == null || user.getName() == null) return;
            WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
            final DiggingAction action = packet.getAction();
            final Vector3i position = packet.getBlockPosition();
            Integer face = packet.getBlockFaceId();
            if (!isBlockDigAction(action) || position == null || face == null || face < 0 || face > 5) return;
            final UUID uuid = user.getUUID();
            final String name = user.getName();
            final long timestamp = System.currentTimeMillis();
            final byte blockFace = face.byteValue();
            final byte sequence = (byte) packet.getSequence();
            session.dispatchInput(uuid, new Runnable() {
                @Override
                public void run() {
                    LegacyPacketQueue.push(uuid, new PacketPlayerBlockRayTrace(
                            timestamp, uuid.toString(), name,
                            action == DiggingAction.START_DIGGING,
                            position.getX(), position.getY(), position.getZ(),
                            position.getX() + 0.5f, position.getY() + 0.5f, position.getZ() + 0.5f,
                            PacketPlayerBlockRayTrace.ACTION_DIG, sequence, digPhase(action)));
                    LegacyPacketQueue.push(uuid, new PacketPlayerBlockFace(
                            timestamp, uuid.toString(), name, blockFace));
                }
            });
        }
    }

    private static final class AttackListener extends PacketListenerAbstract {
        private final LegacyPacketEventsSession session;

        private AttackListener(LegacyPacketEventsSession session) {
            super(PacketListenerPriority.LOWEST);
            this.session = session;
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (session.closed || event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
            User user = event.getUser();
            final UUID uuid = user == null ? null : user.getUUID();
            if (uuid == null) return;
            final int entityId = packet.getEntityId();
            final long timestamp = System.currentTimeMillis();
            session.dispatchInput(uuid, new Runnable() {
                @Override
                public void run() {
                    session.plugin.emitRawAttack(uuid, entityId, timestamp);
                }
            });
        }
    }

    /** Capture client ANIMATION in the same ordered input lane as digging/attacks.
     * Evaluates swing order from the raw client packet, never from Bukkit
     * BlockBreakEvent (which is a later server-side callback).
     */
    private static final class SwingListener extends PacketListenerAbstract {
        private final LegacyPacketEventsSession session;

        private SwingListener(LegacyPacketEventsSession session) {
            super(PacketListenerPriority.LOWEST);
            this.session = session;
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (session.closed || event.getPacketType() != PacketType.Play.Client.ANIMATION) return;
            User user = event.getUser();
            if (user == null || user.getUUID() == null || user.getName() == null) return;
            final UUID uuid = user.getUUID();
            final String name = user.getName();
            final long timestamp = System.currentTimeMillis();
            // ANIMATION has no useful gameplay state beyond its ordered arrival;
            // preserve cancelled status exactly as received.
            final boolean cancelled = event.isCancelled();
            session.dispatchInput(uuid, new Runnable() {
                @Override
                public void run() {
                    LegacyPacketQueue.push(uuid, new PacketPlayerSwingHand(
                            timestamp, uuid.toString(), name, cancelled));
                }
            });
        }
    }

    private static final class HeldItemListener extends PacketListenerAbstract {
        private final LegacyPacketEventsSession session;

        private HeldItemListener(LegacyPacketEventsSession session) {
            super(PacketListenerPriority.LOWEST);
            this.session = session;
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (session.closed || event.getPacketType() != PacketType.Play.Client.HELD_ITEM_CHANGE) return;
            User user = event.getUser();
            if (user == null || user.getUUID() == null) return;
            WrapperPlayClientHeldItemChange packet = new WrapperPlayClientHeldItemChange(event);
            int slot = packet.getSlot();
            if (slot < 0 || slot > 8) return;
            final UUID uuid = user.getUUID();
            final long timestamp = System.currentTimeMillis();
            final int slotIndex = slot;
            session.dispatchInput(uuid, new Runnable() {
                @Override
                public void run() {
                    session.plugin.emitHeldItemSlot(uuid, slotIndex, timestamp);
                }
            });
        }
    }

    private static final class ClickListener extends PacketListenerAbstract {
        private final LegacyPacketEventsSession session;

        private ClickListener(LegacyPacketEventsSession session) {
            super(PacketListenerPriority.LOWEST);
            this.session = session;
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (session.closed || event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) return;
            User user = event.getUser();
            if (user == null || user.getUUID() == null || user.getName() == null) return;
            WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);
            final Short mode = clickMode(packet.getWindowClickType());
            if (mode == null) return;
            Integer action = packet.getActionNumber().orElse(null);

            final long timestamp = System.currentTimeMillis();
            final byte windowId = (byte) packet.getWindowId();
            final short slot = (short) packet.getSlot();
            final byte button = (byte) packet.getButton();
            final short transactionId = LegacyPacketEventsSession.transactionId(action);
            final org.vennv.utils.ItemStack item = item(packet.getCarriedItemStack());
            final UUID playerUuid = user.getUUID();
            final String uuid = playerUuid.toString();
            final String name = user.getName();
            session.dispatchInput(playerUuid, new Runnable() {
                @Override
                public void run() {
                    if (!LegacyPacketQueue.push(playerUuid, new PacketPlayerClickWindow(
                            timestamp, uuid, name, windowId, slot, button, mode, item, transactionId))) {
                        return;
                    }
                    session.plugin.scheduleInventoryTransaction(
                            playerUuid, windowId, slot, button, mode, transactionId);
                }
            });
        }

        private static Short clickMode(WrapperPlayClientClickWindow.WindowClickType type) {
            if (type == null) return null;
            switch (type) {
                case PICKUP: return 0;
                case QUICK_MOVE: return 1;
                case SWAP: return 2;
                case CLONE: return 3;
                case THROW: return 4;
                case QUICK_CRAFT: return 5;
                case PICKUP_ALL: return 6;
                default: return null;
            }
        }

        private static org.vennv.utils.ItemStack item(ItemStack stack) {
            if (stack == null || stack.isEmpty() || stack.getType() == null) {
                return new org.vennv.utils.ItemStack(org.vennv.utils.ItemStack.EMPTY_ID, 0, (byte) 0);
            }
            return new org.vennv.utils.ItemStack(
                    stack.getType().getName().toString(), stack.getLegacyData(), (byte) stack.getAmount());
        }
    }

    static final class PendingTasks {
        private final Queue<PendingTask> pending;
        private final ConcurrentHashMap<UUID, AtomicInteger> blockers =
                new ConcurrentHashMap<UUID, AtomicInteger>();
        private final Set<UUID> failed =
                Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

        PendingTasks(int capacity) {
            pending = new ArrayBlockingQueue<PendingTask>(capacity);
        }

        synchronized boolean offer(UUID uuid, Runnable task, boolean worldDependent) {
            if (uuid == null || task == null || worldDependent && failed.contains(uuid)) return false;
            return pending.offer(new PendingTask(uuid, task, worldDependent));
        }

        synchronized boolean offer(PendingTask task) {
            return pending.offer(task);
        }

        synchronized PendingTask poll() {
            return pending.poll();
        }

        synchronized int size() {
            return pending.size();
        }

        synchronized void block(UUID uuid) {
            if (uuid == null) return;
            AtomicInteger count = blockers.get(uuid);
            if (count == null) {
                AtomicInteger created = new AtomicInteger();
                AtomicInteger existing = blockers.putIfAbsent(uuid, created);
                count = existing == null ? created : existing;
            }
            count.incrementAndGet();
        }

        synchronized void unblock(UUID uuid) {
            AtomicInteger count = blockers.get(uuid);
            if (count != null && count.decrementAndGet() <= 0) blockers.remove(uuid, count);
        }

        synchronized void fail(UUID uuid) {
            if (uuid == null) return;
            failed.add(uuid);
            Iterator<PendingTask> iterator = pending.iterator();
            while (iterator.hasNext()) {
                PendingTask task = iterator.next();
                if (uuid.equals(task.uuid) && task.worldDependent) iterator.remove();
            }
        }

        synchronized void recover(UUID uuid) {
            failed.remove(uuid);
        }

        synchronized boolean isFailed(UUID uuid) {
            return failed.contains(uuid);
        }

        synchronized boolean shouldDrop(PendingTask task) {
            return task.worldDependent && failed.contains(task.uuid);
        }

        synchronized boolean shouldDefer(PendingTask task, Set<UUID> deferred) {
            if (!task.worldDependent) return false;
            AtomicInteger count = blockers.get(task.uuid);
            return deferred.contains(task.uuid) || count != null && count.get() > 0;
        }

        synchronized boolean hasRunnable() {
            for (PendingTask task : pending) {
                if (!shouldDrop(task) && !shouldDefer(task, Collections.<UUID>emptySet())) return true;
            }
            return false;
        }

        synchronized void removePending(UUID uuid) {
            if (uuid == null) return;
            Iterator<PendingTask> iterator = pending.iterator();
            while (iterator.hasNext()) if (uuid.equals(iterator.next().uuid)) iterator.remove();
        }

        synchronized void remove(UUID uuid) {
            removePending(uuid);
            blockers.remove(uuid);
            failed.remove(uuid);
        }

        synchronized Set<UUID> failAllAndClear() {
            Set<UUID> affected = new HashSet<UUID>();
            for (PendingTask task : pending) affected.add(task.uuid);
            failed.addAll(affected);
            pending.clear();
            return affected;
        }

        synchronized void clear() {
            pending.clear();
            blockers.clear();
            failed.clear();
        }
    }

    static final class PendingTask {
        final UUID uuid;
        final Runnable task;
        final boolean worldDependent;

        private PendingTask(UUID uuid, Runnable task, boolean worldDependent) {
            this.uuid = uuid;
            this.task = task;
            this.worldDependent = worldDependent;
        }
    }

    private static final class MovementState {
        private boolean hasPosition;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
    }
}
