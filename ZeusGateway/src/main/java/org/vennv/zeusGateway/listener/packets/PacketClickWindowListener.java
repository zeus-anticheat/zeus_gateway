package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.Converters;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerClickWindow;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.utils.ItemStack;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.debug.PacketDebugEnvelope;
import org.vennv.zeusGateway.platform.ServerVersion;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.utils.ItemUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PacketClickWindowListener extends PacketAdapter {
    private static final Set<String> CLICK_TYPE_NAMES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "PICKUP",
            "QUICK_MOVE",
            "SWAP",
            "CLONE",
            "THROW",
            "QUICK_CRAFT",
            "PICKUP_ALL")));

    private final ZeusGateway plugin;

    public PacketClickWindowListener(ZeusGateway plugin) {
        super(
            plugin,
            ListenerPriority.LOWEST,
            PacketType.Play.Client.WINDOW_CLICK
        );
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        StructureModifier<Integer> integers = event.getPacket().getIntegers();

        Integer window = integers.readSafely(0);
        boolean modern = ServerVersion.isAtLeast(1, 17, 1);
        int slotIndex = modern ? 2 : 1;
        int buttonIndex = modern ? 3 : 2;
        Integer slot = integers.readSafely(slotIndex);
        Integer clickButton = integers.readSafely(buttonIndex);
        Short mode = readClickMode(event);
        Short transactionId = transactionIdForVersion(modern, readLegacyActionNumber(event));

        if (window == null || slot == null || clickButton == null
                || mode == null || transactionId == null) {
            plugin.getLogger().fine(
                    "Skipping WINDOW_CLICK: unreadable fields, integers=" + integers.size()
                            + ", shorts=" + event.getPacket().getShorts().size()
                            + ", handle=" + event.getPacket().getHandle().getClass().getName()
            );
            return;
        }

        org.bukkit.inventory.ItemStack bukkitItem = null;
        try {
            StructureModifier<org.bukkit.inventory.ItemStack> itemModifier =
                    event.getPacket().getItemModifier();
            if (itemModifier.size() > 0) {
                bukkitItem = itemModifier.read(0);
            }
        } catch (Exception ignored) {
            // Item data may not be available in all packet versions.
        }

        ItemStack itemStack = ItemUtil.protocolStack(bukkitItem);

        PacketPlayerClickWindow packet = new PacketPlayerClickWindow(
            timestamp,
            uid,
            name,
            window.byteValue(),
            slot.shortValue(),
            clickButton.byteValue(),
            mode,
            itemStack,
            transactionId
        );
        PacketQueue.push(new PacketDebugEnvelope(packet, readRawInventoryChanges(event, modern, integers)));

        PacketPlayerInventoryTransaction transaction = readInventoryTransaction(
                event,
                modern,
                integers,
                timestamp,
                uid,
                name,
                window.byteValue(),
                slot.shortValue(),
                clickButton.byteValue(),
                mode,
                transactionId);
        if (transaction != null) {
            PacketQueue.push(transaction);
        }
    }

    static Short transactionIdForVersion(boolean modern, Short legacyActionNumber) {
        return modern ? (short) 0 : legacyActionNumber;
    }

    private Short readLegacyActionNumber(PacketEvent event) {
        try {
            return event.getPacket().getShorts().readSafely(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Short readClickMode(PacketEvent event) {
        if (!ServerVersion.isAtLeast(1, 9)) {
            Integer legacyMode = event.getPacket().getIntegers().readSafely(3);
            return legacyMode == null ? null : legacyMode.shortValue();
        }

        Object handle = event.getPacket().getHandle();
        for (Field field : handle.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(handle);
                if (value instanceof Enum<?> && isInventoryClickType((Enum<?>) value)) {
                    return (short) ((Enum<?>) value).ordinal();
                }
            } catch (Exception ignored) {
                // Continue scanning other fields.
            }
        }
        return null;
    }

    private String readRawInventoryChanges(
            PacketEvent event, boolean modern, StructureModifier<Integer> integers) {
        if (!modern) {
            return "";
        }

        Integer stateId = integers.readSafely(1);
        StringBuilder details = new StringBuilder(" rawState=")
                .append(stateId == null ? "?" : stateId);
        try {
            Map<Integer, org.bukkit.inventory.ItemStack> changed = event.getPacket()
                    .getMaps(Converters.passthrough(Integer.class), BukkitConverters.getItemStackConverter())
                    .readSafely(0);
            if (changed != null && !changed.isEmpty()) {
                String slots = changed.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                        .limit(4)
                        .map(entry -> entry.getKey() + "=" + stackSummary(entry.getValue()))
                        .collect(Collectors.joining(","));
                details.append(" rawChanged=[").append(slots);
                if (changed.size() > 4) {
                    details.append(",+").append(changed.size() - 4);
                }
                details.append(']');
            }
        } catch (Exception ignored) {
            // Changed slots are optional diagnostic context on readable modern layouts.
        }
        return details.toString();
    }

    private String stackSummary(org.bukkit.inventory.ItemStack item) {
        ItemStack stack = ItemUtil.protocolStack(item);
        return (stack.isEmpty() ? "empty" : stack.getId())
                + "x" + Byte.toUnsignedInt(stack.getCount());
    }

    private PacketPlayerInventoryTransaction readInventoryTransaction(
            PacketEvent event,
            boolean modern,
            StructureModifier<Integer> integers,
            long timestamp,
            String uid,
            String name,
            byte windowId,
            short clickedSlot,
            byte button,
            short mode,
            short transactionId) {
        if (!modern) {
            return null;
        }

        Integer stateId = integers.readSafely(1);
        ItemStack cursorStack = readCursorStack(event);
        List<PacketPlayerInventoryTransaction.ChangedSlot> changedSlots =
                readChangedSlots(event);

        if (stateId == null && cursorStack == null && changedSlots.isEmpty()) {
            return null;
        }

        return new PacketPlayerInventoryTransaction(
                timestamp,
                uid,
                name,
                windowId,
                stateId == null ? -1 : stateId,
                clickedSlot,
                button,
                mode,
                transactionId,
                cursorStack == null
                        ? new ItemStack(ItemStack.EMPTY_ID, 0, (byte) 0)
                        : cursorStack,
                changedSlots);
    }

    private ItemStack readCursorStack(PacketEvent event) {
        try {
            StructureModifier<org.bukkit.inventory.ItemStack> itemModifier =
                    event.getPacket().getItemModifier();
            if (itemModifier.size() > 0) {
                return ItemUtil.protocolStack(itemModifier.read(0));
            }
        } catch (Exception ignored) {
            // Cursor/carried stack is not present on every protocol layout.
        }
        return null;
    }

    private List<PacketPlayerInventoryTransaction.ChangedSlot> readChangedSlots(PacketEvent event) {
        try {
            Map<Integer, org.bukkit.inventory.ItemStack> changed = event.getPacket()
                    .getMaps(Converters.passthrough(Integer.class), BukkitConverters.getItemStackConverter())
                    .readSafely(0);
            if (changed == null || changed.isEmpty()) {
                return Collections.emptyList();
            }
            List<PacketPlayerInventoryTransaction.ChangedSlot> slots = new ArrayList<>(changed.size());
            changed.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .forEach(entry -> slots.add(new PacketPlayerInventoryTransaction.ChangedSlot(
                            entry.getKey().shortValue(),
                            ItemUtil.protocolStack(entry.getValue()))));
            return slots;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private boolean isInventoryClickType(Enum<?> enumValue) {
        if (CLICK_TYPE_NAMES.contains(enumValue.name())) {
            return true;
        }
        for (Object constant : enumValue.getDeclaringClass().getEnumConstants()) {
            if (constant instanceof Enum<?>
                    && CLICK_TYPE_NAMES.contains(((Enum<?>) constant).name())) {
                return true;
            }
        }
        return false;
    }
}
