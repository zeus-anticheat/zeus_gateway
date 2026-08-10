package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        if (user == null) return;
        UUID uuid = user.getUUID();
        String name = user.getName();
        if (uuid == null || name == null) return;

        WrapperPlayServerUpdateAttributes packet = new WrapperPlayServerUpdateAttributes(event);
        Integer selfEntityId = PacketEntityMetadataListener.getSelfEntityId(uuid);
        if (selfEntityId == null || packet.getEntityId() != selfEntityId) return;

        List<PacketUpdateAttributes.Property> properties = convertProperties(packet.getProperties());
        if (properties.isEmpty()) return;
        PacketQueue.push(PacketUpdateAttributes.merge(
                System.currentTimeMillis(), uuid.toString(), name, properties));
    }

    static List<PacketUpdateAttributes.Property> convertProperties(
            List<WrapperPlayServerUpdateAttributes.Property> properties) {
        Map<String, PacketUpdateAttributes.Property> converted = new LinkedHashMap<>();
        if (properties == null) return new ArrayList<>();
        for (WrapperPlayServerUpdateAttributes.Property property : properties) {
            String key = canonicalKey(property);
            if (key == null || !Double.isFinite(property.getValue()) || property.getModifiers() == null) {
                continue;
            }
            List<PacketUpdateAttributes.Modifier> modifiers = new ArrayList<>();
            boolean complete = true;
            for (WrapperPlayServerUpdateAttributes.PropertyModifier modifier : property.getModifiers()) {
                PacketUpdateAttributes.Modifier raw = convertModifier(modifier);
                if (raw == null) {
                    complete = false;
                    break;
                }
                modifiers.add(raw);
            }
            if (complete) {
                converted.put(key, new PacketUpdateAttributes.Property(key, property.getValue(), modifiers));
            }
        }
        return new ArrayList<>(converted.values());
    }

    private static PacketUpdateAttributes.Modifier convertModifier(
            WrapperPlayServerUpdateAttributes.PropertyModifier modifier) {
        if (modifier == null || modifier.getOperation() == null || !Double.isFinite(modifier.getAmount())) {
            return null;
        }
        String resourceName = modifier.getName() == null ? null : modifier.getName().toString();
        String stableId = modifier.getUUID() == null ? resourceName : modifier.getUUID().toString();
        String name = resourceName == null ? stableId : resourceName;
        if (stableId == null || stableId.isEmpty() || name == null || name.isEmpty()) return null;
        return new PacketUpdateAttributes.Modifier(
                stableId,
                name,
                modifier.getAmount(),
                PacketUpdateAttributes.Operation.valueOf(modifier.getOperation().name()));
    }

    private static String canonicalKey(WrapperPlayServerUpdateAttributes.Property property) {
        if (property == null) return null;
        String key = property.getKey();
        if ("generic.movementSpeed".equals(key)
                || "minecraft:generic.movement_speed".equals(key)
                || "minecraft:movement_speed".equals(key)) return "minecraft:movement_speed";
        if ("generic.gravity".equals(key)
                || "minecraft:generic.gravity".equals(key)
                || "minecraft:gravity".equals(key)) return "minecraft:gravity";
        if ("generic.jumpStrength".equals(key)
                || "minecraft:generic.jump_strength".equals(key)
                || "minecraft:jump_strength".equals(key)) return "minecraft:jump_strength";
        if ("generic.stepHeight".equals(key)
                || "minecraft:generic.step_height".equals(key)
                || "minecraft:step_height".equals(key)) return "minecraft:step_height";
        if ("generic.scale".equals(key)
                || "minecraft:generic.scale".equals(key)
                || "minecraft:scale".equals(key)) return "minecraft:scale";
        if ("generic.sneakingSpeed".equals(key)
                || "minecraft:generic.sneaking_speed".equals(key)
                || "minecraft:sneaking_speed".equals(key)) return "minecraft:sneaking_speed";
        if ("generic.movementEfficiency".equals(key)
                || "minecraft:generic.movement_efficiency".equals(key)
                || "minecraft:movement_efficiency".equals(key)) return "minecraft:movement_efficiency";
        if ("generic.waterMovementEfficiency".equals(key)
                || "minecraft:generic.water_movement_efficiency".equals(key)
                || "minecraft:water_movement_efficiency".equals(key)) {
            return "minecraft:water_movement_efficiency";
        }
        if (isMovementSpeed(property)) return "minecraft:movement_speed";
        if (isGravity(property)) return "minecraft:gravity";
        if (isJumpStrength(property)) return "minecraft:jump_strength";
        if (isStepHeight(property)) return "minecraft:step_height";
        if (isScale(property)) return "minecraft:scale";
        if (isSneakingSpeed(property)) return "minecraft:sneaking_speed";
        if (isMovementEfficiency(property)) return "minecraft:movement_efficiency";
        if (isWaterMovementEfficiency(property)) return "minecraft:water_movement_efficiency";
        return null;
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
