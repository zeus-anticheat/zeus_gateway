package org.vennv.zeusGateway.utils;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.vennv.utils.Armor;
import org.vennv.utils.Item;

public final class ItemUtil {
    private ItemUtil() {}

    public static org.vennv.utils.ItemStack protocolStack(ItemStack item) {
        if (item == null || isAir(item.getType())) {
            return new org.vennv.utils.ItemStack(
                    org.vennv.utils.ItemStack.EMPTY_ID,
                    0,
                    (byte) 0);
        }

        return new org.vennv.utils.ItemStack(
                materialKey(item.getType()),
                customModelData(item),
                (byte) item.getAmount());
    }

    public static Item protocolItem(ItemStack item) {
        if (item == null || isAir(item.getType())) {
            return new Item("", "", protocolStack(item));
        }
        return new Item(materialKey(item.getType()), displayName(item), protocolStack(item));
    }

    public static Armor protocolArmor(ItemStack item) {
        if (item == null || isAir(item.getType())) {
            return null;
        }
        return new Armor(materialKey(item.getType()), displayName(item), customModelData(item));
    }

    private static boolean isAir(Material material) {
        if (material == null) {
            return true;
        }
        try {
            return material.isAir();
        } catch (NoSuchMethodError ignored) {
            return material == Material.AIR;
        }
    }

    private static String materialKey(Material material) {
        try {
            NamespacedKey key = material.getKey();
            return key == null ? fallbackMaterialKey(material) : key.toString();
        } catch (Exception | NoSuchMethodError ignored) {
            return fallbackMaterialKey(material);
        }
    }

    private static String fallbackMaterialKey(Material material) {
        return "minecraft:" + material.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static int customModelData(ItemStack item) {
        try {
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasCustomModelData()) {
                    return meta.getCustomModelData();
                }
            }
        } catch (Exception | NoSuchMethodError ignored) {
            // Custom model data is unavailable on older server APIs.
        }
        return 0;
    }

    private static String displayName(ItemStack item) {
        try {
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    return meta.getDisplayName();
                }
            }
        } catch (Exception ignored) {
            // Keep empty custom name when metadata cannot be read.
        }
        return "";
    }
}
