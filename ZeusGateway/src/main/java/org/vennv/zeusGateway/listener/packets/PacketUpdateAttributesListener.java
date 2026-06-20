package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketUpdateAttributes;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.ZeusGateway;
import java.util.List;

public class PacketUpdateAttributesListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public PacketUpdateAttributesListener(ZeusGateway plugin) {
        super(plugin, PacketType.Play.Server.UPDATE_ATTRIBUTES);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        // Target player attributes only
        int entityId = event.getPacket().getIntegers().readSafely(0);
        if (entityId != player.getEntityId()) return;

        List<WrappedAttribute> attributes = event.getPacket().getAttributeCollectionModifier().readSafely(0);
        if (attributes == null) return;

        for (WrappedAttribute attribute : attributes) {
            String name = attribute.getAttributeKey();
            if (name == null) continue;
            
            // "generic.movementSpeed", "minecraft:generic.movement_speed", etc.
            if (name.contains("movement_speed") || name.contains("movementSpeed")) {
                float value = (float) attribute.getFinalValue();
                
                PacketUpdateAttributes packet = new PacketUpdateAttributes(
                        System.currentTimeMillis(),
                        player.getUniqueId().toString(),
                        player.getName(),
                        value
                );
                PacketQueue.push(packet);
                break; // Found movement speed
            }
        }
    }
}
