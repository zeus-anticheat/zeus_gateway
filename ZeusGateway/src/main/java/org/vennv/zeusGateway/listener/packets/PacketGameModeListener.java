package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRespawn;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.vennv.packets.PacketPlayerChangeMode;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.bukkit.entity.Player;

/** Captures client-visible gamemode, independent of Bukkit version or ViaVersion. */
final class PacketGameModeListener extends PacketListenerAbstract {
    private final ClientAcknowledgementTracker acknowledgements;
    private final OrderedPlayerPacketDispatcher dispatcher;

    PacketGameModeListener(
            ClientAcknowledgementTracker acknowledgements,
            OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.HIGH);
        this.acknowledgements = acknowledgements;
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) return;
        User user = event.getUser();
        if (user == null || user.getUUID() == null || user.getName() == null) return;
        GameMode mode = null;
        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            mode = new WrapperPlayServerJoinGame(event).getGameMode();
        } else if (event.getPacketType() == PacketType.Play.Server.RESPAWN) {
            mode = new WrapperPlayServerRespawn(event).getGameMode();
        } else if (event.getPacketType() == PacketType.Play.Server.CHANGE_GAME_STATE) {
            WrapperPlayServerChangeGameState packet = new WrapperPlayServerChangeGameState(event);
            if (packet.getReason() != WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE) return;
            mode = GameMode.getById((int) packet.getValue());
            if (mode == null) mode = GameMode.SURVIVAL;
        } else {
            return;
        }
        final int gamemode = modeId(mode);
        final UUID uuid = user.getUUID();
        final String uid = uuid.toString();
        final String name = user.getName();
        final long timestamp = System.currentTimeMillis();
        Player player = event.getPlayer();
        if (player == null) player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        final Player target = player;
        Runnable emit = () -> dispatcher.submit(target, () -> PacketQueue.push(
                new PacketPlayerChangeMode(timestamp, uid, name, gamemode)));
        // JOIN_GAME already contains initial mode. No client transaction needed.
        if (event.getPacketType() == PacketType.Play.Server.JOIN_GAME) {
            emit.run();
            return;
        }
        boolean modern = event.getClientVersion() != null
                && event.getClientVersion().isNewerThanOrEquals(
                        com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_17);
        int id = acknowledgements.stage(uuid, modern, emit);
        if (modern) {
            event.getTasksAfterSend().add(() -> user.writePacket(
                    new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing(id)));
        } else {
            event.getTasksAfterSend().add(() -> user.writePacket(
                    new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation(
                            0, (short) id, false)));
        }
    }

    static int modeId(GameMode mode) {
        return mode == null ? 0 : mode.getId();
    }
}
