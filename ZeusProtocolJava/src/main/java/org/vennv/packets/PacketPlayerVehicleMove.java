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
    public static final int FLAG_MOUNTED = 1;
    public static final int FLAG_IN_WATER = 1 << 1;
    public static final int FLAG_ON_GROUND = 1 << 2;

    private final float pitch;
    private final String vehicleType;
    private final int vehicleId;
    private final int vehicleFlags;
    private final Float horseMovementSpeed;
    private final Double horseJumpStrength;
    private final boolean horseSaddleKnown;
    private final boolean horseSaddled;

    public PacketPlayerVehicleMove(long timestamp, String uid, String username,
                                   double x, double y, double z,
                                   float yaw, float pitch) {
        this(timestamp, uid, username, x, y, z, yaw, pitch, "", -1, 0, null, null, false, false);
    }

    public PacketPlayerVehicleMove(long timestamp, String uid, String username,
                                   double x, double y, double z,
                                   float yaw, float pitch,
                                   String vehicleType, int vehicleId, int vehicleFlags) {
        this(timestamp, uid, username, x, y, z, yaw, pitch, vehicleType, vehicleId, vehicleFlags,
                null, null, false, false);
    }

    public PacketPlayerVehicleMove(long timestamp, String uid, String username,
                                   double x, double y, double z,
                                   float yaw, float pitch,
                                   String vehicleType, int vehicleId, int vehicleFlags,
                                   Float horseMovementSpeed, Double horseJumpStrength,
                                   boolean horseSaddleKnown, boolean horseSaddled) {
        super(timestamp, uid, username);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.vehicleType = vehicleType == null ? "" : vehicleType;
        this.vehicleId = vehicleId;
        this.vehicleFlags = vehicleFlags & 0xFF;
        this.horseMovementSpeed = validHorseSpeed(horseMovementSpeed) ? horseMovementSpeed : null;
        this.horseJumpStrength = validHorseJump(horseJumpStrength) ? horseJumpStrength : null;
        this.horseSaddleKnown = horseSaddleKnown;
        this.horseSaddled = horseSaddleKnown && horseSaddled;
    }

    private static boolean validHorseSpeed(Float value) {
        return value != null && Float.isFinite(value) && value >= 0.0f && value <= 1024.0f;
    }

    private static boolean validHorseJump(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 && value <= 32.0;
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

    public String getVehicleType() {
        return vehicleType;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public int getVehicleFlags() {
        return vehicleFlags;
    }

    public Float getHorseMovementSpeed() {
        return horseMovementSpeed;
    }

    public Double getHorseJumpStrength() {
        return horseJumpStrength;
    }

    public boolean isHorseSaddleKnown() {
        return horseSaddleKnown;
    }

    public boolean isHorseSaddled() {
        return horseSaddled;
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

        // Server-observed mount identity. Rust accepts legacy packets which
        // omit this trailing extension, but never trusts them for boat checks.
        byte[] typeBytes = vehicleType.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int typeLength = Math.min(typeBytes.length, 65535);
        out.write((typeLength >> 8) & 0xFF);
        out.write(typeLength & 0xFF);
        out.write(typeBytes, 0, typeLength);
        ByteBufferUtil.putInt(out, vehicleId);
        out.write(vehicleFlags);
        if (horseMovementSpeed != null && horseJumpStrength != null) {
            out.write(1); // horse telemetry version
            ByteBufferUtil.putFloat(out, horseMovementSpeed);
            ByteBufferUtil.putDouble(out, horseJumpStrength);
            int saddleFlags = horseSaddleKnown ? 1 : 0;
            if (horseSaddleKnown && horseSaddled) saddleFlags |= 2;
            out.write(saddleFlags);
        }
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
                ", vehicleType='" + vehicleType + '\'' +
                ", vehicleId=" + vehicleId +
                ", vehicleFlags=" + vehicleFlags +
                '}';
    }
}
