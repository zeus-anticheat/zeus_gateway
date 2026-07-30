package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientVehicleMove;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.vennv.packets.PacketPlayerVehicleMove;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.task.ChunkSyncTask;

public class PacketVehicleMoveListener extends PacketListenerAbstract {
    private final OrderedPlayerPacketDispatcher dispatcher;
    private final ChunkSyncTask chunkSyncTask;

    public PacketVehicleMoveListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.dispatcher = dispatcher;
        this.chunkSyncTask = new ChunkSyncTask(plugin);
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
        long timestamp = System.currentTimeMillis();
        dispatcher.submit(player, () -> {
            Entity vehicle = player.getVehicle();
            if (vehicle == null) return;
            HorseTelemetry horse = horseTelemetry(vehicle);
            PacketPlayerVehicleMove packet = new PacketPlayerVehicleMove(
                    timestamp,
                    uuid.toString(),
                    name,
                    position.getX(),
                    position.getY(),
                    position.getZ(),
                    yaw,
                    pitch,
                    vehicleType(vehicle),
                    vehicle.getEntityId(),
                    vehicleFlags(vehicle),
                    horse.movementSpeed,
                    horse.jumpStrength,
                    horse.saddleKnown,
                    horse.saddled);
            chunkSyncTask.onMovement(player, position.getX(), position.getY(), position.getZ());
            PacketQueue.push(packet);
        });
    }

    public static String vehicleType(Entity vehicle) {
        return vehicle.getType().getKey().toString();
    }

    public static int vehicleFlags(Entity vehicle) {
        int flags = PacketPlayerVehicleMove.FLAG_MOUNTED;
        if (vehicle.isInWater()) flags |= PacketPlayerVehicleMove.FLAG_IN_WATER;
        if (vehicle.isOnGround()) flags |= PacketPlayerVehicleMove.FLAG_ON_GROUND;
        return flags;
    }

    static HorseTelemetry horseTelemetry(Entity vehicle) {
        if (!(vehicle instanceof AbstractHorse)) return HorseTelemetry.UNKNOWN;
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Method getAttribute = vehicle.getClass().getMethod("getAttribute", attributeClass);
            Double speed = readAttribute(
                    getAttribute, vehicle, attributeClass, 1024.0,
                    "GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED");
            Double jump = readAttribute(
                    getAttribute, vehicle, attributeClass, 32.0,
                    "HORSE_JUMP_STRENGTH", "JUMP_STRENGTH");
            ItemStack saddle = ((AbstractHorse) vehicle).getInventory().getSaddle();
            boolean saddled = saddle != null && saddle.getType() != Material.AIR;
            if (speed == null || jump == null) return HorseTelemetry.UNKNOWN;
            return new HorseTelemetry(speed.floatValue(), jump, true, saddled);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return HorseTelemetry.UNKNOWN;
        }
    }

    private static Double readAttribute(
            Method getAttribute,
            Entity vehicle,
            Class<?> attributeClass,
            double maximum,
            String primary,
            String fallback) throws ReflectiveOperationException {
        for (String name : new String[] {primary, fallback}) {
            try {
                Object attribute = attributeClass.getField(name).get(null);
                Object instance = getAttribute.invoke(vehicle, attribute);
                if (instance == null) continue;
                Object value = instance.getClass().getMethod("getValue").invoke(instance);
                if (value instanceof Number) {
                    double number = ((Number) value).doubleValue();
                    if (Double.isFinite(number) && number > 0.0 && number <= maximum) return number;
                }
            } catch (NoSuchFieldException ignored) {
                // Attribute name differs across Bukkit versions.
            }
        }
        return null;
    }

    static final class HorseTelemetry {
        static final HorseTelemetry UNKNOWN = new HorseTelemetry(null, null, false, false);
        final Float movementSpeed;
        final Double jumpStrength;
        final boolean saddleKnown;
        final boolean saddled;

        HorseTelemetry(Float movementSpeed, Double jumpStrength, boolean saddleKnown, boolean saddled) {
            this.movementSpeed = movementSpeed;
            this.jumpStrength = jumpStrength;
            this.saddleKnown = saddleKnown;
            this.saddled = saddled;
        }
    }
}
