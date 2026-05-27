package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerSwingHand extends PacketBaseInfo {

    private final boolean cancelled;

    public PacketPlayerSwingHand(long timestamp, String uid, String username, boolean cancelled) {
        super(timestamp, uid, username);
        this.cancelled = cancelled;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_SWING_HAND;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        ByteBufferUtil.putByte(out, (byte) (cancelled ? 1 : 0));
    }

    public boolean isCancelled() {
        return cancelled;
    }
}