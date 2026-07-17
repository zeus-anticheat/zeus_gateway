package org.vennv.zeusGatewayLegacy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.vennv.packets.PacketPhysicsCaptureSample;
import org.vennv.packets.PacketServerConfig;

/**
 * Legacy movement capture bridge for 1.8-1.13 servers.
 *
 * PacketEvents supplies raw movement cadence while Bukkit enriches each V3
 * frame with server-owned state unavailable on the client packet thread.
 */
final class LegacyPhysicsCaptureManager {
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ConcurrentHashMap<UUID, Snapshot> LAST = new ConcurrentHashMap<UUID, Snapshot>();
    private static final ConcurrentHashMap<UUID, Long> LAST_NANOS = new ConcurrentHashMap<UUID, Long>();
    private static final byte[] RUNTIME_HASH_SALT = runtimeSalt();
    private static final long LEGACY_CAPABILITIES =
            org.vennv.packets.CaptureFrameV3.CAPABILITY_POSITION
            | org.vennv.packets.CaptureFrameV3.CAPABILITY_NO_POSITION_MOVEMENT
            | org.vennv.packets.CaptureFrameV3.CAPABILITY_EFFECTS
            | org.vennv.packets.CaptureFrameV3.CAPABILITY_ATTRIBUTES
            | org.vennv.packets.CaptureFrameV3.CAPABILITY_VEHICLES;
    private static String dashboardHost = "127.0.0.1";
    private static int dashboardPort = 3000;
    private static float serverReach = 3.0f;
    private static float attackCooldownOverride = -1.0f;
    private static byte maxCps;
    private static String platform = "unknown";
    private static String physicsFingerprint = "unattested";
    private static BukkitTask pollTask;

    private LegacyPhysicsCaptureManager() {}

