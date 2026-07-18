package org.vennv.zeusFabric.mixins;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.MoveMinecartAlongTrackS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vennv.packets.PacketBlockChangeEvent;
import org.vennv.packets.PacketEntityDestroy;
import org.vennv.packets.PacketEntityMove;
import org.vennv.packets.PacketEntitySpawn;
import org.vennv.packets.PacketPlayerVelocity;
import org.vennv.packets.PacketUpdateAttributes;
import org.vennv.zeusFabric.listener.ZeusEventListeners;
import org.vennv.zeusFabric.provider.PacketQueue;
import org.vennv.zeusFabric.task.PlayerStateSnapshotService;
import org.vennv.zeusFabric.utils.MinecraftCompat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin {
    @Unique private final Set<Integer> zeus$collidableEntities = new HashSet<>();

    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void zeus$onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayNetworkHandler handler) || handler.player == null) {
            return;
        }
        zeus$processOutbound(handler, packet);
    }

    /**
     * Recursively unwraps {@link BundleS2CPacket} so that nested
     * {@link BlockUpdateS2CPacket}, {@link ChunkDeltaUpdateS2CPacket},
     * entity-lifetime packets, teleport, velocity, equipment, and attribute
     * changes are all captured.  Before this fix, a block update inside a
     * bundle was only seen by {@code zeus$entityLifecycle} — which only
     * processes entity spawn/destroy/move — so every bundled block change
     * was silently dropped.
     */
    @Unique
    private void zeus$processOutbound(ServerPlayNetworkHandler handler, Packet<?> packet) {
        if (packet instanceof BundleS2CPacket bundlePacket) {
            bundlePacket.getPackets().forEach(nested -> zeus$processOutbound(handler, nested));
            return;
        }
        zeus$entityLifecycle(handler, packet);
        if (packet instanceof PlayerPositionLookS2CPacket teleportPacket) {
            var teleportPos = teleportPacket.change().position();
            ZeusEventListeners.authoritativeTeleport(
                    handler.player, teleportPos.x, teleportPos.y, teleportPos.z,
                    teleportPacket.teleportId());
        }
        if (packet instanceof EntityVelocityUpdateS2CPacket velocityPacket
                && velocityPacket.getEntityId() == handler.player.getId()) {
            Vec3d velocity = MinecraftCompat.velocity(velocityPacket);
            if (velocity != null) {
                PacketQueue.push(new PacketPlayerVelocity(
                    System.currentTimeMillis(),
                    handler.player.getUuidAsString(),
                    handler.player.getName().getString(),
                    velocity.x,
                    velocity.y,
                    velocity.z
                ));
            }
        }
        if (packet instanceof EntityEquipmentUpdateS2CPacket equipmentPacket
                && equipmentPacket.getEntityId() == handler.player.getId()) {
            PlayerStateSnapshotService.sendArmorSnapshot(handler.player, false);
            PlayerStateSnapshotService.sendEnchantmentsSnapshot(handler.player, false);
        }
        if (packet instanceof EntityAttributesS2CPacket attributesPacket
                && attributesPacket.getEntityId() == handler.player.getId()) {
            attributesPacket.getEntries().stream()
                .filter(entry -> entry.attribute().matches(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED))
                .mapToDouble(EntityAttributesS2CPacket.Entry::base)
                .filter(value -> Double.isFinite(value) && value > 0.0)
                .findFirst()
                .ifPresent(value -> PacketQueue.push(new PacketUpdateAttributes(
                    System.currentTimeMillis(),
                    handler.player.getUuidAsString(),
                    handler.player.getName().getString(),
                    (float) value
                )));
        }
        if (packet instanceof BlockUpdateS2CPacket blockPacket) {
            zeus$blockChange(
                handler,
                blockPacket.getPos(),
                blockPacket.getState().isAir() ? "minecraft:air" : blockPacket.getState().toString()
            );
        }
        if (packet instanceof ChunkDeltaUpdateS2CPacket chunkPacket) {
            chunkPacket.visitUpdates((pos, state) -> zeus$blockChange(
                handler,
                pos,
                state.isAir() ? "minecraft:air" : state.toString()
            ));
        }
    }

    @Unique
    private void zeus$entityLifecycle(ServerPlayNetworkHandler handler, Packet<?> packet) {
        if (!(packet instanceof EntitySpawnS2CPacket)
                && !(packet instanceof EntitiesDestroyS2CPacket)
                && !(packet instanceof EntityS2CPacket)
                && !(packet instanceof EntityPositionSyncS2CPacket)
                && !(packet instanceof EntityPositionS2CPacket)
                && !(packet instanceof MoveMinecartAlongTrackS2CPacket)) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        String uid = handler.player.getUuidAsString();
        String name = handler.player.getName().getString();
        if (packet instanceof EntitySpawnS2CPacket spawnPacket && zeus$isCollidable(spawnPacket.getEntityType())) {
            zeus$collidableEntities.add(spawnPacket.getEntityId());
            PacketQueue.push(new PacketEntitySpawn(
                timestamp,
                uid,
                name,
                spawnPacket.getEntityId(),
                spawnPacket.getUuid().toString(),
                Registries.ENTITY_TYPE.getId(spawnPacket.getEntityType()).toString(),
                spawnPacket.getX(),
                spawnPacket.getY(),
                spawnPacket.getZ(),
                spawnPacket.getPitch(),
                spawnPacket.getYaw()
            ));
            return;
        }
        if (packet instanceof EntitiesDestroyS2CPacket destroyPacket) {
            List<Integer> destroyed = destroyPacket.getEntityIds().intStream()
                .filter(zeus$collidableEntities::remove)
                .boxed()
                .toList();
            if (!destroyed.isEmpty()) {
                PacketQueue.push(new PacketEntityDestroy(timestamp, uid, name, destroyed));
            }
            return;
        }
        Entity entity = zeus$movedEntity(handler, packet);
        if (entity != null && zeus$collidableEntities.contains(entity.getId())) {
            PacketQueue.push(new PacketEntityMove(
                timestamp,
                uid,
                name,
                entity.getId(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getYaw(),
                entity.getPitch(),
                entity.isOnGround()
            ));
        }
    }

    @Unique
    private static Entity zeus$movedEntity(ServerPlayNetworkHandler handler, Packet<?> packet) {
        var world = MinecraftCompat.entityWorld(handler.player);
        if (world == null) return null;
        if (packet instanceof EntityS2CPacket movePacket) return movePacket.getEntity(world);
        if (packet instanceof EntityPositionSyncS2CPacket syncPacket) return world.getEntityById(syncPacket.id());
        if (packet instanceof EntityPositionS2CPacket positionPacket) return world.getEntityById(positionPacket.entityId());
        if (packet instanceof MoveMinecartAlongTrackS2CPacket minecartPacket) return minecartPacket.getEntity(world);
        return null;
    }

    @Unique
    private static boolean zeus$isCollidable(net.minecraft.entity.EntityType<?> entityType) {
        String type = Registries.ENTITY_TYPE.getId(entityType).getPath();
        return type.contains("boat")
            || type.contains("minecart")
            || type.equals("horse")
            || type.equals("donkey")
            || type.equals("mule")
            || type.equals("skeleton_horse")
            || type.equals("zombie_horse")
            || type.equals("camel")
            || type.equals("pig")
            || type.equals("strider")
            || type.equals("iron_golem")
            || type.equals("ravager")
            || type.equals("shulker")
            || type.equals("sniffer");
    }

    @Unique
    private static void zeus$blockChange(ServerPlayNetworkHandler handler, BlockPos pos, String state) {
        if (!PlayerStateSnapshotService.contains(handler.player, pos.getX(), pos.getY(), pos.getZ())) {
            return;
        }
        PacketQueue.push(new PacketBlockChangeEvent(
            System.currentTimeMillis(),
            handler.player.getUuidAsString(),
            handler.player.getName().getString(),
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            state,
            (byte) 0
        ));
    }
}
