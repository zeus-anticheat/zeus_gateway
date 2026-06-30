package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketChunkData;
import org.vennv.packets.PacketChunkData.BlockData;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

import java.util.ArrayList;
import java.util.List;

/**
 * Intercepts outbound MAP_CHUNK packets and sends the full chunk terrain
 * to the Rust backend as a single PacketChunkData packet.
 *
 * This populates `compensated_world.chunks` with initial terrain so that
 * `geometrically_supported()` and `get_block_type()` work immediately
 * after player join — without waiting for BlockChangeEvent deltas.
 *
 * Note: MAP_CHUNK runs on the netty thread. We hop to the main Bukkit thread
 * to safely query Chunk APIs (getBlockAt, etc.) because Bukkit's Chunk API
 * is not thread-safe on most builds.
 */
public class PacketChunkListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public PacketChunkListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.MONITOR, PacketType.Play.Server.MAP_CHUNK);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player == null) return;

        int chunkX = event.getPacket().getIntegers().read(0);
        int chunkZ = event.getPacket().getIntegers().read(1);

        // Hop to main Bukkit thread synchronously to safely access Bukkit APIs.
        // Using runTask (sync) ensures the chunk data is read on the main thread
        // and is FULLY loaded before we sample it.
        sendChunkBlocks(plugin, player, chunkX, chunkZ);
    }

    public static void sendChunkBlocks(ZeusGateway plugin, Player player, int chunkX, int chunkZ) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                World world = (player != null && player.isOnline()) ? player.getWorld() : null;
                if (world == null) return;

                // Schedule an async chunk load in case Bukkit doesn't have it loaded yet.
                // If it IS loaded, getChunkAt synchronously returns; otherwise we force-load
                // and read on the main thread via a follow-up sync task.
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    // Force-load on main thread (synchronous, no generation if not present)
                    world.getChunkAt(chunkX, chunkZ);
                }

                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                if (chunk == null || !chunk.isLoaded()) return;

                int startX = chunkX << 4;
                int startZ = chunkZ << 4;
                int minY = world.getMinHeight();
                int maxY = world.getMaxHeight();
                long timestamp = System.currentTimeMillis();
                String uid = player.getUniqueId().toString();
                String name = player.getName();

                // Collect ALL non-air blocks (including STONE/DIRT/BEDROCK/WATER/LAVA)
                // so Rust backend has complete collision geometry. Split into batches
                // of 800 blocks to stay well under the 65KB UDP payload limit
                // (~23 bytes/block average → ~18.4KB per packet).
                List<BlockData> blocks = new ArrayList<>(800);
                int blockCount = 0;
                boolean resetChunk = true;

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = minY; y < maxY; y++) {
                            Block block = chunk.getBlock(x, y, z);
                            String typeName = block.getBlockData().getAsString();
                            if (typeName.equals("minecraft:air") || typeName.equals("minecraft:cave_air") || typeName.equals("minecraft:void_air")) {
                                continue;
                            }
                            blocks.add(new BlockData((byte) x, y, (byte) z, typeName));
                            blockCount++;

                            if (blocks.size() >= 800) {
                                PacketQueue.push(new PacketChunkData(
                                    timestamp, uid, name,
                                    chunkX, chunkZ, resetChunk, blocks));
                                resetChunk = false;
                                blocks = new ArrayList<>(800);
                            }
                        }
                    }
                }

                plugin.getLogger().info(
                    "[ZeusGateway] MapChunk at (" + chunkX + "," + chunkZ + ") for "
                    + player.getName() + " -> " + blockCount + " non-air blocks");

                // Send remainder (or empty marker if chunk is all-air so Rust knows it was processed)
                if (!blocks.isEmpty() || blockCount == 0) {
                    PacketQueue.push(new PacketChunkData(
                        timestamp, uid, name,
                        chunkX, chunkZ, resetChunk, blocks));
                }
            } catch (Throwable e) {
                plugin.getLogger().warning("[ZeusGateway] Failed to parse MapChunk: " + e.getMessage());
            }
        });
    }
}
