package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

public final class PacketPlayerAbilities extends PacketBaseInfo {
    public enum Source { SERVER, CLIENT }

    private final long sequence;
    private final Source source;
    private final boolean canFly;
    private final boolean flying;
    private final float flySpeed;

    private PacketPlayerAbilities(
            long timestamp, String uid, String username, long sequence, Source source,
            boolean canFly, boolean flying, float flySpeed) {
        super(timestamp, uid, username);
        if (sequence <= 0) throw new IllegalArgumentException("abilities sequence must be positive");
        if (source == null) throw new IllegalArgumentException("abilities source is required");
        if (source == Source.SERVER) {
            if (!Float.isFinite(flySpeed) || flySpeed < 0.0f || flySpeed > 1.0f) {
                throw new IllegalArgumentException("abilities fly speed is out of range");
            }
            if (flying && !canFly) {
                throw new IllegalArgumentException("server cannot authorize flying without can-fly");
            }
        }
        this.sequence = sequence;
        this.source = source;
        this.canFly = canFly;
        this.flying = flying;
        this.flySpeed = flySpeed;
    }

    public static PacketPlayerAbilities server(
            long timestamp, String uid, String username, long sequence,
            boolean canFly, boolean flying, float flySpeed) {
        return new PacketPlayerAbilities(
                timestamp, uid, username, sequence, Source.SERVER,
                canFly, flying, flySpeed);
    }

    public static PacketPlayerAbilities client(
            long timestamp, String uid, String username, long sequence, boolean flying) {
        return new PacketPlayerAbilities(
                timestamp, uid, username, sequence, Source.CLIENT,
                false, flying, 0.0f);
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_ABILITIES;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putLong(out, sequence);
        ByteBufferUtil.putByte(out, (byte) (source == Source.SERVER ? 0 : 1));
        int flags = (canFly ? 1 : 0) | (flying ? 2 : 0);
        ByteBufferUtil.putByte(out, (byte) flags);
        if (source == Source.SERVER) ByteBufferUtil.putFloat(out, flySpeed);
    }

    public long getSequence() { return sequence; }
    public Source getSource() { return source; }
    public boolean canFly() { return canFly; }
    public boolean isFlying() { return flying; }
    public float getFlySpeed() { return flySpeed; }
}
