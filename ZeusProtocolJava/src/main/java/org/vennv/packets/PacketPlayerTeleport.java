package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerTeleport extends PacketBaseInfo {

    private final double x;
    private final double y;
    private final double z;

    public PacketPlayerTeleport(long timestamp, String uid, String username, double x, double y, double z) {
        super(timestamp, uid, username);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_TELEPORT;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putDouble(out, x);
        ByteBufferUtil.putDouble(out, y);
        ByteBufferUtil.putDouble(out, z);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }
}
