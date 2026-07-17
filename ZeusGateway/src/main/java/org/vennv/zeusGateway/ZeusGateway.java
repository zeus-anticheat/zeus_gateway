package org.vennv.zeusGateway;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.vennv.zeusGateway.debug.PacketDebugService;
import org.vennv.zeusGateway.listener.RawCaptureCapability;
import org.vennv.zeusGateway.platform.PlatformType;
import org.vennv.zeusGateway.platform.SchedulerAdapter;

public final class ZeusGateway extends JavaPlugin {
    private Object runtime;
    private PlatformType platformType;
    private SchedulerAdapter schedulerAdapter;
    private Object packetEventsSession;
    private Set<RawCaptureCapability> rawCapabilities = EnumSet.noneOf(RawCaptureCapability.class);
    private final Set<UUID> packetInputPlayers = ConcurrentHashMap.newKeySet();
    private PacketDebugService packetDebugService;

    @Override
    public void onEnable() {
        String implementation = RuntimeSelector.useLegacy(Bukkit.getBukkitVersion())
                ? "org.vennv.zeusGatewayLegacy.LegacyGatewaySession"
                : "org.vennv.zeusGateway.ModernGatewaySession";
        try {
            runtime = Class.forName(implementation)
                    .getMethod("start", JavaPlugin.class)
                    .invoke(null, this);
            getLogger().info("[ZeusGateway] Plugin enabled successfully with "
                    + (implementation.contains("Legacy") ? "legacy" : "modern") + " runtime.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            getLogger().log(Level.SEVERE, "[ZeusGateway] Runtime initialization failed.", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        invoke(runtime, "close", new Class<?>[0]);
        runtime = null;
        packetEventsSession = null;
        rawCapabilities = EnumSet.noneOf(RawCaptureCapability.class);
        packetInputPlayers.clear();
        if (packetDebugService != null) packetDebugService.clear();
        packetDebugService = null;
    }

    public PlatformType getPlatformType() {
        return platformType;
    }

    public SchedulerAdapter getSchedulerAdapter() {
        return schedulerAdapter;
    }

    public Set<RawCaptureCapability> getRawCapabilities() {
        return rawCapabilities.isEmpty()
                ? EnumSet.noneOf(RawCaptureCapability.class)
                : EnumSet.copyOf(rawCapabilities);
    }

    public boolean isPacketEventsInputAvailable() {
        return rawCapabilities.contains(RawCaptureCapability.PLAYER_INPUT);
    }

    public void markPacketInput(UUID uuid) {
        packetInputPlayers.add(uuid);
    }

    public void clearPacketInput(UUID uuid) {
        packetInputPlayers.remove(uuid);
        invoke(packetEventsSession, "clearPlayer", new Class<?>[] {UUID.class}, uuid);
    }

    public boolean hasPacketInput(UUID uuid) {
        return packetInputPlayers.contains(uuid);
    }

    public PacketDebugService getPacketDebugService() {
        return packetDebugService;
    }

    void configureModern(
            PlatformType platformType,
            SchedulerAdapter schedulerAdapter,
            Object packetEventsSession,
            Set<RawCaptureCapability> rawCapabilities,
            PacketDebugService packetDebugService) {
        this.platformType = platformType;
        this.schedulerAdapter = schedulerAdapter;
        this.packetEventsSession = packetEventsSession;
        this.rawCapabilities = rawCapabilities.isEmpty()
                ? EnumSet.noneOf(RawCaptureCapability.class)
                : EnumSet.copyOf(rawCapabilities);
        this.packetDebugService = packetDebugService;
    }

    void clearModern() {
        platformType = null;
        schedulerAdapter = null;
        packetEventsSession = null;
        rawCapabilities = EnumSet.noneOf(RawCaptureCapability.class);
        packetInputPlayers.clear();
        packetDebugService = null;
    }

    private void invoke(Object target, String method, Class<?>[] parameterTypes, Object... arguments) {
        if (target == null) return;
        try {
            target.getClass().getMethod(method, parameterTypes).invoke(target, arguments);
        } catch (ReflectiveOperationException | LinkageError error) {
            getLogger().log(Level.WARNING, "[ZeusGateway] Runtime " + method + " failed.", error);
        }
    }
}
