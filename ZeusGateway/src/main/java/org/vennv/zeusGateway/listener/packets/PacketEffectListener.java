package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import java.util.UUID;
import org.vennv.Effect;
import org.vennv.packets.PacketPlayerEffect;
import org.vennv.utils.EffectFlags;
import org.vennv.utils.EffectType;
import org.vennv.zeusGateway.provider.PacketQueue;

final class PacketEffectListener extends PacketListenerAbstract {
    private final ClientAcknowledgementTracker acknowledgements;

    PacketEffectListener(ClientAcknowledgementTracker acknowledgements) {
        super(PacketListenerPriority.LOWEST);
        this.acknowledgements = acknowledgements;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) return;
        User user = event.getUser();
        if (user == null || user.getUUID() == null || user.getName() == null) return;
        UUID uuid = user.getUUID();
        Integer selfEntityId = PacketEntityMetadataListener.getSelfEntityId(uuid);
        if (selfEntityId == null) return;

        PendingEffect pending;
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_EFFECT) {
            WrapperPlayServerEntityEffect wrapper = new WrapperPlayServerEntityEffect(event);
            if (wrapper.getEntityId() != selfEntityId) return;
            Byte effectId = effectId(wrapper.getPotionType());
            if (effectId == null || wrapper.getEffectAmplifier() < 0
                    || wrapper.getEffectAmplifier() > 255
                    || !isValidDuration(wrapper.getEffectDurationTicks())) return;
            pending = new PendingEffect(
                    System.currentTimeMillis(), uuid.toString(), user.getName(),
                    new Effect(effectId, (byte) wrapper.getEffectAmplifier(),
                            wrapper.getEffectDurationTicks(), EffectFlags.ADD));
        } else if (event.getPacketType() == PacketType.Play.Server.REMOVE_ENTITY_EFFECT) {
            WrapperPlayServerRemoveEntityEffect wrapper = new WrapperPlayServerRemoveEntityEffect(event);
            if (wrapper.getEntityId() != selfEntityId) return;
            Byte effectId = effectId(wrapper.getPotionType());
            if (effectId == null) return;
            pending = new PendingEffect(
                    System.currentTimeMillis(), uuid.toString(), user.getName(),
                    new Effect(effectId, (byte) 0, 0, EffectFlags.REMOVE));
        } else {
            return;
        }

        boolean modern = event.getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_17);
        int id = stage(acknowledgements, uuid, modern, pending);
        if (modern) {
            event.getTasksAfterSend().add(() -> user.writePacket(new WrapperPlayServerPing(id)));
        } else {
            event.getTasksAfterSend().add(() -> user.writePacket(
                    new WrapperPlayServerWindowConfirmation(0, (short) id, false)));
        }
    }

    static int stage(
            ClientAcknowledgementTracker tracker,
            UUID playerId,
            boolean modern,
            PendingEffect pending) {
        return tracker.stage(playerId, modern, () -> PacketQueue.push(pending.toPacket()));
    }

    static boolean isValidDuration(int duration) {
        return duration >= -1;
    }

    private static Byte effectId(PotionType type) {
        if (type == null || type.getName() == null) return null;
        String key = type.getName().toString();
        int separator = key.indexOf(':');
        if (separator >= 0) key = key.substring(separator + 1);
        EffectType effectType = EffectType.fromKey(key);
        return effectType == EffectType.UNDEFINED ? null : (byte) effectType.getValue();
    }

    static final class PendingEffect {
        final long timestamp;
        final String uid;
        final String username;
        final Effect effect;

        PendingEffect(long timestamp, String uid, String username, Effect effect) {
            this.timestamp = timestamp;
            this.uid = uid;
            this.username = username;
            this.effect = effect;
        }

        PacketPlayerEffect toPacket() {
            return new PacketPlayerEffect(timestamp, uid, username, effect);
        }
    }
}
