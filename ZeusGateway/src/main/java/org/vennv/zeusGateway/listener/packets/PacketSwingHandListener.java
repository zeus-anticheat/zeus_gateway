package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerSwingHand;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketSwingHandListener extends PacketListenerAbstract {
    private final OrderedPlayerPacketDispatcher dispatcher;

    public PacketSwingHandListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.ANIMATION) {
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
            if (new WrapperPlayClientAnimation(event).getHand() == null) {
                return;
            }
        } catch (RuntimeException ignored) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) return;
        PacketPlayerSwingHand packet = new PacketPlayerSwingHand(
                System.currentTimeMillis(),
                uuid.toString(),
                name,
                event.isCancelled());
        dispatcher.submit(player, () -> PacketQueue.push(packet));
    }
}
