package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMoveMinecart;
import java.util.List;
import java.util.UUID;
import org.vennv.packets.PacketEntityMove;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public final class EntityMoveListener extends PacketListenerAbstract {

    public EntityMoveListener(ZeusGateway plugin) {
        super(PacketListenerPriority.MONITOR);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) return;

        User user = event.getUser();
        UUID receiver = user.getUUID();
        String name = user.getName();
        if (receiver == null || name == null) return;

        PacketTypeCommon type = event.getPacketType();
        int entityId;
        EntitySpawnListener.EntityState previous;
        EntitySpawnListener.EntityState current;

        if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove packet = new WrapperPlayServerEntityRelativeMove(event);
            entityId = packet.getEntityId();
            previous = EntitySpawnListener.getState(receiver, entityId);
            if (previous == null) return;
            current = new EntitySpawnListener.EntityState(
                previous.x + packet.getDeltaX(),
                previous.y + packet.getDeltaY(),
                previous.z + packet.getDeltaZ(),
                previous.yaw, previous.pitch, packet.isOnGround());
        } else if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation packet =
                new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            entityId = packet.getEntityId();
            previous = EntitySpawnListener.getState(receiver, entityId);
            if (previous == null) return;
            current = new EntitySpawnListener.EntityState(
                previous.x + packet.getDeltaX(),
                previous.y + packet.getDeltaY(),
                previous.z + packet.getDeltaZ(),
                packet.getYaw(), packet.getPitch(), packet.isOnGround());
        } else if (type == PacketType.Play.Server.ENTITY_ROTATION) {
            WrapperPlayServerEntityRotation packet = new WrapperPlayServerEntityRotation(event);
            entityId = packet.getEntityId();
            previous = EntitySpawnListener.getState(receiver, entityId);
            if (previous == null) return;
            current = new EntitySpawnListener.EntityState(
                previous.x, previous.y, previous.z,
                packet.getYaw(), packet.getPitch(), packet.isOnGround());
        } else if (type == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(event);
            entityId = packet.getEntityId();
            previous = EntitySpawnListener.getState(receiver, entityId);
            Vector3d position = packet.getPosition();
            RelativeFlag flags = packet.getRelativeFlags();
            if (position == null || previous == null && hasRelativeState(flags)) return;
            current = new EntitySpawnListener.EntityState(
                relative(position.getX(), previous == null ? 0.0 : previous.x, flags, RelativeFlag.X),
                relative(position.getY(), previous == null ? 0.0 : previous.y, flags, RelativeFlag.Y),
                relative(position.getZ(), previous == null ? 0.0 : previous.z, flags, RelativeFlag.Z),
                relative(packet.getYaw(), previous == null ? 0.0f : previous.yaw, flags, RelativeFlag.YAW),
                relative(packet.getPitch(), previous == null ? 0.0f : previous.pitch, flags, RelativeFlag.PITCH),
                packet.isOnGround());
        } else if (type == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
            WrapperPlayServerEntityPositionSync packet = new WrapperPlayServerEntityPositionSync(event);
            entityId = packet.getId();
            EntityPositionData values = packet.getValues();
            if (values == null || values.getPosition() == null) return;
            Vector3d position = values.getPosition();
            current = new EntitySpawnListener.EntityState(
                position.getX(), position.getY(), position.getZ(),
                values.getYaw(), values.getPitch(), packet.isOnGround());
        } else if (type == PacketType.Play.Server.MOVE_MINECART) {
            WrapperPlayServerMoveMinecart packet = new WrapperPlayServerMoveMinecart(event);
            entityId = packet.getEntityId();
            List<WrapperPlayServerMoveMinecart.MinecartStep> steps = packet.getLerpSteps();
            if (steps == null || steps.isEmpty()) return;
            WrapperPlayServerMoveMinecart.MinecartStep step = steps.get(steps.size() - 1);
            Vector3d position = step.getPosition();
            if (position == null) return;
            previous = EntitySpawnListener.getState(receiver, entityId);
            current = new EntitySpawnListener.EntityState(
                position.getX(), position.getY(), position.getZ(),
                step.getYaw(), step.getPitch(), previous != null && previous.onGround);
        } else {
            return;
        }

        EntitySpawnListener.setState(receiver, entityId, current);
        PacketQueue.push(new PacketEntityMove(
            System.currentTimeMillis(), receiver.toString(), name,
            entityId, current.x, current.y, current.z,
            current.yaw, current.pitch, current.onGround));
    }

    public static void removePlayer(UUID playerId) {
        EntitySpawnListener.removePlayer(playerId);
    }

    private static boolean hasRelativeState(RelativeFlag flags) {
        return flags != null && (flags.has(RelativeFlag.X)
            || flags.has(RelativeFlag.Y) || flags.has(RelativeFlag.Z)
            || flags.has(RelativeFlag.YAW) || flags.has(RelativeFlag.PITCH));
    }

    private static double relative(
            double value, double previous, RelativeFlag flags, RelativeFlag flag) {
        return flags != null && flags.has(flag) ? previous + value : value;
    }

    private static float relative(
            float value, float previous, RelativeFlag flags, RelativeFlag flag) {
        return flags != null && flags.has(flag) ? previous + value : value;
    }
}
