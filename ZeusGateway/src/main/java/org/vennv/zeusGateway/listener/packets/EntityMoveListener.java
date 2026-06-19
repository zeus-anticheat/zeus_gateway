package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketEntityMove;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

/**
 * Listens to server-to-client EntityMovement packets to update entity positions.
 * Handles: ENTITY_TELEPORT, REL_ENTITY_MOVE, REL_ENTITY_MOVE_LOOK
 */
public final class EntityMoveListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public EntityMoveListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.MONITOR,
                PacketType.Play.Server.ENTITY_TELEPORT,
                PacketType.Play.Server.REL_ENTITY_MOVE,
                PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player == null) return;

        int entityId;
        try {
            entityId = event.getPacket().getIntegers().read(0);
        } catch (Exception e) {
            return;
        }

        // We track entities nearby the player
        if (plugin.getSchedulerAdapter() == null) return;
        
        plugin.getSchedulerAdapter().runEntityTask(plugin, player, () -> {
            Entity entity = player.getWorld().getEntities().stream()
                    .filter(e -> e.getEntityId() == entityId)
                    .findFirst()
                    .orElse(null);
            if (entity == null) return;

            String uid = player.getUniqueId().toString();
            String name = player.getName();
            long timestamp = System.currentTimeMillis();

            PacketEntityMove packet = new PacketEntityMove(
                timestamp, uid, name,
                entityId,
                entity.getLocation().getX(),
                entity.getLocation().getY(),
                entity.getLocation().getZ(),
                entity.getLocation().getYaw(),
                entity.getLocation().getPitch(),
                entity.isOnGround()
            );
            PacketQueue.push(packet);
        });
    }
}
