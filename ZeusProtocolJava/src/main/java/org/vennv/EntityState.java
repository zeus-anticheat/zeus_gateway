package org.vennv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class EntityState {

    private final String eid;
    private final double packetX;
    private final double packetY;
    private final double packetZ;
    private final double eyeX;
    private final double eyeY;
    private final double eyeZ;
    private final float yaw;
    private final float pitch;
    private final float height;
    private final float width;
    private final boolean onGround;

    public EntityState(String eid, double packetX, double packetY, double packetZ,
                       double eyeX, double eyeY, double eyeZ,
                       float yaw, float pitch, float height, float width, boolean onGround) {
        this.eid = eid;
        this.packetX = packetX;
        this.packetY = packetY;
        this.packetZ = packetZ;
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeZ = eyeZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.height = height;
        this.width = width;
        this.onGround = onGround;
    }

    public void encode(ByteArrayOutputStream out) throws IOException {
        byte[] eidBytes = eid.getBytes(StandardCharsets.UTF_8);
        ByteBufferUtil.putShort(out, (short) eidBytes.length);
        ByteBufferUtil.putBytes(out, eidBytes);

        ByteBufferUtil.putDouble(out, eyeX);
        ByteBufferUtil.putDouble(out, eyeY);
        ByteBufferUtil.putDouble(out, eyeZ);

        ByteBufferUtil.putFloat(out, yaw);
        ByteBufferUtil.putFloat(out, pitch);

        ByteBufferUtil.putFloat(out, height);
        ByteBufferUtil.putFloat(out, width);

        ByteBufferUtil.putDouble(out, packetX);
        ByteBufferUtil.putDouble(out, packetY);
        ByteBufferUtil.putDouble(out, packetZ);

        ByteBufferUtil.putByte(out, (byte) (onGround ? 1 : 0));
    }

    public String getEid() {
        return eid;
    }

    public double getPacketX() {
        return packetX;
    }

    public double getPacketY() {
        return packetY;
    }

    public double getPacketZ() {
        return packetZ;
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

    public float getWidth() {
        return width;
    }

    public boolean isOnGround() {
        return onGround;
    }
}
