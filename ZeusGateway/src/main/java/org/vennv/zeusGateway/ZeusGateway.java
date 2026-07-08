package org.vennv.zeusGateway;

import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.vennv.zeusGateway.debug.PacketDebugService;
import org.vennv.zeusGateway.debug.ZeusDebugCommand;
import org.vennv.zeusGateway.init.ZeusLoader;
import org.vennv.zeusGateway.task.ChunkSyncTask;
import org.vennv.zeusGateway.platform.PlatformDetector;
import org.vennv.zeusGateway.platform.PlatformType;
import org.vennv.zeusGateway.platform.SchedulerAdapter;

public final class ZeusGateway extends JavaPlugin {

    private Object protocolManager; // Object to avoid hard dependency on ProtocolLib at class-load time
    private PlatformType platformType;
    private SchedulerAdapter schedulerAdapter;
    private boolean protocolLibAvailable = false;
    private PacketDebugService packetDebugService;

    public PlatformType getPlatformType() {
        return platformType;
    }

    public SchedulerAdapter getSchedulerAdapter() {
        return schedulerAdapter;
    }

    public boolean isProtocolLibAvailable() {
        return protocolLibAvailable;
    }

    public PacketDebugService getPacketDebugService() {
        return packetDebugService;
    }

    /**
     * Returns the ProtocolLib ProtocolManager instance, or null if ProtocolLib is not loaded.
     * Callers must cast to {@code com.comphenix.protocol.ProtocolManager} themselves.
     */
    public Object getProtocolManager() {
        return protocolManager;
    }

    @Override
    public void onLoad() {
        // Detect platform first
        platformType = PlatformDetector.detect(getLogger());

        // Try to hook into ProtocolLib (optional dependency)
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
            Class<?> registrar = Class.forName(
                "org.vennv.zeusGateway.listener.packets.ProtocolLibListenerRegistrar"
            );
            protocolManager = registrar.getMethod("resolveProtocolManager").invoke(null);
            protocolLibAvailable = true;

            // Detect ProtocolLib major version for API compatibility
            org.bukkit.plugin.Plugin plPlugin =
                getServer().getPluginManager().getPlugin("ProtocolLib");
            String plVer = (plPlugin != null)
                ? plPlugin.getDescription().getVersion() : "unknown";
            getLogger().info(
                "[ZeusGateway] ProtocolLib v" + plVer + " detected and hooked successfully."
            );
        } catch (ClassNotFoundException e) {
            protocolLibAvailable = false;
            getLogger().warning(
                "[ZeusGateway] ProtocolLib not found. Packet-level listeners will be disabled."
            );
            getLogger().warning(
                "[ZeusGateway] Only Bukkit event-based listeners will be active."
            );
            getLogger().warning(
                "[ZeusGateway] Install ProtocolLib for full anti-cheat coverage."
            );
        } catch (ReflectiveOperationException | LinkageError e) {
            protocolLibAvailable = false;
            getLogger().log(
                Level.WARNING,
                "[ZeusGateway] Failed to initialize ProtocolLib. Packet listeners disabled.",
                e
            );
        }
    }

    @Override
    public void onEnable() {
        // Create the appropriate scheduler adapter for the detected platform
        schedulerAdapter = SchedulerAdapter.create();
        getLogger().info(
            "[ZeusGateway] Using scheduler adapter for platform: " + platformType
        );

        packetDebugService = new PacketDebugService(this);
        getServer().getPluginManager().registerEvents(packetDebugService, this);
        registerDebugPermissions();
        ZeusDebugCommand handler = new ZeusDebugCommand(this, packetDebugService);
        if (PlatformDetector.isPaper() || PlatformDetector.isFolia()) {
            registerPaperDebugCommand(handler);
        } else {
            PluginCommand debugCommand = getCommand("zeusdebug");
            if (debugCommand == null) {
                getLogger().warning("[ZeusGateway] /zeusdebug is missing from plugin.yml.");
            } else {
                debugCommand.setExecutor(handler);
                debugCommand.setTabCompleter(handler);
            }
        }

        // Initialize everything
        ZeusLoader loader = new ZeusLoader(this);
        loader.init();

        getServer().getScheduler().runTaskTimer(this, new ChunkSyncTask(this), 60L, 60L);

        // Start physics capture state poller
        org.vennv.zeusGateway.listener.packets.PhysicsCaptureManager.start(this);

        getLogger().info(
            "[ZeusGateway] Plugin enabled successfully on " + platformType + "!");
    }

    @Override
    public void onDisable() {
        if (packetDebugService != null) {
            packetDebugService.clear();
        }
        getLogger().info("[ZeusGateway] Plugin disabled.");
    }

    private void registerDebugPermissions() {
        if (getServer().getPluginManager().getPermission("zeusgateway.debug.self") == null) {
            getServer().getPluginManager().addPermission(new Permission(
                    "zeusgateway.debug.self",
                    "View transmitted Zeus packets for yourself",
                    PermissionDefault.TRUE));
        }
        if (getServer().getPluginManager().getPermission("zeusgateway.debug.others") == null) {
            getServer().getPluginManager().addPermission(new Permission(
                    "zeusgateway.debug.others",
                    "View transmitted Zeus packets for another player",
                    PermissionDefault.OP));
        }
    }

    private void registerPaperDebugCommand(ZeusDebugCommand handler) {
        try {
            Class<?> registrar = Class.forName(
                    "org.vennv.zeusGateway.debug.PaperDebugCommandRegistrar");
            registrar.getMethod("register", ZeusGateway.class, ZeusDebugCommand.class)
                    .invoke(null, this, handler);
        } catch (ReflectiveOperationException | LinkageError e) {
            getLogger().log(Level.WARNING,
                    "[ZeusGateway] Failed to register /zeusdebug through Paper Command API.", e);
        }
    }
}
