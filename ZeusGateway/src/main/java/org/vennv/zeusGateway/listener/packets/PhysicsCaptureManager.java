package org.vennv.zeusGateway.listener.packets;

import org.vennv.packets.PacketPhysicsCaptureSample;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

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

    private static String backendHost = "127.0.0.1";
    private static int backendPort = 8080;
    private static final String runtimeHashSalt = Long.toHexString(new SecureRandom().nextLong());

    /** Start the capture state poller. Call once on plugin enable. */
    public static void start(ZeusGateway plugin) {
        // Read the dashboard host/port if available from plugin config or env
        String envHost = System.getenv("ZEUS_DASHBOARD_HOST");
        if (envHost != null && !envHost.isEmpty()) {
            backendHost = envHost;
        }
        // Start polling thread
        Thread poller = new Thread(() -> {
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
            byte tickDurationMs,
            byte worldDimension,
            byte simErrorPct) {

        long playerHash = hashPlayer(playerUuid);

        PacketPhysicsCaptureSample packet = new PacketPhysicsCaptureSample(
                timestamp,
                serverProtocol,
                clientProtocol,
                playerHash,
                posX, posY, posZ,
                prevDx, prevDy, prevDz,
                velocityX, velocityY, velocityZ,
                baseSpeed,
                inputFlags,
                supportBlockId, frictionBlockId, surfaceCategory,
                bodyInWater, eyeInWater, inLava,
                effectLevels,
                tickDurationMs,
                worldDimension,
                simErrorPct
        );

        PacketQueue.push(packet);
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
