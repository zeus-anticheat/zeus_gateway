package org.vennv.zeusGateway.compat;

import java.lang.reflect.Method;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

/**
 * Cross-version InventoryView access.
 *
 * On Paper 1.21+ {@code org.bukkit.inventory.InventoryView} is an interface;
 * on Spigot 1.8 - 1.20 (and Paper before 1.21) it is an abstract class. Code
 * compiled against the Paper 1.21+ headers emits {@code INVOKEINTERFACE}
 * bytecode for InventoryView methods, which fails with
 * {@link IncompatibleClassChangeError} when the runtime exposes a class
 * instead of an interface (and vice-versa).
 *
 * Reflection sidesteps the bytecode dispatch difference by resolving the
 * method at runtime against whichever flavour of InventoryView is loaded.
 */
public final class InventoryViewCompat {

    private InventoryViewCompat() {}

    /** Reflectively call {@code Player.getOpenInventory()}; never throws. */
    public static Object getOpenInventory(HumanEntity player) {
        if (player == null) {
            return null;
        }
        try {
            Method m = player.getClass().getMethod("getOpenInventory");
            return m.invoke(player);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Reflectively call {@code InventoryEvent.getView()}; never throws. */
    public static Object getView(InventoryEvent event) {
        if (event == null) {
            return null;
        }
        try {
            Method m = event.getClass().getMethod("getView");
            return m.invoke(event);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Reflectively call {@code InventoryView.getTopInventory()}; never throws. */
    public static Inventory getTopInventory(Object view) {
        if (view == null) {
            return null;
        }
        try {
            Method m = view.getClass().getMethod("getTopInventory");
            Object result = m.invoke(view);
            return result instanceof Inventory ? (Inventory) result : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Reflectively call {@code InventoryView.getBottomInventory()}; never throws. */
    public static Inventory getBottomInventory(Object view) {
        if (view == null) {
            return null;
        }
        try {
            Method m = view.getClass().getMethod("getBottomInventory");
            Object result = m.invoke(view);
            return result instanceof Inventory ? (Inventory) result : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Returns {@link Object#hashCode()} of the view; safe across class/interface. */
    public static int viewHashCode(Object view) {
        return view == null ? 0 : view.hashCode();
    }

    /**
     * Returns the type of the top inventory, or {@code null} when the view or
     * its top inventory is unavailable. Avoids throwing across versions.
     */
    public static InventoryType topInventoryType(Object view) {
        Inventory top = getTopInventory(view);
        if (top == null) {
            return null;
        }
        try {
            return top.getType();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Reflectively call {@code InventoryView.countSlots()}; returns 0 on error. */
    public static int countSlots(Object view) {
        if (view == null) {
            return 0;
        }
        try {
            Method m = view.getClass().getMethod("countSlots");
            Object result = m.invoke(view);
            return result instanceof Integer ? (Integer) result : 0;
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    /** Reflectively call {@code InventoryView.getItem(int)}; returns null on error. */
    public static org.bukkit.inventory.ItemStack getItem(Object view, int slot) {
        if (view == null) {
            return null;
        }
        try {
            Method m = view.getClass().getMethod("getItem", int.class);
            Object result = m.invoke(view, slot);
            return result instanceof org.bukkit.inventory.ItemStack
                    ? (org.bukkit.inventory.ItemStack) result
                    : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
