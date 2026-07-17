package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemCustomModelData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerClickWindow;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.utils.ItemStack;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.debug.PacketDebugEnvelope;
import org.vennv.zeusGateway.provider.PacketQueue;

public class PacketClickWindowListener extends PacketListenerAbstract {
    private final OrderedPlayerPacketDispatcher dispatcher;

    public PacketClickWindowListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
        Short mode = mapClickMode(wrapper.getWindowClickType());
        if (mode == null || event.getUser().getUUID() == null || event.getUser().getName() == null) {
            return;
        }

        int window = wrapper.getWindowId();
        int slot = wrapper.getSlot();
        int button = wrapper.getButton();
        boolean modern = event.getServerVersion().isNewerThanOrEquals(
                com.github.retrooper.packetevents.manager.server.ServerVersion.V_1_17_1);
        boolean hasLegacyAction = event.getServerVersion().isOlderThan(
                com.github.retrooper.packetevents.manager.server.ServerVersion.V_1_17);
        Short transactionId = transactionIdForVersion(
                !hasLegacyAction, wrapper.getActionNumber().map(Integer::shortValue).orElse(null));
        if (transactionId == null) {
            return;
        }
        String uid = event.getUser().getUUID().toString();
        String name = event.getUser().getName();
        ItemStack itemStack = protocolStack(wrapper.getCarriedItemStack());

        PacketPlayerClickWindow packet = new PacketPlayerClickWindow(
                timestamp,
                uid,
                name,
                (byte) window,
                (short) slot,
                (byte) button,
                mode,
                itemStack,
                transactionId);
        PacketDebugEnvelope debugPacket = new PacketDebugEnvelope(
                packet, readRawInventoryChanges(wrapper, modern));
        PacketPlayerInventoryTransaction transaction = readInventoryTransaction(
                wrapper,
                modern,
                timestamp,
                uid,
                name,
                (byte) window,
                (short) slot,
                (byte) button,
                mode,
                transactionId);
        Player player = event.getPlayer();
        if (player == null) return;
        dispatcher.submit(player, () -> {
            PacketQueue.push(debugPacket);
            if (transaction != null) PacketQueue.push(transaction);
        });
    }

    static Short transactionIdForVersion(boolean modern, Short legacyActionNumber) {
        return modern ? (short) 0 : legacyActionNumber;
    }

    private Short mapClickMode(WrapperPlayClientClickWindow.WindowClickType clickType) {
        if (clickType == null) {
            return null;
        }
        switch (clickType) {
            case PICKUP:
                return 0;
            case QUICK_MOVE:
                return 1;
            case SWAP:
                return 2;
            case CLONE:
                return 3;
            case THROW:
                return 4;
            case QUICK_CRAFT:
                return 5;
            case PICKUP_ALL:
                return 6;
            default:
                return null;
        }
    }

    private String readRawInventoryChanges(WrapperPlayClientClickWindow wrapper, boolean modern) {
        if (!modern) {
            return "";
        }

        StringBuilder details = new StringBuilder(" rawState=")
                .append(wrapper.getStateId().map(String::valueOf).orElse("?"));
        Optional<Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack>> slots =
                wrapper.getSlots();
        if (slots.isPresent() && !slots.get().isEmpty()) {
            String changed = slots.get().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .limit(4)
                    .map(entry -> entry.getKey() + "=" + stackSummary(entry.getValue()))
                    .collect(Collectors.joining(","));
            details.append(" rawChanged=[").append(changed);
            if (slots.get().size() > 4) {
                details.append(",+").append(slots.get().size() - 4);
            }
            details.append(']');
        }
        return details.toString();
    }

    private String stackSummary(com.github.retrooper.packetevents.protocol.item.ItemStack item) {
        ItemStack stack = protocolStack(item);
        return (stack.isEmpty() ? "empty" : stack.getId())
                + "x" + Byte.toUnsignedInt(stack.getCount());
    }

    private PacketPlayerInventoryTransaction readInventoryTransaction(
            WrapperPlayClientClickWindow wrapper,
            boolean modern,
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

        int stateId = wrapper.getStateId().orElse(-1);
        ItemStack cursorStack = protocolStack(wrapper.getCarriedItemStack());
        List<PacketPlayerInventoryTransaction.ChangedSlot> changedSlots = readChangedSlots(wrapper);
        return new PacketPlayerInventoryTransaction(
                timestamp,
                uid,
                name,
                windowId,
                stateId,
                clickedSlot,
                button,
                mode,
                transactionId,
                cursorStack,
                changedSlots);
    }

    private List<PacketPlayerInventoryTransaction.ChangedSlot> readChangedSlots(
            WrapperPlayClientClickWindow wrapper) {
        Optional<Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack>> changed =
                wrapper.getSlots();
        if (!changed.isPresent() || changed.get().isEmpty()) {
            return Collections.emptyList();
        }
        List<PacketPlayerInventoryTransaction.ChangedSlot> slots =
                new ArrayList<>(changed.get().size());
        changed.get().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> slots.add(new PacketPlayerInventoryTransaction.ChangedSlot(
                        entry.getKey().shortValue(),
                        protocolStack(entry.getValue()))));
        return slots;
    }

    private ItemStack protocolStack(
            com.github.retrooper.packetevents.protocol.item.ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getType() == null) {
            return new ItemStack(ItemStack.EMPTY_ID, 0, (byte) 0);
        }
        ItemCustomModelData modelData = stack.getComponent(
                ComponentTypes.CUSTOM_MODEL_DATA_LISTS).orElse(null);
        int customModelData = modelData == null
                ? stack.getComponent(ComponentTypes.CUSTOM_MODEL_DATA).orElse(0)
                : modelData.getLegacyId();
        return new ItemStack(
                stack.getType().getName().toString(),
                customModelData,
                (byte) stack.getAmount());
    }
}
