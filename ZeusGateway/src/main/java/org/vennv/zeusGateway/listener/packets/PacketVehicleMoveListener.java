package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerVehicleMove;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketVehicleMoveListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public PacketVehicleMoveListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Client.VEHICLE_MOVE);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        StructureModifier<Double> doubles = event.getPacket().getDoubles();
        if (doubles.size() < 3 && event.getPacket().getStructures().size() > 0) {
            try {
                if (event.getPacket().getStructures().read(0) != null
                        && event.getPacket().getStructures().read(0).getDoubles().size() >= 3) {
                    doubles = event.getPacket().getStructures().read(0).getDoubles();
                }
            } catch (Exception ignored) {
                // Fall through to the unreadable guard below.
            }
        }

        StructureModifier<Float> floats = event.getPacket().getFloat();
        if (doubles.size() < 3 || floats.size() < 2) {
            plugin.getLogger().fine(
                    "Skipping VEHICLE_MOVE: unreadable fields, doubles=" + doubles.size()
                            + ", floats=" + floats.size()
                            + ", structures=" + event.getPacket().getStructures().size()
                            + ", handle=" + event.getPacket().getHandle().getClass().getName()
            );
            return;
        }

        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        try {
            x = doubles.read(0);
            y = doubles.read(1);
            z = doubles.read(2);
            yaw = floats.read(0);
            pitch = floats.read(1);
        } catch (Exception e) {
            plugin.getLogger().fine("Skipping VEHICLE_MOVE: failed to read fields: " + e.getMessage());
            return;
        }

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            plugin.getLogger().fine("Skipping VEHICLE_MOVE: non-finite position or rotation");
            return;
        }

        PacketPlayerVehicleMove packet = new PacketPlayerVehicleMove(
                timestamp,
                uid,
                name,
                x,
                y,
                z,
                yaw,
                pitch
        );
        PacketQueue.push(packet);
    }
}
