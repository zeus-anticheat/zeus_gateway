package org.vennv.zeusFabric;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vennv.zeusFabric.listener.ZeusEventListeners;
import org.vennv.zeusFabric.network.ProxyClient;
import org.vennv.zeusFabric.provider.PacketQueue;
import org.vennv.zeusFabric.task.BatchSender;
import org.vennv.zeusFabric.task.PlayerStateSnapshotService;
import org.vennv.zeusFabric.task.ResyncTask;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Zeus Anti-Cheat data collector mod for Fabric servers.
 * <p>
 * This is the server-side entry point. It initialises the UDP proxy connection,
 * registers all Fabric event callbacks that mirror the packets defined in
 * ZeusProtocolJava, and starts the batch-sender daemon thread.
 */
public final class ZeusFabricMod implements DedicatedServerModInitializer {

    public static final String MOD_ID = "zeusfabric";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ZeusFabricMod INSTANCE;
    private static MinecraftServer server;

    private ProxyClient proxyClient;
    private BatchSender batchSender;
    private Thread batchThread;

    // ── Config fields ──
    private String proxyHost = "127.0.0.1";
    private int proxyPort = 9999;
    private int batchSize = 100;

    // ── TPS tracking ──
    private long lastTickNanos = 0;
    private double emaTps = 20.0;
    private final ResyncTask resyncTask = new ResyncTask();

    public static ZeusFabricMod getInstance() {
        return INSTANCE;
    }

    public static MinecraftServer getServer() {
        return server;
    }

    public ProxyClient getProxyClient() {
        return proxyClient;
    }

    @Override
    public void onInitializeServer() {
        INSTANCE = this;
        LOGGER.info("[ZeusFabric] Initializing Zeus Anti-Cheat Fabric mod...");

        // ─── Load config ────────────────────────────────────────────────
        loadConfig();

        // ─── Server starting ────────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);

        // ─── Server started ─────────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);

        // ─── Server stopping ────────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        // ─── Init server combat settings ────────────────────────────────
        // Fabric 1.21+ always has attack cooldown (10 ticks) and vanilla reach (3.0)
        ServerCombatSettings.init(3.0f, 10.0f, (byte) 0);
        LOGGER.info("[ZeusFabric] Server combat settings: reach=3.0 cooldown=10.0 maxCps=0");

        // ─── Register all event listeners ───────────────────────────────
        ZeusEventListeners.registerAll();

        ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);

