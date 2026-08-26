package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerBlockFace;
import org.vennv.packets.PacketPlayerBlockRayTrace;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketBlockFaceListener extends PacketListenerAbstract {
    private final OrderedPlayerPacketDispatcher dispatcher;

    public PacketBlockFaceListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        boolean diggingPacket = event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING;
        boolean placementPacket = event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT;
        if (!diggingPacket && !placementPacket) {
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
        Byte face;
        int sequence = 0;
        PacketPlayerBlockRayTrace trace = null;
        try {
            if (diggingPacket) {
                WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
                if (!isBlockDigAction(digging.getAction())) {
                    return;
                }
                sequence = digging.getSequence();
                face = validFace(digging.getBlockFaceId());
                com.github.retrooper.packetevents.util.Vector3i position = digging.getBlockPosition();
                if (position != null) {
                    trace = new PacketPlayerBlockRayTrace(
                            System.currentTimeMillis(),
                            uuid.toString(),
                            name,
                            digging.getAction() == DiggingAction.START_DIGGING,
                            position.getX(),
                            position.getY(),
                            position.getZ(),
                            position.getX() + 0.5f,
                            position.getY() + 0.5f,
                            position.getZ() + 0.5f,
                            PacketPlayerBlockRayTrace.ACTION_DIG,
                            (byte) sequence,
                            digPhase(digging.getAction()));
                }
            } else {
                WrapperPlayClientPlayerBlockPlacement placement =
                        new WrapperPlayClientPlayerBlockPlacement(event);
                sequence = placement.getSequence();
                face = validFace(placement.getFaceId());
                com.github.retrooper.packetevents.util.Vector3i position = placement.getBlockPosition();
                com.github.retrooper.packetevents.util.Vector3f cursor = placement.getCursorPosition();
                if (position != null && cursor != null) {
                    trace = new PacketPlayerBlockRayTrace(
                            System.currentTimeMillis(),
                            uuid.toString(),
                            name,
                            true,
                            position.getX(),
                            position.getY(),
                            position.getZ(),
                            position.getX() + cursor.getX(),
                            position.getY() + cursor.getY(),
                            position.getZ() + cursor.getZ(),
                            PacketPlayerBlockRayTrace.ACTION_INTERACT,
                            (byte) sequence);
                }
            }
        } catch (RuntimeException ignored) {
            return;
        }
        if (face == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) return;
        PacketPlayerBlockFace packet = new PacketPlayerBlockFace(
                System.currentTimeMillis(),
                uuid.toString(),
                name,
                face);
        PacketPlayerBlockRayTrace finalTrace = trace;
        dispatcher.submit(player, () -> {
            if (finalTrace != null) {
                PacketQueue.push(finalTrace);
            }
            PacketQueue.push(packet);
        });
    }

    static boolean isBlockDigAction(DiggingAction action) {
        return action == DiggingAction.START_DIGGING
                || action == DiggingAction.CANCELLED_DIGGING
                || action == DiggingAction.FINISHED_DIGGING;
    }

    static byte digPhase(DiggingAction action) {
        if (action == DiggingAction.START_DIGGING) {
            return PacketPlayerBlockRayTrace.DIG_PHASE_START;
        }
        if (action == DiggingAction.FINISHED_DIGGING) {
            return PacketPlayerBlockRayTrace.DIG_PHASE_FINISH;
        }
        if (action == DiggingAction.CANCELLED_DIGGING) {
            return PacketPlayerBlockRayTrace.DIG_PHASE_CANCEL;
        }
        return PacketPlayerBlockRayTrace.DIG_PHASE_UNKNOWN;
    }

    static Byte validFace(Integer face) {
        return face != null && face >= 0 && face <= 5 ? face.byteValue() : null;
    }

    static Byte mapDirectionToFace(Object direction) {
        if (direction == null) {
            return null;
        }
        switch (direction.toString()) {
            case "DOWN":
                return (byte) 0;
            case "UP":
                return (byte) 1;
            case "NORTH":
                return (byte) 2;
            case "SOUTH":
                return (byte) 3;
            case "WEST":
                return (byte) 4;
            case "EAST":
                return (byte) 5;
            default:
                return null;
        }
    }
}
