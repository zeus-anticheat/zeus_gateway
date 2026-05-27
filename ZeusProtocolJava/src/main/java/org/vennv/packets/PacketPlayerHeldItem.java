package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;
import org.vennv.utils.Item;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Packet for tracking the item held by the player
 */
public final class PacketPlayerHeldItem extends PacketBaseInfo {

    private final Item item;

    public PacketPlayerHeldItem(long timestamp, String uid, String username, Item item) {
        super(timestamp, uid, username);
        this.item = item;
    }

    public Item getItem() {
        return item;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_HELD_ITEM;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        item.encode(out);
    }

    @Override
    public String toString() {
        return "PacketPlayerHeldItem{" +
                "timestamp=" + getTimestamp() +
                ", uid='" + getUid() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", item=" + item +
                '}';
    }
}
