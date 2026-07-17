package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
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
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.listener.RawCaptureCapability;
import org.vennv.zeusGateway.platform.SchedulerAdapter;
import org.vennv.zeusGateway.task.ChunkSyncTask;

public final class PacketEventsListenerRegistrar {

    private PacketEventsListenerRegistrar() {}

    public static Session register(ZeusGateway plugin) {
        EventManager manager = PacketEvents.getAPI().getEventManager();
        Session session = new Session(plugin, manager);
        OrderedPlayerPacketDispatcher dispatcher = session.dispatcher;
        OrderedWorldPacketDispatcher worldDispatcher = session.worldDispatcher;
        session.register("PacketPositionListener", () -> new PacketPositionListener(plugin, dispatcher), null);
        session.register("PacketSwingHandListener", () -> new PacketSwingHandListener(plugin, dispatcher), RawCaptureCapability.SWING_HAND);
        session.register("PacketAttackEntityListener", () -> new PacketAttackEntityListener(plugin, dispatcher), RawCaptureCapability.ATTACK_ENTITY);
        session.register("PacketKeepAliveListener", () -> new PacketKeepAliveListener(plugin, dispatcher), null);
        session.velocityListener = new PacketVelocityListener(dispatcher);
        session.register("PacketVelocityListener", () -> session.velocityListener, RawCaptureCapability.VELOCITY);
        session.register("PacketBlockFaceListener", () -> new PacketBlockFaceListener(plugin, dispatcher), RawCaptureCapability.BLOCK_FACE);
        session.register("PacketHeldItemListener", () -> new PacketHeldItemListener(plugin, dispatcher), RawCaptureCapability.HELD_ITEM);
        session.register("PacketClickWindowListener", () -> new PacketClickWindowListener(plugin, dispatcher), RawCaptureCapability.CLICK_WINDOW);
        session.register("PacketUseItemListener", () -> new PacketUseItemListener(plugin, dispatcher), RawCaptureCapability.USE_ITEM);
        session.register("PacketSteerVehicleListener", () -> new PacketSteerVehicleListener(plugin, dispatcher), null);
        session.register("PacketVehicleMoveListener", () -> new PacketVehicleMoveListener(plugin, dispatcher), RawCaptureCapability.VEHICLE_MOVE);
        session.register("PacketPlayerCommandListener", () -> new PacketPlayerCommandListener(dispatcher), RawCaptureCapability.PLAYER_COMMAND);
        session.register("PacketPlayerInputListener", () -> new PacketPlayerInputListener(plugin, dispatcher), RawCaptureCapability.PLAYER_INPUT);
        session.register("EntitySpawnListener", () -> new EntitySpawnListener(plugin), null);
        session.register("EntityMoveListener", () -> new EntityMoveListener(plugin), null);
        session.register("EntityDestroyListener", () -> new EntityDestroyListener(plugin), null);
        session.register("PacketBlockChangeListener", () -> new PacketBlockChangeListener(plugin, worldDispatcher), null);
        session.register("PacketUpdateAttributesListener", () -> new PacketUpdateAttributesListener(plugin), null);
        plugin.getLogger().info("[ZeusGateway] Registered " + session.handles.size() + " PacketEvents packet listeners.");
        return session;
    }

    public static final class Session implements AutoCloseable {

        private final ZeusGateway plugin;
        private final EventManager manager;
        private final List<PacketListenerCommon> handles = new ArrayList<>();
        private final EnumSet<RawCaptureCapability> capabilities = EnumSet.noneOf(RawCaptureCapability.class);
        private final OrderedPlayerPacketDispatcher dispatcher;
        private final OrderedWorldPacketDispatcher worldDispatcher;
        private PacketVelocityListener velocityListener;
        private boolean closed;

        private Session(ZeusGateway plugin, EventManager manager) {
            this.plugin = plugin;
            this.manager = manager;
            this.dispatcher = new OrderedPlayerPacketDispatcher(plugin);
            this.worldDispatcher = new OrderedWorldPacketDispatcher(plugin, dispatcher);
        }

        public synchronized Set<RawCaptureCapability> capabilities() {
            return Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        }

