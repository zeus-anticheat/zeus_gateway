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

        // Accumulate all recognized entity attributes from this packet.
        Float movementSpeed = null;
        Double gravity = null;
        Double jumpStrength = null;
        Double stepHeight = null;
        Double scale = null;
        Double sneakingSpeed = null;
        Double movementEfficiency = null;
        Double waterMovementEfficiency = null;

        for (WrapperPlayServerUpdateAttributes.Property property : packet.getProperties()) {
            if (movementSpeed == null && isMovementSpeed(property)) {
                double baseValue = property.getValue();
                if (Double.isFinite(baseValue) && baseValue > 0.0) {
                    movementSpeed = (float) baseValue;
                }
            } else if (gravity == null && isGravity(property)) {
                double baseValue = property.getValue();
                if (Double.isFinite(baseValue)) gravity = baseValue;
            } else if (jumpStrength == null && isJumpStrength(property)) {
                double baseValue = property.getValue();
                if (Double.isFinite(baseValue) && baseValue >= 0.0) jumpStrength = baseValue;
            } else if (stepHeight == null && isStepHeight(property)) {
                double baseValue = property.getValue();
                if (Double.isFinite(baseValue) && baseValue >= 0.0) stepHeight = baseValue;
            } else if (scale == null && isScale(property)) {
                double baseValue = property.getValue();
                if (Double.isFinite(baseValue) && baseValue > 0.0) scale = baseValue;
            } else if (sneakingSpeed == null && isSneakingSpeed(property)) {
                double baseValue = property.getValue();
                if (Double.isFinite(baseValue) && baseValue >= 0.0) sneakingSpeed = baseValue;
            } else if (movementEfficiency == null && isMovementEfficiency(property)) {
                double baseValue = property.getValue();
                if (Double.isFinite(baseValue) && baseValue >= 0.0) movementEfficiency = baseValue;
            } else if (waterMovementEfficiency == null && isWaterMovementEfficiency(property)) {
                double baseValue = property.getValue();
                if (Double.isFinite(baseValue) && baseValue >= 0.0) waterMovementEfficiency = baseValue;
            }
        }

        // Movement speed is the bare minimum — without it the packet is useless.
        if (movementSpeed == null) return;

        PacketQueue.push(new PacketUpdateAttributes(
            System.currentTimeMillis(), uuid.toString(), name,
            movementSpeed, gravity, jumpStrength, stepHeight, scale,
            sneakingSpeed, movementEfficiency, waterMovementEfficiency));
    }

    // -- Attribute matchers (handle version-dependent names) -----------------

    private static boolean isMovementSpeed(WrapperPlayServerUpdateAttributes.Property p) {
        return p.getAttribute() == Attributes.MOVEMENT_SPEED
            || p.getAttribute() == Attributes.GENERIC_MOVEMENT_SPEED
            || "generic.movementSpeed".equals(p.getKey())
            || "minecraft:generic.movement_speed".equals(p.getKey())
            || "minecraft:movement_speed".equals(p.getKey());
    }

    private static boolean isGravity(WrapperPlayServerUpdateAttributes.Property p) {
        return p.getAttribute() == Attributes.GRAVITY
            || p.getAttribute() == Attributes.GENERIC_GRAVITY
            || "generic.gravity".equals(p.getKey())
            || "minecraft:generic.gravity".equals(p.getKey());
    }

    private static boolean isJumpStrength(WrapperPlayServerUpdateAttributes.Property p) {
        return p.getAttribute() == Attributes.JUMP_STRENGTH
            || p.getAttribute() == Attributes.GENERIC_JUMP_STRENGTH
            || "generic.jumpStrength".equals(p.getKey())
            || "minecraft:generic.jump_strength".equals(p.getKey());
    }

    private static boolean isStepHeight(WrapperPlayServerUpdateAttributes.Property p) {
        return p.getAttribute() == Attributes.STEP_HEIGHT
            || p.getAttribute() == Attributes.GENERIC_STEP_HEIGHT
            || "generic.stepHeight".equals(p.getKey())
            || "minecraft:generic.step_height".equals(p.getKey());
    }

    private static boolean isScale(WrapperPlayServerUpdateAttributes.Property p) {
        return p.getAttribute() == Attributes.SCALE
            || p.getAttribute() == Attributes.GENERIC_SCALE
            || "generic.scale".equals(p.getKey())
            || "minecraft:generic.scale".equals(p.getKey());
    }

    private static boolean isSneakingSpeed(WrapperPlayServerUpdateAttributes.Property p) {
        return p.getAttribute() == Attributes.SNEAKING_SPEED
            || p.getAttribute() == Attributes.PLAYER_SNEAKING_SPEED
            || "generic.sneakingSpeed".equals(p.getKey())
            || "minecraft:generic.sneaking_speed".equals(p.getKey());
    }

    private static boolean isMovementEfficiency(WrapperPlayServerUpdateAttributes.Property p) {
        return p.getAttribute() == Attributes.MOVEMENT_EFFICIENCY
            || p.getAttribute() == Attributes.GENERIC_MOVEMENT_EFFICIENCY
            || "generic.movementEfficiency".equals(p.getKey())
            || "minecraft:generic.movement_efficiency".equals(p.getKey());
    }

    private static boolean isWaterMovementEfficiency(WrapperPlayServerUpdateAttributes.Property p) {
        return p.getAttribute() == Attributes.WATER_MOVEMENT_EFFICIENCY
            || p.getAttribute() == Attributes.GENERIC_WATER_MOVEMENT_EFFICIENCY
            || "generic.waterMovementEfficiency".equals(p.getKey())
            || "minecraft:generic.water_movement_efficiency".equals(p.getKey());
    }
}
