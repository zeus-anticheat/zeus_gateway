package org.vennv.zeusGateway.debug;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketEncode;
import org.vennv.packets.PacketPlayerBlockRayTrace;
import org.vennv.packets.PacketPlayerDiggingBlock;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketPlayerPlaceBlock;
import org.vennv.packets.PacketPlayerTeleport;
import org.vennv.packets.PacketPlayerVehicleMove;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.network.PacketTransmitObserver;

public final class PacketDebugService implements PacketTransmitObserver, Listener {
    public static final class Status {
        private final String targetName;
        private final PacketDebugFilter filter;

        Status(String targetName, PacketDebugFilter filter) {
            this.targetName = targetName;
            this.filter = filter;
        }

        public String targetName() {
            return targetName;
        }

        public PacketDebugFilter filter() {
            return filter;
        }
    }

    private static final class Subscription {
        private final Player viewer;
        private final UUID targetId;
        private final String targetName;
        private final PacketDebugFilter filter;

        Subscription(Player viewer, UUID targetId, String targetName, PacketDebugFilter filter) {
            this.viewer = viewer;
            this.targetId = targetId;
            this.targetName = targetName;
            this.filter = filter;
        }

        Player viewer() { return viewer; }
        UUID targetId() { return targetId; }
        String targetName() { return targetName; }
        PacketDebugFilter filter() { return filter; }
    }

    private final ZeusGateway plugin;
    private final ConcurrentMap<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> latestPositions = new ConcurrentHashMap<>();

    public PacketDebugService(ZeusGateway plugin) {
        this.plugin = plugin;
    }

    public void subscribe(Player viewer, Player target, PacketDebugFilter filter) {
        subscriptions.put(viewer.getUniqueId(), new Subscription(
                viewer, target.getUniqueId(), target.getName(), filter));
    }

    public boolean unsubscribe(UUID viewerId) {
        return subscriptions.remove(viewerId) != null;
    }

    public Status status(UUID viewerId) {
        Subscription subscription = subscriptions.get(viewerId);
        return subscription == null ? null
                : new Status(subscription.targetName(), subscription.filter());
    }

    public void clear() {
        subscriptions.clear();
        latestPositions.clear();
    }

    @Override
    public void onPacketTransmitted(PacketEncode packet) {
        PacketEncode transmittedPacket = PacketDebugFormatter.unwrap(packet);
        if (!(transmittedPacket instanceof PacketBaseInfo)) {
            return;
        }
        PacketBaseInfo info = (PacketBaseInfo) transmittedPacket;

        UUID targetId;
        try {
            targetId = UUID.fromString(info.getUid());
        } catch (IllegalArgumentException ignored) {
            return;
        }

        if (transmittedPacket instanceof PacketPlayerPosition) {
            PacketPlayerPosition position = (PacketPlayerPosition) transmittedPacket;
            latestPositions.put(targetId, String.format(Locale.ROOT, "%.2f/%.2f/%.2f",
                    position.getX(), position.getY(), position.getZ()));
        }

        boolean hudPacket = PacketDebugFormatter.isHudPacket(transmittedPacket);
        String message = null;
        for (Subscription subscription : subscriptions.values()) {
            if (!subscription.targetId().equals(targetId)
                    || !subscription.filter().matches(packet)) {
                continue;
            }
            if (message == null) {
                message = hudPacket
                        ? PacketDebugFormatter.formatHud(transmittedPacket)
                        : PacketDebugFormatter.format(packet);
                if (message == null) {
                    return;
                }
                String lastPosition = latestPositions.get(targetId);
                if (!hudPacket && lastPosition != null && needsPositionContext(transmittedPacket)) {
                    message += " lastPos=" + lastPosition;
                }
            }

            String snapshot = message;
            Player viewer = subscription.viewer();
            try {
                plugin.getSchedulerAdapter().runEntityTask(plugin, viewer, () -> {
                    if (viewer.isOnline()) {
                        if (hudPacket) {
                            viewer.spigot().sendMessage(
                                    ChatMessageType.ACTION_BAR,
                                    TextComponent.fromLegacyText(snapshot));
                        } else {
                            viewer.sendMessage(snapshot);
                        }
                    }
                });
            } catch (RuntimeException e) {
                plugin.getLogger().fine("Unable to deliver debug packet display: " + e.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        unsubscribe(playerId);
        latestPositions.remove(playerId);
    }

    private boolean needsPositionContext(PacketEncode packet) {
        return !(packet instanceof PacketPlayerPosition
                || packet instanceof PacketPlayerTeleport
                || packet instanceof PacketPlayerVehicleMove
                || packet instanceof PacketPlayerPlaceBlock
                || packet instanceof PacketPlayerDiggingBlock
                || packet instanceof PacketPlayerBlockRayTrace);
    }
}