        public void clearPlayer(UUID uuid) {
            if (uuid == null) {
                return;
            }
            worldDispatcher.clearPlayer(uuid);
            dispatcher.clearPlayer(uuid);
            if (velocityListener != null) velocityListener.clearPlayer(uuid);
            PacketPositionListener.removePlayer(uuid);
            EntitySpawnListener.removePlayer(uuid);
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (int index = handles.size() - 1; index >= 0; index--) {
                try {
                    manager.unregisterListener(handles.get(index));
                } catch (RuntimeException | LinkageError error) {
                    plugin.getLogger().warning("[ZeusGateway] Failed to unregister PacketEvents listener: " + error.getMessage());
                }
            }
            handles.clear();
            if (velocityListener != null) velocityListener.clear();
            worldDispatcher.close();
            dispatcher.close();
            capabilities.clear();
            PacketPositionListener.clear();
            EntitySpawnListener.clear();
        }

        private void register(
                String name,
                Supplier<? extends PacketListenerCommon> listenerFactory,
                RawCaptureCapability capability) {
            if (closed) {
                throw new IllegalStateException("PacketEvents listener session is closed");
            }
            try {
                PacketListenerCommon listener = listenerFactory.get();
                PacketListenerCommon handle = manager.registerListener(listener);
                if (handle == null) {
                    throw new IllegalStateException("PacketEvents returned a null listener handle");
                }
                handles.add(handle);
                if (capability != null) {
                    capabilities.add(capability);
                }
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().warning("[ZeusGateway] Failed to register " + name + ": " + error.getMessage());
                close();
                throw new IllegalStateException("Required PacketEvents listener failed: " + name, error);
            }
        }

    }
}

final class OrderedWorldPacketDispatcher implements AutoCloseable {
    private static final int MAX_PENDING_PACKETS = 16;
    private static final long WARNING_INTERVAL_MS = 5000L;

    private final ZeusGateway plugin;
    private final OrderedPlayerPacketDispatcher playerDispatcher;
    private final ThreadPoolExecutor executor;
    private final ConcurrentHashMap<UUID, AtomicLong> sequences = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> failedAt = new ConcurrentHashMap<>();
    private final ThreadLocal<Long> currentSequence = new ThreadLocal<>();
    private final AtomicLong nextWarningAt = new AtomicLong();
    private volatile boolean closed;

