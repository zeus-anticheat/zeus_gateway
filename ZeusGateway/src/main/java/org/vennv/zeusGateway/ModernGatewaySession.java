package org.vennv.zeusGateway;

import java.util.Set;
import org.bukkit.command.PluginCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.vennv.zeusGateway.debug.PacketDebugService;
import org.vennv.zeusGateway.debug.ZeusDebugCommand;
import org.vennv.zeusGateway.init.ZeusLoader;
import org.vennv.zeusGateway.listener.RawCaptureCapability;
import org.vennv.zeusGateway.listener.packets.PacketEventsListenerRegistrar;
import org.vennv.zeusGateway.platform.PlatformDetector;
import org.vennv.zeusGateway.platform.PlatformType;
import org.vennv.zeusGateway.platform.SchedulerAdapter;
import org.vennv.zeusGateway.task.ChunkSyncTask;

public final class ModernGatewaySession implements AutoCloseable {
    private final ZeusGateway plugin;
    private PacketEventsListenerRegistrar.Session packetEvents;
    private ZeusLoader loader;
    private PacketDebugService debug;
    private boolean closed;

    private ModernGatewaySession(ZeusGateway plugin) {
        this.plugin = plugin;
    }

    public static ModernGatewaySession start(JavaPlugin bootstrap) {
        ZeusGateway plugin = (ZeusGateway) bootstrap;
        ModernGatewaySession session = new ModernGatewaySession(plugin);
        try {
            session.open();
            return session;
        } catch (RuntimeException | LinkageError error) {
            session.close();
            throw error;
        }
    }

    private void open() {
        PlatformType platform = PlatformDetector.detect(plugin.getLogger());
        SchedulerAdapter scheduler = SchedulerAdapter.create();
        debug = new PacketDebugService(plugin);
        plugin.configureModern(
                platform, scheduler, null, java.util.Collections.emptySet(), debug);
        plugin.getServer().getPluginManager().registerEvents(debug, plugin);
        registerDebugCommand();
        packetEvents = PacketEventsListenerRegistrar.register(plugin);
        Set<RawCaptureCapability> capabilities = packetEvents.capabilities();
        plugin.configureModern(platform, scheduler, packetEvents, capabilities, debug);
        loader = new ZeusLoader(plugin);
        loader.init();
        plugin.getLogger().info("[ZeusGateway] Modern PacketEvents capture registered: " + capabilities);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (packetEvents != null) {
            packetEvents.close();
            packetEvents = null;
        }
        if (loader != null) {
            loader.close();
            loader = null;
        }
        if (debug != null) debug.clear();
        ChunkSyncTask.clearAll();
        plugin.clearModern();
    }

    private void registerDebugCommand() {
        addPermission("zeusgateway.debug.self", "View transmitted Zeus packets for yourself", PermissionDefault.TRUE);
        addPermission("zeusgateway.debug.others", "View transmitted Zeus packets for another player", PermissionDefault.OP);
        ZeusDebugCommand handler = new ZeusDebugCommand(plugin, debug);
        if (PlatformDetector.isPaper() || PlatformDetector.isFolia()) {
            try {
                Class<?> registrar = Class.forName("org.vennv.zeusGateway.debug.PaperDebugCommandRegistrar");
                registrar.getMethod("register", ZeusGateway.class, ZeusDebugCommand.class)
                        .invoke(null, plugin, handler);
                return;
            } catch (ReflectiveOperationException | LinkageError error) {
                plugin.getLogger().warning("[ZeusGateway] Paper debug command unavailable: " + error.getMessage());
            }
        }
        PluginCommand command = plugin.getCommand("zeusdebug");
        if (command != null) {
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
    }

    private void addPermission(String name, String description, PermissionDefault value) {
        if (plugin.getServer().getPluginManager().getPermission(name) == null) {
            plugin.getServer().getPluginManager().addPermission(new Permission(name, description, value));
        }
    }
}
