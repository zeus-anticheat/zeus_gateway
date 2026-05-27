package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerDiggingBlock;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketDiggingBlockListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public PacketDiggingBlockListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Client.BLOCK_DIG);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        EnumWrappers.PlayerDigType digType = readDigType(event);
        if (!isBlockDigAction(digType)) {
            return;
        }

        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();
        boolean cancelled = event.isCancelled();

        Double x = null;
        Double y = null;
        Double z = null;

        try {
            BlockPosition pos = event.getPacket().getBlockPositionModifier().readSafely(0);
            if (pos != null) {
                x = (double) pos.getX();
                y = (double) pos.getY();
                z = (double) pos.getZ();
            }
        } catch (Exception ignored) {
            // No reliable raw block position; skip rather than guessing.
        }

        if (x == null || y == null || z == null) {
            plugin.getLogger().fine(
                    "Skipping BLOCK_DIG: unreadable block target, handle="
                            + event.getPacket().getHandle().getClass().getName()
            );
            return;
        }

        PacketPlayerDiggingBlock packet = new PacketPlayerDiggingBlock(
                timestamp,
                uid,
                name,
                cancelled,
                x,
                y,
                z
        );
        PacketQueue.push(packet);
    }

    private EnumWrappers.PlayerDigType readDigType(PacketEvent event) {
        try {
            return event.getPacket().getPlayerDigTypes().readSafely(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isBlockDigAction(EnumWrappers.PlayerDigType digType) {
        return digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK
                || digType == EnumWrappers.PlayerDigType.ABORT_DESTROY_BLOCK
                || digType == EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK;
    }
}
