package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketBlockChangeEvent;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.task.ChunkSyncTask;

public class PacketBlockChangeListener extends PacketListenerAbstract {
    private final OrderedWorldPacketDispatcher dispatcher;

    public PacketBlockChangeListener(ZeusGateway plugin, OrderedWorldPacketDispatcher dispatcher) {
        super(PacketListenerPriority.MONITOR);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) return;
        PacketTypeCommon type = event.getPacketType();
        if (type != PacketType.Play.Server.BLOCK_CHANGE
                && type != PacketType.Play.Server.MULTI_BLOCK_CHANGE) return;
        Object eventPlayer = event.getPlayer();
        if (!(eventPlayer instanceof Player)) return;
        Player player = (Player) eventPlayer;
        World world = player.getWorld();
        if (world == null) return;
        UUID uuid = player.getUniqueId();
        UUID worldId = world.getUID();
        String name = player.getName();
        if (uuid == null || worldId == null || name == null) return;
        long timestamp = System.currentTimeMillis();
        dispatcher.submit(event,
                packetEvent -> process(packetEvent, uuid, worldId, name, timestamp),
                true,
                player);
    }

    private void process(
            PacketSendEvent event,
            UUID uuid,
            UUID worldId,
            String name,
            long timestamp) {
        PacketTypeCommon type = event.getPacketType();
        List<PacketBlockChangeEvent> changes = new ArrayList<>();
        if (type == PacketType.Play.Server.BLOCK_CHANGE) {
            WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(event);
            Vector3i position = packet.getBlockPosition();
            add(changes, timestamp, uuid, worldId, name,
                    position.getX(), position.getY(), position.getZ(), blockType(packet.getBlockState()));
        } else if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(event);
            WrapperPlayServerMultiBlockChange.EncodedBlock[] blocks = packet.getBlocks();
            if (blocks == null) return;
            ClientVersion version = event.getClientVersion();
            for (WrapperPlayServerMultiBlockChange.EncodedBlock block : blocks) {
                add(changes, timestamp, uuid, worldId, name,
                        block.getX(), block.getY(), block.getZ(),
                        blockType(block.getBlockState(version)));
            }
        }
        if (!changes.isEmpty() && !PacketQueue.pushAll(changes)) {
            throw new IllegalStateException("block change queue discontinuity");
        }
    }

    private static void add(
            List<PacketBlockChangeEvent> changes,
            long timestamp,
            UUID uuid,
            UUID worldId,
            String name,
            int x,
            int y,
            int z,
            String blockType) {
        if (!ChunkSyncTask.recordBlockChange(
                uuid, worldId, x, y, z, blockType, timestamp)) return;
        changes.add(new PacketBlockChangeEvent(
                timestamp, uuid.toString(), name, x, y, z, blockType, (byte) 0));
    }

    static boolean shouldEmit(UUID uuid, UUID worldId, int x, int y, int z) {
        return uuid != null && worldId != null && ChunkSyncTask.contains(uuid, worldId, x, y, z);
    }

    static String blockType(WrappedBlockState state) {
        return state == null
                ? normalizeBlockType(true, null)
                : normalizeBlockType(
                        state.getType() == null || state.getType().isAir(), state.toString());
    }

    static String normalizeBlockType(boolean air, String blockType) {
        return air || blockType == null ? "minecraft:air" : blockType;
    }
}
