package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerSteerVehicle;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketSteerVehicleListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public PacketSteerVehicleListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Client.STEER_VEHICLE);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Float sideway = null;
        Float forward = null;
        Boolean jump = null;
        Boolean unmount = null;

        try {
            StructureModifier<Float> floats = event.getPacket().getFloat();
            StructureModifier<Boolean> booleans = event.getPacket().getBooleans();
            if (floats.size() >= 2) {
                sideway = floats.read(0);
                forward = floats.read(1);
            }
            if (booleans.size() >= 2) {
                jump = booleans.read(0);
                unmount = booleans.read(1);
            }
        } catch (Exception ignored) {
            // Fall through to the structured input reader below.
        }

        if ((sideway == null || forward == null || jump == null || unmount == null)
                && event.getPacket().getStructures().size() > 0) {
            try {
                StructureModifier<Boolean> booleans =
                        event.getPacket().getStructures().read(0).getBooleans();
                if (booleans.size() >= 6) {
                    boolean forwardKey = booleans.read(0);
                    boolean backwardKey = booleans.read(1);
                    boolean leftKey = booleans.read(2);
                    boolean rightKey = booleans.read(3);
                    sideway = (leftKey ? 1.0f : 0.0f) + (rightKey ? -1.0f : 0.0f);
                    forward = (forwardKey ? 1.0f : 0.0f) + (backwardKey ? -1.0f : 0.0f);
                    jump = booleans.read(4);
                    unmount = booleans.read(5);
                }
            } catch (Exception ignored) {
                // Fall through to the unreadable guard below.
            }
        }

        if (sideway == null || forward == null || jump == null || unmount == null) {
            plugin.getLogger().fine(
                    "Skipping STEER_VEHICLE: unreadable fields, floats="
                            + event.getPacket().getFloat().size()
                            + ", booleans=" + event.getPacket().getBooleans().size()
                            + ", structures=" + event.getPacket().getStructures().size()
                            + ", handle=" + event.getPacket().getHandle().getClass().getName()
            );
            return;
        }

        PacketPlayerSteerVehicle packet = new PacketPlayerSteerVehicle(
                timestamp,
                uid,
                name,
                sideway,
                forward,
                jump,
                unmount
        );
        PacketQueue.push(packet);
    }
}
