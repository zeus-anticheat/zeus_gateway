package org.vennv.zeusGateway.compat;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.vennv.packets.PacketUpdateAttributes;
import org.vennv.zeusGateway.platform.ServerVersion;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AttributeCompat {

    static PacketUpdateAttributes.Property toProperty(Object instance, String key) {
        if (instance == null || key == null || key.isEmpty()) return null;
        try {
            double baseValue = ((Number) instance.getClass().getMethod("getBaseValue").invoke(instance))
                    .doubleValue();
            if (!Double.isFinite(baseValue)) return null;
            Object rawModifiers = instance.getClass().getMethod("getModifiers").invoke(instance);
            if (!(rawModifiers instanceof Iterable<?>)) return null;
            List<PacketUpdateAttributes.Modifier> modifiers = new ArrayList<>();
            for (Object modifier : (Iterable<?>) rawModifiers) {
                PacketUpdateAttributes.Modifier converted = toModifier(modifier);
                if (converted == null) {
                    java.util.logging.Logger.getLogger("ZeusGateway").warning(
                            "[ZEUS-ATTR] modifier conversion failed for " + key
                                    + " class=" + modifier.getClass().getName());
                    return null;
                }
                modifiers.add(converted);
            }
            return new PacketUpdateAttributes.Property(key, baseValue, modifiers);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            java.util.logging.Logger.getLogger("ZeusGateway").warning(
                    "[ZEUS-ATTR] property conversion failed for " + key + ": "
                            + failure.getClass().getSimpleName() + " " + failure.getMessage());
            return null;
        }
    }

    public static List<PacketUpdateAttributes.Property> getMovementProperties(Player player) {
        List<PacketUpdateAttributes.Property> properties = new ArrayList<>();
        if (!addProperty(properties, player, "minecraft:movement_speed",
                "MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED")
                || !addProperty(properties, player, "minecraft:gravity",
                        "GRAVITY", "GENERIC_GRAVITY")
                || !addProperty(properties, player, "minecraft:jump_strength",
                        "JUMP_STRENGTH", "GENERIC_JUMP_STRENGTH")
                || !addProperty(properties, player, "minecraft:step_height",
                        "STEP_HEIGHT", "GENERIC_STEP_HEIGHT")
                || !addProperty(properties, player, "minecraft:scale",
                        "SCALE", "GENERIC_SCALE")
                || !addProperty(properties, player, "minecraft:sneaking_speed",
                        "SNEAKING_SPEED", "GENERIC_SNEAKING_SPEED")
                || !addProperty(properties, player, "minecraft:movement_efficiency",
                        "MOVEMENT_EFFICIENCY", "GENERIC_MOVEMENT_EFFICIENCY")
                || !addProperty(properties, player, "minecraft:water_movement_efficiency",
                        "WATER_MOVEMENT_EFFICIENCY", "GENERIC_WATER_MOVEMENT_EFFICIENCY")) {
            return null;
        }
        return Collections.unmodifiableList(properties);
    }

    private static boolean addProperty(
            List<PacketUpdateAttributes.Property> properties,
            Player player,
            String key,
            String... attributeNames) {
        Object instance = null;
        for (String attributeName : attributeNames) {
            instance = readAttributeInstance(player, attributeName);
            if (instance != null) break;
        }
        PacketUpdateAttributes.Property property = toProperty(instance, key);
        if (property == null) return false;
        properties.add(property);
        return true;
    }

    private static Object readAttributeInstance(Player player, String name) {
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Object attribute = attributeClass.getMethod("valueOf", String.class).invoke(null, name);
            Object instance = player.getClass()
                    .getMethod("getAttribute", attributeClass)
                    .invoke(player, attribute);
            if (instance == null) {
                java.util.logging.Logger.getLogger("ZeusGateway").warning(
                        "[ZEUS-ATTR] getAttribute returned null for " + name);
            }
            return instance;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            java.util.logging.Logger.getLogger("ZeusGateway").fine(
                    "[ZEUS-ATTR] read failed for " + name + ": "
                            + failure.getClass().getSimpleName() + " " + failure.getMessage());
            return null;
        }
    }

    private static PacketUpdateAttributes.Modifier toModifier(Object modifier) {
        if (modifier == null) return null;
        try {
            double amount = ((Number) modifier.getClass().getMethod("getAmount").invoke(modifier))
                    .doubleValue();
            Object operation = modifier.getClass().getMethod("getOperation").invoke(modifier);
            if (!Double.isFinite(amount) || operation == null) return null;

            String stableId = invokeText(modifier, "getUniqueId");
            String resourceName = invokeText(modifier, "getKey");
            String legacyName = invokeText(modifier, "getName");
            if (stableId == null) stableId = resourceName != null ? resourceName : legacyName;
            String name = resourceName != null ? resourceName : stableId;
            if (stableId == null || stableId.isEmpty() || name == null || name.isEmpty()) return null;

            PacketUpdateAttributes.Operation mapped;
            switch (operation.toString()) {
                case "ADD_NUMBER":
                    mapped = PacketUpdateAttributes.Operation.ADDITION;
                    break;
                case "ADD_SCALAR":
                    mapped = PacketUpdateAttributes.Operation.MULTIPLY_BASE;
                    break;
                case "MULTIPLY_SCALAR_1":
                    mapped = PacketUpdateAttributes.Operation.MULTIPLY_TOTAL;
                    break;
                default:
                    return null;
            }
            return new PacketUpdateAttributes.Modifier(stableId, name, amount, mapped);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String invokeText(Object target, String method) {
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static Double getAttackSpeed(Player player) {
        if (!ServerVersion.HAS_GENERIC_ATTACK_SPEED) return null;
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object attribute = Enum.valueOf((Class<Enum>) Class.forName("org.bukkit.attribute.Attribute"), "GENERIC_ATTACK_SPEED");
            Object attributeInstance = player.getClass().getMethod("getAttribute", Class.forName("org.bukkit.attribute.Attribute")).invoke(player, attribute);
            if (attributeInstance != null) {
                return (Double) attributeInstance.getClass().getMethod("getValue").invoke(attributeInstance);
            }
        } catch (Exception | LinkageError ignored) {}
        return null;
    }

    public static Double getMovementSpeed(Player player) {
        return readAttributeOptional(player, "MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
    }

    public static Double getInteractionRange(Player player) {
        if (!ServerVersion.HAS_ENTITY_INTERACTION_RANGE) return null;
        return readAttribute(player, "PLAYER_ENTITY_INTERACTION_RANGE");
    }

    public static Double getKnockbackResistance(Player player) {
        Double value = readAttribute(player, "GENERIC_KNOCKBACK_RESISTANCE");
        if (value == null) {
            value = readAttribute(player, "KNOCKBACK_RESISTANCE");
        }
        double equippedValue = equippedKnockbackResistance(player);
        if (value == null) {
            return equippedValue;
        }
        return Math.max(value, equippedValue);
    }

    private static Double readAttribute(Player player, String name) {
        try {
            Object attributeInstance = readAttributeInstance(player, name);
            if (attributeInstance != null) {
                // Use getBaseValue() instead of getValue() to avoid double-counting active potion effects
                // that are already simulated engine-side by Zeus.
                return (Double) attributeInstance.getClass().getMethod("getBaseValue").invoke(attributeInstance);
            }
        } catch (Exception | LinkageError ignored) {}
        return null;
    }

    private static double equippedKnockbackResistance(Player player) {
        double total = 0.0;
        total += itemKnockbackResistance(player.getInventory().getHelmet());
        total += itemKnockbackResistance(player.getInventory().getChestplate());
        total += itemKnockbackResistance(player.getInventory().getLeggings());
        total += itemKnockbackResistance(player.getInventory().getBoots());
        return Math.max(0.0, Math.min(1.0, total));
    }

    private static double itemKnockbackResistance(ItemStack item) {
        if (item == null || item.getType() == null) {
            return 0.0;
        }

        double value = materialKnockbackResistance(item);
        Double modifierValue = readItemAttributeModifier(item, "GENERIC_KNOCKBACK_RESISTANCE");
        if (modifierValue == null) {
            modifierValue = readItemAttributeModifier(item, "KNOCKBACK_RESISTANCE");
        }
        if (modifierValue != null) {
            value += modifierValue;
        }
        return value;
    }

    private static double materialKnockbackResistance(ItemStack item) {
        String material = item.getType().name();
        if (material.startsWith("NETHERITE_")
                && (material.endsWith("_HELMET")
                || material.endsWith("_CHESTPLATE")
                || material.endsWith("_LEGGINGS")
                || material.endsWith("_BOOTS"))) {
            return 0.1;
        }
        return 0.0;
    }

    private static Double readItemAttributeModifier(ItemStack item, String attributeName) {
        try {
            if (!item.hasItemMeta() || item.getItemMeta() == null) {
                return null;
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            Object attribute = Enum.valueOf((Class<Enum>) Class.forName("org.bukkit.attribute.Attribute"), attributeName);
            Method getAttributeModifiers = item.getItemMeta().getClass()
                    .getMethod("getAttributeModifiers", Class.forName("org.bukkit.attribute.Attribute"));
            Object modifiers = getAttributeModifiers.invoke(item.getItemMeta(), attribute);
            if (modifiers == null) {
                return null;
            }

            Collection<?> values;
            if (modifiers instanceof Collection<?>) {
                values = (Collection<?>) modifiers;
            } else if (modifiers instanceof Iterable<?>) {
                java.util.ArrayList<Object> list = new java.util.ArrayList<>();
                for (Object value : (Iterable<?>) modifiers) {
                    list.add(value);
                }
                values = list;
            } else {
                return null;
            }

            double total = 0.0;
            for (Object modifier : values) {
                total += modifierAmount(modifier);
            }
            return total;
        } catch (Exception | NoSuchMethodError ignored) {
            return null;
        }
    }

    public static Double getGravity(Player player) {
        return readAttributeOptional(player, "GRAVITY", "GENERIC_GRAVITY");
    }

    public static Double getJumpStrength(Player player) {
        return readAttributeOptional(player, "JUMP_STRENGTH", "GENERIC_JUMP_STRENGTH");
    }

    public static Double getStepHeight(Player player) {
        return readAttributeOptional(player, "STEP_HEIGHT", "GENERIC_STEP_HEIGHT");
    }

    public static Double getScale(Player player) {
        return readAttributeOptional(player, "SCALE", "GENERIC_SCALE");
    }

    public static Double getSneakingSpeed(Player player) {
        return readAttributeOptional(player, "SNEAKING_SPEED", "GENERIC_SNEAKING_SPEED");
    }

    public static Double getMovementEfficiency(Player player) {
        return readAttributeOptional(player, "MOVEMENT_EFFICIENCY", "GENERIC_MOVEMENT_EFFICIENCY");
    }

    public static Double getWaterMovementEfficiency(Player player) {
        return readAttributeOptional(player, "WATER_MOVEMENT_EFFICIENCY", "GENERIC_WATER_MOVEMENT_EFFICIENCY");
    }

    /** Try primary name first, then fallback name. */
    private static Double readAttributeOptional(Player player, String primary, String fallback) {
        Double value = readAttribute(player, primary);
        if (value == null) {
            value = readAttribute(player, fallback);
        }
        return value;
    }

    private static double modifierAmount(Object modifier) {
        try {
            Object amount = modifier.getClass().getMethod("getAmount").invoke(modifier);
            if (amount instanceof Number) {
                return ((Number) amount).doubleValue();
            }
        } catch (Exception ignored) {
            // Keep best-effort attribute decoding.
        }
        return 0.0;
    }
}
