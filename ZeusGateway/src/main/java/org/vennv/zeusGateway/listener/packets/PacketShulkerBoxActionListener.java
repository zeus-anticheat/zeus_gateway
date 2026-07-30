package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketShulkerBoxAction;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

/** Captures recipient-visible vanilla shulker animation state. */
public final class PacketShulkerBoxActionListener extends PacketListenerAbstract {
    private static final int SHULKER_ANIMATION_ACTION = 1;
    private static final Set<String> VANILLA_SHULKER_PATHS = new HashSet<String>(Arrays.asList(
            "shulker_box", "white_shulker_box", "orange_shulker_box",
            "magenta_shulker_box", "light_blue_shulker_box", "yellow_shulker_box",
            "lime_shulker_box", "pink_shulker_box", "gray_shulker_box",
            "light_gray_shulker_box", "cyan_shulker_box", "purple_shulker_box",
            "blue_shulker_box", "brown_shulker_box", "green_shulker_box",
            "red_shulker_box", "black_shulker_box"));

    private final OrderedWorldPacketDispatcher dispatcher;

    public PacketShulkerBoxActionListener(ZeusGateway plugin, OrderedWorldPacketDispatcher dispatcher) {
        super(PacketListenerPriority.MONITOR);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()
                || event.getPacketType() != PacketType.Play.Server.BLOCK_ACTION
                || !isVanillaShulkerAction(event)) return;
        Object eventPlayer = event.getPlayer();
        if (!(eventPlayer instanceof Player)) return;
        User user = event.getUser();
        if (user == null || user.getUUID() == null || user.getName() == null) return;

        Player player = (Player) eventPlayer;
        UUID uuid = user.getUUID();
        String name = user.getName();
        long timestamp = System.currentTimeMillis();
        dispatcher.submit(event,
                packetEvent -> process(packetEvent, uuid, name, timestamp),
                true,
                player);
    }

    private static void process(PacketSendEvent event, UUID uuid, String name, long timestamp) {
        PacketTypeCommon type = event.getPacketType();
        if (type != PacketType.Play.Server.BLOCK_ACTION) return;

        WrapperPlayServerBlockAction action = new WrapperPlayServerBlockAction(event);
        if (!isVanillaShulkerAction(blockType(action.getBlockType()), action.getActionId())) return;

        Vector3i position = action.getBlockPosition();
        if (position == null) return;
        if (!PacketQueue.push(new PacketShulkerBoxAction(
                timestamp,
                uuid.toString(),
                name,
                position.getX(),
                position.getY(),
                position.getZ(),
                (byte) action.getActionId(),
                (byte) action.getActionData()))) {
            throw new IllegalStateException("shulker action queue discontinuity");
        }
    }

    private static boolean isVanillaShulkerAction(PacketSendEvent event) {
        PacketSendEvent probe = null;
        try {
            probe = event.clone();
            WrapperPlayServerBlockAction action = new WrapperPlayServerBlockAction(probe);
            return isVanillaShulkerAction(blockType(action.getBlockType()), action.getActionId());
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        } finally {
            if (probe != null) probe.cleanUp();
        }
    }

    private static String blockType(WrappedBlockState block) {
        return block == null || block.getType() == null ? null : block.getType().getName();
    }

    static boolean isVanillaShulkerAction(String blockType, int actionId) {
        if (actionId != SHULKER_ANIMATION_ACTION || blockType == null) return false;
        String normalized = blockType.toLowerCase(java.util.Locale.ROOT);
        int separator = normalized.indexOf(':');
        if (separator >= 0 && !normalized.startsWith("minecraft:")) return false;
        String path = separator < 0 ? normalized : normalized.substring(separator + 1);
        return VANILLA_SHULKER_PATHS.contains(path);
    }
}
