package org.vennv.zeusGateway.debug;

import java.util.Locale;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketEncode;
import org.vennv.EntityState;
import org.vennv.packets.*;
import org.vennv.utils.Armor;
import org.vennv.utils.EffectFlags;
import org.vennv.utils.Item;
import org.vennv.utils.ItemStack;

public final class PacketDebugFormatter {
    private PacketDebugFormatter() {}

    public static String packetName(PacketEncode packet) {
        packet = unwrap(packet);
        if (packet instanceof PacketServerBoundPlayerCommand) {
            return "PlayerCommand";
        }
        String name = packet.getClass().getSimpleName();
        if (name.startsWith("PacketPlayer")) {
            return name.substring("PacketPlayer".length());
        }
        if (name.startsWith("Packet")) {
            return name.substring("Packet".length());
        }
        return name;
    }

    public static String format(PacketEncode packet) {
        String producerDetails = packet instanceof PacketDebugEnvelope
                ? ((PacketDebugEnvelope) packet).getProducerDetails() : "";
        packet = unwrap(packet);
        if (!(packet instanceof PacketBaseInfo)) {
            return null;
        }
        PacketBaseInfo info = (PacketBaseInfo) packet;

        StringBuilder message = new StringBuilder("[ZTX] ")
                .append(packetName(packet))
                .append(" target=")
                .append(info.getUsername());

        if (packet instanceof PacketPlayerHeldItem) {
            PacketPlayerHeldItem held = (PacketPlayerHeldItem) packet;
            appendItem(message, held.getItem());
        } else if (packet instanceof PacketPlayerArmorsEquipment) {
            PacketPlayerArmorsEquipment armor = (PacketPlayerArmorsEquipment) packet;
            message.append(" helmet=").append(formatArmor(armor.getArmors().getHelmet()))
                    .append(" chest=").append(formatArmor(armor.getArmors().getChestplate()))
                    .append(" legs=").append(formatArmor(armor.getArmors().getLeggings()))
                    .append(" boots=").append(formatArmor(armor.getArmors().getBoots()));
        } else if (packet instanceof PacketPlayerClickWindow) {
            PacketPlayerClickWindow click = (PacketPlayerClickWindow) packet;
            message.append(" window=").append(Byte.toUnsignedInt(click.getWindowId()))
                    .append(" slot=").append(click.getSlotId())
                    .append(" button=").append(Byte.toUnsignedInt(click.getButton()))
                    .append(" mode=").append(clickMode(click.getMode()))
                    .append(" tx=").append(click.getTransactionId());
            appendStack(message, click.getItemStack());
        } else if (packet instanceof PacketPlayerInventoryTransaction) {
            PacketPlayerInventoryTransaction transaction = (PacketPlayerInventoryTransaction) packet;
            message.append(" window=").append(Byte.toUnsignedInt(transaction.getWindowId()))
                    .append(" state=").append(transaction.getStateId())
                    .append(" clicked=").append(transaction.getClickedSlot())
                    .append(" button=").append(Byte.toUnsignedInt(transaction.getButton()))
                    .append(" mode=").append(clickMode(transaction.getMode()))
                    .append(" tx=").append(transaction.getTransactionId())
                    .append(" changed=").append(transaction.getChangedSlots().size())
                    .append(" cursor=");
            message.append(formatStack(transaction.getCursorStack()));
        } else if (packet instanceof PacketPlayerPosition) {
            PacketPlayerPosition position = (PacketPlayerPosition) packet;
            message.append(String.format(Locale.ROOT,
                    " xyz=%.2f/%.2f/%.2f yaw=%.1f pitch=%.1f ground=%s",
                    position.getX(), position.getY(), position.getZ(),
                    position.getYaw(), position.getPitch(), position.isOnGround()));
        } else if (packet instanceof PacketPlayerTeleport) {
            PacketPlayerTeleport teleport = (PacketPlayerTeleport) packet;
            appendCoordinates(message, teleport.getX(), teleport.getY(), teleport.getZ());
        } else if (packet instanceof PacketPlayerVelocity) {
            PacketPlayerVelocity velocity = (PacketPlayerVelocity) packet;
            message.append(String.format(Locale.ROOT, " velocity=%.3f/%.3f/%.3f",
                    velocity.getX(), velocity.getY(), velocity.getZ()));
        } else if (packet instanceof PacketPlayerExternalForce) {
            PacketPlayerExternalForce force = (PacketPlayerExternalForce) packet;
            message.append(String.format(Locale.ROOT,
                    " type=%s src=%.2f/%.2f/%.2f dir=%.2f/%.2f/%.2f vel=%.3f/%.3f/%.3f strength=%.3f ticks=%d flags=0x%08x",
                    force.getForceType().name(),
                    force.getSourceX(), force.getSourceY(), force.getSourceZ(),
                    force.getDirX(), force.getDirY(), force.getDirZ(),
                    force.getVelocityX(), force.getVelocityY(), force.getVelocityZ(),
                    force.getStrength(), Short.toUnsignedInt(force.getDurationTicks()),
                    force.getFlags()));
        } else if (packet instanceof PacketPlayerBlockFace) {
            PacketPlayerBlockFace face = (PacketPlayerBlockFace) packet;
            message.append(" face=").append(faceName(face.getFace()));
        } else if (packet instanceof PacketPlayerBlockRayTrace) {
            PacketPlayerBlockRayTrace trace = (PacketPlayerBlockRayTrace) packet;
            message.append(" hit=").append(trace.isHitBlock())
                    .append(" action=").append(Byte.toUnsignedInt(trace.getAction()));
            if (trace.isHitBlock()) {
                message.append(" block=").append(trace.getBlockX()).append('/')
                        .append(trace.getBlockY()).append('/').append(trace.getBlockZ())
                        .append(String.format(Locale.ROOT, " point=%.2f/%.2f/%.2f",
                                trace.getHitX(), trace.getHitY(), trace.getHitZ()));
            }
        } else if (packet instanceof PacketPlayerUseItem) {
            PacketPlayerUseItem use = (PacketPlayerUseItem) packet;
            message.append(" hand=").append(use.getHand().name())
                    .append(" sequence=").append(Byte.toUnsignedInt(use.getSequence()));
        } else if (packet instanceof PacketPlayerReleaseUseItem) {
            PacketPlayerReleaseUseItem release = (PacketPlayerReleaseUseItem) packet;
            message.append(" hand=").append(release.getHand().name());
        } else if (packet instanceof PacketPlayerSwingHand) {
            PacketPlayerSwingHand swing = (PacketPlayerSwingHand) packet;
            message.append(" cancelled=").append(swing.isCancelled());
        } else if (packet instanceof PacketPlayerEntityInteraction) {
            PacketPlayerEntityInteraction interaction = (PacketPlayerEntityInteraction) packet;
            appendEntity(message, interaction.getEntityState());
        } else if (packet instanceof PacketPlayerAttackEntity) {
            PacketPlayerAttackEntity attack = (PacketPlayerAttackEntity) packet;
            appendEntity(message, attack.getEntityState());
        } else if (packet instanceof PacketPlayerAttackedByEntity) {
            PacketPlayerAttackedByEntity attack = (PacketPlayerAttackedByEntity) packet;
            appendEntity(message, attack.getEntityState());
        } else if (packet instanceof PacketPlayerAttackedByPlayer) {
            PacketPlayerAttackedByPlayer attack = (PacketPlayerAttackedByPlayer) packet;
            appendEntity(message, attack.getEntityState());
        } else if (packet instanceof PacketPlayerVehicleMove) {
            PacketPlayerVehicleMove vehicle = (PacketPlayerVehicleMove) packet;
            message.append(String.format(Locale.ROOT,
                    " x=%.2f y=%.2f z=%.2f yaw=%.2f pitch=%.2f",
                    vehicle.getX(), vehicle.getY(), vehicle.getZ(),
                    vehicle.getYaw(), vehicle.getPitch()));
        } else if (packet instanceof PacketPlayerSteerVehicle) {
            PacketPlayerSteerVehicle steer = (PacketPlayerSteerVehicle) packet;
            message.append(String.format(Locale.ROOT,
                    " side=%.2f forward=%.2f jump=%s unmount=%s",
                    steer.getSideway(), steer.getForward(), steer.isJump(), steer.isUnmount()));
        } else if (packet instanceof PacketServerBoundPlayerCommand) {
            PacketServerBoundPlayerCommand command = (PacketServerBoundPlayerCommand) packet;
            message.append(" action=").append(command.getAction().name());
        } else if (packet instanceof PacketPlayerOpenWindow) {
            PacketPlayerOpenWindow open = (PacketPlayerOpenWindow) packet;
            message.append(" window=").append(Byte.toUnsignedInt(open.getWindowId()));
        } else if (packet instanceof PacketPlayerCloseWindow) {
            PacketPlayerCloseWindow close = (PacketPlayerCloseWindow) packet;
            message.append(" window=").append(Byte.toUnsignedInt(close.getWindowId()));
        } else if (packet instanceof PacketPlayerConfirmTransaction) {
            PacketPlayerConfirmTransaction confirmation = (PacketPlayerConfirmTransaction) packet;
            message.append(" window=").append(Byte.toUnsignedInt(confirmation.getWindowId()))
                    .append(" action=").append(confirmation.getActionNumber())
                    .append(" accepted=").append(confirmation.isAccepted());
        } else if (packet instanceof PacketPlayerChangeMode) {
            PacketPlayerChangeMode mode = (PacketPlayerChangeMode) packet;
            message.append(" mode=").append(mode.getGamemode());
        } else if (packet instanceof PacketPlayerKeepAlive) {
            PacketPlayerKeepAlive keepAlive = (PacketPlayerKeepAlive) packet;
            message.append(" ping=").append(keepAlive.getPing()).append("ms");
        } else if (packet instanceof PacketPlayerGotDamage) {
            PacketPlayerGotDamage damage = (PacketPlayerGotDamage) packet;
            message.append(" cause=").append(damage.getCause().name());
        } else if (packet instanceof PacketPlayerEffect) {
            PacketPlayerEffect effect = (PacketPlayerEffect) packet;
            message.append(" effect=").append(Byte.toUnsignedInt(effect.getEffect().getEffectId()))
                    .append(" amp=").append(Byte.toUnsignedInt(effect.getEffect().getAmplifier()))
                    .append(" ticks=").append(effect.getEffect().getDuration())
                    .append(" action=").append(EffectFlags.getFlagName(effect.getEffect().getFlags()));
        } else if (packet instanceof PacketPlayerEnchantments) {
            PacketPlayerEnchantments enchantments = (PacketPlayerEnchantments) packet;
            message.append(String.format(Locale.ROOT, " reach=%.2f enchants=",
                    enchantments.getEntityInteractionRange()));
            String encodedEnchantments = enchantments.getEnchantments().stream()
                    .limit(3)
                    .map(enchantment -> enchantment.getName() + ":"
                            + Byte.toUnsignedInt(enchantment.getLevel()))
                    .reduce((left, right) -> left + "," + right)
                    .orElse("none");
            message.append(encodedEnchantments);
            if (enchantments.getEnchantments().size() > 3) {
                message.append(",+").append(enchantments.getEnchantments().size() - 3);
            }
        } else if (packet instanceof PacketServerConfig) {
            message.append(" identity");
        } else if (packet instanceof PacketBlockChangeEvent) {
            PacketBlockChangeEvent bc = (PacketBlockChangeEvent) packet;
            message.append(" block_change=").append(bc.getBlockType());
        } else if (packet instanceof PacketShulkerBoxAction) {
            PacketShulkerBoxAction action = (PacketShulkerBoxAction) packet;
            message.append(" pos=").append(action.getWorldX()).append('/')
                    .append(action.getWorldY()).append('/').append(action.getWorldZ())
                    .append(" action=").append(Byte.toUnsignedInt(action.getActionType()))
                    .append(" viewers=").append(Byte.toUnsignedInt(action.getViewerCount()));
        } else if (packet instanceof PacketPlayerCustomFeature) {
            PacketPlayerCustomFeature feature = (PacketPlayerCustomFeature) packet;
            message.append(" category=").append(feature.getCategoryId())
                    .append(" feature=").append(feature.getFeatureId())
                    .append(String.format(Locale.ROOT, " value=%.3f", feature.getFeatureValue()));
        }
        message.append(producerDetails);
        return message.toString();
    }