    static synchronized void start(final JavaPlugin plugin) {
        stop();
        final long generation = GENERATION.incrementAndGet();
        dashboardHost = valueOrDefault(System.getenv("ZEUS_DASHBOARD_HOST"), "127.0.0.1");
        try {
            dashboardPort = Integer.parseInt(valueOrDefault(System.getenv("ZEUS_DASHBOARD_PORT"), "3000"));
        } catch (NumberFormatException ignored) {
            dashboardPort = 3000;
        }
        String brand = serverBrand();
        platform = platformForBrand(brand, valueOrDefault(
                System.getenv("ZEUS_PLATFORM"), plugin.getConfig().getString("identity.platform", "auto")));
        physicsFingerprint = physicsFingerprint(System.getenv("ZEUS_PHYSICS_FINGERPRINT"),
                plugin.getConfig().getString("identity.physics-fingerprint", "unattested"));
        configureCapabilityBitmap();
        ACTIVE.set(plugin.getConfig().getBoolean("physics-capture.enabled", false));
        float configuredReach = (float) plugin.getConfig().getDouble("server-combat.reach-override", 0.0);
        serverReach = configuredReach > 0.0f ? configuredReach : 3.0f;
        attackCooldownOverride = (float) plugin.getConfig().getDouble("server-combat.cooldown-override", -1.0);
        maxCps = (byte) plugin.getConfig().getInt("server-combat.max-cps", 0);
        pollTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override public void run() { pollCaptureState(generation); }
        }, 20L, 100L);
    }

    static synchronized void stop() {
        GENERATION.incrementAndGet();
        ACTIVE.set(false);
        BukkitTask task = pollTask;
        pollTask = null;
        if (task != null) task.cancel();
        LAST.clear();
        LAST_NANOS.clear();
    }

    static void remove(Player player) {
        if (player != null) reset(player.getUniqueId());
    }

    static void reset(UUID uuid) {
        if (uuid == null) return;
        LAST.remove(uuid);
        LAST_NANOS.remove(uuid);
    }

    static void capture(
            Player player, long timestamp, long movementSequence,
            double x, double y, double z, boolean hasPosition, boolean hasLook,
            float yaw, float pitch) {
        if (!ACTIVE.get() || player == null || movementSequence <= 0L) return;
        UUID uuid = player.getUniqueId();
        Vector velocity = player.getVelocity();
        int flags = stateFlags(player);
        Snapshot current = new Snapshot(x, y, z, velocity, flags);
        Snapshot previous = LAST.put(uuid, current);
        long nowNanos = System.nanoTime();
        Long previousNanos = LAST_NANOS.put(uuid, nowNanos);
        if (previous == null) return;
        float tickDuration = previousNanos == null ? Float.NaN
                : (float) Math.min(10_000.0, (nowNanos - previousNanos) / 1_000_000.0);
        String version = serverVersion();
        String bodyFluid = bodyFluid(player);
        String vehicleType = "";
        long vehicleId = 0L;
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            vehicleType = "minecraft:" + vehicle.getType().name().toLowerCase(Locale.ROOT);
            vehicleId = vehicle.getEntityId();
        }
        String block = "minecraft:" + player.getLocation().getBlock().getType().name().toLowerCase(Locale.ROOT);
        float walkSpeed;
        try { walkSpeed = player.getWalkSpeed() / 2.0f; } catch (Throwable ignored) { walkSpeed = Float.NaN; }
        long unknown = Float.isFinite(tickDuration) ? 0L : 1L << 11;
        int clientProtocol = clientProtocol(player);

        LegacyPacketQueue.push(uuid, new PacketPhysicsCaptureSample(
                timestamp, movementSequence, serverProtocol(), clientProtocol,
                version, clientVersion(clientProtocol),
                serverBrand(), platform, physicsFingerprint, physicsFingerprint,
                captureSubjectId(uuid), "", "legacy", hashPlayer(uuid),
                previous.x, previous.y, previous.z,
                (float) (x - previous.x), (float) (y - previous.y), (float) (z - previous.z),
                (float) previous.velocity.getX(), (float) previous.velocity.getY(), (float) previous.velocity.getZ(),
                (float) velocity.getX(), (float) velocity.getY(), (float) velocity.getZ(), walkSpeed,
                flags, previous.flags, flags, 0, 0, (byte) 0,
                block, "", block, "", Float.NaN, Float.NaN,
                "water".equals(bodyFluid), false, "lava".equals(bodyFluid), bodyFluid,
                Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, bodyFluid, "",
                effects(player), walkSpeed, Float.NaN, (byte) 0, (byte) 0,
                vehicle != null, vehicleType, vehicleId, 0,
                false, "", Float.NaN, Float.NaN, Float.NaN, 0L, 0,
                tickDuration, Float.NaN, (byte) player.getWorld().getEnvironment().ordinal(),
                0, unknown, (byte) 0xff, hasPosition, hasLook, yaw, pitch,
                lookX(yaw, pitch), lookY(pitch), lookZ(yaw, pitch)));
    }

    private static void pollCaptureState(long generation) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://" + dashboardHost + ":" + dashboardPort + "/api/physics-capture/status");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            connection.setRequestMethod("GET");
            StringBuilder body = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            applyPollResult(generation, body.toString().contains("\"active\":true"));
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static synchronized boolean applyPollResult(long generation, boolean active) {
        return applyPollResult(generation, active, pollTask != null);
    }

    static synchronized boolean applyPollResult(long generation, boolean active, boolean taskCurrent) {
        if (!taskCurrent || GENERATION.get() != generation) return false;
        ACTIVE.set(active);
        return true;
    }

    static PacketServerConfig serverConfig(Player player, long timestamp) {
        String version = serverVersion();
        int protocol = serverProtocol();
        int clientProtocol = clientProtocol(player);
        float movementSpeed;
        try { movementSpeed = player.getWalkSpeed() / 2.0f; } catch (Throwable ignored) { movementSpeed = 0.1f; }
        float cooldown = cooldownTicks(protocol, attackCooldownOverride);
        return new PacketServerConfig(timestamp, player.getUniqueId().toString(), player.getName(),
                serverReach, cooldown, maxCps, movementSpeed, protocol, version,
                serverBrand(), platform, physicsFingerprint, clientProtocol,
                clientVersion(clientProtocol), "", "legacy");
    }

    static float cooldownTicks(int protocol, float override) {
        return override >= 0.0f ? override : protocol <= 47 ? 0.0f : 10.0f;
    }

    static int serverProtocol() {
        try {
            com.github.retrooper.packetevents.manager.server.ServerVersion version =
                    com.github.retrooper.packetevents.PacketEvents.getAPI().getServerManager().getVersion();
            if (version != null && version.getProtocolVersion() > 0) return version.getProtocolVersion();
        } catch (RuntimeException | LinkageError ignored) {}
        return serverProtocol(serverVersion());
    }

    static int clientProtocol(Player player) {
        try {
            com.github.retrooper.packetevents.protocol.player.ClientVersion version =
                    com.github.retrooper.packetevents.PacketEvents.getAPI()
                            .getPlayerManager().getClientVersion(player);
            if (version != null && version.getProtocolVersion() > 0) return version.getProtocolVersion();
        } catch (RuntimeException | LinkageError ignored) {}
        return serverProtocol();
    }

    private static String clientVersion(int protocol) {
        com.github.retrooper.packetevents.protocol.player.ClientVersion version =
                com.github.retrooper.packetevents.protocol.player.ClientVersion.getById(protocol);
        return version == null ? "unknown" : version.getReleaseName();
    }

    private static String serverBrand() {
        String name = Bukkit.getName();
        return name == null ? "unknown" : name.toLowerCase(Locale.ROOT);
    }

    static String platformForBrand(String brand, String override) {
        if (override != null && !override.trim().isEmpty()
                && !"auto".equalsIgnoreCase(override.trim())) {
            return override.trim().toLowerCase(Locale.ROOT);
        }
        String normalized = brand == null ? "" : brand.toLowerCase(Locale.ROOT);
        if (normalized.contains("paper")) return "paper";
        if (normalized.contains("spigot")) return "spigot";
        if (normalized.contains("craftbukkit") || normalized.contains("bukkit")) return "bukkit";
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    static long legacyCapabilities() {
        return LEGACY_CAPABILITIES;
    }

    static void configureCapabilityBitmap() {
        System.setProperty("zeus.capture.capabilities", "0x" + Long.toHexString(LEGACY_CAPABILITIES));
    }

    static String physicsFingerprint(String environment, String configured) {
        return valueOrDefault(environment, valueOrDefault(configured, "unattested"));
    }

    static long generation() {
        return GENERATION.get();
    }

    private static int stateFlags(Player player) {
        int flags = 0;
        if (player.isOnGround()) flags |= 0x01;
        if (player.isSprinting()) flags |= 0x02;
        if (player.isSneaking()) flags |= 0x08;
        if (player.getVehicle() != null) flags |= 0x1000;
        return flags;
    }

    private static float lookX(float yaw, float pitch) {
        return (float) (-Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
    }

    private static float lookY(float pitch) {
        return (float) -Math.sin(Math.toRadians(pitch));
    }

    private static float lookZ(float yaw, float pitch) {
        return (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
    }

    private static String effects(Player player) {
        StringBuilder result = new StringBuilder();
        try {
            for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
                if (result.length() > 0) result.append(',');
                result.append(effect.getType().getName().toLowerCase(Locale.ROOT))
                        .append('=').append(effect.getAmplifier() + 1);
            }
        } catch (Throwable ignored) {}
        return result.toString();
    }

    private static String bodyFluid(Player player) {
        String name = player.getLocation().getBlock().getType().name();
        if (name.contains("LAVA")) return "lava";
        if (name.contains("WATER")) return "water";
        return "air";
    }

    private static String serverVersion() {
        String version = Bukkit.getVersion();
        if (version == null) return "1.8";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:MC: |minecraft server version )?(1\\.\\d+(?:\\.\\d+)?)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(version);
        return matcher.find() ? matcher.group(1) : "1.8";
    }

    static int serverProtocol(String version) {
        if (version.startsWith("1.13.2")) return 404;
        if (version.startsWith("1.13.1")) return 401;
        if (version.startsWith("1.13")) return 393;
        if (version.startsWith("1.12.2")) return 340;
        if (version.startsWith("1.12.1")) return 338;
        if (version.startsWith("1.12")) return 335;
        if (version.startsWith("1.11.1") || version.startsWith("1.11.2")) return 316;
        if (version.startsWith("1.11")) return 315;
        if (version.startsWith("1.10")) return 210;
        if (version.startsWith("1.9.4") || version.startsWith("1.9.3")) return 110;
        if (version.startsWith("1.9.2")) return 109;
        if (version.startsWith("1.9.1")) return 108;
        if (version.startsWith("1.9")) return 107;
        return 47;
    }

    static long hashPlayer(UUID uuid) {
        return hashPlayer(uuid, RUNTIME_HASH_SALT);
    }

    static long hashPlayer(UUID uuid, byte[] salt) {
        byte[] uuidBytes = uuid.toString().getBytes(StandardCharsets.UTF_8);
        byte[] effectiveSalt = salt == null ? new byte[0] : salt;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(effectiveSalt);
            digest.update((byte) ':');
            digest.update(uuidBytes);
            byte[] bytes = digest.digest();
            long result = 0L;
            for (int i = 0; i < 8; i++) result = (result << 8) | (bytes[i] & 0xffL);
            return result;
        } catch (NoSuchAlgorithmException impossible) {
            long result = 0xcbf29ce484222325L;
            for (byte value : effectiveSalt) {
                result ^= value & 0xffL;
                result *= 0x100000001b3L;
            }
            for (byte value : uuidBytes) {
                result ^= value & 0xffL;
                result *= 0x100000001b3L;
            }
            return result;
        }
    }

    private static byte[] runtimeSalt() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static String captureSubjectId(UUID uuid) {
        long hash = 0xcbf29ce484222325L;
        byte[] bytes = (valueOrDefault(System.getenv("ZEUS_CAPTURE_SUBJECT_SALT"), "zeus-capture-subject-v1") + ":" + uuid)
                .getBytes(StandardCharsets.UTF_8);
        for (byte value : bytes) { hash ^= value & 0xffL; hash *= 0x100000001b3L; }
        return "subject-" + String.format(Locale.ROOT, "%016x", hash);
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static final class Snapshot {
        private final double x;
        private final double y;
        private final double z;
        private final Vector velocity;
        private final int flags;

        private Snapshot(double x, double y, double z, Vector velocity, int flags) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.velocity = velocity == null ? new Vector() : velocity.clone();
            this.flags = flags;
        }
    }
}
