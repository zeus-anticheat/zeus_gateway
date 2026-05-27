package org.vennv.zeusFabric.utils;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class MinecraftCompat {
    private MinecraftCompat() {}

    /**
     * Cross-version accessor for {@code Entity.getEntityWorld()}/{@code getWorld()}.
     * Yarn renamed the method between Minecraft versions in the 1.21.x line; reflection
     * picks whichever name the runtime exposes.
     */
    public static World entityWorld(Entity entity) {
        Object world = invokeAny(entity, "getEntityWorld", "getWorld", "method_37908");
        return world instanceof World ? (World) world : null;
    }

    public static ItemStack selectedStack(ServerPlayerEntity player) {
        Object inventory = player.getInventory();
        Object stack = invokeAny(inventory, "getSelectedStack", "getMainHandStack");
        if (stack instanceof ItemStack) {
            return (ItemStack) stack;
        }

        stack = invokeAny(player, "getMainHandStack");
        if (stack instanceof ItemStack) {
            return (ItemStack) stack;
        }

        return ItemStack.EMPTY;
    }

    public static int gameModeIndex(ServerPlayerEntity player) {
        Object gameMode = player.interactionManager.getGameMode();
        Object index = invokeAny(gameMode, "getIndex", "getId");
        if (index instanceof Number) {
            return ((Number) index).intValue();
        }

        if (gameMode instanceof Enum<?>) {
            String name = ((Enum<?>) gameMode).name();
            if ("SURVIVAL".equals(name)) {
                return 0;
            }
            if ("CREATIVE".equals(name)) {
                return 1;
            }
            if ("ADVENTURE".equals(name)) {
                return 2;
            }
            if ("SPECTATOR".equals(name)) {
                return 3;
            }
        }

        return 0;
    }

    public static int clickSyncId(ClickSlotC2SPacket packet) {
        return intValue(invokeAny(packet, "syncId", "getSyncId"));
    }

    public static int clickRevision(ClickSlotC2SPacket packet) {
        return intValue(invokeAny(packet, "revision", "getRevision"));
    }

    public static short clickSlot(ClickSlotC2SPacket packet) {
        return (short) intValue(invokeAny(packet, "slot", "getSlot"));
    }

    public static byte clickButton(ClickSlotC2SPacket packet) {
        return (byte) intValue(invokeAny(packet, "button", "getButton"));
    }

    public static short clickActionTypeIndex(ClickSlotC2SPacket packet) {
        Object actionType = invokeAny(packet, "actionType", "getActionType");
        Object index = invokeAny(actionType, "getIndex");
        if (index instanceof Number) {
            return (short) ((Number) index).intValue();
        }
        if (actionType instanceof Enum<?>) {
            return (short) ((Enum<?>) actionType).ordinal();
        }
        return 0;
    }

    public static List<Integer> clickModifiedSlotIds(ClickSlotC2SPacket packet) {
        Object modifiedStacks = invokeAny(packet, "modifiedStacks", "getModifiedStacks");
        Object keySet = invokeAny(modifiedStacks, "keySet");
        List<Integer> slots = new ArrayList<>();
        if (!(keySet instanceof Iterable<?>)) {
            return slots;
        }

        for (Object slot : (Iterable<?>) keySet) {
            if (slot instanceof Number) {
                slots.add(((Number) slot).intValue());
            }
        }
        return slots;
    }

    private static int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private static Object invokeAny(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        for (String methodName : methodNames) {
            try {
                Method method = type.getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try the next name used by another Yarn/Minecraft target.
            }
        }
        return null;
    }
}
