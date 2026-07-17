package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkDataBulk;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.vennv.packets.PacketChunkData;
import org.vennv.packets.PacketChunkData.BlockData;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketChunkListener extends PacketListenerAbstract {
    private final ZeusGateway plugin;
    private final OrderedWorldPacketDispatcher dispatcher;

    public PacketChunkListener(ZeusGateway plugin, OrderedWorldPacketDispatcher dispatcher) {
        super(PacketListenerPriority.MONITOR);
        this.plugin = plugin;
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) return;
        PacketTypeCommon type = event.getPacketType();
        if (type != PacketType.Play.Server.CHUNK_DATA
                && type != PacketType.Play.Server.MAP_CHUNK_BULK) return;
        dispatcher.submit(event, this::process, false);
    }

    private void process(PacketSendEvent event) {
        User user = event.getUser();
        UUID uuid = user.getUUID();
        String name = user.getName();
        if (uuid == null || name == null) return;

        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Server.CHUNK_DATA) {
            Column column = new WrapperPlayServerChunkData(event).getColumn();
            if (column != null) {
                emitColumn(user, uuid, name, column);
                if (column.isFullChunk() && !Thread.currentThread().isInterrupted()) {
                    dispatcher.recover(uuid);
                }
            }
        } else if (type == PacketType.Play.Server.MAP_CHUNK_BULK) {
            WrapperPlayServerChunkDataBulk packet = new WrapperPlayServerChunkDataBulk(event);
            int[] chunkX = packet.getX();
            int[] chunkZ = packet.getZ();
            BaseChunk[][] columns = packet.getChunks();
            if (chunkX == null || chunkZ == null || columns == null) return;
            int count = Math.min(Math.min(chunkX.length, chunkZ.length), columns.length);
            for (int i = 0; i < count && !Thread.currentThread().isInterrupted(); i++) {
                emitSections(user, uuid, name, chunkX[i], chunkZ[i], columns[i], 0, true);
            }
            if (count > 0 && !Thread.currentThread().isInterrupted()) dispatcher.recover(uuid);
        }
    }

    private void emitColumn(User user, UUID uuid, String name, Column column) {
        int minSection = Math.floorDiv(user.getMinWorldHeight(), 16);
        emitSections(user, uuid, name, column.getX(), column.getZ(), column.getChunks(),
            minSection, column.isFullChunk());
    }

    private void emitSections(
            User user, UUID uuid, String name, int chunkX, int chunkZ,
            BaseChunk[] sections, int minSection, boolean fullChunk) {
        long timestamp = System.currentTimeMillis();
        ClientVersion version = user.getClientVersion();
        int baseSize = PacketChunkData.encodedBaseSize(uuid.toString(), name);
        int encodedSize = baseSize;
        List<BlockData> blocks = new ArrayList<>();
        boolean reset = fullChunk;

        if (sections != null) {
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                if (Thread.currentThread().isInterrupted()) return;
                BaseChunk section = sections[sectionIndex];
                if (section == null || section.isEmpty()) continue;
                int baseY = (minSection + sectionIndex) << 4;
                for (int y = 0; y < 16; y++) {
                    if (Thread.currentThread().isInterrupted()) return;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            WrappedBlockState state = section.get(version, x, y, z);
                            if (state == null || state.getType().isAir()) continue;
                            String blockType = state.toString();
                            int blockSize = PacketChunkData.encodedBlockSize(blockType);
                            if (baseSize + blockSize > PacketChunkData.MAX_UDP_PAYLOAD) {
                                plugin.getLogger().warning("[ZeusGateway] Dropped oversized chunk block state");
                                continue;
                            }
                            if (encodedSize + blockSize > PacketChunkData.MAX_UDP_PAYLOAD) {
                                emit(timestamp, uuid, name, chunkX, chunkZ, reset, false, blocks);
                                reset = false;
                                blocks = new ArrayList<>();
                                encodedSize = baseSize;
                            }
                            blocks.add(new BlockData((byte) x, baseY + y, (byte) z, blockType));
                            encodedSize += blockSize;
                        }
                    }
                }
            }
        }

        if (!blocks.isEmpty() || reset) {
            emit(timestamp, uuid, name, chunkX, chunkZ, reset, false, blocks);
        }
        if (fullChunk) {
            emit(timestamp, uuid, name, chunkX, chunkZ, false, true, Collections.emptyList());
        }
    }

    private static void emit(
            long timestamp, UUID uuid, String name, int chunkX, int chunkZ,
            boolean reset, boolean complete, List<BlockData> blocks) {
        PacketQueue.push(new PacketChunkData(
                timestamp, uuid.toString(), name, chunkX, chunkZ, reset, complete, blocks));
    }
}
