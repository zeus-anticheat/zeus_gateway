package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerBlockFace extends PacketBaseInfo {

    private final byte face;

    public PacketPlayerBlockFace(long timestamp, String uid, String username, byte face) {
        super(timestamp, uid, username);
        this.face = face;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_BLOCK_FACE;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putByte(out, face);
    }

    public byte getFace() {
        return face;
    }
}