        LOGGER.info("[ZeusFabric] Event listeners registered.");
    }

    // ─────────────────────── Lifecycle callbacks ────────────────────────

    private void onServerStarting(MinecraftServer minecraftServer) {
        server = minecraftServer;
        LOGGER.info("[ZeusFabric] Server starting...");

        // ── Init proxy connection ──
        try {
            proxyClient = new ProxyClient(proxyHost, proxyPort);
            LOGGER.info("[ZeusFabric] Proxy client created -> {}:{}", proxyHost, proxyPort);
        } catch (IOException e) {
            LOGGER.error("[ZeusFabric] Failed to create proxy client!", e);
            return;
        }

        // ── Start batch sender daemon ──
        batchSender = new BatchSender(proxyClient, batchSize);
        batchThread = new Thread(batchSender, "ZeusFabric-BatchSender");
        batchThread.setDaemon(true);
        batchThread.start();
        LOGGER.info("[ZeusFabric] Batch sender thread started (batch-size={}).", batchSize);
    }

    private void onServerStarted(MinecraftServer minecraftServer) {
        LOGGER.info("[ZeusFabric] Server started. Zeus Anti-Cheat Fabric mod is now active.");
        for (var player : minecraftServer.getPlayerManager().getPlayerList()) {
            PlayerStateSnapshotService.sendFullSnapshot(player);
        }
    }

    private void onServerStopping(MinecraftServer minecraftServer) {
        LOGGER.info("[ZeusFabric] Server stopping – shutting down Zeus...");
        ZeusEventListeners.stopCaptureControlPoller();

        if (batchSender != null) {
            batchSender.stop();
        }

        if (batchThread != null) {
            try {
                batchThread.join(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (batchThread.isAlive()) {
                LOGGER.warn("[ZeusFabric] Sender drain timed out with {} queued packets", PacketQueue.size());
                if (proxyClient != null) {
                    proxyClient.close();
                }
                batchThread.interrupt();
                try {
                    batchThread.join(1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                if (batchThread.isAlive()) {
                    LOGGER.error("[ZeusFabric] Sender thread did not stop after forced shutdown");
                }
            }
        }

        if (proxyClient != null && !proxyClient.isClosed()) {
            proxyClient.close();
        }

        long dropped = PacketQueue.droppedCount();
        if (dropped > 0L) {
            LOGGER.warn("[ZeusFabric] Dropped {} packets while queue capacity was {}", dropped, PacketQueue.capacity());
        }
        PlayerStateSnapshotService.clearAll();
        PacketQueue.clear();
        server = null;
        LOGGER.info("[ZeusFabric] Shutdown complete.");
    }

    // ──────────────────────── TPS sampling ─────────────────────────────

    private void onEndServerTick(MinecraftServer minecraftServer) {
        if (PacketQueue.discontinuityRequired()) {
            boolean recovered = PacketQueue.recoverFromDiscontinuity(() -> {
                for (var player : minecraftServer.getPlayerManager().getPlayerList()) {
                    PlayerStateSnapshotService.invalidate(player);
                    PlayerStateSnapshotService.sendResyncSnapshot(player);
                }
            });
            if (!recovered) {
                LOGGER.warn("[ZeusFabric] Queue discontinuity resync did not fit capacity {}", PacketQueue.capacity());
            }
        }
        resyncTask.tick(minecraftServer);
        long now = System.nanoTime();

        if (lastTickNanos == 0) {
            lastTickNanos = now;
            return;
        }

        double tickTimeMs = (now - lastTickNanos) / 1_000_000.0;
        lastTickNanos = now;

        if (tickTimeMs <= 0) {
            tickTimeMs = 50.0;
        }

        double rawTps = Math.min(20.0, 1000.0 / tickTimeMs);
        emaTps = 0.2 * rawTps + 0.8 * emaTps;
        double tps = Math.max(5.0, Math.min(20.0, emaTps));

        PacketQueue.push(new org.vennv.packets.PacketTPSServer(tps));
    }

    // ──────────────────────── Config loading ───────────────────────────

    private void loadConfig() {
        Path configDir = Path.of("config");
        Path configFile = configDir.resolve("zeusfabric.properties");

        try {
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            if (!Files.exists(configFile)) {
                // Write default config
                String defaults = """
                        # Zeus Anti-Cheat Fabric Mod Configuration
                        proxy-host=127.0.0.1
                        proxy-port=9999
                        batch-size=100
                        """;
                Files.writeString(configFile, defaults);
                LOGGER.info("[ZeusFabric] Default config written to {}", configFile);
            }

            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(configFile)) {
                props.load(in);
            }

            proxyHost = props.getProperty("proxy-host", "127.0.0.1");
            proxyPort = Integer.parseInt(props.getProperty("proxy-port", "9999"));
            batchSize = Integer.parseInt(props.getProperty("batch-size", "100"));
            if (proxyPort < 1 || proxyPort > 65535) {
                throw new IllegalArgumentException("proxy-port must be between 1 and 65535");
            }
            if (batchSize < 1 || batchSize > PacketQueue.capacity()) {
                throw new IllegalArgumentException("batch-size must be between 1 and " + PacketQueue.capacity());
            }

            LOGGER.info("[ZeusFabric] Config loaded: host={}, port={}, batch-size={}",
                    proxyHost, proxyPort, batchSize);

        } catch (Exception e) {
            proxyHost = "127.0.0.1";
            proxyPort = 9999;
            batchSize = 100;
            LOGGER.error("[ZeusFabric] Failed to load config, using defaults.", e);
        }
    }
}
