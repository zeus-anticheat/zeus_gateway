package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Packet for vehicle steering events
 */
public final class PacketPlayerSteerVehicle extends PacketBaseInfo {

    private final float sideway;
    private final float forward;
    private final boolean jump;
    private final boolean unmount;

    public PacketPlayerSteerVehicle(long timestamp, String uid, String username,
                                    float sideway, float forward, boolean jump, boolean unmount) {
        super(timestamp, uid, username);
        this.sideway = sideway;
        this.forward = forward;
        this.jump = jump;
        this.unmount = unmount;
    }

    public float getSideway() {
        return sideway;
    }

    public float getForward() {
        return forward;
    }

    public boolean isJump() {
        return jump;
    }

    public boolean isUnmount() {
        return unmount;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_STEER_VEHICLE;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // Write sideway (4 bytes, float)
        byte[] sidewayBytes = ByteBuffer.allocate(4).putFloat(sideway).array();
        out.write(sidewayBytes);

        // Write forward (4 bytes, float)
        byte[] forwardBytes = ByteBuffer.allocate(4).putFloat(forward).array();
        out.write(forwardBytes);

        // Write jump (1 byte, boolean)
        out.write(jump ? 1 : 0);

        // Write unmount (1 byte, boolean)
        out.write(unmount ? 1 : 0);
    }

    @Override
    public String toString() {
        return "PacketPlayerSteerVehicle{" +
                "timestamp=" + getTimestamp() +
                ", uid='" + getUid() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", sideway=" + sideway +
                ", forward=" + forward +
                ", jump=" + jump +
                ", unmount=" + unmount +
                '}';
    }
}
