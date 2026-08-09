package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnExperienceOrb;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPainting;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnWeatherEntity;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.vennv.packets.PacketEntitySpawn;
import org.vennv.packets.PacketPlayerExternalForce;
import org.vennv.utils.ExternalForceFlags;
import org.vennv.utils.ExternalForceType;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public final class EntitySpawnListener extends PacketListenerAbstract {
    private static final Map<UUID, Map<Integer, EntityState>> ENTITIES = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<Integer, EntityMetadata>> METADATA = new ConcurrentHashMap<>();

    public EntitySpawnListener(ZeusGateway plugin) {
        super(PacketListenerPriority.MONITOR);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) return;

        User user = event.getUser();
        UUID receiver = user.getUUID();
        String name = user.getName();
        if (receiver == null || name == null) return;

        PacketTypeCommon type = event.getPacketType();
        int entityId;
        UUID entityUuid;
        String entityType;
        Vector3d position;
        float pitch;
        float yaw;

        if (type == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            entityId = packet.getEntityId();
            entityUuid = packet.getUUID().orElseGet(() -> fallbackUuid(receiver, entityId));
            entityType = typeName(packet.getEntityType());
            position = packet.getPosition();
            pitch = packet.getPitch();
            yaw = packet.getYaw();
        } else if (type == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            WrapperPlayServerSpawnLivingEntity packet = new WrapperPlayServerSpawnLivingEntity(event);
            entityId = packet.getEntityId();
            entityUuid = packet.getEntityUUID();
            entityType = typeName(packet.getEntityType());
            position = packet.getPosition();
            pitch = packet.getPitch();
            yaw = packet.getYaw();
        } else if (type == PacketType.Play.Server.SPAWN_PLAYER) {
            WrapperPlayServerSpawnPlayer packet = new WrapperPlayServerSpawnPlayer(event);
            entityId = packet.getEntityId();
            entityUuid = packet.getUUID();
            entityType = "minecraft:player";
            position = packet.getPosition();
            pitch = packet.getPitch();
            yaw = packet.getYaw();
        } else if (type == PacketType.Play.Server.SPAWN_EXPERIENCE_ORB) {
            WrapperPlayServerSpawnExperienceOrb packet = new WrapperPlayServerSpawnExperienceOrb(event);
            entityId = packet.getEntityId();
            entityUuid = fallbackUuid(receiver, entityId);
            entityType = "minecraft:experience_orb";
            position = new Vector3d(packet.getX(), packet.getY(), packet.getZ());
            pitch = 0.0f;
            yaw = 0.0f;
        } else if (type == PacketType.Play.Server.SPAWN_PAINTING) {
            WrapperPlayServerSpawnPainting packet = new WrapperPlayServerSpawnPainting(event);
            entityId = packet.getEntityId();
            entityUuid = packet.getUUID();
            if (entityUuid == null) entityUuid = fallbackUuid(receiver, entityId);
            entityType = "minecraft:painting";
            Vector3i blockPosition = packet.getPosition();
            position = new Vector3d(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
            pitch = 0.0f;
            yaw = 0.0f;
        } else if (type == PacketType.Play.Server.SPAWN_WEATHER_ENTITY) {
            WrapperPlayServerSpawnWeatherEntity packet = new WrapperPlayServerSpawnWeatherEntity(event);
            entityId = packet.getEntityId();
            entityUuid = fallbackUuid(receiver, entityId);
            entityType = "minecraft:lightning_bolt";
            position = new Vector3d(packet.getX(), packet.getY(), packet.getZ());
            pitch = 0.0f;
            yaw = 0.0f;
        } else {
            return;
        }

        if (entityUuid == null || position == null) return;
        setState(receiver, entityId, new EntityState(
            position.getX(), position.getY(), position.getZ(), yaw, pitch, false));
        METADATA.computeIfAbsent(receiver, ignored -> new ConcurrentHashMap<>()).put(
                entityId, new EntityMetadata(entityUuid, entityType));
        PacketQueue.push(new PacketEntitySpawn(
            System.currentTimeMillis(), receiver.toString(), name,
            entityId, entityUuid.toString(), entityType,
            position.getX(), position.getY(), position.getZ(), pitch, yaw));

        // Grim parity (CompensatedFireworks): a spawned firework rocket means
        // the player (or another entity) is boosting with elytra. Grim tracks
        // active firework entities and expands prediction uncertainty while
        // any is alive. Push an ElytraFirework external force so the engine
        // opens the boost lenience (firework_boost_ticks) — without it, the
        // rocket thrust shows up as an unexplained dy/dz spike → false flag.
        if (type == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(event);
            if (spawn.getEntityType() == EntityTypes.FIREWORK_ROCKET) {
                Optional<Vector3d> vel = spawn.getVelocity();
                double vx = vel.map(Vector3d::getX).orElse(0.0);
                double vy = vel.map(Vector3d::getY).orElse(0.0);
                double vz = vel.map(Vector3d::getZ).orElse(0.0);
                double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
                PacketQueue.push(new PacketPlayerExternalForce(
                    System.currentTimeMillis(), receiver.toString(), name,
                    ExternalForceType.ELYTRA_FIREWORK,
                    position.getX(), position.getY(), position.getZ(),
                    len > 1.0e-9 ? vx / len : 0.0,
                    len > 1.0e-9 ? vy / len : 0.0,
                    len > 1.0e-9 ? vz / len : 0.0,
                    vx, vy, vz,
                    Math.max(1.0, len),
                    (short) 40,
                    ExternalForceFlags.ENVIRONMENT_BACKED));
            }
        }
    }

    public static void removePlayer(UUID playerId) {
        if (playerId != null) {
            ENTITIES.remove(playerId);
            METADATA.remove(playerId);
        }
    }

    public static void clear() {
        ENTITIES.clear();
        METADATA.clear();
    }

    public static void removeEntity(UUID playerId, int entityId) {
        Map<Integer, EntityState> states = ENTITIES.get(playerId);
        if (states != null) states.remove(entityId);
        Map<Integer, EntityMetadata> metadata = METADATA.get(playerId);
        if (metadata != null) metadata.remove(entityId);
    }

    static EntityState getState(UUID playerId, int entityId) {
        Map<Integer, EntityState> states = ENTITIES.get(playerId);
        return states == null ? null : states.get(entityId);
    }

    static EntityMetadata getMetadata(UUID playerId, int entityId) {
        Map<Integer, EntityMetadata> metadata = METADATA.get(playerId);
        return metadata == null ? null : metadata.get(entityId);
    }

    static void setState(UUID playerId, int entityId, EntityState state) {
        ENTITIES.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>()).put(entityId, state);
    }

    private static UUID fallbackUuid(UUID receiver, int entityId) {
        return UUID.nameUUIDFromBytes(
            (receiver.toString() + ':' + entityId).getBytes(StandardCharsets.UTF_8));
    }

    private static String typeName(EntityType type) {
        return type == null || type.getName() == null ? "minecraft:unknown" : type.getName().toString();
    }

    static final class EntityMetadata {
        final UUID uuid;
        final String type;

        EntityMetadata(UUID uuid, String type) {
            this.uuid = uuid;
            this.type = type;
        }
    }

    static final class EntityState {
        final double x;
        final double y;
        final double z;
        final float yaw;
        final float pitch;
        final boolean onGround;

        EntityState(double x, double y, double z, float yaw, float pitch, boolean onGround) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.onGround = onGround;
        }
    }
}
