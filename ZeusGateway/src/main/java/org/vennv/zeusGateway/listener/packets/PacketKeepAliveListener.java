package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientWindowConfirmation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowConfirmation;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerKeepAlive;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.platform.SchedulerAdapter;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketKeepAliveListener extends PacketListenerAbstract {

    private final ZeusGateway plugin;
    private final OrderedPlayerPacketDispatcher dispatcher;
    private final NettyPingTracker pingTracker = new NettyPingTracker();

    public PacketKeepAliveListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        startPingTask();
    }

    public NettyPingTracker getPingTracker() {
        return pingTracker;
    }

    private void startPingTask() {
        if (plugin == null) {
            return;
        }
        SchedulerAdapter scheduler = plugin.getSchedulerAdapter();
        if (scheduler == null) {
            return;
        }
        // Run every 10 ticks (500ms)
        scheduler.runTaskTimerAsync(plugin, this::sendPings, 10L, 10L);
    }

    void sendPings() {
        if (plugin == null || plugin.getServer() == null) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user == null) {
                continue;
            }
            boolean modern = user.getClientVersion() != null
                    && user.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_17);
            int id = pingTracker.stage(player.getUniqueId(), modern);
            if (modern) {
                user.writePacket(new WrapperPlayServerPing(id));
            } else {
                user.writePacket(new WrapperPlayServerWindowConfirmation(0, (short) id, false));
            }
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        User user = event.getUser();
        if (user == null) {
            return;
        }
        UUID uuid = user.getUUID();
        String name = user.getName();
        if (uuid == null || name == null || name.isEmpty()) {
            return;
        }

        long measuredRtt = -1;
        if (event.getPacketType() == PacketType.Play.Client.PONG) {
            int id;
            try {
                id = new WrapperPlayClientPong(event).getId();
            } catch (RuntimeException ignored) {
                return;
            }
            measuredRtt = pingTracker.complete(uuid, id);
        } else if (event.getPacketType() == PacketType.Play.Client.WINDOW_CONFIRMATION) {
            int id;
            try {
                id = new WrapperPlayClientWindowConfirmation(event).getActionId();
            } catch (RuntimeException ignored) {
                return;
            }
            measuredRtt = pingTracker.complete(uuid, id);
        } else if (event.getPacketType() == PacketType.Play.Client.KEEP_ALIVE) {
            try {
                new WrapperPlayClientKeepAlive(event).getId();
            } catch (RuntimeException ignored) {
                return;
            }
            // Fallback only if no Netty ping measurement exists yet
            if (pingTracker.getLastRtt(uuid) == null) {
                Player player = event.getPlayer();
                if (player != null) {
                    int ping;
                    try {
                        ping = PacketEvents.getAPI().getPlayerManager().getPing(player);
                    } catch (Exception | LinkageError ignored) {
                        ping = 0;
                    }
                    if (ping >= 0) {
                        measuredRtt = ping;
                    }
                }
            }
        } else {
            return;
        }

        if (measuredRtt >= 0) {
            long timestamp = System.currentTimeMillis();
            PacketQueue.push(new PacketPlayerKeepAlive(
                    timestamp,
                    uuid.toString(),
                    name,
                    measuredRtt
            ));
        }
    }

    public void clearPlayer(UUID uuid) {
        pingTracker.clearPlayer(uuid);
    }

    public void clear() {
        pingTracker.clear();
    }
}
