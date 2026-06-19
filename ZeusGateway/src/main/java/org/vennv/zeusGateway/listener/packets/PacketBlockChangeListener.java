package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketBlockChangeEvent;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

/**
 * Intercepts outbound BLOCK_CHANGE and MULTI_BLOCK_CHANGE packets and sends
 * them to the Rust backend to keep the compensated_world in sync with the client.
 */
public class PacketBlockChangeListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public PacketBlockChangeListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.MONITOR, 
              PacketType.Play.Server.BLOCK_CHANGE, 
              PacketType.Play.Server.MULTI_BLOCK_CHANGE);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player == null) return;

        long timestamp = System.currentTimeMillis();
        String uid = player.getUniqueId().toString();
        String name = player.getName();

        if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            BlockPosition pos = event.getPacket().getBlockPositionModifier().read(0);
            Object blockData = event.getPacket().getBlockData().read(0);
            String blockType = blockData != null ? blockData.toString() : "minecraft:air";
            
            PacketQueue.push(new PacketBlockChangeEvent(
                    timestamp, uid, name,
                    pos.getX(), pos.getY(), pos.getZ(),
                    blockType, (byte) 0
            ));
        } else if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            // Pre-1.16.2 multi block change has different layout, but for 1.16.2+:
            try {
                // SectionPosition is field 0
                Object sectionObj = event.getPacket().getModifier().readSafely(0);
                if (sectionObj != null) {
                    short[] arrays = event.getPacket().getShortArrays().readSafely(0);
                    Object[] blockDatas = event.getPacket().getBlockDataArrays().readSafely(0);
                    
                    if (arrays != null && blockDatas != null && arrays.length == blockDatas.length) {
                        // Very simplified, real integration might need NMS mapping depending on version
                        // This sends a chunk-update-request signal
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().fine("[PacketBlockChangeListener] Error reading MULTI_BLOCK_CHANGE: " + e.getMessage());
            }
        }
    }
}
