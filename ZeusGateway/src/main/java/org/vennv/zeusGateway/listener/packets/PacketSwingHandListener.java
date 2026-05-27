package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerSwingHand;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.ZeusGateway;

public class PacketSwingHandListener extends PacketAdapter {

    public PacketSwingHandListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Client.ARM_ANIMATION);
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        PacketPlayerSwingHand packet = new PacketPlayerSwingHand(
                timestamp,
                uid,
                name,
                event.isCancelled()
        );
        PacketQueue.push(packet);
    }
}
