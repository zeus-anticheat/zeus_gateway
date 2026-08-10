package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusGateway.provider.PacketQueue;

/**
 * Grim parity (PacketSelfMetadataListener + PacketPlayerRespawn): the client
 * flips its isGliding flag via the server->client SetEntityMetadata packet
 * (entity flags byte, index 0, bit 0x80 on 1.9+). We mirror that by watching
 * ENTITY_METADATA packets addressed to the player's own entity id and emit
 * START_FALL_FLYING / STOP_FALL_FLYING whenever the gliding bit changes.
 *
 * The player's own entity id is taken from the JoinGame packet (Grim stores
 * player.entityID = joinGame.getEntityId(), NOT PacketEvents' user.getEntityId()),
 * so we track it exactly like Grim does.
 *
 * This covers toggles Bukkit's EntityToggleGlideEvent does NOT see: opening
 * elytra from a jump (server-side state on 1.15+, no client packet) and —
 * critically — the automatic glide exit when the player jumps while gliding,
 * which is server-side state only. Without this, Zeus never learns the glide
 * ended and keeps applying elytra cos² gravity on the jump arc.
 */
public final class PacketEntityMetadataListener extends PacketListenerAbstract {

    /** Player entity id per receiver-player, captured from JoinGame (Grim parity). */
    private static final Map<UUID, Integer> SELF_ENTITY_IDS = new ConcurrentHashMap<>();

    /**
     * Grim parity: the player's own entity id comes from the JoinGame packet,
     * NOT from PacketEvents' user.getEntityId() (which may be unset/0 for a
     * long window after join). Shared with PacketVelocityListener so knockback
     * filtering works from the very first tick.
     */
    public static Integer getSelfEntityId(UUID receiver) {
        return SELF_ENTITY_IDS.get(receiver);
    }

    /** Tracked gliding state per receiver-player, to detect changes. */
    private static final Map<UUID, Boolean> GLIDING_STATE = new ConcurrentHashMap<>();

    public PacketEntityMetadataListener() {
        super(PacketListenerPriority.HIGH);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) return;

        User user = event.getUser();
        if (user == null) return;
        UUID receiver = user.getUUID();
        String name = user.getName();
        if (receiver == null || name == null) return;

        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            WrapperPlayServerJoinGame joinGame = new WrapperPlayServerJoinGame(event);
            SELF_ENTITY_IDS.put(receiver, joinGame.getEntityId());
            // Reset glide state on (re)join so a stale gliding flag cannot
            // suppress the first metadata toggle after respawn.
            GLIDING_STATE.remove(receiver);
            return;
        }

        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) return;

        Integer selfEntityId = SELF_ENTITY_IDS.get(receiver);
        if (selfEntityId == null) return;

        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        int entityId = packet.getEntityId();
        // Only the player's own entity drives its movement physics.
        if (entityId != selfEntityId) return;

        List<EntityData<?>> metadata = packet.getEntityMetadata();
        if (metadata == null) return;

        boolean gliding = false;
        // Grim parity (PacketSelfMetadataListener): the gliding bit (index 0,
        // bit 0x80) is only sent ONCE when the player starts gliding, then the
        // server stops resending it. The EntityPose (index 6, FALL_FLYING) is
        // stable for the whole flight — use it as the authoritative source and
        // fall back to the flags byte only when pose is absent.
        for (EntityData<?> data : metadata) {
            if (data == null) continue;
            if (data.getIndex() == 6 && data.getValue() instanceof com.github.retrooper.packetevents.protocol.entity.pose.EntityPose) {
                gliding = data.getValue() == com.github.retrooper.packetevents.protocol.entity.pose.EntityPose.FALL_FLYING;
                break;
            }
        }
        if (!gliding) {
            for (EntityData<?> data : metadata) {
                if (data == null) continue;
                // Index 0 = entity flags byte (bit 0x80 = gliding on 1.9+).
                if (data.getIndex() == 0 && data.getValue() instanceof Byte) {
                    gliding = ((Byte) data.getValue() & 0x80) == 0x80;
                    break;
                }
            }
        }
        Boolean last = GLIDING_STATE.get(receiver);
        if (last != null && last == gliding) {
            return; // no change
        }
        GLIDING_STATE.put(receiver, gliding);

        long timestamp = System.currentTimeMillis();
        PacketQueue.push(new PacketServerBoundPlayerCommand(
                timestamp,
                receiver.toString(),
                name,
                gliding
                        ? ServerBoundPlayerCommandActions.START_FALL_FLYING
                        : ServerBoundPlayerCommandActions.STOP_FALL_FLYING));
    }

    public static void removePlayer(UUID playerId) {
        if (playerId != null) {
            GLIDING_STATE.remove(playerId);
            SELF_ENTITY_IDS.remove(playerId);
        }
    }

    public static void clear() {
        GLIDING_STATE.clear();
        SELF_ENTITY_IDS.clear();
    }
}
