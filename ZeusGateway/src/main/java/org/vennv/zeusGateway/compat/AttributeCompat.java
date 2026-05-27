package org.vennv.zeusGateway.compat;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.vennv.zeusGateway.platform.ServerVersion;

import java.lang.reflect.Method;
import java.util.Collection;

public class AttributeCompat {

    public static Double getAttackSpeed(Player player) {
        if (!ServerVersion.HAS_GENERIC_ATTACK_SPEED) return null;
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object attribute = Enum.valueOf((Class<Enum>) Class.forName("org.bukkit.attribute.Attribute"), "GENERIC_ATTACK_SPEED");
            Object attributeInstance = player.getClass().getMethod("getAttribute", Class.forName("org.bukkit.attribute.Attribute")).invoke(player, attribute);
            if (attributeInstance != null) {
                return (Double) attributeInstance.getClass().getMethod("getValue").invoke(attributeInstance);
            }
        } catch (Exception ignored) {}
        return null;
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
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object attribute = Enum.valueOf((Class<Enum>) Class.forName("org.bukkit.attribute.Attribute"), name);
            Object attributeInstance = player.getClass().getMethod("getAttribute", Class.forName("org.bukkit.attribute.Attribute")).invoke(player, attribute);
            if (attributeInstance != null) {
                return (Double) attributeInstance.getClass().getMethod("getValue").invoke(attributeInstance);
            }
        } catch (Exception ignored) {}
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
