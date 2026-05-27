package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerKeepAlive extends PacketBaseInfo {

    private final long ping;

    public PacketPlayerKeepAlive(long timestamp, String uid, String username, long ping) {
        super(timestamp, uid, username);
        this.ping = ping;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_KEEP_ALIVE;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        ByteBufferUtil.putLong(out, ping);
    }

    public long getPing() {
        return ping;
    }
}