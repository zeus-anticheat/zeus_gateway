package org.vennv.zeusFabric.utils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.vennv.utils.Armor;
import org.vennv.utils.Item;

public final class ItemUtil {

    private ItemUtil() {}

    public static org.vennv.utils.ItemStack protocolStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new org.vennv.utils.ItemStack(
                org.vennv.utils.ItemStack.EMPTY_ID,
                0,
                (byte) 0
            );
        }

        return new org.vennv.utils.ItemStack(
            stableId(stack),
            0,
            (byte) stack.getCount()
        );
    }

    public static Item item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new Item("", "", protocolStack(stack));
        }

        return new Item(stableId(stack), customName(stack), protocolStack(stack));
    }

    public static Armor armor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        return new Armor(stableId(stack), customName(stack), 0);
    }

    private static String stableId(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static String customName(ItemStack stack) {
        if (!stack.contains(DataComponentTypes.CUSTOM_NAME)) {
            return "";
        }
        return stack.getName().getString();
    }
}
