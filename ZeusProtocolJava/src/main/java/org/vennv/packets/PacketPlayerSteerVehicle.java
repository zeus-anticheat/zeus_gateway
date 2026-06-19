package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Packet for vehicle steering events.
 * vehicleType: string identifier of the vehicle entity (e.g. "pig", "strider", "boat").
 * Empty string means unknown vehicle type.
 */
public final class PacketPlayerSteerVehicle extends PacketBaseInfo {

    private final float sideway;
    private final float forward;
    private final boolean jump;
    private final boolean unmount;
    private final String vehicleType;

    public PacketPlayerSteerVehicle(long timestamp, String uid, String username,
                                   float sideway, float forward, boolean jump, boolean unmount) {
        this(timestamp, uid, username, sideway, forward, jump, unmount, "");
    }

    public PacketPlayerSteerVehicle(long timestamp, String uid, String username,
                                   float sideway, float forward, boolean jump, boolean unmount,
                                   String vehicleType) {
        super(timestamp, uid, username);
        this.sideway = sideway;
        this.forward = forward;
        this.jump = jump;
        this.unmount = unmount;
        this.vehicleType = vehicleType != null ? vehicleType : "";
    }

    public float getSideway() { return sideway; }
    public float getForward() { return forward; }
    public boolean isJump() { return jump; }
    public boolean isUnmount() { return unmount; }
    public String getVehicleType() { return vehicleType; }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_STEER_VEHICLE;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        byte[] sidewayBytes = ByteBuffer.allocate(4).putFloat(sideway).array();
        out.write(sidewayBytes);

        byte[] forwardBytes = ByteBuffer.allocate(4).putFloat(forward).array();
        out.write(forwardBytes);

        out.write(jump ? 1 : 0);
        out.write(unmount ? 1 : 0);

        // vehicleType: u16 length-prefixed UTF-8 string (matches Rust String ByteCodec)
        byte[] typeBytes = vehicleType.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int len = Math.min(typeBytes.length, 65535);
        out.write((len >> 8) & 0xFF);
        out.write(len & 0xFF);
        if (len > 0) {
            out.write(typeBytes, 0, len);
        }
    }

    @Override
    public String toString() {
        return "PacketPlayerSteerVehicle{" +
                "sideway=" + sideway +
                ", forward=" + forward +
                ", jump=" + jump +
                ", unmount=" + unmount +
                ", vehicleType='" + vehicleType + '\'' +
                '}';
    }
}
