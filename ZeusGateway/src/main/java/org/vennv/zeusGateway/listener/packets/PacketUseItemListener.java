package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerUseItem;
import org.vennv.utils.Hand;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketUseItemListener extends PacketListenerAbstract {
    private final OrderedPlayerPacketDispatcher dispatcher;

    public PacketUseItemListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.USE_ITEM) {
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
        InteractionHand packetHand;
        int sequence;
        try {
            WrapperPlayClientUseItem useItem = new WrapperPlayClientUseItem(event);
            packetHand = useItem.getHand();
            sequence = useItem.getSequence();
        } catch (RuntimeException ignored) {
            return;
        }
        if (packetHand == null || sequence < 0) {
            return;
        }
        Hand hand;
        if (packetHand == InteractionHand.MAIN_HAND) {
            hand = Hand.MAIN_HAND;
        } else if (packetHand == InteractionHand.OFF_HAND) {
            hand = Hand.OFF_HAND;
        } else {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) return;
        PacketPlayerUseItem packet = new PacketPlayerUseItem(
                System.currentTimeMillis(),
                uuid.toString(),
                name,
                hand,
                (byte) sequence);
        dispatcher.submit(player, () -> PacketQueue.push(packet));
    }
}
