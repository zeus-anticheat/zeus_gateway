package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBase;
import org.vennv.PacketEncode;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPhysicsCaptureSample implements PacketBase, PacketEncode {

    public static final byte SAMPLE_SCHEMA_VERSION = 1;

    private final long timestamp;
    private final byte sampleSchemaVersion;
    private final int serverProtocol;
    private final int clientProtocol;
    private final long playerHash;
    private final double posBeforeX;
    private final double posBeforeY;
    private final double posBeforeZ;
    private final float posAfterDx;
    private final float posAfterDy;
    private final float posAfterDz;
    private final float velX;
    private final float velY;
    private final float velZ;
    private final float baseSpeed;
    private final byte inputFlags;
    private final int supportBlockId;
    private final int frictionBlockId;
    private final byte surfaceCategory;
    private final boolean bodyInWater;
    private final boolean eyeInWater;
    private final boolean inLava;
    private final byte effectLevels;
    private final byte tickDurationMs;
    private final byte worldDimension;
    private final byte simErrorPct;

    public PacketPhysicsCaptureSample(
            long timestamp,
            int serverProtocol, int clientProtocol, long playerHash,
            double posBeforeX, double posBeforeY, double posBeforeZ,
            float posAfterDx, float posAfterDy, float posAfterDz,
            float velX, float velY, float velZ,
            float baseSpeed, byte inputFlags,
            int supportBlockId, int frictionBlockId, byte surfaceCategory,
            boolean bodyInWater, boolean eyeInWater, boolean inLava,
            byte effectLevels, byte tickDurationMs, byte worldDimension, byte simErrorPct) {
        this.timestamp = timestamp;
        this.sampleSchemaVersion = SAMPLE_SCHEMA_VERSION;
        this.serverProtocol = serverProtocol;
        this.clientProtocol = clientProtocol;
        this.playerHash = playerHash;
        this.posBeforeX = posBeforeX;
        this.posBeforeY = posBeforeY;
        this.posBeforeZ = posBeforeZ;
        this.posAfterDx = posAfterDx;
        this.posAfterDy = posAfterDy;
        this.posAfterDz = posAfterDz;
        this.velX = velX;
        this.velY = velY;
        this.velZ = velZ;
        this.baseSpeed = baseSpeed;
        this.inputFlags = inputFlags;
        this.supportBlockId = supportBlockId;
        this.frictionBlockId = frictionBlockId;
        this.surfaceCategory = surfaceCategory;
        this.bodyInWater = bodyInWater;
        this.eyeInWater = eyeInWater;
        this.inLava = inLava;
        this.effectLevels = effectLevels;
        this.tickDurationMs = tickDurationMs;
        this.worldDimension = worldDimension;
        this.simErrorPct = simErrorPct;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PHYSICS_CAPTURE_SAMPLE;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        ByteBufferUtil.putByte(out, packetId());
        ByteBufferUtil.putLong(out, timestamp);
        ByteBufferUtil.putByte(out, sampleSchemaVersion);
        ByteBufferUtil.putShort(out, (short) serverProtocol);
        ByteBufferUtil.putShort(out, (short) clientProtocol);
        ByteBufferUtil.putLong(out, playerHash);
        ByteBufferUtil.putDouble(out, posBeforeX);
        ByteBufferUtil.putDouble(out, posBeforeY);
        ByteBufferUtil.putDouble(out, posBeforeZ);
        ByteBufferUtil.putFloat(out, posAfterDx);
        ByteBufferUtil.putFloat(out, posAfterDy);
        ByteBufferUtil.putFloat(out, posAfterDz);
        ByteBufferUtil.putFloat(out, velX);
        ByteBufferUtil.putFloat(out, velY);
        ByteBufferUtil.putFloat(out, velZ);
        ByteBufferUtil.putFloat(out, baseSpeed);
        ByteBufferUtil.putByte(out, inputFlags);
        // BlockContext
        ByteBufferUtil.putShort(out, (short) supportBlockId);
        ByteBufferUtil.putShort(out, (short) frictionBlockId);
        ByteBufferUtil.putByte(out, surfaceCategory);
        // FluidState
        byte fluid = 0;
        if (bodyInWater) fluid |= 0x01;
        if (eyeInWater) fluid |= 0x02;
        if (inLava) fluid |= 0x04;
        ByteBufferUtil.putByte(out, fluid);
        ByteBufferUtil.putByte(out, effectLevels);
        ByteBufferUtil.putByte(out, tickDurationMs);
        out.write(worldDimension); // i8 raw byte
        ByteBufferUtil.putByte(out, simErrorPct);
    }

    // Getters
    public long getTimestamp() { return timestamp; }
    public byte getSampleSchemaVersion() { return sampleSchemaVersion; }
    public int getServerProtocol() { return serverProtocol; }
    public int getClientProtocol() { return clientProtocol; }
    public long getPlayerHash() { return playerHash; }
    public double getPosBeforeX() { return posBeforeX; }
    public double getPosBeforeY() { return posBeforeY; }
    public double getPosBeforeZ() { return posBeforeZ; }
    public float getPosAfterDx() { return posAfterDx; }
    public float getPosAfterDy() { return posAfterDy; }
    public float getPosAfterDz() { return posAfterDz; }
    public float getVelX() { return velX; }
    public float getVelY() { return velY; }
    public float getVelZ() { return velZ; }
    public float getBaseSpeed() { return baseSpeed; }
    public byte getInputFlags() { return inputFlags; }
    public int getSupportBlockId() { return supportBlockId; }
    public int getFrictionBlockId() { return frictionBlockId; }
    public byte getSurfaceCategory() { return surfaceCategory; }
    public boolean isBodyInWater() { return bodyInWater; }
    public boolean isEyeInWater() { return eyeInWater; }
    public boolean isInLava() { return inLava; }
    public byte getEffectLevels() { return effectLevels; }
    public byte getTickDurationMs() { return tickDurationMs; }
    public byte getWorldDimension() { return worldDimension; }
    public byte getSimErrorPct() { return simErrorPct; }
}
