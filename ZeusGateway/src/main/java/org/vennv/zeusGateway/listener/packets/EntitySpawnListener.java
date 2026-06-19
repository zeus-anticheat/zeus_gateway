package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketEntitySpawn;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

import java.util.UUID;

/**
 * Listens to server-to-client SpawnEntity packets and forwards them to Zeus Core
 * so the entity collision tracker knows about vehicles (Boats, Minecarts, etc.)
 * and mobs with hitboxes near the player.
 */
public final class EntitySpawnListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public EntitySpawnListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.MONITOR, PacketType.Play.Server.SPAWN_ENTITY);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        int entityId;
        UUID entityUuid;
        try {
            entityId = event.getPacket().getIntegers().read(0);
            entityUuid = event.getPacket().getUUIDs().read(0);
        } catch (Exception e) {
            return;
        }

        plugin.getSchedulerAdapter().runEntityTask(plugin, player, () -> {
            Entity entity = null;
            for (World world : Bukkit.getWorlds()) {
                entity = world.getEntity(entityUuid);
                if (entity != null) break;
            }
            if (entity == null) {
                return;
            }
            String entityClass = entity.getType().getKey().toString();

            String uid = player.getUniqueId().toString();
            String name = player.getName();
            long timestamp = System.currentTimeMillis();

            PacketEntitySpawn packet = new PacketEntitySpawn(
                timestamp, uid, name,
                entityId, entityUuid.toString(), entityClass,
                entity.getLocation().getX(),
                entity.getLocation().getY(),
                entity.getLocation().getZ(),
                entity.getLocation().getPitch(),
                entity.getLocation().getYaw()
            );
            PacketQueue.push(packet);
        });
    }
}
