package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

public final class PacketPlayerDiggingBlock extends PacketBaseInfo {

    private final boolean cancelled;
    private final double x;
    private final double y;
    private final double z;

    public PacketPlayerDiggingBlock(
        long timestamp,
        String uid,
        String username,
        boolean cancelled,
        double x,
        double y,
        double z
    ) {
        super(timestamp, uid, username);
        this.cancelled = cancelled;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_DIGGING_BLOCK;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // cancelled
        ByteBufferUtil.putByte(out, (byte) (cancelled ? 1 : 0));

        // block position
        ByteBufferUtil.putDouble(out, x);
        ByteBufferUtil.putDouble(out, y);
        ByteBufferUtil.putDouble(out, z);
    }

    public boolean isCancelled() {
        return cancelled;
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
