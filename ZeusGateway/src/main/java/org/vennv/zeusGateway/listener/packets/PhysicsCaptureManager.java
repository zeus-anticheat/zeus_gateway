package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.vennv.packets.PacketPhysicsCaptureSample;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.platform.ServerVersion;
import org.bukkit.Bukkit;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages physics capture sample sending from the Gateway.
 * Polls the Rust backend for capture state and builds/sends
 * compact movement samples when capture is active.
 */
public class PhysicsCaptureManager {

    private static final AtomicBoolean CAPTURE_ACTIVE = new AtomicBoolean(false);
    private static final long POLL_INTERVAL_MS = 5000;
    private static volatile Thread poller;

    private static String backendHost = "127.0.0.1";
    private static int backendPort = 3000;
    private static final String runtimeHashSalt = Long.toHexString(new SecureRandom().nextLong());
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Long> lastSampleNanos =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Start the capture state poller. Call once on plugin enable. */
    public static synchronized void start(ZeusGateway plugin) {
        stop();
        // Read the dashboard host/port if available from plugin config or env
        String envHost = System.getenv("ZEUS_DASHBOARD_HOST");
        if (envHost != null && !envHost.isEmpty()) {
            backendHost = envHost;
        }
        try {
            backendPort = Integer.parseInt(System.getenv().getOrDefault("ZEUS_DASHBOARD_PORT", "3000"));
        } catch (NumberFormatException ignored) {
            backendPort = 3000;
        }
        // Start polling thread
        poller = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                    pollCaptureState();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "PhysicsCapturePoller");
        poller.setDaemon(true);
        poller.start();
        plugin.getLogger().info("[PhysicsCapture] Started capture state poller");
    }

    public static synchronized void stop() {
        Thread activePoller = poller;
        poller = null;
        if (activePoller != null) {
            activePoller.interrupt();
        }
        CAPTURE_ACTIVE.set(false);
        lastSampleNanos.clear();
    }

    public static boolean isCaptureActive() {
        return CAPTURE_ACTIVE.get();
    }

    /**
     * Hash a player UUID with a runtime salt so raw UUIDs never leave the Gateway.
     */
    public static long hashPlayer(UUID uuid) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(runtimeHashSalt.getBytes(StandardCharsets.UTF_8));
            md.update((byte) ':');
            md.update(uuid.toString().getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest();
            // Truncate to 8 bytes (u64)
            long result = 0;
            for (int i = 0; i < 8; i++) {
                result = (result << 8) | (hash[i] & 0xFF);
            }
            return result;
        } catch (Exception e) {
            return fallbackHash(uuid);
        }
    }

    private static long fallbackHash(UUID uuid) {
        byte[] bytes = (runtimeHashSalt + ":" + uuid.toString()).getBytes(StandardCharsets.UTF_8);
        long hash = 0xcbf29ce484222325L;
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /**
     * Build and send a capture sample from per-tick context.
     * Called from the position listener when capture is active.
     */
    public static void sendSample(
            long timestamp,
            UUID playerUuid,
            int serverProtocol,
            int clientProtocol,
            double posX, double posY, double posZ,
            float prevDx, float prevDy, float prevDz,
            float velocityX, float velocityY, float velocityZ,
            float baseSpeed,
            byte inputFlags,
            int supportBlockId, int frictionBlockId,
            byte surfaceCategory,
            boolean bodyInWater, boolean eyeInWater, boolean inLava,
            byte effectLevels,
            float tickDurationMs,
            byte worldDimension,
            byte simErrorPct) {
        sendSampleV2(
                timestamp, timestamp, playerUuid, serverProtocol, clientProtocol,
                posX, posY, posZ, prevDx, prevDy, prevDz,
                velocityX, velocityY, velocityZ, velocityX, velocityY, velocityZ,
                baseSpeed, inputFlags & 0xff, 0, inputFlags & 0xff,
                supportBlockId, frictionBlockId, surfaceCategory,
                "", "", "", "", Float.NaN, Float.NaN,
                bodyInWater, eyeInWater, inLava, null, Float.NaN, Float.NaN,
                Float.NaN, Float.NaN, Float.NaN, "", "", null, baseSpeed,
                Float.NaN, (byte) 0, (byte) 0, false, "", 0L, 0,
                false, "", Float.NaN, Float.NaN, Float.NaN, 0L, 0,
                tickDurationMs, tickDurationMs, worldDimension, 0, simErrorPct,
                true, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f
        );
    }

    /**
     * Full v2 capture entry point.  Adapters pass every value they can obtain;
     * unavailable values remain NaN/empty and are marked in the packet mask.
     */
    public static void sendSampleV2(
            long timestamp, long tickIndex, UUID playerUuid,
            int serverProtocol, int clientProtocol,
            double posX, double posY, double posZ,
            float posDx, float posDy, float posDz,
            float velocityBeforeX, float velocityBeforeY, float velocityBeforeZ,
            float velocityAfterX, float velocityAfterY, float velocityAfterZ,
            float baseSpeed, int inputFlags, int previousStateFlags, int stateFlags,
            int supportBlockId, int frictionBlockId, byte surfaceCategory,
            String blockId, String blockProperties, String blockStateId,
            String supportShapeId, float friction, float velocityMultiplier,
            boolean bodyInWater, boolean eyeInWater, boolean inLava,
            String fluidKind, float fluidLevel, float fluidHeight,
            float flowX, float flowY, float flowZ, String bodyFluid, String eyeFluid,
            String effectLevels, float attributeBaseSpeed, float movementSpeedModifier,
            byte jumpBoostLevel, byte slownessLevel,
            boolean vehicleMounted, String vehicleType, long vehicleId, int vehicleStateFlags,
            boolean externalForceActive, String externalForceKind,
            float externalForceX, float externalForceY, float externalForceZ,
            long externalForceSourceTick, int externalForceTimingTicks,
            float tickDurationMs, float mspt, byte worldDimension, int resyncFlags,
            byte simErrorPct, boolean positionIncluded, boolean lookIncluded,
            float yaw, float pitch, float lookX, float lookY, float lookZ) {
        long playerHash = hashPlayer(playerUuid);
        long unknownMask = 0L;
        if (Float.isNaN(posDx) || Float.isNaN(posDy) || Float.isNaN(posDz)) unknownMask |= 1L << 1;
        if (Float.isNaN(velocityBeforeX) || Float.isNaN(velocityBeforeY)
                || Float.isNaN(velocityBeforeZ) || Float.isNaN(velocityAfterX)
                || Float.isNaN(velocityAfterY) || Float.isNaN(velocityAfterZ)) {
            unknownMask |= 1L << 2;
        }
        if (Float.isNaN(baseSpeed)) unknownMask |= 1L << 3;
        if (blockId == null || blockId.isEmpty()) unknownMask |= 1L << 4;
        if (supportShapeId == null || supportShapeId.isEmpty()) unknownMask |= 1L << 5;
        if (Float.isNaN(friction) || Float.isNaN(velocityMultiplier)) unknownMask |= 1L << 6;
        boolean fluidScalarUnavailable = fluidKind != null && !fluidKind.isEmpty()
                && !"air".equalsIgnoreCase(fluidKind)
                && (Float.isNaN(fluidLevel) || Float.isNaN(fluidHeight)
                    || Float.isNaN(flowX) || Float.isNaN(flowY) || Float.isNaN(flowZ));
        if (fluidKind == null || fluidKind.isEmpty() || fluidScalarUnavailable) unknownMask |= 1L << 7;
        if (effectLevels == null) unknownMask |= 1L << 8;
        if (vehicleMounted && (vehicleType == null || vehicleType.isEmpty())) unknownMask |= 1L << 9;
        if (externalForceActive && (externalForceKind == null || externalForceKind.isEmpty())) unknownMask |= 1L << 10;
        if (Float.isNaN(tickDurationMs) || Float.isNaN(mspt)) unknownMask |= 1L << 11;

        PacketPhysicsCaptureSample packet = new PacketPhysicsCaptureSample(
                timestamp, tickIndex,
                serverProtocol == 0 ? PacketPhysicsCaptureSample.UNKNOWN_U16 : serverProtocol,
                clientProtocol == 0 ? PacketPhysicsCaptureSample.UNKNOWN_U16 : clientProtocol,
                serverVersion(), clientVersion(clientProtocol), serverBrand(), platform(),
                physicsFingerprint(), physicsFingerprint(), captureSubjectId(playerUuid),
                translationBehaviorFingerprint(playerUuid, clientProtocol), "gateway", playerHash,
                posX, posY, posZ, posDx, posDy, posDz,
                velocityBeforeX, velocityBeforeY, velocityBeforeZ,
                velocityAfterX, velocityAfterY, velocityAfterZ, baseSpeed,
                inputFlags, previousStateFlags, stateFlags,
                supportBlockId, frictionBlockId, surfaceCategory,
                valueOrEmpty(blockId), valueOrEmpty(blockProperties), valueOrEmpty(blockStateId),
                valueOrEmpty(supportShapeId), friction, velocityMultiplier,
                bodyInWater, eyeInWater, inLava, valueOrEmpty(fluidKind), fluidLevel, fluidHeight,
                flowX, flowY, flowZ, valueOrEmpty(bodyFluid), valueOrEmpty(eyeFluid),
                valueOrEmpty(effectLevels), attributeBaseSpeed, movementSpeedModifier,
                jumpBoostLevel, slownessLevel,
                vehicleMounted, valueOrEmpty(vehicleType), vehicleId, vehicleStateFlags,
                externalForceActive, valueOrEmpty(externalForceKind),
                externalForceX, externalForceY, externalForceZ,
                externalForceSourceTick, externalForceTimingTicks,
                tickDurationMs, mspt, worldDimension, resyncFlags, unknownMask, simErrorPct,
                positionIncluded, lookIncluded, yaw, pitch, lookX, lookY, lookZ
        );
        PacketQueue.push(packet);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Returns an observed inter-sample duration; first sample is unknown. */
    public static float observedTickDurationMs(UUID playerUuid, long nowNanos) {
        Long previous = lastSampleNanos.put(playerUuid, nowNanos);
        if (previous == null || nowNanos <= previous) return Float.NaN;
        return Math.min(10_000.0f, (nowNanos - previous) / 1_000_000.0f);
    }

    public static String serverVersion() {
        return ServerVersion.major() + "." + ServerVersion.minor() + "." + ServerVersion.patch();
    }

    public static int serverProtocol() {
        try {
            com.github.retrooper.packetevents.manager.server.ServerVersion version =
                    PacketEvents.getAPI().getServerManager().getVersion();
            if (version != null && version.getProtocolVersion() > 0) {
                return version.getProtocolVersion();
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        if (ServerVersion.major() == 1 && ServerVersion.minor() == 20) {
            return ServerVersion.patch() >= 5 ? 766 : ServerVersion.patch() >= 3 ? 765 : 764;
        }
        if (ServerVersion.major() == 1 && ServerVersion.minor() == 21) {
            if (ServerVersion.patch() >= 11) return 774;
            if (ServerVersion.patch() >= 9) return 773;
            if (ServerVersion.patch() >= 7) return 772;
            if (ServerVersion.patch() >= 6) return 771;
            if (ServerVersion.patch() >= 5) return 770;
            if (ServerVersion.patch() >= 4) return 769;
            if (ServerVersion.patch() >= 2) return 768;
            return 767;
        }
        return 0;
    }

    public static int clientProtocol(org.bukkit.entity.Player player) {
        int fallback = serverProtocol();
        ClientVersion version = PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
        if (version != null && version.getProtocolVersion() > 0) {
            fallback = version.getProtocolVersion();
        }
        return clientProtocol(player.getUniqueId(), fallback);
    }

    public static String serverBrand() {
        try {
            return Bukkit.getName().toLowerCase(java.util.Locale.ROOT);
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    public static String platform() {
        String value = System.getenv("ZEUS_PLATFORM");
        if (value != null && !value.isEmpty()) return value;
        return serverBrand().contains("spigot") ? "spigot" : "paper";
    }

    public static String physicsFingerprint() {
        String value = System.getenv("ZEUS_PHYSICS_FINGERPRINT");
        return value == null || value.isEmpty() ? "vanilla" : value;
    }

    /**
     * Opaque subject id used only for the live capture session.  The core may
     * map it to an in-memory player binding, but it must never persist it in a
     * candidate profile.
     */
    public static String captureSubjectId(UUID uuid) {
        String salt = System.getenv().getOrDefault("ZEUS_CAPTURE_SUBJECT_SALT", "zeus-capture-subject-v1");
        long hash = 0xcbf29ce484222325L;
        byte[] bytes = (salt + ":" + uuid).getBytes(StandardCharsets.UTF_8);
        for (byte value : bytes) {
            hash ^= value & 0xffL;
            hash *= 0x100000001b3L;
        }
        return "subject-" + String.format(java.util.Locale.ROOT, "%016x", hash);
    }

    /**
     * A protocol number is metadata, not a physics identity.  An adapter may
     * opt in to a translation behaviour fingerprint only when it has verified
     * that the installed ViaVersion/configuration changes movement semantics.
     */
    public static String translationBehaviorFingerprint(UUID uuid, int clientProtocol) {
        String configured = System.getenv("ZEUS_TRANSLATION_BEHAVIOR_FINGERPRINT");
        if (configured == null || configured.isEmpty()) return "";
        return configured;
    }

    public static int clientProtocol(UUID uuid, int fallback) {
        try {
            Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
            Object api = via.getMethod("getAPI").invoke(null);
            Object value = api.getClass().getMethod("getPlayerVersion", UUID.class)
                    .invoke(api, uuid);
            if (value instanceof Number && ((Number) value).intValue() > 0) {
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    public static String clientVersion(int clientProtocol) {
        if (clientProtocol <= 0) return "unknown";
        ClientVersion version = ClientVersion.getById(clientProtocol);
        if (version == null || version.getProtocolVersion() != clientProtocol) {
            return "protocol-" + clientProtocol;
        }
        String releaseName = version.getReleaseName();
        return releaseName == null || releaseName.isEmpty()
                ? "protocol-" + clientProtocol
                : releaseName;
    }

    // ── Polling logic ──

    private static void pollCaptureState() {
        try {
            URL url = new URL("http://" + backendHost + ":" + backendPort + "/api/physics-capture/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            int status = conn.getResponseCode();
            if (status != 200) {
                CAPTURE_ACTIVE.set(false);
                return;
            }

            byte[] bytes = readAll(conn.getInputStream());
            String body = new String(bytes, StandardCharsets.UTF_8);

            // Simple JSON parse: look for "active":true
            boolean active = body.contains("\"active\":true") || body.contains("\"active\": true");
            CAPTURE_ACTIVE.set(active);

            conn.disconnect();
        } catch (Exception e) {
            // Network error — keep previous state
        }
    }

    private static byte[] readAll(InputStream input) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
