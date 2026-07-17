package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerSteerVehicle;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketSteerVehicleListener extends PacketListenerAbstract {
    private final OrderedPlayerPacketDispatcher dispatcher;

    public PacketSteerVehicleListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.STEER_VEHICLE) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        WrapperPlayClientSteerVehicle wrapper = new WrapperPlayClientSteerVehicle(event);
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        float sideway = wrapper.getSideways();
        float forward = wrapper.getForward();
        boolean jump = wrapper.isJump();
        boolean unmount = wrapper.isUnmount();
        dispatcher.submit(player, () -> emitSteerVehicle(
                player, timestamp, sideway, forward, jump, unmount));
    }

    private void emitSteerVehicle(
            Player player,
            long timestamp,
            float sideway,
            float forward,
            boolean jump,
            boolean unmount) {
        if (!player.isOnline()) {
            return;
        }
        String vehicleType = "";
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            vehicleType = extractVehicleType(vehicle);
        }
        PacketQueue.push(new PacketPlayerSteerVehicle(
                timestamp,
                player.getUniqueId().toString(),
                player.getName(),
                sideway,
                forward,
                jump,
                unmount,
                vehicleType));
    }

    private static String extractVehicleType(Entity entity) {
        String className = entity.getClass().getSimpleName().toUpperCase();
        if (className.contains("BOAT")) return "boat";
        if (className.contains("PIG")) return "pig";
        if (className.contains("STRIDER")) return "strider";
        if (className.contains("CAMEL")) return "camel";
        if (className.contains("HAPPY_GHAST") || className.contains("HAPPYGHAST")) return "happy_ghast";
        if (className.contains("NAUTILUS")) return "nautilus";
        if (className.contains("MINECART")) return "minecart";
        if (className.contains("HORSE")) return "horse";
        if (className.contains("DONKEY")) return "donkey";
        if (className.contains("MULE")) return "mule";
        if (className.contains("SKELETONHORSE")) return "skeleton_horse";
        if (className.contains("ZOMBIEHORSE")) return "zombie_horse";
        if (className.contains("LLAMA")) return "llama";
        return "unknown";
    }
}
