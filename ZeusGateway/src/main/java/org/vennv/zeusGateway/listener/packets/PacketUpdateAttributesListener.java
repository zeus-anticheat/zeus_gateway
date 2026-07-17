package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import java.util.UUID;
import org.vennv.packets.PacketUpdateAttributes;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketUpdateAttributesListener extends PacketListenerAbstract {

    public PacketUpdateAttributesListener(ZeusGateway plugin) {
        super(PacketListenerPriority.MONITOR);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled() || event.getPacketType() != PacketType.Play.Server.UPDATE_ATTRIBUTES) return;

        User user = event.getUser();
        UUID uuid = user.getUUID();
        String name = user.getName();
        if (uuid == null || name == null) return;

        WrapperPlayServerUpdateAttributes packet = new WrapperPlayServerUpdateAttributes(event);
        if (packet.getEntityId() != user.getEntityId()) return;

        for (WrapperPlayServerUpdateAttributes.Property property : packet.getProperties()) {
            if (property.getAttribute() == Attributes.MOVEMENT_SPEED
                    || property.getAttribute() == Attributes.GENERIC_MOVEMENT_SPEED
                    || "generic.movementSpeed".equals(property.getKey())
                    || "minecraft:generic.movement_speed".equals(property.getKey())
                    || "minecraft:movement_speed".equals(property.getKey())) {
                double baseValue = property.getValue();
                if (!Double.isFinite(baseValue) || baseValue <= 0.0) return;
                PacketQueue.push(new PacketUpdateAttributes(
                    System.currentTimeMillis(), uuid.toString(), name, (float) baseValue));
                return;
            }
        }
    }
}
