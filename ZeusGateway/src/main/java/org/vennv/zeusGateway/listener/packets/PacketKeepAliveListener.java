package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerKeepAlive;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketKeepAliveListener extends PacketListenerAbstract {

    private final OrderedPlayerPacketDispatcher dispatcher;

    public PacketKeepAliveListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.KEEP_ALIVE) {
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
        try {
            new WrapperPlayClientKeepAlive(event).getId();
        } catch (RuntimeException ignored) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        dispatcher.submit(player, () -> {
            int ping = player.getPing();
            if (ping < 0) {
                return;
            }
            PacketQueue.push(new PacketPlayerKeepAlive(
                    timestamp,
                    uuid.toString(),
                    name,
                    ping
            ));
        });
    }
}
