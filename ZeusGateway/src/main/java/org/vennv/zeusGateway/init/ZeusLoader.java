package org.vennv.zeusGateway.init;

import java.io.IOException;
import java.util.EnumSet;
import java.util.logging.Level;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.listener.RawCaptureCapability;
import org.vennv.zeusGateway.listener.event.EventListener;
import org.vennv.zeusGateway.listener.event.PaperEventListener;
import org.vennv.zeusGateway.network.ProxyClient;
import org.vennv.zeusGateway.platform.PlatformDetector;
import org.vennv.zeusGateway.platform.PlatformType;
import org.vennv.zeusGateway.platform.ServerCombatSettings;
import org.vennv.zeusGateway.platform.ServerVersion;
import org.vennv.zeusGateway.task.BatchSender;
import org.vennv.zeusGateway.task.PlayerStateSnapshotService;
import org.vennv.zeusGateway.task.UpdateTPS;

public class ZeusLoader {

    protected ZeusGateway plugin;

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

        // ── Server combat settings for adaptive detection ──
        org.bukkit.configuration.file.FileConfiguration cfg = this.plugin.getConfig();
        float reachOverride = (float) cfg.getDouble("server-combat.reach-override", 0);
        float cooldownOverride = (float) cfg.getDouble("server-combat.cooldown-override", -1);
        byte maxCps = (byte) cfg.getInt("server-combat.max-cps", 0);

        // Auto-detect reach (vanilla base is always 3.0; per-player attribute overrides
        // are already sent via PacketPlayerEnchantments)
        float serverReach = reachOverride > 0 ? reachOverride : 3.0f;

        // Auto-detect cooldown from MC version
        float cooldownTicks;
        if (cooldownOverride >= 0) {
            cooldownTicks = cooldownOverride;
        } else {
            // MC 1.9+ has 10-tick attack cooldown, 1.8 has none
            cooldownTicks = ServerVersion.isAtLeast(1, 9) ? 10.0f : 0.0f;
        }

        ServerCombatSettings.init(serverReach, cooldownTicks, maxCps);
        plugin.getLogger().info("[ZeusGateway] Server combat settings: reach=" + serverReach
                + " cooldown=" + cooldownTicks + " maxCps=" + maxCps);
    }

    private void initProxy() {
        org.bukkit.configuration.file.FileConfiguration cfg = this.plugin.getConfig();
        try {
            ProxyClient proxy = new ProxyClient(
                cfg.getString("proxy-ac.host"),
                cfg.getInt("proxy-ac.port"),
                plugin.getPacketDebugService()
            );

            Thread thread = new Thread(
                new BatchSender(proxy, cfg.getInt("packets.batch-size"))
            );
            thread.setDaemon(true);
            thread.start();

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
            this.plugin.getServer()
                .getPluginManager()
                .disablePlugin(this.plugin);
        }
    }

    private void initListeners() {
        EnumSet<RawCaptureCapability> rawCapabilities =
            EnumSet.noneOf(RawCaptureCapability.class);

        // ─────────────────────────────────────────────────────────────────
        // 1. Register ProtocolLib-based packet listeners (if ProtocolLib is available)
        // ─────────────────────────────────────────────────────────────────
        if (plugin.isProtocolLibAvailable()) {
            rawCapabilities = registerProtocolLibListenersIfAvailable();
        } else {
            plugin
                .getLogger()
                .warning(
                    "[ZeusGateway] ProtocolLib not available — skipping raw packet listeners."
                );
            plugin
                .getLogger()
                .warning(
                    "[ZeusGateway] Some anti-cheat data sources will be Bukkit-event-only (less precise)."
                );
        }

        // ─────────────────────────────────────────────────────────────────
        // 2. Register cross-platform Bukkit event listeners (Paper / Spigot / Folia)
        // ─────────────────────────────────────────────────────────────────
        this.plugin.getServer()
            .getPluginManager()
            .registerEvents(new EventListener(this.plugin, rawCapabilities), this.plugin);
        plugin
            .getLogger()
            .info("[ZeusGateway] Registered Bukkit event listeners. Raw fallback coverage="
                    + rawCapabilities);

        // ─────────────────────────────────────────────────────────────────
        // 3. Register Paper-exclusive event listeners (only on Paper / Paper forks)
        // ─────────────────────────────────────────────────────────────────
        if (PlatformDetector.isPaper() || PlatformDetector.isFolia()) {
            try {
                this.plugin.getServer()
                    .getPluginManager()
                    .registerEvents(
                            new PaperEventListener(
                                    this.plugin,
                                    rawCapabilities.contains(RawCaptureCapability.ATTACK_ENTITY)),
                            this.plugin);
                plugin
                    .getLogger()
                    .info(
                        "[ZeusGateway] Registered Paper-exclusive event listeners."
                    );
            } catch (Exception | NoClassDefFoundError e) {
                plugin
                    .getLogger()
                    .warning(
                        "[ZeusGateway] Failed to register Paper event listeners: " +
                            e.getMessage()
                    );
                plugin
                    .getLogger()
                    .warning(
                        "[ZeusGateway] Falling back to Bukkit-only listeners for attack/armor events."
                    );
            }
        } else {
            plugin
                .getLogger()
                .info(
                    "[ZeusGateway] Running on Spigot — Paper-exclusive listeners skipped."
                );
        }

        // ─────────────────────────────────────────────────────────────────
        // 4. Handle players already online (reload or late start)
        // ─────────────────────────────────────────────────────────────────
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerStateSnapshotService.sendFullSnapshot(player);
        }
    }

    @SuppressWarnings("unchecked")
    private EnumSet<RawCaptureCapability> registerProtocolLibListenersIfAvailable() {
        try {
            Class<?> registrar = Class.forName(
                "org.vennv.zeusGateway.listener.packets.ProtocolLibListenerRegistrar"
            );
            Object result = registrar.getMethod("register", ZeusGateway.class)
                .invoke(null, plugin);
            return (EnumSet<RawCaptureCapability>) result;
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().log(
                Level.WARNING,
                "[ZeusGateway] ProtocolLib capability adapter could not be loaded; using event fallbacks.",
                e
            );
            return EnumSet.noneOf(RawCaptureCapability.class);
        }
    }

    /**
     * Sets up repeating tasks (e.g. TPS monitor).
     * Uses the platform-appropriate scheduler.
     */
    private void initTasks() {
        PlatformType platform = plugin.getPlatformType();

        UpdateTPS tpsTask = new UpdateTPS();
        org.vennv.zeusGateway.task.ResyncTask resyncTask = new org.vennv.zeusGateway.task.ResyncTask();

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
