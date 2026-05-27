package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import java.util.EnumSet;
import java.util.function.Supplier;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.listener.RawCaptureCapability;

/**
 * ProtocolLib-specific loading boundary. This class is loaded only after
 * ProtocolLib is detected, so event-only operation does not resolve its API.
 */
public final class ProtocolLibListenerRegistrar {
    private ProtocolLibListenerRegistrar() {}

    public static Object resolveProtocolManager() {
        return ProtocolLibrary.getProtocolManager();
    }

    public static EnumSet<RawCaptureCapability> register(ZeusGateway plugin) {
        EnumSet<RawCaptureCapability> capabilities =
            EnumSet.noneOf(RawCaptureCapability.class);
        ProtocolManager manager = (ProtocolManager) plugin.getProtocolManager();
        if (manager == null) {
            plugin.getLogger().severe(
                "[ZeusGateway] ProtocolManager is null despite ProtocolLib being available!"
            );
            return capabilities;
        }

        int count = 0;
        count += register(plugin, manager, "PacketPositionListener",
                () -> new PacketPositionListener(plugin), null, capabilities);
        count += register(plugin, manager, "PacketSwingHandListener",
                () -> new PacketSwingHandListener(plugin),
                RawCaptureCapability.SWING_HAND, capabilities);
        count += register(plugin, manager, "PacketAttackEntityListener",
                () -> new PacketAttackEntityListener(plugin),
                RawCaptureCapability.ATTACK_ENTITY, capabilities);
        count += register(plugin, manager, "PacketKeepAliveListener",
                () -> new PacketKeepAliveListener(plugin), null, capabilities);
        count += register(plugin, manager, "PacketPlaceBlockListener",
                () -> new PacketPlaceBlockListener(plugin),
                RawCaptureCapability.PLACE_BLOCK, capabilities);
        count += register(plugin, manager, "PacketDiggingBlockListener",
                () -> new PacketDiggingBlockListener(plugin),
                RawCaptureCapability.DIGGING_BLOCK, capabilities);
        count += register(plugin, manager, "PacketBlockFaceListener",
                () -> new PacketBlockFaceListener(plugin),
                RawCaptureCapability.BLOCK_FACE, capabilities);
        count += register(plugin, manager, "PacketHeldItemListener",
                () -> new PacketHeldItemListener(plugin),
                RawCaptureCapability.HELD_ITEM, capabilities);
        count += register(plugin, manager, "PacketClickWindowListener",
                () -> new PacketClickWindowListener(plugin),
                RawCaptureCapability.CLICK_WINDOW, capabilities);
        count += register(plugin, manager, "PacketUseItemListener",
                () -> new PacketUseItemListener(plugin),
                RawCaptureCapability.USE_ITEM, capabilities);
        count += register(plugin, manager, "PacketSteerVehicleListener",
                () -> new PacketSteerVehicleListener(plugin), null, capabilities);
        count += register(plugin, manager, "PacketVehicleMoveListener",
                () -> new PacketVehicleMoveListener(plugin),
                RawCaptureCapability.VEHICLE_MOVE, capabilities);
        count += register(plugin, manager, "PacketPlayerCommandListener",
                () -> new PacketPlayerCommandListener(plugin),
                RawCaptureCapability.PLAYER_COMMAND, capabilities);

        plugin.getLogger().info(
            "[ZeusGateway] Registered " + count + " ProtocolLib packet listeners."
        );
        return capabilities;
    }

    private static int register(
            ZeusGateway plugin,
            ProtocolManager manager,
            String name,
            Supplier<com.comphenix.protocol.events.PacketListener> listenerFactory,
            RawCaptureCapability capability,
            EnumSet<RawCaptureCapability> capabilities) {
        try {
            com.comphenix.protocol.events.PacketListener listener = listenerFactory.get();
            manager.addPacketListener(listener);
            if (capability != null) {
                capabilities.add(capability);
            }
            return 1;
        } catch (Exception | NoClassDefFoundError | NoSuchFieldError e) {
            plugin.getLogger().warning(
                "[ZeusGateway] Failed to register " + name + ": " + e.getMessage()
            );
            plugin.getLogger().warning(
                "[ZeusGateway] This may be due to packet type changes in your server version."
            );
            return 0;
        }
    }
}
