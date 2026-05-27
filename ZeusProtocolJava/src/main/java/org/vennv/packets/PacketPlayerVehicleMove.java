package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Packet for vehicle movement events
 */
public final class PacketPlayerVehicleMove extends PacketBaseInfo {

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public PacketPlayerVehicleMove(long timestamp, String uid, String username,
                                   double x, double y, double z,
                                   float yaw, float pitch) {
        super(timestamp, uid, username);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
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

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_VEHICLE_MOVE;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // Write position (packet_xyz) - 3 doubles (24 bytes)
        ByteBufferUtil.putDouble(out, x);
        ByteBufferUtil.putDouble(out, y);
        ByteBufferUtil.putDouble(out, z);

        // Write rotation (packet_rotation) - 2 floats (8 bytes)
        ByteBufferUtil.putFloat(out, yaw);
        ByteBufferUtil.putFloat(out, pitch);
    }

    @Override
    public String toString() {
        return "PacketPlayerVehicleMove{" +
                "timestamp=" + getTimestamp() +
                ", uid='" + getUid() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", yaw=" + yaw +
                ", pitch=" + pitch +
                '}';
    }
}
