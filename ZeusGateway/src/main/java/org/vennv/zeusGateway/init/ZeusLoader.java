package org.vennv.zeusGateway.init;

import java.io.IOException;
import java.util.EnumSet;
import java.util.logging.Level;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.listener.RawCaptureCapability;
import org.vennv.zeusGateway.listener.event.EventListener;
import org.vennv.zeusGateway.network.ProxyClient;
import org.vennv.zeusGateway.platform.PlatformDetector;
import org.vennv.zeusGateway.platform.PlatformType;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.task.BatchSender;
import org.vennv.zeusGateway.task.ChunkSyncTask;
import org.vennv.zeusGateway.task.PlayerStateSnapshotService;
import org.vennv.zeusGateway.task.UpdateTPS;

public class ZeusLoader {

    protected ZeusGateway plugin;
    private ProxyClient proxy;
    private Thread senderThread;

    public ZeusLoader(ZeusGateway plugin) {
        this.plugin = plugin;
    }

    public void init() {
        initConfig();
        initProxy();
        initListeners();
        initTasks();
    }

    private void initConfig() {
        this.plugin.saveDefaultConfig();

    }

    private void initProxy() {
        org.bukkit.configuration.file.FileConfiguration cfg = this.plugin.getConfig();
        try {
            proxy = new ProxyClient(
                cfg.getString("proxy-ac.host"),
                cfg.getInt("proxy-ac.port"),
                plugin.getPacketDebugService()
            );

            senderThread = new Thread(
                new BatchSender(
                        proxy,
                        cfg.getInt("packets.batch-size"),
                        uid -> ChunkSyncTask.invalidateAndRequestFullResync(plugin, uid)),
                "ZeusGateway-BatchSender"
            );
            senderThread.setDaemon(true);
            senderThread.start();

            plugin
                .getLogger()
                .info(
                    "[ZeusGateway] Proxy client connected to " +
                        cfg.getString("proxy-ac.host") +
                        ":" +
                        cfg.getInt("proxy-ac.port")
                );
        } catch (IOException e) {
            this.plugin.getLogger().log(
                Level.SEVERE,
                "[ZeusGateway] Failed to load proxy configuration!",
                e
            );
            throw new IllegalStateException("Failed to initialize proxy client", e);
        }
    }

    public void close() {
        if (senderThread != null) {
            senderThread.interrupt();
            try {
                senderThread.join(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            senderThread = null;
        }
        if (proxy != null) {
            proxy.close();
            proxy = null;
        }
        PacketQueue.clear();
    }

    private void initListeners() {
        EnumSet<RawCaptureCapability> rawCapabilities =
                EnumSet.noneOf(RawCaptureCapability.class);
        rawCapabilities.addAll(plugin.getRawCapabilities());

        // ─────────────────────────────────────────────────────────────────
        // 1. Register cross-platform Bukkit event listeners (Paper / Spigot / Folia)
        // ─────────────────────────────────────────────────────────────────
        this.plugin.getServer()
            .getPluginManager()
            .registerEvents(new EventListener(this.plugin, rawCapabilities), this.plugin);
        plugin
            .getLogger()
            .info("[ZeusGateway] Registered Bukkit event listeners. Raw fallback coverage="
                    + rawCapabilities);

        // ─────────────────────────────────────────────────────────────────
        // 2. Handle players already online (reload or late start)
        // ─────────────────────────────────────────────────────────────────
        ChunkSyncTask chunkSyncTask = new ChunkSyncTask(plugin);
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            plugin.getSchedulerAdapter().runEntityTask(plugin, player, () -> {
                ChunkSyncTask.invalidate(player);
                PlayerStateSnapshotService.sendFullSnapshot(player);
                chunkSyncTask.forceFull(player);
            });
        }
    }

    /**
     * Sets up repeating tasks (e.g. TPS monitor).
     * Uses the platform-appropriate scheduler.
     */
    private void initTasks() {
        PlatformType platform = plugin.getPlatformType();

        UpdateTPS tpsTask = new UpdateTPS();
        org.vennv.zeusGateway.task.ResyncTask resyncTask =
                new org.vennv.zeusGateway.task.ResyncTask(plugin);

        switch (platform) {
            case FOLIA:
                // On Folia we use the scheduler adapter which wraps the global region scheduler
                plugin
                    .getSchedulerAdapter()
                    .runTaskTimer(plugin, tpsTask, 20L, 20L);
                plugin
                    .getSchedulerAdapter()
                    .runTaskTimer(plugin, resyncTask, 100L, 200L);
                plugin
                    .getLogger()
                    .info(
                        "[ZeusGateway] TPS monitor and Resync task started via Folia scheduler."
                    );
                break;
            case PAPER:
            case SPIGOT:
                plugin
                    .getServer()
                    .getScheduler()
                    .runTaskTimer(plugin, tpsTask, 20L, 20L);
                plugin
                    .getServer()
                    .getScheduler()
                    .runTaskTimer(plugin, resyncTask, 100L, 200L);
                plugin
                    .getLogger()
                    .info(
                        "[ZeusGateway] TPS monitor and Resync task started via Bukkit scheduler."
                    );
                break;
            default:
                throw new IllegalStateException("Unsupported platform: " + platform);
        }
    }
}
