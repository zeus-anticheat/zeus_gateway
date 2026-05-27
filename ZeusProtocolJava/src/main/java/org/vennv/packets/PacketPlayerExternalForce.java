package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;
import org.vennv.utils.ExternalForceType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerExternalForce extends PacketBaseInfo {
    private final ExternalForceType forceType;
    private final double sourceX;
    private final double sourceY;
    private final double sourceZ;
    private final double dirX;
    private final double dirY;
    private final double dirZ;
    private final double velocityX;
    private final double velocityY;
    private final double velocityZ;
    private final double strength;
    private final short durationTicks;
    private final int flags;

    public PacketPlayerExternalForce(
            long timestamp,
            String uid,
            String username,
            ExternalForceType forceType,
            double sourceX,
            double sourceY,
            double sourceZ,
            double dirX,
            double dirY,
            double dirZ,
            double velocityX,
            double velocityY,
            double velocityZ,
            double strength,
            short durationTicks,
            int flags) {
        super(timestamp, uid, username);
        this.forceType = forceType == null ? ExternalForceType.GENERIC : forceType;
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.sourceZ = sourceZ;
        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.strength = strength;
        this.durationTicks = durationTicks;
        this.flags = flags;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_EXTERNAL_FORCE;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putByte(out, (byte) forceType.getValue());
        ByteBufferUtil.putDouble(out, sourceX);
        ByteBufferUtil.putDouble(out, sourceY);
        ByteBufferUtil.putDouble(out, sourceZ);
        ByteBufferUtil.putDouble(out, dirX);
        ByteBufferUtil.putDouble(out, dirY);
        ByteBufferUtil.putDouble(out, dirZ);
        ByteBufferUtil.putDouble(out, velocityX);
        ByteBufferUtil.putDouble(out, velocityY);
        ByteBufferUtil.putDouble(out, velocityZ);
        ByteBufferUtil.putDouble(out, strength);
        ByteBufferUtil.putShort(out, durationTicks);
        ByteBufferUtil.putInt(out, flags);
    }

    public ExternalForceType getForceType() { return forceType; }
    public double getSourceX() { return sourceX; }
    public double getSourceY() { return sourceY; }
    public double getSourceZ() { return sourceZ; }
    public double getDirX() { return dirX; }
    public double getDirY() { return dirY; }
    public double getDirZ() { return dirZ; }
    public double getVelocityX() { return velocityX; }
    public double getVelocityY() { return velocityY; }
    public double getVelocityZ() { return velocityZ; }
    public double getStrength() { return strength; }
    public short getDurationTicks() { return durationTicks; }
    public int getFlags() { return flags; }
}
