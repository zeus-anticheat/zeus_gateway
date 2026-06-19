package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.entity.Entity;
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
            }
        }

        if (sideway == null || forward == null || jump == null || unmount == null) {
            plugin.getLogger().fine(
                    "Skipping STEER_VEHICLE: unreadable fields"
            );
            return;
        }

        // Detect vehicle type from the player's current mount (server-side)
        String vehicleType = "";
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            vehicleType = extractVehicleType(vehicle);
        }

        PacketPlayerSteerVehicle packet = new PacketPlayerSteerVehicle(
                timestamp, uid, name, sideway, forward, jump, unmount, vehicleType);
        PacketQueue.push(packet);
    }

    /**
     * Maps a Bukkit entity to a simple vehicle type string.
     * Matches the Rust VehicleType enum in vehicle_state.rs.
     */
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
