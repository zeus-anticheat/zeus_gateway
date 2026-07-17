package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerInput;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public final class PacketPlayerInputListener extends PacketListenerAbstract {

    private final ZeusGateway plugin;
    private final OrderedPlayerPacketDispatcher dispatcher;

    public PacketPlayerInputListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOW);
        this.plugin = plugin;
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_INPUT) {
            return;
        }
        User user = event.getUser();
        UUID uuid = user.getUUID();
        String name = user.getName();
        if (uuid == null || name == null) {
            return;
        }
        WrapperPlayClientPlayerInput input = new WrapperPlayClientPlayerInput(event);
        byte flags = encodeInput(input);
        Player player = event.getPlayer();
        if (player == null) return;
        PacketPlayerInput packet = new PacketPlayerInput(
            System.currentTimeMillis(),
            uuid.toString(),
            name,
            flags);
        dispatcher.submit(player, () -> {
            plugin.markPacketInput(uuid);
            PacketQueue.push(packet);
        });
    }

    static byte encodeInput(WrapperPlayClientPlayerInput input) {
        int flags = PacketPlayerInput.TRUSTED_CAPTURE;
        if (input.isForward()) flags |= 0x01;
        if (input.isBackward()) flags |= 0x02;
        if (input.isLeft()) flags |= 0x04;
        if (input.isRight()) flags |= 0x08;
        if (input.isJump()) flags |= 0x10;
        if (input.isShift()) flags |= 0x20;
        if (input.isSprint()) flags |= 0x40;
        return (byte) flags;
    }
}
