package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;
import org.vennv.utils.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extended inventory transaction details for modern container clicks.
 *
 * This packet is additive: {@link PacketPlayerClickWindow} remains the stable
 * event used by existing analysis code, while this packet carries the state id,
 * cursor stack and changed-slot map when the platform exposes them.
 */
public final class PacketPlayerInventoryTransaction extends PacketBaseInfo {
    private final byte windowId;
    private final int stateId;
    private final short clickedSlot;
    private final byte button;
    private final short mode;
    private final short transactionId;
    private final ItemStack cursorStack;
    private final List<ChangedSlot> changedSlots;

    public PacketPlayerInventoryTransaction(
            long timestamp,
            String uid,
            String username,
            byte windowId,
            int stateId,
            short clickedSlot,
            byte button,
            short mode,
            short transactionId,
            ItemStack cursorStack,
            List<ChangedSlot> changedSlots) {
        super(timestamp, uid, username);
        this.windowId = windowId;
        this.stateId = stateId;
        this.clickedSlot = clickedSlot;
        this.button = button;
        this.mode = mode;
        this.transactionId = transactionId;
        this.cursorStack = cursorStack == null
                ? new ItemStack(ItemStack.EMPTY_ID, 0, (byte) 0)
                : cursorStack;
        if (changedSlots == null || changedSlots.isEmpty()) {
            this.changedSlots = Collections.emptyList();
        } else {
            List<ChangedSlot> normalized = new ArrayList<>(changedSlots.size());
            for (ChangedSlot changedSlot : changedSlots) {
                if (changedSlot == null) {
                    continue;
                }
                ItemStack stack = changedSlot.itemStack() == null
                        ? new ItemStack(ItemStack.EMPTY_ID, 0, (byte) 0)
                        : changedSlot.itemStack();
                normalized.add(new ChangedSlot(changedSlot.slot(), stack));
            }
            this.changedSlots = Collections.unmodifiableList(normalized);
        }
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_INVENTORY_TRANSACTION;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putByte(out, windowId);
        ByteBufferUtil.putInt(out, stateId);
        ByteBufferUtil.putShort(out, clickedSlot);
        ByteBufferUtil.putByte(out, button);
        ByteBufferUtil.putShort(out, mode);
        ByteBufferUtil.putShort(out, transactionId);
        cursorStack.encode(out);

        ByteBufferUtil.putInt(out, changedSlots.size());
        for (ChangedSlot changedSlot : changedSlots) {
            ByteBufferUtil.putShort(out, changedSlot.slot());
            changedSlot.itemStack().encode(out);
        }
    }

    public byte getWindowId() {
        return windowId;
    }

    public int getStateId() {
        return stateId;
    }

    public short getClickedSlot() {
        return clickedSlot;
    }

    public byte getButton() {
        return button;
    }

    public short getMode() {
        return mode;
    }

    public short getTransactionId() {
        return transactionId;
    }

    public ItemStack getCursorStack() {
        return cursorStack;
    }

    public List<ChangedSlot> getChangedSlots() {
        return changedSlots;
    }

    public static final class ChangedSlot {
        private final short slot;
        private final ItemStack itemStack;

        public ChangedSlot(short slot, ItemStack itemStack) {
            this.slot = slot;
            this.itemStack = itemStack;
        }

        public short slot() {
            return slot;
        }

        public ItemStack itemStack() {
            return itemStack;
        }
    }
}
