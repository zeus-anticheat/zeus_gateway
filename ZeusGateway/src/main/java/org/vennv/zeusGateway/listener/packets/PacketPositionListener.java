package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketPlayerSurroundingBlocks;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.utils.RelativeBlock;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.compat.EntityCompat;
import org.vennv.zeusGateway.platform.ServerVersion;
import org.vennv.zeusGateway.utils.BlockUtil;

public class PacketPositionListener extends PacketAdapter {

    private final ZeusGateway plugin;

    /**
     * Tracks the last known SWIMMING pose state per player.
     * Used to detect transitions and only send START/STOP_SWIMMING on change,
     * rather than spamming every position packet.
     */
    private static final ConcurrentHashMap<UUID, Boolean> swimmingPoseState = new ConcurrentHashMap<>();

    public PacketPositionListener(ZeusGateway plugin) {
        super(
            plugin,
            ListenerPriority.LOWEST,
            PacketType.Play.Client.POSITION,
            PacketType.Play.Client.POSITION_LOOK
        );
        this.plugin = plugin;
    }

    /**
     * Remove a player's tracked pose state (call on quit).
     */
    public static void removePlayer(UUID uuid) {
        swimmingPoseState.remove(uuid);
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        long timestamp = System.currentTimeMillis();

        StructureModifier<Double> coords = event.getPacket().getDoubles();
        double x = coords.read(0);
        double y = coords.read(1);
        double z = coords.read(2);
        Vector packetPos = new Vector(x, y, z);

        Float yaw = null;
        Float pitch = null;
        if (event.getPacketType() == PacketType.Play.Client.POSITION_LOOK) {
            yaw = event.getPacket().getFloat().read(0);
            pitch = event.getPacket().getFloat().read(1);
        }

        boolean cancelled = event.isCancelled();
        if (plugin.getSchedulerAdapter() == null) {
            return;
        }
        Float packetYaw = yaw;
        Float packetPitch = pitch;
        plugin.getSchedulerAdapter().runEntityTask(plugin, player, () -> emitPosition(
                player, timestamp, packetPos, packetYaw, packetPitch, cancelled));
    }

    private void emitPosition(
            Player player,
            long timestamp,
            Vector packetPos,
            Float packetYaw,
            Float packetPitch,
            boolean cancelled) {
        if (!player.isOnline()) {
            return;
        }
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        float yaw = packetYaw == null ? player.getLocation().getYaw() : packetYaw;
        float pitch = packetPitch == null ? player.getLocation().getPitch() : packetPitch;
        double eyeX = player.getEyeLocation().getX();
        double eyeY = player.getEyeLocation().getY();
        double eyeZ = player.getEyeLocation().getZ();
        float height = EntityCompat.getPlayerHeight(player);
        // Detect swimming/crawling pose transitions (1.14+ only)
        if (ServerVersion.HAS_ENTITY_POSE) {
            try {
                org.bukkit.entity.Pose currentPose = player.getPose();
                boolean isSwimming = (currentPose == org.bukkit.entity.Pose.SWIMMING);
                Boolean lastState = swimmingPoseState.put(player.getUniqueId(), isSwimming);

                // Send action only on transition (or first time seen)
                if (lastState == null || lastState != isSwimming) {
                    ServerBoundPlayerCommandActions action = isSwimming
                        ? ServerBoundPlayerCommandActions.START_SWIMMING
                        : ServerBoundPlayerCommandActions.STOP_SWIMMING;
                    PacketQueue.push(new PacketServerBoundPlayerCommand(
                        timestamp, uid, name, action
                    ));
                }
            } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
                // Pose API not available on this version despite flag — skip gracefully
            }
        }

        try {
            boolean onGround = BlockUtil.isOnGround(player, packetPos);
            PacketQueue.push(new PacketPlayerPosition(
                    timestamp, uid, name, cancelled,
                    packetPos.getX(), packetPos.getY(), packetPos.getZ(),
                    eyeX, eyeY, eyeZ, yaw, pitch, height, onGround));

            org.bukkit.Location packetLoc = new org.bukkit.Location(player.getWorld(), packetPos.getX(), packetPos.getY(), packetPos.getZ());
            List<RelativeBlock> blocks = BlockUtil.getRelativeBlocks(player, packetLoc);
            PacketQueue.push(new PacketPlayerSurroundingBlocks(timestamp, uid, name, blocks));
        } catch (Exception e) {
            plugin
                .getLogger()
                .warning("Error processing position packet for " + name + ": " + e.getMessage());
        }
    }
}