    OrderedWorldPacketDispatcher(
            ZeusGateway plugin, OrderedPlayerPacketDispatcher playerDispatcher) {
        this.plugin = plugin;
        this.playerDispatcher = playerDispatcher;
        this.executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_PACKETS),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable task) {
                        Thread thread = new Thread(task, "ZeusGateway-WorldPackets");
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    void submit(PacketSendEvent event, java.util.function.Consumer<PacketSendEvent> handler) {
        submit(event, handler, true);
    }

    void submit(
            PacketSendEvent event,
            java.util.function.Consumer<PacketSendEvent> handler,
            boolean blockMovement) {
        submit(event, handler, blockMovement, null);
    }

    void submit(
            PacketSendEvent event,
            java.util.function.Consumer<PacketSendEvent> handler,
            boolean blockMovement,
            Player recoveryPlayer) {
        if (closed || event == null || handler == null || event.getUser() == null) return;
        UUID uuid = event.getUser().getUUID();
        if (uuid == null) return;
        long sequence = sequences.computeIfAbsent(uuid, ignored -> new AtomicLong())
                .incrementAndGet();
        PacketSendEvent cloned = event.clone();
        Runnable recovery = recoveryPlayer == null
                ? null
                : () -> ChunkSyncTask.invalidateAndRequestFullResync(plugin, recoveryPlayer);
        WorldPacketTask task = new WorldPacketTask(
                cloned, handler, uuid, sequence, playerDispatcher, currentSequence,
                blockMovement, recovery);
        if (blockMovement) playerDispatcher.block(uuid);
        try {
            executor.execute(task);
        } catch (RejectedExecutionException error) {
            failedAt.merge(uuid, sequence, Math::max);
            task.reject();
            warn("World capture saturated for " + uuid + "; dropped packet awaiting resync");
        }
    }

    void clearPlayer(UUID uuid) {
        if (uuid == null) return;
        sequences.remove(uuid);
        failedAt.remove(uuid);
        for (Runnable task : new ArrayList<>(executor.getQueue())) {
            if (task instanceof WorldPacketTask
                    && uuid.equals(((WorldPacketTask) task).uuid)
                    && executor.remove(task)) {
                ((WorldPacketTask) task).cancel();
            }
        }
    }

    void recover(UUID uuid) {
        Long sequence = currentSequence.get();
        Long failure = failedAt.get(uuid);
        if (sequence != null && (failure == null || sequence > failure)) {
            failedAt.remove(uuid);
            playerDispatcher.recover(uuid);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        List<Runnable> pending = executor.shutdownNow();
        for (Runnable task : pending) {
            if (task instanceof WorldPacketTask) ((WorldPacketTask) task).cancel();
        }
        try {
            executor.awaitTermination(1L, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        sequences.clear();
        failedAt.clear();
        currentSequence.remove();
    }

    private void warn(String message) {
        long now = System.currentTimeMillis();
        long next = nextWarningAt.get();
        if (now >= next && nextWarningAt.compareAndSet(next, now + WARNING_INTERVAL_MS)) {
            plugin.getLogger().warning("[ZeusGateway] " + message);
        }
    }

    private static final class WorldPacketTask implements Runnable {
        private final PacketSendEvent event;
        private final java.util.function.Consumer<PacketSendEvent> handler;
        private final UUID uuid;
        private final long sequence;
        private final OrderedPlayerPacketDispatcher playerDispatcher;
        private final ThreadLocal<Long> currentSequence;
        private final boolean blockMovement;
        private final Runnable recovery;
        private final AtomicBoolean cleaned = new AtomicBoolean();
        private final AtomicBoolean recovered = new AtomicBoolean();

        private WorldPacketTask(
                PacketSendEvent event,
                java.util.function.Consumer<PacketSendEvent> handler,
                UUID uuid,
                long sequence,
                OrderedPlayerPacketDispatcher playerDispatcher,
                ThreadLocal<Long> currentSequence,
                boolean blockMovement,
                Runnable recovery) {
            this.event = event;
            this.handler = handler;
            this.uuid = uuid;
            this.sequence = sequence;
            this.playerDispatcher = playerDispatcher;
            this.currentSequence = currentSequence;
            this.blockMovement = blockMovement;
            this.recovery = recovery;
        }

        @Override
        public void run() {
            boolean success = false;
            currentSequence.set(sequence);
            try {
                handler.accept(event);
                success = true;
            } finally {
                currentSequence.remove();
                cleanUp();
                if (!success && blockMovement) recover();
                if (blockMovement) playerDispatcher.unblock(uuid);
            }
        }

        private void reject() {
            cleanUp();
            if (blockMovement) {
                recover();
                playerDispatcher.unblock(uuid);
            }
        }

        private void cancel() {
            cleanUp();
            if (blockMovement) {
                recover();
                playerDispatcher.unblock(uuid);
            }
        }

        private void recover() {
            if (recovery != null && recovered.compareAndSet(false, true)) recovery.run();
        }

        private void cleanUp() {
            if (cleaned.compareAndSet(false, true)) event.cleanUp();
        }
    }
}

final class OrderedPlayerPacketDispatcher implements AutoCloseable {
    private static final int MAX_PENDING_PER_PLAYER = 1024;
    private static final int MAX_TASKS_PER_DRAIN = 128;
    private static final long WARNING_INTERVAL_MS = 5000L;

    private final ZeusGateway plugin;
    private final ConcurrentHashMap<UUID, Lane> lanes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AtomicInteger> blockers = new ConcurrentHashMap<>();
    private final Set<UUID> failed = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextWarningAt = new AtomicLong();
    private volatile boolean closed;

    OrderedPlayerPacketDispatcher(ZeusGateway plugin) {
        this.plugin = plugin;
    }

    boolean submit(Player player, Runnable task) {
        if (closed || player == null || task == null) return false;
        SchedulerAdapter scheduler = plugin.getSchedulerAdapter();
        if (scheduler == null) return false;

        UUID uuid = player.getUniqueId();
        if (failed.contains(uuid)) return false;
        Lane lane = lanes.computeIfAbsent(uuid, ignored -> new Lane());
        synchronized (lane) {
            if (closed || lane.closed || failed.contains(uuid)) return false;
            if (lane.pending.size() >= MAX_PENDING_PER_PLAYER) {
                fail(uuid);
                warn("Inbound packet lane saturated for " + uuid + "; capture paused until resync");
                return false;
            }
            lane.player = player;
            lane.pending.addLast(task);
            if (lane.scheduled || isBlocked(uuid)) return true;
            lane.scheduled = true;
        }
        schedule(player, uuid, lane, scheduler);
        return true;
    }

    void block(UUID uuid) {
        if (closed || uuid == null) return;
        blockers.computeIfAbsent(uuid, ignored -> new AtomicInteger()).incrementAndGet();
    }

    void unblock(UUID uuid) {
        AtomicInteger count = blockers.get(uuid);
        if (count != null && count.decrementAndGet() <= 0) blockers.remove(uuid, count);
        resume(uuid);
    }

    void fail(UUID uuid) {
        if (uuid == null) return;
        failed.add(uuid);
        Lane lane = lanes.get(uuid);
        if (lane != null) {
            synchronized (lane) {
                lane.pending.clear();
                lane.scheduled = false;
            }
        }
    }

    void recover(UUID uuid) {
        if (uuid == null) return;
        failed.remove(uuid);
        resume(uuid);
    }

    void clearPlayer(UUID uuid) {
        if (uuid == null) return;
        blockers.remove(uuid);
        failed.remove(uuid);
        Lane lane = lanes.remove(uuid);
        if (lane == null) return;
        synchronized (lane) {
            lane.closed = true;
            lane.pending.clear();
            lane.scheduled = false;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (UUID uuid : lanes.keySet()) clearPlayer(uuid);
        lanes.clear();
        blockers.clear();
        failed.clear();
    }

    private boolean isBlocked(UUID uuid) {
        AtomicInteger count = blockers.get(uuid);
        return count != null && count.get() > 0;
    }

    private void resume(UUID uuid) {
        if (closed || failed.contains(uuid) || isBlocked(uuid)) return;
        Lane lane = lanes.get(uuid);
        if (lane == null) return;
        Player player;
        synchronized (lane) {
            if (lane.closed || lane.scheduled || lane.pending.isEmpty()) return;
            player = lane.player;
            if (player == null) return;
            lane.scheduled = true;
        }
        SchedulerAdapter scheduler = plugin.getSchedulerAdapter();
        if (scheduler == null) {
            discard(uuid, lane);
            return;
        }
        schedule(player, uuid, lane, scheduler);
    }

    private void schedule(Player player, UUID uuid, Lane lane, SchedulerAdapter scheduler) {
        try {
            scheduler.runEntityTask(plugin, player, () -> drain(player, uuid, lane, scheduler));
        } catch (RuntimeException | LinkageError error) {
            discard(uuid, lane);
            plugin.getLogger().warning("[ZeusGateway] Failed to schedule ordered packet drain: " + error.getMessage());
        }
    }

    private void drain(Player player, UUID uuid, Lane lane, SchedulerAdapter scheduler) {
        int processed = 0;
        while (processed < MAX_TASKS_PER_DRAIN) {
            Runnable task;
            synchronized (lane) {
                if (closed || lane.closed || failed.contains(uuid) || !player.isOnline()) {
                    lane.pending.clear();
                    lane.scheduled = false;
                    lanes.remove(uuid, lane);
                    return;
                }
                if (isBlocked(uuid)) {
                    lane.scheduled = false;
                    return;
                }
                task = lane.pending.pollFirst();
                if (task == null) {
                    lane.scheduled = false;
                    return;
                }
            }
            try {
                task.run();
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().warning("[ZeusGateway] Ordered packet task failed: " + error.getMessage());
            }
            processed++;
        }
        synchronized (lane) {
            if (lane.pending.isEmpty()) {
                lane.scheduled = false;
                return;
            }
        }
        schedule(player, uuid, lane, scheduler);
    }

    private void discard(UUID uuid, Lane lane) {
        synchronized (lane) {
            lane.closed = true;
            lane.pending.clear();
            lane.scheduled = false;
        }
        lanes.remove(uuid, lane);
    }

    private void warn(String message) {
        long now = System.currentTimeMillis();
        long next = nextWarningAt.get();
        if (now >= next && nextWarningAt.compareAndSet(next, now + WARNING_INTERVAL_MS)) {
            plugin.getLogger().warning("[ZeusGateway] " + message);
        }
    }

    private static final class Lane {
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        private Player player;
        private boolean scheduled;
        private boolean closed;
    }
}
