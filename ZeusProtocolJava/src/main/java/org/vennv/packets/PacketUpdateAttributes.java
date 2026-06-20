package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketUpdateAttributes extends PacketBaseInfo {

    private final float movementSpeed;

    public PacketUpdateAttributes(long timestamp, String uid, String username, float movementSpeed) {
        super(timestamp, uid, username);
        this.movementSpeed = movementSpeed;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_UPDATE_ATTRIBUTES;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        writeFloat(out, movementSpeed);
    }

    public float getMovementSpeed() {
        return movementSpeed;
    }

    private static void writeFloat(ByteArrayOutputStream out, float v) throws IOException {
        int bits = Float.floatToRawIntBits(v);
        out.write(bits & 0xFF);
        out.write((bits >> 8) & 0xFF);
        out.write((bits >> 16) & 0xFF);
        out.write((bits >> 24) & 0xFF);
    }
}
