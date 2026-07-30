package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerTeleport extends PacketBaseInfo {

    /**
     * High-level platform teleport event. The position is already applied
     * server-side, so the engine reanchors immediately.
     */
    public static final byte SOURCE_SERVER_EVENT = 0;
    /**
     * Raw outbound {@code PLAYER_POSITION_AND_LOOK}. The client keeps moving
     * from its old position until it processes the teleport, then echoes this
     * exact position back. The engine queues it and waits for that echo.
     */
    public static final byte SOURCE_OUTBOUND_PACKET = 1;

    private static final byte METADATA_VERSION = 1;

    private final double x;
    private final double y;
    private final double z;
    private final byte source;
    private final int teleportId;
    private final boolean hasMetadata;

    public PacketPlayerTeleport(long timestamp, String uid, String username, double x, double y, double z) {
        super(timestamp, uid, username);
        this.x = x;
        this.y = y;
        this.z = z;
        this.source = SOURCE_SERVER_EVENT;
        this.teleportId = 0;
        this.hasMetadata = false;
    }

    public PacketPlayerTeleport(
            long timestamp,
            String uid,
            String username,
            double x,
            double y,
            double z,
            byte source,
            int teleportId) {
        super(timestamp, uid, username);
        if (source != SOURCE_SERVER_EVENT && source != SOURCE_OUTBOUND_PACKET) {
            throw new IllegalArgumentException("Invalid teleport source: " + source);
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.source = source;
        this.teleportId = teleportId;
        this.hasMetadata = true;
    }

    /** Outbound {@code PLAYER_POSITION_AND_LOOK} awaiting the client echo. */
    public static PacketPlayerTeleport outbound(
            long timestamp,
            String uid,
            String username,
            double x,
            double y,
            double z,
            int teleportId) {
        return new PacketPlayerTeleport(
                timestamp, uid, username, x, y, z, SOURCE_OUTBOUND_PACKET, teleportId);
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_TELEPORT;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putDouble(out, x);
        ByteBufferUtil.putDouble(out, y);
        ByteBufferUtil.putDouble(out, z);
        // Trailing and optional: legacy engines stop reading after the position.
        if (hasMetadata) {
            ByteBufferUtil.putByte(out, METADATA_VERSION);
            ByteBufferUtil.putByte(out, source);
            ByteBufferUtil.putInt(out, teleportId);
        }
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

    public byte getSource() {
        return source;
    }

    public int getTeleportId() {
        return teleportId;
    }

    public boolean awaitsClientConfirmation() {
        return source == SOURCE_OUTBOUND_PACKET;
    }
}
