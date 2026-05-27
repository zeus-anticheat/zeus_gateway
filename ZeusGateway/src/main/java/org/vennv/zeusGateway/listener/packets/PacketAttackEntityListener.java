package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.vennv.EntityState;
import org.vennv.packets.PacketPlayerAttackEntity;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.compat.EntityCompat;
import org.vennv.zeusGateway.provider.PacketQueue;

/**
 * Captures client attack intent before damage/cancellation rules are applied.
 */
public final class PacketAttackEntityListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public PacketAttackEntityListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.LOWEST, PacketType.Play.Client.USE_ENTITY);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if (!isAttack(event)) {
            return;
        }

        int targetEntityId;
        try {
            if (event.getPacket().getIntegers().size() == 0) {
                return;
            }
            targetEntityId = event.getPacket().getIntegers().read(0);
        } catch (Exception e) {
            plugin.getLogger().fine("Skipping USE_ENTITY ATTACK: cannot read target id: " + e.getMessage());
            return;
        }

        Player player = event.getPlayer();
        long timestamp = System.currentTimeMillis();
        if (plugin.getSchedulerAdapter() == null) {
            return;
        }
        plugin.getSchedulerAdapter().runEntityTask(
                plugin, player, () -> emitAttack(player, targetEntityId, timestamp));
    }

    private void emitAttack(Player player, int targetEntityId, long timestamp) {
        com.comphenix.protocol.ProtocolManager manager =
                (com.comphenix.protocol.ProtocolManager) plugin.getProtocolManager();
        if (!player.isOnline() || manager == null) {
            return;
        }

        Entity target;
        try {
            target = manager.getEntityFromID(player.getWorld(), targetEntityId);
        } catch (Exception e) {
            plugin.getLogger().fine("Skipping USE_ENTITY ATTACK: cannot resolve target: " + e.getMessage());
            return;
        }
        if (target == null) {
            return;
        }

        Location location = target.getLocation();
        double height = EntityCompat.getHeight(target);
        double width = EntityCompat.getWidth(target);
        double eyeY = location.getY()
                + (target instanceof LivingEntity ? ((LivingEntity) target).getEyeHeight() : height * 0.85);

        PacketQueue.push(new PacketPlayerAttackEntity(
                timestamp,
                player.getUniqueId().toString(),
                player.getName(),
                new EntityState(
                        target.getUniqueId().toString(),
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getX(),
                        eyeY,
                        location.getZ(),
                        location.getYaw(),
                        location.getPitch(),
                        (float) height,
                        (float) width,
                        target.isOnGround())));
    }

    private boolean isAttack(PacketEvent event) {
        try {
            if (event.getPacket().getEnumEntityUseActions().size() > 0) {
                return event.getPacket().getEnumEntityUseActions().read(0).getAction()
                        == EnumWrappers.EntityUseAction.ATTACK;
            }
        } catch (Exception ignored) {
            // Fall through for legacy packet layouts.
        }

        try {
            return event.getPacket().getEntityUseActions().size() > 0
                    && event.getPacket().getEntityUseActions().read(0)
                    == EnumWrappers.EntityUseAction.ATTACK;
        } catch (Exception ignored) {
            return false;
        }
    }
}
