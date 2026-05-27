package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;
import org.vennv.utils.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Packet for inventory click events
 */
public final class PacketPlayerClickWindow extends PacketBaseInfo {

    private final byte windowId;
    private final short slotId;
    private final byte button;
    private final short mode;
    private final ItemStack itemStack;
    private final short transactionId;

    public PacketPlayerClickWindow(long timestamp, String uid, String username,
                                   byte windowId, short slotId, byte button, short mode,
                                   ItemStack itemStack, short transactionId) {
        super(timestamp, uid, username);
        this.windowId = windowId;
        this.slotId = slotId;
        this.button = button;
        this.mode = mode;
        this.itemStack = itemStack;
        this.transactionId = transactionId;
    }

    public byte getWindowId() {
        return windowId;
    }

    public short getSlotId() {
        return slotId;
    }

    public byte getButton() {
        return button;
    }

    public short getMode() {
        return mode;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public short getTransactionId() {
        return transactionId;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_CLICK_WINDOW;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // Write window ID (1 byte)
        out.write(windowId);

        // Write slot ID (2 bytes)
        out.write((slotId >> 8) & 0xFF);
        out.write(slotId & 0xFF);

        // Write button (1 byte)
        out.write(button);

        // Write mode (2 bytes)
        out.write((mode >> 8) & 0xFF);
        out.write(mode & 0xFF);

        // Write transaction ID (2 bytes)
        out.write((transactionId >> 8) & 0xFF);
        out.write(transactionId & 0xFF);

        // Write item stack
        itemStack.encode(out);
    }

    @Override
    public String toString() {
        return "PacketPlayerClickWindow{" +
                "timestamp=" + getTimestamp() +
                ", uid='" + getUid() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", windowId=" + windowId +
                ", slotId=" + slotId +
                ", button=" + button +
                ", mode=" + mode +
                ", itemStack=" + itemStack +
                ", transactionId=" + transactionId +
                '}';
    }
}
