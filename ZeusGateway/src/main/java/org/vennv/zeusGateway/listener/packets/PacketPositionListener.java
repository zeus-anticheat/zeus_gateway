package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.compat.EntityCompat;
import org.vennv.zeusGateway.platform.SchedulerAdapter;
import org.vennv.zeusGateway.platform.ServerVersion;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.task.ChunkSyncTask;

public class PacketPositionListener extends PacketListenerAbstract {

    private final ZeusGateway plugin;
    private final OrderedPlayerPacketDispatcher dispatcher;
    private final ChunkSyncTask chunkSyncTask;

    public static class PlayerCache {
        public double lastX;
        public double lastY;
        public double lastZ;
        public float lastYaw;
        public float lastPitch;
        public double eyeHeight = 1.62;
        public float height = 1.8f;
        public boolean hasPosition;
    }

    private static final ConcurrentHashMap<UUID, PlayerCache> playerCaches = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> swimmingPoseState = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, AtomicLong> movementSequences = new ConcurrentHashMap<>();

    public PacketPositionListener(ZeusGateway plugin) {
        this(plugin, new OrderedPlayerPacketDispatcher(plugin));
    }

    PacketPositionListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.chunkSyncTask = new ChunkSyncTask(plugin);
        startCacheUpdateTask();
    }

    private void startCacheUpdateTask() {
        SchedulerAdapter scheduler = plugin.getSchedulerAdapter();
        if (scheduler == null) {
            return;
        }
        scheduler.runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                scheduler.runEntityTask(plugin, player, () -> updatePlayerCache(player));
            }
        }, 1L, 1L);
    }

    private void updatePlayerCache(Player player) {
        if (!player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        PlayerCache cache = playerCaches.computeIfAbsent(uuid, ignored -> new PlayerCache());
        try {
            org.bukkit.Location body = player.getLocation();
            org.bukkit.Location eye = player.getEyeLocation();
            double eyeHeight = eye.getY() - body.getY();
            float height = EntityCompat.getPlayerHeight(player);
            synchronized (cache) {
                if (Double.isFinite(eyeHeight)) {
                    cache.eyeHeight = eyeHeight;
                }
                if (Float.isFinite(height)) {
                    cache.height = height;
                }
            }
        } catch (Throwable ignored) {}

        if (ServerVersion.HAS_ENTITY_POSE) {
            try {
                org.bukkit.entity.Pose currentPose = player.getPose();
                boolean isSwimming = (currentPose == org.bukkit.entity.Pose.SWIMMING);
                Boolean lastState = swimmingPoseState.put(uuid, isSwimming);

                if (lastState == null || lastState != isSwimming) {
                    ServerBoundPlayerCommandActions action = isSwimming
                        ? ServerBoundPlayerCommandActions.START_SWIMMING
                        : ServerBoundPlayerCommandActions.STOP_SWIMMING;
                    PacketQueue.push(new PacketServerBoundPlayerCommand(
                        System.currentTimeMillis(), uuid.toString(), player.getName(), action
                    ));
                }
            } catch (NoSuchMethodError | NoClassDefFoundError ignored) {}
        }
    }

    /**
     * Last position the client claimed, used to resolve relative outbound
     * teleports. Returns null while no position has been seen, so callers must
     * not guess a destination.
     */
    static double[] lastClaimedPosition(UUID uuid) {
        PlayerCache cache = playerCaches.get(uuid);
        if (cache == null) {
            return null;
        }
        synchronized (cache) {
            return cache.hasPosition
                    ? new double[] {cache.lastX, cache.lastY, cache.lastZ}
                    : null;
        }
    }

    public static void removePlayer(UUID uuid) {
        swimmingPoseState.remove(uuid);
        movementSequences.remove(uuid);
        playerCaches.remove(uuid);
    }

    public static void clear() {
        swimmingPoseState.clear();
        movementSequences.clear();
        playerCaches.clear();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            return;
        }

        User user = event.getUser();
        Player player = event.getPlayer();
        if (user == null || player == null) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
        Location location = flying.getLocation();
        boolean hasPosition = flying.hasPositionChanged();
        boolean hasLook = flying.hasRotationChanged();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        float yaw = location.getYaw();
        float pitch = location.getPitch();
        boolean packetOnGround = flying.isOnGround();
        boolean cancelled = event.isCancelled();
        UUID uuid = user.getUUID();
        String name = user.getName();
        if (uuid == null || name == null
                || hasPosition && (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                || hasLook && (!Float.isFinite(yaw) || !Float.isFinite(pitch))) {
            return;
        }

        PlayerCache cache = playerCaches.computeIfAbsent(uuid, ignored -> new PlayerCache());
        double sendX;
        double sendY;
        double sendZ;
        float sendYaw;
        float sendPitch;
        float sendHeight;
        double eyeHeight;
        long movementSequence;
        synchronized (cache) {
            if (!hasPosition && !cache.hasPosition) {
                return;
            }
            if (hasPosition) {
                cache.lastX = x;
                cache.lastY = y;
                cache.lastZ = z;
                cache.hasPosition = true;
            }
            if (hasLook) {
                cache.lastYaw = yaw;
                cache.lastPitch = pitch;
            }
            sendX = cache.lastX;
            sendY = cache.lastY;
            sendZ = cache.lastZ;
            sendYaw = cache.lastYaw;
            sendPitch = cache.lastPitch;
            sendHeight = cache.height;
            eyeHeight = cache.eyeHeight;
            movementSequence = movementSequences
                    .computeIfAbsent(uuid, ignored -> new AtomicLong())
                    .incrementAndGet();
        }

        PacketPlayerPosition position = new PacketPlayerPosition(
                timestamp, uuid.toString(), name, cancelled,
                sendX, sendY, sendZ,
                sendX, sendY + eyeHeight, sendZ,
                sendYaw, sendPitch, sendHeight, packetOnGround,
                PacketPlayerPosition.SOURCE_RAW_CLIENT, movementSequence,
                hasPosition, hasLook);
        dispatcher.submit(player, () -> {
            if (hasPosition) {
                chunkSyncTask.onMovement(player, sendX, sendY, sendZ);
            }
            PacketQueue.push(position);
        });
    }
}
