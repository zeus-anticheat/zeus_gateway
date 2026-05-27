package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBase;
import org.vennv.PacketEncode;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketTPSServer implements PacketBase, PacketEncode {

    private final double tps;

    public PacketTPSServer(double tps) {
        this.tps = tps;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_TPS_SERVER;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        ByteBufferUtil.putByte(out, packetId());
        ByteBufferUtil.putDouble(out, tps);
    }

    public double getTps() {
        return tps;
    }
}
