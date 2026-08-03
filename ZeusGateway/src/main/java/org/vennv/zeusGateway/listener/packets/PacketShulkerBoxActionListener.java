package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketShulkerBoxAction;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

/** Captures recipient-visible shulker animation events. */
public final class PacketShulkerBoxActionListener extends PacketListenerAbstract {
    static final int SHULKER_ANIMATION_ACTION = 1;

    private final OrderedWorldPacketDispatcher dispatcher;

    public PacketShulkerBoxActionListener(ZeusGateway plugin, OrderedWorldPacketDispatcher dispatcher) {
        super(PacketListenerPriority.MONITOR);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled() || event.getPacketType() != PacketType.Play.Server.BLOCK_ACTION) return;
        Object eventPlayer = event.getPlayer();
        if (!(eventPlayer instanceof Player)) return;
        User user = event.getUser();
        if (user == null || user.getUUID() == null || user.getName() == null) return;

        Player player = (Player) eventPlayer;
        UUID uuid = user.getUUID();
        String name = user.getName();
        long timestamp = System.currentTimeMillis();
        dispatcher.submit(event,
                packetEvent -> process(packetEvent, uuid, name, timestamp),
                true,
                player);
    }

    private static void process(PacketSendEvent event, UUID uuid, String name, long timestamp) {
        PacketTypeCommon type = event.getPacketType();
        if (type != PacketType.Play.Server.BLOCK_ACTION) return;

        WrapperPlayServerBlockAction action = new WrapperPlayServerBlockAction(event);
        if (!shouldCaptureAction(action.getActionId())) return;
        Vector3i position = action.getBlockPosition();
        if (position == null) return;

        // PacketEvents' global-state mapping can be stale for a newer server
        // protocol. Rust accepts this only when compensated world state later
        // proves the packet position is a vanilla shulker box.
        if (!PacketQueue.push(new PacketShulkerBoxAction(
                timestamp,
                uuid.toString(),
                name,
                position.getX(),
                position.getY(),
                position.getZ(),
                (byte) action.getActionId(),
                (byte) action.getActionData()))) {
            throw new IllegalStateException("shulker action queue discontinuity");
        }
    }

    static boolean shouldCaptureAction(int actionId) {
        return actionId == SHULKER_ANIMATION_ACTION;
    }
}
