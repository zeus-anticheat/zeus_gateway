package org.vennv.zeusGatewayLegacy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.vennv.packets.PacketPhysicsCaptureSample;
import org.vennv.packets.PacketServerConfig;

/**
 * Legacy movement capture bridge for 1.8-1.13 servers.
 *
 * ProtocolLib is deliberately not required here: this bridge records the
 * Bukkit movement snapshot as a V3 frame with an honest capability bitmap.
 * It is useful for runtime fallback and diagnostics, but cannot claim Full
 * readiness without the richer ProtocolLib/client packet capabilities.
 */
final class LegacyPhysicsCaptureManager {
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final ConcurrentHashMap<UUID, Snapshot> LAST = new ConcurrentHashMap<UUID, Snapshot>();
    private static final ConcurrentHashMap<UUID, Long> SEQUENCES = new ConcurrentHashMap<UUID, Long>();
    private static final ConcurrentHashMap<UUID, Long> LAST_NANOS = new ConcurrentHashMap<UUID, Long>();
    private static String dashboardHost = "127.0.0.1";
    private static int dashboardPort = 3000;

    private LegacyPhysicsCaptureManager() {}

    static void start(final ZeusGatewayLegacy plugin) {
        dashboardHost = valueOrDefault(System.getenv("ZEUS_DASHBOARD_HOST"), "127.0.0.1");
        try {
            dashboardPort = Integer.parseInt(valueOrDefault(System.getenv("ZEUS_DASHBOARD_PORT"), "3000"));
        } catch (NumberFormatException ignored) {
            dashboardPort = 3000;
        }
        ACTIVE.set(plugin.getConfig().getBoolean("physics-capture.enabled", false));
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override public void run() { pollCaptureState(plugin); }
        }, 20L, 100L);
    }

    static void stop() {
        ACTIVE.set(false);
        LAST.clear();
        SEQUENCES.clear();
        LAST_NANOS.clear();
    }

    static void remove(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        LAST.remove(id);
        SEQUENCES.remove(id);
        LAST_NANOS.remove(id);
    }

    static void capture(Player player, long timestamp) {
        if (!ACTIVE.get() || player == null) return;
        UUID uuid = player.getUniqueId();
        Location location = player.getLocation();
        Vector velocity = player.getVelocity();
        Snapshot previous = LAST.put(uuid, new Snapshot(location, velocity));
        long sequence = SEQUENCES.merge(uuid, 1L, (left, right) -> left + right);
        long nowNanos = System.nanoTime();
        Long previousNanos = LAST_NANOS.put(uuid, nowNanos);
        float tickDuration = previousNanos == null ? Float.NaN
                : (float) Math.min(10_000.0, (nowNanos - previousNanos) / 1_000_000.0);
        if (previous == null) {
            LegacyPacketQueue.push(serverConfig(player, timestamp));
            return;
        }

        String version = serverVersion();
        String bodyFluid = bodyFluid(player);
        int flags = 0;
        if (player.isOnGround()) flags |= 0x01;
        if (player.isSprinting()) flags |= 0x02;
        if (player.isSneaking()) flags |= 0x08;
        String vehicleType = "";
        long vehicleId = 0L;
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            flags |= 0x1000;
            vehicleType = "minecraft:" + vehicle.getType().name().toLowerCase(Locale.ROOT);
            vehicleId = vehicle.getEntityId();
        }
        String block = "minecraft:" + player.getLocation().getBlock().getType().name().toLowerCase(Locale.ROOT);
        String effects = effects(player);
        int protocol = serverProtocol(version);
        float walkSpeed;
        try { walkSpeed = player.getWalkSpeed() / 2.0f; } catch (Throwable ignored) { walkSpeed = Float.NaN; }
        long unknown = 0L;
        if (!Float.isFinite(tickDuration)) unknown |= 1L << 11;

        LegacyPacketQueue.push(new PacketPhysicsCaptureSample(
                timestamp, sequence, protocol, PacketPhysicsCaptureSample.UNKNOWN_U16,
                version, "unknown", "legacy", "bukkit",
                "vanilla", "vanilla", captureSubjectId(uuid), "", "legacy", hashPlayer(uuid),
                previous.location.getX(), previous.location.getY(), previous.location.getZ(),
                (float) (location.getX() - previous.location.getX()),
                (float) (location.getY() - previous.location.getY()),
                (float) (location.getZ() - previous.location.getZ()),
                (float) previous.velocity.getX(), (float) previous.velocity.getY(), (float) previous.velocity.getZ(),
                (float) velocity.getX(), (float) velocity.getY(), (float) velocity.getZ(), walkSpeed,
                flags, 0, flags, 0, 0, (byte) 0,
                block, "", block, "", Float.NaN, Float.NaN,
                "water".equals(bodyFluid), false, "lava".equals(bodyFluid), bodyFluid,
                Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, bodyFluid, "",
                effects, walkSpeed, Float.NaN, (byte) 0, (byte) 0,
                vehicle != null, vehicleType, vehicleId, 0,
                false, "", Float.NaN, Float.NaN, Float.NaN, 0L, 0,
                tickDuration, Float.NaN, (byte) 0, 0, unknown, (byte) 0xff));
    }

    private static void pollCaptureState(ZeusGatewayLegacy plugin) {
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
            boolean active = body.toString().contains("\"active\":true");
            ACTIVE.set(active);
        } catch (Exception ignored) {
            // Keep the explicit local fallback; a dashboard outage must not
            // silently turn a legacy runtime into a Full capture.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static PacketServerConfig serverConfig(Player player, long timestamp) {
        String version = serverVersion();
        float movementSpeed;
        try { movementSpeed = player.getWalkSpeed() / 2.0f; } catch (Throwable ignored) { movementSpeed = 0.1f; }
        return new PacketServerConfig(timestamp, player.getUniqueId().toString(), player.getName(),
                3.0f, 0.0f, (byte) 0, movementSpeed, serverProtocol(version), version,
                "legacy", "bukkit", "vanilla", PacketPhysicsCaptureSample.UNKNOWN_U16,
                "unknown", "", "legacy");
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
        if (version.contains("1.13")) return "1.13";
        if (version.contains("1.12")) return "1.12";
        if (version.contains("1.9")) return "1.9";
        return "1.8";
    }

    private static int serverProtocol(String version) {
        if (version.startsWith("1.13")) return 393;
        if (version.startsWith("1.12")) return 340;
        if (version.startsWith("1.9")) return 107;
        return 47;
    }

    private static long hashPlayer(UUID uuid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("zeus-capture-player-v1:".getBytes(StandardCharsets.UTF_8));
            digest.update(uuid.toString().getBytes(StandardCharsets.UTF_8));
            byte[] bytes = digest.digest();
            long result = 0L;
            for (int i = 0; i < 8; i++) result = (result << 8) | (bytes[i] & 0xffL);
            return result;
        } catch (Exception ignored) {
            return uuid.getLeastSignificantBits();
        }
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
        private final Location location;
        private final Vector velocity;
        private Snapshot(Location location, Vector velocity) {
            this.location = location.clone();
            this.velocity = velocity == null ? new Vector() : velocity.clone();
        }
    }
}
