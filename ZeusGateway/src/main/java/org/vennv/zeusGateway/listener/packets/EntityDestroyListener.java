package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketEntityDestroy;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens to server-to-client ENTITY_DESTROY to clean up tracked entities.
 */
public final class EntityDestroyListener extends PacketAdapter {

    public EntityDestroyListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.MONITOR, PacketType.Play.Server.ENTITY_DESTROY);
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player == null) return;

        List<Integer> entityIds = new ArrayList<>();
        try {
            // Depending on version, this can be an array of ints or a List of ints
            if (event.getPacket().getIntegerArrays().size() > 0) {
                int[] arr = event.getPacket().getIntegerArrays().read(0);
                for (int id : arr) entityIds.add(id);
            } else if (event.getPacket().getIntLists().size() > 0) {
                entityIds.addAll(event.getPacket().getIntLists().read(0));
            } else {
                // Pre-1.17 sometimes used single integers for destruction in some packets?
                // ENTITY_DESTROY is usually an array.
                return;
            }
        } catch (Exception e) {
            return;
        }

        if (entityIds.isEmpty()) return;

        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        PacketEntityDestroy packet = new PacketEntityDestroy(
            timestamp, uid, name, entityIds
        );
        PacketQueue.push(packet);
    }
}
