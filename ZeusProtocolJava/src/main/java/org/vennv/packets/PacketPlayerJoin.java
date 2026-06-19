package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerJoin extends PacketBaseInfo {

    public PacketPlayerJoin(long timestamp, String uid, String username) {
       super(timestamp, uid, username);
    }

    public PacketPlayerJoin(long timestamp, String uid, String username, int protocolVersion) {
       super(timestamp, uid, username, protocolVersion);
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_JOIN; // 0x01
    }


    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
    }
}
