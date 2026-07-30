package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketPlayerCommandListener extends PacketListenerAbstract {
    private final OrderedPlayerPacketDispatcher dispatcher;

    public PacketPlayerCommandListener(OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.ENTITY_ACTION) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction(event);
        ServerBoundPlayerCommandActions action = mapPlayerAction(wrapper.getAction());
        Integer horseJumpCharge = "START_JUMPING_WITH_HORSE".equals(
                wrapper.getAction() == null ? null : wrapper.getAction().name())
                ? validJumpCharge(wrapper.getJumpBoost()) : null;
        if (action == null || event.getUser().getUUID() == null || event.getUser().getName() == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) return;
        PacketServerBoundPlayerCommand packet = new PacketServerBoundPlayerCommand(
                timestamp,
                event.getUser().getUUID().toString(),
                event.getUser().getName(),
                action,
                horseJumpCharge);
        dispatcher.submit(player, () -> PacketQueue.push(packet));
    }

    private static Integer validJumpCharge(int value) {
        return value >= 0 && value <= 100 ? value : null;
    }

    private ServerBoundPlayerCommandActions mapPlayerAction(
            WrapperPlayClientEntityAction.Action playerAction) {
        if (playerAction == null) {
            return null;
        }
        switch (playerAction.name()) {
            case "START_SNEAKING":
                return ServerBoundPlayerCommandActions.START_SNEAKING;
            case "STOP_SNEAKING":
                return ServerBoundPlayerCommandActions.STOP_SNEAKING;
            case "LEAVE_BED":
                return ServerBoundPlayerCommandActions.STOP_SLEEPING;
            case "START_SPRINTING":
                return ServerBoundPlayerCommandActions.START_SPRINTING;
            case "STOP_SPRINTING":
                return ServerBoundPlayerCommandActions.STOP_SPRINTING;
            case "START_JUMPING_WITH_HORSE":
                return ServerBoundPlayerCommandActions.START_RIDING_JUMP;
            case "STOP_JUMPING_WITH_HORSE":
                return ServerBoundPlayerCommandActions.STOP_RIDING_JUMP;
            case "OPEN_HORSE_INVENTORY":
                return ServerBoundPlayerCommandActions.OPEN_INVENTORY;
            case "START_FLYING_WITH_ELYTRA":
                return ServerBoundPlayerCommandActions.START_FALL_FLYING;
            default:
                return null;
        }
    }
}