    public static boolean isHudPacket(PacketEncode packet) {
        packet = unwrap(packet);
        return packet instanceof PacketPlayerPosition
                || packet instanceof PacketPlayerVehicleMove;
    }

    public static String formatHud(PacketEncode packet) {
        packet = unwrap(packet);
        if (!(packet instanceof PacketBaseInfo)) {
            return null;
        }
        PacketBaseInfo info = (PacketBaseInfo) packet;
        if (packet instanceof PacketPlayerPosition) {
            PacketPlayerPosition position = (PacketPlayerPosition) packet;
            return String.format(Locale.ROOT,
                    "[Z POS] %s %.2f / %.2f / %.2f  yaw %.1f pitch %.1f",
                    info.getUsername(), position.getX(), position.getY(), position.getZ(),
                    position.getYaw(), position.getPitch());
        }
        if (packet instanceof PacketPlayerVehicleMove) {
            PacketPlayerVehicleMove vehicle = (PacketPlayerVehicleMove) packet;
            return String.format(Locale.ROOT,
                    "[Z VEH] %s %.2f / %.2f / %.2f  yaw %.1f pitch %.1f",
                    info.getUsername(), vehicle.getX(), vehicle.getY(), vehicle.getZ(),
                    vehicle.getYaw(), vehicle.getPitch());
        }
        return null;
    }

