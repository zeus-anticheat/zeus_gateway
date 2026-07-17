package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.entity.Player;
import org.vennv.EntityState;
import org.vennv.packets.PacketPlayerAttackEntity;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public final class PacketAttackEntityListener extends PacketListenerAbstract {
    private final OrderedPlayerPacketDispatcher dispatcher;

    public PacketAttackEntityListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        long timestamp = System.currentTimeMillis();
        int targetEntityId;
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            targetEntityId = new WrapperPlayClientAttack(event).getEntityId();
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
            targetEntityId = packet.getEntityId();
        } else {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) return;
        dispatcher.submit(player, () -> emitAttack(player, targetEntityId, timestamp));
    }

    private void emitAttack(Player player, int targetEntityId, long timestamp) {
        if (!player.isOnline()) return;
        EntitySpawnListener.EntityState target = EntitySpawnListener.getState(
                player.getUniqueId(), targetEntityId);
        EntitySpawnListener.EntityMetadata metadata = EntitySpawnListener.getMetadata(
                player.getUniqueId(), targetEntityId);
        if (target == null || metadata == null || metadata.uuid == null) return;

        float[] dimensions = conservativeDimensions(metadata.type);
        float height = dimensions[0];
        float width = dimensions[1];
        double eyeY = target.y + ("minecraft:player".equals(metadata.type) ? 1.62 : height * 0.85);
        PacketQueue.push(new PacketPlayerAttackEntity(
                timestamp,
                player.getUniqueId().toString(),
                player.getName(),
                new EntityState(
                        metadata.uuid.toString(),
                        target.x,
                        target.y,
                        target.z,
                        target.x,
                        eyeY,
                        target.z,
                        target.yaw,
                        target.pitch,
                        height,
                        width,
                        target.onGround)));
    }

    private static float[] conservativeDimensions(String type) {
        if ("minecraft:player".equals(type)) return new float[] {1.8f, 0.6f};
        if (type != null && type.contains("dragon")) return new float[] {8.0f, 16.0f};
        if (type != null && (type.contains("ghast") || type.contains("slime")
                || type.contains("magma_cube"))) return new float[] {4.0f, 4.0f};
        if (type != null && type.contains("giant")) return new float[] {12.0f, 4.0f};
        return new float[] {3.0f, 2.0f};
    }
}
