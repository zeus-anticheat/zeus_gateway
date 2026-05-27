package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerKeepAlive;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketKeepAliveListener extends PacketAdapter {

    public PacketKeepAliveListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Client.KEEP_ALIVE);
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();
        int ping = player.getPing();

        PacketPlayerKeepAlive packet = new PacketPlayerKeepAlive(
                timestamp,
                uid,
                name,
                ping
        );
        PacketQueue.push(packet);
    }
}