    public static PacketEncode unwrap(PacketEncode packet) {
        return packet instanceof PacketDebugEnvelope ? ((PacketDebugEnvelope) packet).getPacket() : packet;
    }

    private static void appendItem(StringBuilder message, Item item) {
        appendStack(message, item.getItemStack());
        if (item.getCustomName() != null && !item.getCustomName().isEmpty()) {
            message.append(" custom=").append(item.getCustomName());
        }
    }

    private static void appendStack(StringBuilder message, ItemStack stack) {
        message.append(" item=").append(stack.isEmpty() ? "empty" : stack.getId())
                .append(" meta=").append(stack.getMeta())
                .append(" count=").append(Byte.toUnsignedInt(stack.getCount()));
    }

    private static String formatStack(ItemStack stack) {
        return (stack == null || stack.isEmpty() ? "empty" : stack.getId())
                + ":" + (stack == null ? 0 : stack.getMeta())
                + "x" + (stack == null ? 0 : Byte.toUnsignedInt(stack.getCount()));
    }

    private static void appendBlock(
            StringBuilder message, double x, double y, double z, boolean cancelled) {
        message.append(String.format(Locale.ROOT, " x=%.0f y=%.0f z=%.0f cancelled=%s",
                x, y, z, cancelled));
    }

    private static void appendCoordinates(StringBuilder message, double x, double y, double z) {
        message.append(String.format(Locale.ROOT, " xyz=%.2f/%.2f/%.2f", x, y, z));
    }

    private static void appendEntity(StringBuilder message, EntityState entity) {
        message.append(" entity=").append(entity.getEid())
                .append(String.format(Locale.ROOT, " xyz=%.2f/%.2f/%.2f",
                        entity.getPacketX(), entity.getPacketY(), entity.getPacketZ()));
    }

    private static String formatArmor(Armor armor) {
        return armor == null ? "empty" : armor.getName() + ":" + armor.getMeta();
    }

    private static String clickMode(short mode) {
        String[] modes = {"PICKUP", "QUICK_MOVE", "SWAP", "CLONE",
                "THROW", "QUICK_CRAFT", "PICKUP_ALL"};
        return mode >= 0 && mode < modes.length ? modes[mode] : Short.toString(mode);
    }

    private static String faceName(byte face) {
        switch (face) {
            case 0:
                return "DOWN";
            case 1:
                return "UP";
            case 2:
                return "NORTH";
            case 3:
                return "SOUTH";
            case 4:
                return "WEST";
            case 5:
                return "EAST";
            default:
                return Byte.toString(face);
        }
    }
}
