package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import java.util.ArrayList;
import java.util.List;
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
import org.vennv.zeusGateway.platform.ServerVersion;

public class PacketPositionListener extends PacketAdapter {

    private final ZeusGateway plugin;

    public static class PlayerCache {
        public double lastX;
        public double lastY;
        public double lastZ;
        public float lastYaw;
        public float lastPitch;
        public double eyeX;
        public double eyeY;
        public double eyeZ;
        public float height = 1.8f;
        public boolean onGround;
        public boolean hasPosition;
    }

    private static final ConcurrentHashMap<UUID, PlayerCache> playerCaches = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> swimmingPoseState = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, AtomicLong> movementSequences = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Vector> captureVelocities = new ConcurrentHashMap<>();

    private static final PacketType TYPE_POSITION = PacketType.Play.Client.POSITION;
    private static final PacketType TYPE_POSITION_LOOK = PacketType.Play.Client.POSITION_LOOK;
    private static final PacketType TYPE_LOOK = getPacketTypeReflectively("LOOK");
    private static final PacketType TYPE_FLYING = getPacketTypeReflectively("FLYING");

    private static PacketType getPacketTypeReflectively(String fieldName) {
        try {
            java.lang.reflect.Field field = PacketType.Play.Client.class.getField(fieldName);
            return (PacketType) field.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<PacketType> getTargetTypes() {
        List<PacketType> types = new ArrayList<>();
        types.add(TYPE_POSITION);
        types.add(TYPE_POSITION_LOOK);
        if (TYPE_LOOK != null) {
            types.add(TYPE_LOOK);
        }
        if (TYPE_FLYING != null) {
            types.add(TYPE_FLYING);
        }
        return types;
    }

    public PacketPositionListener(ZeusGateway plugin) {
        super(
            plugin,
            ListenerPriority.LOWEST,
            getTargetTypes()
        );
        this.plugin = plugin;
        startCacheUpdateTask();
    }

    private void startCacheUpdateTask() {
        if (plugin.getSchedulerAdapter() == null) {
            return;
        }
        plugin.getSchedulerAdapter().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                PlayerCache cache = playerCaches.computeIfAbsent(uuid, ignored -> new PlayerCache());
                try {
                    org.bukkit.Location eye = player.getEyeLocation();
                    cache.eyeX = eye.getX();
                    cache.eyeY = eye.getY();
                    cache.eyeZ = eye.getZ();
                    cache.height = EntityCompat.getPlayerHeight(player);
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
        }, 1L, 1L);
    }

    /**
     * Remove a player's tracked state (call on quit).
     */
    public static void removePlayer(UUID uuid) {
        swimmingPoseState.remove(uuid);
        movementSequences.remove(uuid);
        captureVelocities.remove(uuid);
        playerCaches.remove(uuid);
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        long timestamp = System.currentTimeMillis();
        PacketType type = event.getPacketType();

        boolean hasPosition = (type == TYPE_POSITION || type == TYPE_POSITION_LOOK);
        boolean hasLook = (type == TYPE_POSITION_LOOK || (TYPE_LOOK != null && type == TYPE_LOOK));

        double x = 0;
        double y = 0;
        double z = 0;
        if (hasPosition) {
            StructureModifier<Double> coords = event.getPacket().getDoubles();
            if (coords.size() >= 3) {
                x = coords.read(0);
                y = coords.read(1);
                z = coords.read(2);
            }
        }

        float yaw = 0;
        float pitch = 0;
        if (hasLook) {
            StructureModifier<Float> floats = event.getPacket().getFloat();
            if (floats.size() >= 2) {
                yaw = floats.read(0);
                pitch = floats.read(1);
            }
        }

        boolean packetOnGround = readPacketOnGround(event);
        UUID uuid = player.getUniqueId();
        PlayerCache cache = playerCaches.computeIfAbsent(uuid, ignored -> new PlayerCache());
        boolean hadPreviousPosition = cache.hasPosition;
        boolean previousOnGround = cache.onGround;
        double previousX = cache.lastX;
        double previousY = cache.lastY;
        double previousZ = cache.lastZ;

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

        long movementSequence = movementSequences
                .computeIfAbsent(uuid, ignored -> new AtomicLong())
                .incrementAndGet();

        boolean cancelled = event.isCancelled();
        String uid = uuid.toString();
        String name = player.getName();

        double sendX = hasPosition ? x : cache.lastX;
        double sendY = hasPosition ? y : cache.lastY;
        double sendZ = hasPosition ? z : cache.lastZ;

        float sendYaw = hasLook ? yaw : cache.lastYaw;
        float sendPitch = hasLook ? pitch : cache.lastPitch;

        double sendEyeX = cache.eyeX != 0.0 ? cache.eyeX : sendX;
        double sendEyeY = cache.eyeY != 0.0 ? cache.eyeY : sendY + 1.62;
        double sendEyeZ = cache.eyeZ != 0.0 ? cache.eyeZ : sendZ;
        float sendHeight = cache.height;

        try {
            PacketQueue.push(new PacketPlayerPosition(
                    timestamp, uid, name, cancelled,
                    sendX, sendY, sendZ,
                    sendEyeX, sendEyeY, sendEyeZ,
                    sendYaw, sendPitch, sendHeight, packetOnGround,
                    PacketPlayerPosition.SOURCE_RAW_CLIENT, movementSequence,
                    hasPosition, hasLook));

            // ── Physics capture sample ──
            if (hadPreviousPosition && PhysicsCaptureManager.isCaptureActive()) {
                try {
                    double prevDx = sendX - previousX;
                    double prevDy = sendY - previousY;
                    double prevDz = sendZ - previousZ;

                    Vector vel = player.getVelocity();
                    Vector previousVel = captureVelocities.put(uuid, vel.clone());

                    byte flags = 0;
                    if (packetOnGround) flags |= 0x01;
                    if (player.isSprinting()) flags |= 0x02;
                    if (player.isSwimming()) flags |= 0x04;
                    if (player.isSneaking()) flags |= 0x08;
                    if (playerBoolean(player, "isClimbing")) flags |= 0x10;
                    if (playerBoolean(player, "isGliding")) flags |= 0x20;

                    // Determine block context from player location
                    int supportBlockId = 0;
                    int frictionBlockId = 0;
                    byte surfaceCategory = 0;
                    String supportBlockName = "";
                    String blockProperties = "";
                    String supportShapeId = "";
                    float blockFriction = Float.NaN;
                    float velocityMultiplier = Float.NaN;
                    try {
                        org.bukkit.Location feetLoc = player.getLocation().clone();
                        org.bukkit.Location below = feetLoc.clone().add(0, -0.001, 0);
                        Block belowBlock = below.getBlock();
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

                    String bodyBlock = player.getLocation().getBlock().getType().name();
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

                    float tickDurationMs = PhysicsCaptureManager.observedTickDurationMs(uuid, System.nanoTime());
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

                    PhysicsCaptureManager.sendSampleV2(
                            timestamp,
                            movementSequence,
                            uuid,
                            serverProtocol(), clientProtocol(player),
                            previousX, previousY, previousZ,
                            (float) prevDx, (float) prevDy, (float) prevDz,
                            previousVelocityX, previousVelocityY, previousVelocityZ,
                            (float) vel.getX(), (float) vel.getY(), (float) vel.getZ(),
                            actualBaseSpeed(player),
                            flags & 0xff, previousFlags, currentFlags,
                            supportBlockId, frictionBlockId, surfaceCategory,
                            supportBlockName, blockProperties, blockProperties, supportShapeId,
                            blockFriction, velocityMultiplier,
                            bodyInWater, eyeInWater, inLava,
                            fluidKind, Float.NaN, Float.NaN,
                            Float.NaN, Float.NaN, Float.NaN, bodyFluid, eyeFluid,
                            effectState, actualBaseSpeed(player), Float.NaN,
                            jumpBoostLevel, slownessLevel,
                            vehicleMounted, vehicleType, vehicleMounted ? vehicle.getEntityId() : 0L, 0,
                            false, "", Float.NaN, Float.NaN, Float.NaN, 0L, 0,
                            tickDurationMs,
                            // Gateway does not own server-tick MSPT; preserve unknown
                            // instead of copying packet inter-arrival time.
                            Float.NaN, worldDim, 0,
                            (byte) 0xff, // prediction residual is not known on Gateway side
                            hasPosition, hasLook,
                            sendYaw, sendPitch,
                            lookVectorX(sendYaw, sendPitch),
                            lookVectorY(sendYaw, sendPitch),
                            lookVectorZ(sendYaw, sendPitch)
                    );
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            plugin
                .getLogger()
                .warning("Error processing position packet for " + name + ": " + e.getMessage());
        }
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

    private static int serverProtocol() {
        if (ServerVersion.major() == 1 && ServerVersion.minor() == 20) {
            return ServerVersion.patch() >= 5 ? 766 : ServerVersion.patch() >= 3 ? 765 : 764;
        }
        if (ServerVersion.major() == 1 && ServerVersion.minor() == 21) {
            if (ServerVersion.patch() >= 6) return 771;
            if (ServerVersion.patch() >= 5) return 770;
            if (ServerVersion.patch() >= 4) return 769;
            if (ServerVersion.patch() >= 2) return 768;
            return 767;
        }
        return 0;
    }

    private static int clientProtocol(Player player) {
        int fallback;
        try {
            fallback = com.comphenix.protocol.ProtocolLibrary.getProtocolManager().getProtocolVersion(player);
        } catch (Throwable ignored) {
            fallback = serverProtocol();
        }
        return PhysicsCaptureManager.clientProtocol(player.getUniqueId(), fallback);
    }

    private boolean readPacketOnGround(PacketEvent event) {
        try {
            StructureModifier<Boolean> booleans = event.getPacket().getBooleans();
            if (booleans.size() > 0) {
                return booleans.read(0);
            }
        } catch (RuntimeException ignored) {
        }
        return event.getPlayer().isOnGround();
    }
}
