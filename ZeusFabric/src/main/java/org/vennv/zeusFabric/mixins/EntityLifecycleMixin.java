package org.vennv.zeusFabric.mixins;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vennv.packets.PacketEntityDestroy;
import org.vennv.packets.PacketEntityMove;
import org.vennv.packets.PacketEntitySpawn;
import org.vennv.zeusFabric.provider.PacketQueue;

import java.util.List;
import java.util.UUID;

/**
 * Mixin to track entity lifecycle (spawn, move, despawn) for EntityCollisionTracker.
 * This captures entities that players can stand on (boats, minecarts, etc.)
 */
@Mixin(World.class)
public abstract class EntityLifecycleMixin {

    @Inject(method = "spawnEntity", at = @At("HEAD"))
    private void zeus$onEntitySpawn(Entity entity, CallbackInfo ci) {
        World self = (World) (Object) this;
        if (self.isClient() || !(self instanceof ServerWorld serverWorld)) return;

        // Only track collidable entities (vehicles, large mobs, shulkers)
        if (!isCollidable(entity)) return;

        String entityClass = entity.getType().getKey().toString();

        // Notify all players in the same world about this entity
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            String uid = player.getUuidAsString();
            String name = player.getName().getString();
            long timestamp = System.currentTimeMillis();

            PacketEntitySpawn packet = new PacketEntitySpawn(
                timestamp, uid, name,
                entity.getId(), entity.getUuidAsString(), entityClass,
                entity.getX(), entity.getY(), entity.getZ(),
                entity.getPitch(), entity.getYaw()
            );
            PacketQueue.push(packet);
        }
    }

    @Inject(method = "removeEntity(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void zeus$onEntityRemove(Entity entity, net.minecraft.entity.Entity.RemovalReason reason, CallbackInfo ci) {
        World self = (World) (Object) this;
        if (self.isClient() || !(self instanceof ServerWorld serverWorld)) return;

        if (!isCollidable(entity)) return;

        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            String uid = player.getUuidAsString();
            String name = player.getName().getString();
            long timestamp = System.currentTimeMillis();

            PacketEntityDestroy packet = new PacketEntityDestroy(
                timestamp, uid, name, List.of(entity.getId())
            );
            PacketQueue.push(packet);
        }
    }

    /**
     * Tick-based position updates for collidable entities.
     * We inject after entity tick to update positions.
     */
    @Inject(method = "tickEntity", at = @At("RETURN"))
    private void zeus$onEntityTick(Entity entity, CallbackInfo ci) {
        World self = (World) (Object) this;
        if (self.isClient() || !(self instanceof ServerWorld serverWorld)) return;

        if (!isCollidable(entity)) return;

        // Only send updates if entity actually moved (avoid spam)
        // We send every 4 ticks (200ms) to balance accuracy vs bandwidth
        if (entity.age % 4 != 0) return;

        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            // Only send if player is within 16 blocks
            double distSq = player.squaredDistanceTo(entity);
            if (distSq > 16.0 * 16.0) continue;

            String uid = player.getUuidAsString();
            String name = player.getName().getString();
            long timestamp = System.currentTimeMillis();

            PacketEntityMove packet = new PacketEntityMove(
                timestamp, uid, name,
                entity.getId(),
                entity.getX(), entity.getY(), entity.getZ(),
                entity.getYaw(), entity.getPitch(),
                entity.isOnGround()
            );
            PacketQueue.push(packet);
        }
    }

    private static boolean isCollidable(Entity entity) {
        EntityType<?> type = entity.getType();
        // Boats, minecarts, horses, iron golems, shulkers, etc.
        return type == EntityType.BOAT
            || type == EntityType.CHEST_BOAT
            || type == EntityType.MINECART
            || type == EntityType.HOPPER_MINECART
            || type == EntityType.CHEST_MINECART
            || type == EntityType.FURNACE_MINECART
            || type == EntityType.TNT_MINECART
            || type == EntityType.COMMAND_BLOCK_MINECART
            || type == EntityType.HORSE
            || type == EntityType.DONKEY
            || type == EntityType.MULE
            || type == EntityType.SKELETON_HORSE
            || type == EntityType.ZOMBIE_HORSE
            || type == EntityType.CAMEL
            || type == EntityType.PIG
            || type == EntityType.STRIDER
            || type == EntityType.IRON_GOLEM
            || type == EntityType.RAVAGER
            || type == EntityType.SHULKER
            || type == EntityType.SNIFFER;
    }
}
