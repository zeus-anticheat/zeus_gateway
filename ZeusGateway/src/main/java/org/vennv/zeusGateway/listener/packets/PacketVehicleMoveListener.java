package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerVehicleMove;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketVehicleMoveListener extends PacketListenerAbstract {
    private final OrderedPlayerPacketDispatcher dispatcher;

    public PacketVehicleMoveListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.VEHICLE_MOVE) {
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
        Vector3d position;
        float yaw;
        float pitch;
        try {
            WrapperPlayClientVehicleMove move = new WrapperPlayClientVehicleMove(event);
            position = move.getPosition();
            yaw = move.getYaw();
            pitch = move.getPitch();
        } catch (RuntimeException ignored) {
            return;
        }
        if (position == null
                || !Double.isFinite(position.getX())
                || !Double.isFinite(position.getY())
                || !Double.isFinite(position.getZ())
                || !Float.isFinite(yaw)
                || !Float.isFinite(pitch)) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) return;
        PacketPlayerVehicleMove packet = new PacketPlayerVehicleMove(
                System.currentTimeMillis(),
                uuid.toString(),
                name,
                position.getX(),
                position.getY(),
                position.getZ(),
                yaw,
                pitch);
        dispatcher.submit(player, () -> PacketQueue.push(packet));
    }
}
