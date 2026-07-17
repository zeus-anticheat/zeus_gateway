package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerExternalForce;
import org.vennv.utils.ExternalForceFlags;
import org.vennv.utils.ExternalForceType;
import org.vennv.zeusGateway.provider.PacketQueue;

final class PacketVelocityListener extends PacketListenerAbstract {
    private final OrderedPlayerPacketDispatcher dispatcher;
    private final AtomicInteger transactionCounter = new AtomicInteger();
    private final AtomicLong forceClock = new AtomicLong();
    private final Map<UUID, Map<Integer, Acknowledgement>> acknowledgements = new ConcurrentHashMap<>();

    PacketVelocityListener(OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled() || event.getPacketType() != PacketType.Play.Server.ENTITY_VELOCITY) return;
        User user = event.getUser();
        if (user == null || user.getUUID() == null || user.getName() == null) return;
        WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
        if (wrapper.getEntityId() != user.getEntityId()) return;
        Vector3d velocity = wrapper.getVelocity();
        if (velocity == null) return;

        UUID uuid = user.getUUID();
        PendingVelocity pending = new PendingVelocity(
                nextForceTimestamp(), uuid.toString(), user.getName(),
                velocity.getX(), velocity.getY(), velocity.getZ());
        boolean modern = event.getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_17);
        int beforeId = nextAcknowledgementId(modern);
        int afterId = nextAcknowledgementId(modern);
        Map<Integer, Acknowledgement> playerAcks = acknowledgements.computeIfAbsent(
                uuid, ignored -> new ConcurrentHashMap<>());
        playerAcks.put(beforeId, new Acknowledgement(pending, false));
        playerAcks.put(afterId, new Acknowledgement(pending, true));

        if (modern) {
            user.writePacket(new WrapperPlayServerPing(beforeId));
            event.getTasksAfterSend().add(() -> user.writePacket(new WrapperPlayServerPing(afterId)));
        } else {
            user.writePacket(new WrapperPlayServerWindowConfirmation(0, (short) beforeId, false));
            event.getTasksAfterSend().add(() -> user.writePacket(
                    new WrapperPlayServerWindowConfirmation(0, (short) afterId, false)));
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        int id;
        if (event.getPacketType() == PacketType.Play.Client.PONG) {
            id = new WrapperPlayClientPong(event).getId();
        } else if (event.getPacketType() == PacketType.Play.Client.WINDOW_CONFIRMATION) {
            id = new WrapperPlayClientWindowConfirmation(event).getActionId();
        } else {
            return;
        }

        User user = event.getUser();
        if (user == null || user.getUUID() == null) return;
        Acknowledgement acknowledgement = remove(user.getUUID(), id);
        if (acknowledgement == null) return;
        Player player = event.getPlayer();
        if (player == null) return;
        dispatcher.submit(player, () -> PacketQueue.push(acknowledgement.toPacket()));
    }

    void clearPlayer(UUID uuid) {
        acknowledgements.remove(uuid);
    }

    void clear() {
        acknowledgements.clear();
    }

    private Acknowledgement remove(UUID uuid, int id) {
        Map<Integer, Acknowledgement> playerAcks = acknowledgements.get(uuid);
        if (playerAcks == null) return null;
        Acknowledgement acknowledgement = playerAcks.remove(id);
        if (playerAcks.isEmpty()) acknowledgements.remove(uuid, playerAcks);
        return acknowledgement;
    }

    static int nextAcknowledgementId(AtomicInteger counter, boolean modern) {
        if (modern) {
            int id;
            do {
                id = Integer.MIN_VALUE | (counter.incrementAndGet() & Integer.MAX_VALUE);
            } while (id == (short) id);
            return id;
        }
        int id;
        do {
            id = -(counter.incrementAndGet() & 0x7fff);
        } while (id == 0);
        return id;
    }

    private int nextAcknowledgementId(boolean modern) {
        return nextAcknowledgementId(transactionCounter, modern);
    }

    private long nextForceTimestamp() {
        long now = System.currentTimeMillis();
        return forceClock.updateAndGet(previous -> Math.max(now, previous + 1));
    }

    static final class PendingVelocity {
        final long timestamp;
        final String uid;
        final String username;
        final double x;
        final double y;
        final double z;

        PendingVelocity(long timestamp, String uid, String username, double x, double y, double z) {
            this.timestamp = timestamp;
            this.uid = uid;
            this.username = username;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    static final class Acknowledgement {
        final PendingVelocity velocity;
        final boolean required;

        Acknowledgement(PendingVelocity velocity, boolean required) {
            this.velocity = velocity;
            this.required = required;
        }

        PacketPlayerExternalForce toPacket() {
            int flags = ExternalForceFlags.SERVER_VELOCITY_PACKET
                    | (required
                    ? ExternalForceFlags.VELOCITY_REQUIRED
                    : ExternalForceFlags.VELOCITY_FIRST_BREAD);
            return new PacketPlayerExternalForce(
                    velocity.timestamp,
                    velocity.uid,
                    velocity.username,
                    ExternalForceType.GENERIC,
                    0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0,
                    velocity.x, velocity.y, velocity.z,
                    Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z),
                    (short) 1,
                    flags);
        }
    }
}
