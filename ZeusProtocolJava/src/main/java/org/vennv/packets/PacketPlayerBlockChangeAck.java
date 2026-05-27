package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerBlockChangeAck extends PacketBaseInfo {

    public PacketPlayerBlockChangeAck(long timestamp, String uid, String username) {
        super(timestamp, uid, username);
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_BLOCK_CHANGE_ACK;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
    }
}
