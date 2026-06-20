package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketUpdateAttributes;
import org.vennv.zeusGateway.compat.AttributeCompat;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketUpdateAttributesListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public PacketUpdateAttributesListener(ZeusGateway plugin) {
        super(plugin, PacketType.Play.Server.UPDATE_ATTRIBUTES);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player == null) return;

        int entityId = event.getPacket().getIntegers().readSafely(0);
        if (entityId != player.getEntityId()) return;

        // Skip payload parsing completely since it's fragile. Just trigger a sync Bukkit check.
        // It's perfectly safe to over-query attributes since it happens infrequently.
        sendMovementSpeed(plugin, player);
    }

    private static void sendMovementSpeed(ZeusGateway plugin, Player player) {
        // Must be sync to safely query Bukkit attributes
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Double speed = AttributeCompat.getMovementSpeed(player);
            if (speed == null || speed <= 0.0) return;

            PacketUpdateAttributes packet = new PacketUpdateAttributes(
                    System.currentTimeMillis(),
                    player.getUniqueId().toString(),
                    player.getName(),
                    speed.floatValue()
            );
            PacketQueue.push(packet);
        });
    }
}
