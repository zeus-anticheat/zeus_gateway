package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerUseItem;
import org.vennv.utils.Hand;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.platform.ServerVersion;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketUseItemListener extends PacketAdapter {

    public PacketUseItemListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Client.USE_ITEM);
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Hand hand = Hand.MAIN_HAND;
        byte sequence = 0;

        try {
            // Read the hand enum from the packet
            StructureModifier<EnumWrappers.Hand> hands = event.getPacket().getHands();
            if (hands.size() > 0) {
                EnumWrappers.Hand packetHand = hands.read(0);
                if (packetHand == EnumWrappers.Hand.OFF_HAND) {
                    hand = Hand.OFF_HAND;
                }
            }
        } catch (Exception e) {
            // Fallback: try reading as integer
            try {
                StructureModifier<Integer> integers = event.getPacket().getIntegers();
                if (integers.size() > 0) {
                    int handValue = integers.read(0);
                    if (handValue == 1) {
                        hand = Hand.OFF_HAND;
                    }
                }
            } catch (Exception ignored) {
                // Default to MAIN_HAND
            }
        }

        if (ServerVersion.isAtLeast(1, 19, 2)) {
            try {
                StructureModifier<Integer> integers = event.getPacket().getIntegers();
                int lastIndex = integers.size() - 1;
                if (lastIndex >= 0) {
                    sequence = integers.read(lastIndex).byteValue();
                }
            } catch (Exception ignored) {
                // Sequence not available on this packet/version.
            }
        }

        PacketPlayerUseItem packet = new PacketPlayerUseItem(
                timestamp,
                uid,
                name,
                hand,
                sequence
        );
        PacketQueue.push(packet);
    }
}
