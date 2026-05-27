package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerLeave extends PacketBaseInfo {

    public PacketPlayerLeave(long timestamp, String uid, String username) {
        super(timestamp, uid, username);
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_LEAVE; // 0x02
    }


    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
    }
}