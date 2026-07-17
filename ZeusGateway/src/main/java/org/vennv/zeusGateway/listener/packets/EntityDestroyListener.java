package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.vennv.packets.PacketEntityDestroy;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public final class EntityDestroyListener extends PacketListenerAbstract {

    public EntityDestroyListener(ZeusGateway plugin) {
        super(PacketListenerPriority.MONITOR);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.DESTROY_ENTITIES
                || event.isCancelled()) {
            return;
        }
        User user = event.getUser();
        if (user == null) {
            return;
        }
        UUID uuid = user.getUUID();
        String name = user.getName();
        if (uuid == null || name == null || name.isEmpty()) {
            return;
        }
        int[] ids;
        try {
            ids = new WrapperPlayServerDestroyEntities(event).getEntityIds();
        } catch (RuntimeException ignored) {
            return;
        }
        if (ids == null || ids.length == 0) {
            return;
        }
        List<Integer> entityIds = new ArrayList<>(ids.length);
        for (int id : ids) {
            if (id < 0) {
                return;
            }
            EntitySpawnListener.removeEntity(uuid, id);
            entityIds.add(id);
        }
        PacketQueue.push(new PacketEntityDestroy(
                System.currentTimeMillis(),
                uuid.toString(),
                name,
                entityIds
        ));
    }
}
