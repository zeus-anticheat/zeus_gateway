package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerBlockFace;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketBlockFaceListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public PacketBlockFaceListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Client.BLOCK_DIG);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        EnumWrappers.PlayerDigType digType = readDigType(event);
        if (!isBlockDigAction(digType)) {
            return;
        }

        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Byte face = readFace(event);

        if (face == null) {
            plugin.getLogger().fine(
                    "Skipping BLOCK_DIG face: unreadable direction, handle="
                            + event.getPacket().getHandle().getClass().getName()
            );
            return;
        }

        PacketPlayerBlockFace packet = new PacketPlayerBlockFace(
                timestamp,
                uid,
                name,
                face
        );
        PacketQueue.push(packet);
    }

    private EnumWrappers.PlayerDigType readDigType(PacketEvent event) {
        try {
            return event.getPacket().getPlayerDigTypes().readSafely(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isBlockDigAction(EnumWrappers.PlayerDigType digType) {
        return digType == EnumWrappers.PlayerDigType.START_DESTROY_BLOCK
                || digType == EnumWrappers.PlayerDigType.ABORT_DESTROY_BLOCK
                || digType == EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK;
    }

    /**
     * Maps a ProtocolLib Direction enum to the protocol's face byte value.
     * <p>
     * Face values:
     * 0 = DOWN   (-Y)
     * 1 = UP     (+Y)
     * 2 = NORTH  (-Z)
     * 3 = SOUTH  (+Z)
     * 4 = WEST   (-X)
     * 5 = EAST   (+X)
     */
    private Byte readFace(PacketEvent event) {
        try {
            StructureModifier<EnumWrappers.Direction> directions = event.getPacket().getDirections();
            if (directions.size() > 0) {
                Byte face = mapDirectionToFace(directions.readSafely(0));
                if (face != null) {
                    return face;
                }
            }
        } catch (Exception ignored) {
            // Try integer layout below.
        }

        try {
            StructureModifier<Integer> integers = event.getPacket().getIntegers();
            if (integers.size() > 1) {
                Byte face = validFace(integers.readSafely(1));
                if (face != null) {
                    return face;
                }
            }
            if (integers.size() > 0) {
                return validFace(integers.readSafely(0));
            }
        } catch (Exception ignored) {
            // Unreadable face; caller skips the packet.
        }
        return null;
    }

    static Byte validFace(Integer face) {
        return face != null && face >= 0 && face <= 5 ? face.byteValue() : null;
    }

    static Byte mapDirectionToFace(EnumWrappers.Direction direction) {
        if (direction == null) {
            return null;
        }

        switch (direction) {
            case DOWN:
                return (byte) 0;
            case UP:
                return (byte) 1;
            case NORTH:
                return (byte) 2;
            case SOUTH:
                return (byte) 3;
            case WEST:
                return (byte) 4;
            case EAST:
                return (byte) 5;
            default:
                return null;
        }
    }
}
