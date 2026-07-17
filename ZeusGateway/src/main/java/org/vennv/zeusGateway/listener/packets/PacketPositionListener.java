package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.compat.EntityCompat;
import org.vennv.zeusGateway.compat.BlockCompat;
import org.vennv.zeusGateway.platform.SchedulerAdapter;
import org.vennv.zeusGateway.platform.ServerVersion;
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
        public boolean onGround;
        public boolean hasPosition;
    }

    private static final ConcurrentHashMap<UUID, PlayerCache> playerCaches = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> swimmingPoseState = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, AtomicLong> movementSequences = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Vector> captureVelocities = new ConcurrentHashMap<>();

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

    public static void removePlayer(UUID uuid) {
        swimmingPoseState.remove(uuid);
        movementSequences.remove(uuid);
        captureVelocities.remove(uuid);
        playerCaches.remove(uuid);
    }

    public static void clear() {
        swimmingPoseState.clear();
        movementSequences.clear();
        captureVelocities.clear();
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
        long receiveNanos = System.nanoTime();
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
        boolean hadPreviousPosition;
        boolean previousOnGround;
        double previousX;
        double previousY;
        double previousZ;
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
            hadPreviousPosition = cache.hasPosition;
            previousOnGround = cache.onGround;
            previousX = cache.lastX;
            previousY = cache.lastY;
            previousZ = cache.lastZ;
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
            cache.onGround = packetOnGround;
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
        boolean capture = hadPreviousPosition && PhysicsCaptureManager.isCaptureActive();
        int serverProtocol = capture ? PhysicsCaptureManager.serverProtocol() : 0;
        int clientProtocol = capture ? clientProtocol(user) : 0;
        dispatcher.submit(player, () -> {
            PacketQueue.push(position);
            if (hasPosition) {
                chunkSyncTask.onMovement(player, sendX, sendY, sendZ);
            }
            if (capture) {
                capturePhysics(
                        player, timestamp, receiveNanos, movementSequence, uuid,
                        serverProtocol, clientProtocol,
                        previousX, previousY, previousZ,
                        sendX, sendY, sendZ,
                        previousOnGround, packetOnGround,
                        hasPosition, hasLook, sendYaw, sendPitch);
            }
        });
    }

    private static void capturePhysics(
            Player player,
            long timestamp,
            long receiveNanos,
            long movementSequence,
            UUID uuid,
            int serverProtocol,
            int clientProtocol,
            double previousX,
            double previousY,
            double previousZ,
            double currentX,
            double currentY,
            double currentZ,
            boolean previousOnGround,
            boolean packetOnGround,
            boolean hasPosition,
            boolean hasLook,
            float yaw,
            float pitch) {
        if (!player.isOnline()) {
            return;
        }
        try {
            double prevDx = currentX - previousX;
            double prevDy = currentY - previousY;
            double prevDz = currentZ - previousZ;

            Vector vel = player.getVelocity();
            Vector previousVel = captureVelocities.put(uuid, vel.clone());

            byte flags = 0;
            if (packetOnGround) flags |= 0x01;
            if (player.isSprinting()) flags |= 0x02;
            if (player.isSwimming()) flags |= 0x04;
            if (player.isSneaking()) flags |= 0x08;
            if (playerBoolean(player, "isClimbing")) flags |= 0x10;
            if (playerBoolean(player, "isGliding")) flags |= 0x20;

            int supportBlockId = 0;
            int frictionBlockId = 0;
            byte surfaceCategory = 0;
            String supportBlockName = "";
            String blockProperties = "";
            String supportShapeId = "";
            float blockFriction = Float.NaN;
            float velocityMultiplier = Float.NaN;
            org.bukkit.Location feetLoc = player.getLocation().clone();
            try {
                Block belowBlock = feetLoc.clone().add(0, -0.001, 0).getBlock();
                if (belowBlock != null) {
                    supportBlockId = belowBlock.getType().ordinal();
                    frictionBlockId = supportBlockId;
                    String mat = belowBlock.getType().name();
                    supportBlockName = "minecraft:" + mat.toLowerCase(java.util.Locale.ROOT);
                    blockProperties = BlockCompat.getBlockDataString(belowBlock);
                    double[] bounds = BlockCompat.getBlockBoundsArray(belowBlock);
                    if (bounds != null) supportShapeId = java.util.Arrays.toString(bounds);
                    blockFriction = reflectedBlockScalar(belowBlock, "getFriction");
                    velocityMultiplier = reflectedBlockScalar(belowBlock, "getVelocityMultiplier");
                    if (mat.contains("ICE")) surfaceCategory = 1;
                    else if (mat.contains("SLIME")) surfaceCategory = 2;
                    else if (mat.contains("HONEY")) surfaceCategory = 3;
                    else if (mat.contains("SOUL_SAND") || mat.contains("SOUL_SOIL")) surfaceCategory = 4;
                    else if (mat.contains("WEB")) surfaceCategory = 5;
                }
            } catch (Exception ignored) {}

            String bodyBlock = feetLoc.getBlock().getType().name();
            String eyeBlock = player.getEyeLocation().getBlock().getType().name();
            boolean bodyInWater = bodyBlock.contains("WATER");
            boolean eyeInWater = eyeBlock.contains("WATER");
            boolean inLava = bodyBlock.contains("LAVA");

            byte jumpBoostLevel = 0;
            byte slownessLevel = 0;
            String effectState = "";
            try {
                effectState = player.getActivePotionEffects().stream()
                    .map(effect -> effect.getType().getName().toLowerCase(java.util.Locale.ROOT)
                        + "=" + (effect.getAmplifier() + 1))
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(","));
                org.bukkit.potion.PotionEffectType jumpType = potionEffectType("JUMP", "JUMP_BOOST");
                org.bukkit.potion.PotionEffect jumpEff = jumpType == null ? null : player.getPotionEffect(jumpType);
                if (jumpEff != null) jumpBoostLevel = (byte) Math.min(255, jumpEff.getAmplifier() + 1);
                org.bukkit.potion.PotionEffectType slowType = potionEffectType("SLOW", "SLOWNESS");
                org.bukkit.potion.PotionEffect slowEff = slowType == null ? null : player.getPotionEffect(slowType);
                if (slowEff != null) slownessLevel = (byte) Math.min(255, slowEff.getAmplifier() + 1);
            } catch (Exception ignored) {}

            float tickDurationMs = PhysicsCaptureManager.observedTickDurationMs(uuid, receiveNanos);
            byte worldDim = (byte) player.getWorld().getEnvironment().ordinal();
            int previousFlags = previousOnGround ? 0x01 : 0;
            int currentFlags = flags & 0xff;
            org.bukkit.entity.Entity vehicle = player.getVehicle();
            boolean vehicleMounted = vehicle != null;
            String vehicleType = vehicleMounted
                ? "minecraft:" + vehicle.getType().name().toLowerCase(java.util.Locale.ROOT)
                : "";
            String fluidKind = bodyInWater ? "water" : inLava ? "lava" : "air";
            String bodyFluid = bodyInWater ? "water" : inLava ? "lava" : "";
            String eyeFluid = eyeInWater ? "water" : "";
            float previousVelocityX = previousVel == null ? Float.NaN : (float) previousVel.getX();
            float previousVelocityY = previousVel == null ? Float.NaN : (float) previousVel.getY();
            float previousVelocityZ = previousVel == null ? Float.NaN : (float) previousVel.getZ();
            float baseSpeed = actualBaseSpeed(player);

            PhysicsCaptureManager.sendSampleV2(
                    timestamp,
                    movementSequence,
                    uuid,
                    serverProtocol, clientProtocol,
                    previousX, previousY, previousZ,
                    (float) prevDx, (float) prevDy, (float) prevDz,
                    previousVelocityX, previousVelocityY, previousVelocityZ,
                    (float) vel.getX(), (float) vel.getY(), (float) vel.getZ(),
                    baseSpeed,
                    flags & 0xff, previousFlags, currentFlags,
                    supportBlockId, frictionBlockId, surfaceCategory,
                    supportBlockName, blockProperties, blockProperties, supportShapeId,
                    blockFriction, velocityMultiplier,
                    bodyInWater, eyeInWater, inLava,
                    fluidKind, Float.NaN, Float.NaN,
                    Float.NaN, Float.NaN, Float.NaN, bodyFluid, eyeFluid,
                    effectState, baseSpeed, Float.NaN,
                    jumpBoostLevel, slownessLevel,
                    vehicleMounted, vehicleType, vehicleMounted ? vehicle.getEntityId() : 0L, 0,
                    false, "", Float.NaN, Float.NaN, Float.NaN, 0L, 0,
                    tickDurationMs,
                    Float.NaN, worldDim, 0,
                    (byte) 0xff,
                    hasPosition, hasLook,
                    yaw, pitch,
                    lookVectorX(yaw, pitch),
                    lookVectorY(yaw, pitch),
                    lookVectorZ(yaw, pitch)
            );
        } catch (Exception ignored) {}
    }

    private static boolean playerBoolean(org.bukkit.entity.Player player, String methodName) {
        try {
            Object value = player.getClass().getMethod(methodName).invoke(player);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static float reflectedBlockScalar(Block block, String methodName) {
        try {
            Object value = block.getClass().getMethod(methodName).invoke(block);
            return value instanceof Number ? ((Number) value).floatValue() : Float.NaN;
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    private static float actualBaseSpeed(org.bukkit.entity.Player player) {
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Object movement;
            try {
                movement = java.lang.Enum.valueOf((Class) attributeClass, "GENERIC_MOVEMENT_SPEED");
            } catch (IllegalArgumentException ignored) {
                movement = java.lang.Enum.valueOf((Class) attributeClass, "MOVEMENT_SPEED");
            }
            Object instance = player.getClass().getMethod("getAttribute", attributeClass)
                    .invoke(player, movement);
            if (instance == null) return Float.NaN;
            Object value = instance.getClass().getMethod("getValue").invoke(instance);
            return value instanceof Number ? ((Number) value).floatValue() : Float.NaN;
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    private static float lookVectorX(float yaw, float pitch) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        return (float) (-Math.sin(yawRadians) * Math.cos(pitchRadians));
    }

    private static float lookVectorY(float yaw, float pitch) {
        return (float) (-Math.sin(Math.toRadians(pitch)));
    }

    private static float lookVectorZ(float yaw, float pitch) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        return (float) (Math.cos(yawRadians) * Math.cos(pitchRadians));
    }

    private static org.bukkit.potion.PotionEffectType potionEffectType(String... names) {
        for (String name : names) {
            try {
                org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByName(name);
                if (type != null) {
                    return type;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static int clientProtocol(User user) {
        ClientVersion version = user.getClientVersion();
        return version == null ? PhysicsCaptureManager.serverProtocol() : version.getProtocolVersion();
    }
}
