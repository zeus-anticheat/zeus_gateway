package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.MovingObjectPositionBlock;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerPlaceBlock;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketPlaceBlockListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public PacketPlaceBlockListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Client.USE_ITEM_ON,
                PacketType.Play.Client.BLOCK_PLACE);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();
        boolean cancelled = event.isCancelled();

        Double x = null;
        Double y = null;
        Double z = null;

        try {
            MovingObjectPositionBlock hit = event.getPacket().getMovingBlockPositions().readSafely(0);
            BlockPosition pos = hit != null ? hit.getBlockPosition() : null;
            if (pos == null) {
                pos = event.getPacket().getBlockPositionModifier().readSafely(0);
            }
            if (pos != null) {
                x = (double) pos.getX();
                y = (double) pos.getY();
                z = (double) pos.getZ();
            }
        } catch (Exception ignored) {
            // No reliable raw hit target; skip rather than guessing from the player's current ray.
        }

        if (x == null || y == null || z == null) {
            plugin.getLogger().fine(
                    "Skipping block placement packet: unreadable block target, type="
                            + event.getPacketType()
                            + ", handle="
                            + event.getPacket().getHandle().getClass().getName()
            );
            return;
        }

        PacketPlayerPlaceBlock packet = new PacketPlayerPlaceBlock(
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
}
