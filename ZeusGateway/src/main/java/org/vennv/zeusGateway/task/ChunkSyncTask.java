package org.vennv.zeusGateway.task;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketChunkData;
import org.vennv.packets.PacketChunkData.BlockData;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChunkSyncTask implements Runnable {

    private static final int CHUNK_RADIUS = 3;
    private static final int Y_PADDING = 5;
    private static final int BATCH_SIZE = 200;

    private static final Map<UUID, Map<String, Object>> SENT_CHUNKS = new HashMap<>();

    private final ZeusGateway plugin;

    public ChunkSyncTask(ZeusGateway plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline()) continue;
            syncPlayer(player);
        }
    }

    private void syncPlayer(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        int baseY = loc.getBlockY();

        Map<String, Object> sent = SENT_CHUNKS.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                int chunkX = cx + dx;
                int chunkZ = cz + dz;
                int minY = Math.max(world.getMinHeight(), baseY - Y_PADDING);
                int maxY = Math.min(world.getMaxHeight(), baseY + Y_PADDING + 1);
                String key = chunkX + ":" + chunkZ + ":" + minY + ":" + maxY;

                if (sent.containsKey(key)) continue;

                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    world.getChunkAt(chunkX, chunkZ);
                }

                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                if (chunk == null || !chunk.isLoaded()) continue;

                sent.put(key, Boolean.TRUE);

                List<BlockData> blocks = new ArrayList<>(BATCH_SIZE);
                int blockCount = 0;
                boolean resetChunk = false;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = minY; y < maxY; y++) {
                            Block block = chunk.getBlock(x, y, z);
                            String typeName = block.getBlockData().getAsString();
                            if (typeName.equals("minecraft:air")
                                    || typeName.equals("minecraft:cave_air")
                                    || typeName.equals("minecraft:void_air")) {
                                continue;
                            }
                            blocks.add(new BlockData((byte) x, y, (byte) z, typeName));
                            blockCount++;

                            if (blocks.size() >= BATCH_SIZE) {
                                PacketQueue.push(new PacketChunkData(
                                        timestamp, uid, name, chunkX, chunkZ, resetChunk, blocks));
                                resetChunk = false;
                                blocks = new ArrayList<>(BATCH_SIZE);
                            }
                        }
                    }
                }

                if (!blocks.isEmpty() || blockCount == 0) {
                    PacketQueue.push(new PacketChunkData(
                            timestamp, uid, name, chunkX, chunkZ, resetChunk, blocks));
                }
            }
        }
    }

    public static void clearPlayer(UUID uid) {
        SENT_CHUNKS.remove(uid);
    }
}
