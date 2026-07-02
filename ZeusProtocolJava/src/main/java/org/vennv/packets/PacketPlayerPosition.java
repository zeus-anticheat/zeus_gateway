package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerPosition extends PacketBaseInfo {

    public static final byte SOURCE_RAW_CLIENT = 0;
    public static final byte SOURCE_SNAPSHOT = 1;
    public static final byte SOURCE_RESYNC = 2;

    private final boolean cancelled;
    private final double x;
    private final double y;
    private final double z;
    private final double eyeX;
    private final double eyeY;
    private final double eyeZ;
    private final float yaw;
    private final float pitch;
    private final float height;
    private final boolean onGround;
    private final byte source;

    public PacketPlayerPosition(long timestamp, String uid, String username, boolean cancelled,
                                double x, double y, double z,
                                double eyeX, double eyeY, double eyeZ,
                                float yaw, float pitch, float height, boolean onGround) {
        this(timestamp, uid, username, cancelled, x, y, z, eyeX, eyeY, eyeZ, yaw, pitch, height, onGround, SOURCE_RAW_CLIENT);
    }

    public PacketPlayerPosition(long timestamp, String uid, String username, boolean cancelled,
                                double x, double y, double z,
                                double eyeX, double eyeY, double eyeZ,
                                float yaw, float pitch, float height, boolean onGround, byte source) {
        super(timestamp, uid, username);
        this.cancelled = cancelled;
        this.x = x;
        this.y = y;
        this.z = z;
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeZ = eyeZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.height = height;
        this.onGround = onGround;
        this.source = source;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_POSITION;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // cancelled
        ByteBufferUtil.putByte(out, (byte) (cancelled ? 1 : 0));

        // position (packet_xyz)
        ByteBufferUtil.putDouble(out, x);
        ByteBufferUtil.putDouble(out, y);
        ByteBufferUtil.putDouble(out, z);

        // eye position (eye_xyz)
        ByteBufferUtil.putDouble(out, eyeX);
        ByteBufferUtil.putDouble(out, eyeY);
        ByteBufferUtil.putDouble(out, eyeZ);

        // rotation
        ByteBufferUtil.putFloat(out, yaw);
        ByteBufferUtil.putFloat(out, pitch);

        // height
        ByteBufferUtil.putFloat(out, height);

        // on_ground
        ByteBufferUtil.putByte(out, (byte) (onGround ? 1 : 0));

        // source
        ByteBufferUtil.putByte(out, source);
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

    public double getEyeX() {
        return eyeX;
    }

    public double getEyeY() {
        return eyeY;
    }

    public double getEyeZ() {
        return eyeZ;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getHeight() {
        return height;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public byte getSource() {
        return source;
    }
}
