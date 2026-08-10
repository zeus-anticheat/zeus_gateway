package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.vennv.packets.PacketPlayerAbilities;
import org.vennv.zeusGateway.provider.PacketQueue;

final class PacketAbilitiesListener extends PacketListenerAbstract {
    private final ClientAcknowledgementTracker acknowledgements;
    private final ConcurrentHashMap<UUID, AtomicLong> serverSequences = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AtomicLong> clientSequences = new ConcurrentHashMap<>();

    PacketAbilitiesListener(ClientAcknowledgementTracker acknowledgements) {
        super(PacketListenerPriority.LOWEST);
        this.acknowledgements = acknowledgements;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled() || event.getPacketType() != PacketType.Play.Server.PLAYER_ABILITIES) return;
        User user = event.getUser();
        if (user == null || user.getUUID() == null || user.getName() == null) return;
        WrapperPlayServerPlayerAbilities wrapper = new WrapperPlayServerPlayerAbilities(event);
        boolean modern = event.getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_17);
        int id = stageServer(
                user.getUUID(), modern, System.currentTimeMillis(), user.getUUID().toString(),
                user.getName(), wrapper.isFlightAllowed(),
                wrapper.isFlying() && wrapper.isFlightAllowed(), wrapper.getFlySpeed());
        if (modern) {
            event.getTasksAfterSend().add(() -> user.writePacket(new WrapperPlayServerPing(id)));
        } else {
            event.getTasksAfterSend().add(() -> user.writePacket(
                    new WrapperPlayServerWindowConfirmation(0, (short) id, false)));
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled() || event.getPacketType() != PacketType.Play.Client.PLAYER_ABILITIES) return;
        User user = event.getUser();
        if (user == null || user.getUUID() == null || user.getName() == null) return;
        enqueueClient(
                user.getUUID(), System.currentTimeMillis(), user.getUUID().toString(), user.getName(),
                new WrapperPlayClientPlayerAbilities(event).isFlying());
    }

    int stageServer(
            UUID playerId, boolean modern, long timestamp, String uid, String username,
            boolean canFly, boolean flying, float flySpeed) {
        long sequence = next(serverSequences, playerId);
        PacketPlayerAbilities packet = PacketPlayerAbilities.server(
                timestamp, uid, username, sequence, canFly, flying, flySpeed);
        return acknowledgements.stage(playerId, modern, () -> PacketQueue.push(packet));
    }

    void enqueueClient(
            UUID playerId, long timestamp, String uid, String username, boolean flying) {
        PacketQueue.push(PacketPlayerAbilities.client(
                timestamp, uid, username, next(clientSequences, playerId), flying));
    }

    void clearPlayer(UUID playerId) {
        serverSequences.remove(playerId);
        clientSequences.remove(playerId);
    }

    void clear() {
        serverSequences.clear();
        clientSequences.clear();
    }

    private static long next(ConcurrentHashMap<UUID, AtomicLong> sequences, UUID playerId) {
        return sequences.computeIfAbsent(playerId, ignored -> new AtomicLong()).incrementAndGet();
    }
}
