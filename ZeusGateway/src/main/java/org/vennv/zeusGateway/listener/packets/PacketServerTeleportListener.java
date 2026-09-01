package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerTeleport;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

/**
 * Captures outbound {@code PLAYER_POSITION_AND_LOOK}.
 *
 * <p>A server teleport is not authoritative when it is sent: the client keeps
 * reporting movement from its old position until it processes the teleport, then
 * echoes the teleport destination back as a position-and-look packet. Without
 * this capture the engine sees that echo as a large player-authored displacement
 * and false-flags it. Matches standard authoritative teleport queueing.
 */
public final class PacketServerTeleportListener extends PacketListenerAbstract {

    private final OrderedPlayerPacketDispatcher dispatcher;

    public PacketServerTeleportListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.MONITOR);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()
                || event.getPacketType() != PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
            return;
        }
        Object eventPlayer = event.getPlayer();
        if (!(eventPlayer instanceof Player)) return;
        User user = event.getUser();
        if (user == null || user.getUUID() == null || user.getName() == null) return;

        double[] destination;
        int teleportId;
        try {
            WrapperPlayServerPlayerPositionAndLook teleport =
                    new WrapperPlayServerPlayerPositionAndLook(event);
            teleportId = teleport.getTeleportId();
            destination = resolve(user.getUUID(), teleport);
        } catch (RuntimeException | LinkageError ignored) {
            // An unreadable teleport must not silently become trusted movement.
            return;
        }
        if (destination == null) return;

        Player player = (Player) eventPlayer;
        long timestamp = System.currentTimeMillis();
        String uid = user.getUUID().toString();
        String name = user.getName();
        double x = destination[0];
        double y = destination[1];
        double z = destination[2];
        dispatcher.submit(player, () -> PacketQueue.push(
                PacketPlayerTeleport.outbound(timestamp, uid, name, x, y, z, teleportId)));
    }

    /**
     * Resolves the absolute destination, converting relative axes against the
     * client's last claimed position. Returns null when the destination cannot
     * be resolved, because an unresolved teleport must never be queued: it
     * could later match a real movement and waive a check.
     */
    private static double[] resolve(UUID uuid, WrapperPlayServerPlayerPositionAndLook teleport) {
        Vector3d position = teleport.getPosition();
        if (position == null) return null;
        double x = position.getX();
        double y = position.getY();
        double z = position.getZ();
        boolean relativeX = teleport.isRelativeFlag(RelativeFlag.X);
        boolean relativeY = teleport.isRelativeFlag(RelativeFlag.Y);
        boolean relativeZ = teleport.isRelativeFlag(RelativeFlag.Z);
        if (relativeX || relativeY || relativeZ) {
            double[] last = PacketPositionListener.lastClaimedPosition(uuid);
            if (last == null) return null;
            if (relativeX) x += last[0];
            if (relativeY) y += last[1];
            if (relativeZ) z += last[2];
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        return new double[] {x, y, z};
    }
}
