package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBase;
import org.vennv.PacketEncode;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Deprecated source compatibility wrapper for the coordinated V3 capture
 * packet.  It never writes V2 bytes: adapters still using this constructor are
 * normalized into {@link CaptureFrameV3} with explicit presence/validity bits.
 */
public final class PacketPhysicsCaptureSample implements PacketBase, PacketEncode {
    public static final byte SAMPLE_SCHEMA_VERSION = CaptureFrameV3.CAPTURE_SCHEMA_VERSION;
    public static final int UNKNOWN_U16 = 0xffff;
    public static final float UNKNOWN_F32 = Float.NaN;

    private final long timestamp;
    private final byte sampleSchemaVersion;
    private final long tickIndex;
    private final int serverProtocol;
    private final int clientProtocol;
    private final String serverVersion;
    private final String clientVersion;
    private final String serverBrand;
    private final String platform;
    private final String physicsFingerprint;
    private final String modPluginFingerprint;
    private final String captureSubjectId;
    private final String translationBehaviorFingerprint;
    private final String identitySource;
    private final long playerHash;
    private final double posBeforeX;
    private final double posBeforeY;
    private final double posBeforeZ;
    private final float posAfterDx;
    private final float posAfterDy;
    private final float posAfterDz;
    private final float velBeforeX;
    private final float velBeforeY;
    private final float velBeforeZ;
    private final float velAfterX;
    private final float velAfterY;
    private final float velAfterZ;
    private final float baseSpeed;
    private final int inputFlags;
    private final int previousStateFlags;
    private final int stateFlags;
    private final int supportBlockId;
    private final int frictionBlockId;
    private final byte surfaceCategory;
    private final String blockId;
    private final String blockProperties;
    private final String blockStateId;
    private final String supportShapeId;
    private final float friction;
    private final float velocityMultiplier;
    private final boolean bodyInWater;
    private final boolean eyeInWater;
    private final boolean inLava;
    private final String fluidKind;
    private final float fluidLevel;
    private final float fluidHeight;
    private final float flowX;
    private final float flowY;
    private final float flowZ;
    private final String bodyFluid;
    private final String eyeFluid;
    private final String effectLevels;
    private final float attributeBaseSpeed;
    private final float movementSpeedModifier;
    private final byte jumpBoostLevel;
    private final byte slownessLevel;
    private final boolean vehicleMounted;
    private final String vehicleType;
    private final long vehicleId;
    private final int vehicleStateFlags;
    private final boolean externalForceActive;
    private final String externalForceKind;
    private final float externalForceX;
    private final float externalForceY;
    private final float externalForceZ;
    private final long externalForceSourceTick;
    private final int externalForceTimingTicks;
    private final float tickDurationMs;
    private final float mspt;
    private final byte worldDimension;
    private final int resyncFlags;
    private final long unknownMask;
    private final byte simErrorPct;
    // Packet inclusion is distinct from the cached position used to build a
    // prediction frame. A LOOK/FLYING packet must not be advertised as a
    // position update merely because the adapter has a previous position.
    private boolean positionIncluded = true;
    private boolean lookIncluded;
    private float yaw;
    private float pitch;
    private float lookX;
    private float lookY;
    private float lookZ;

    /** Compatibility constructor for adapters being migrated to v2. */
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
        this(timestamp, 0L, serverProtocol, clientProtocol,
                "unknown", "unknown", "unknown", "unknown", "unknown", "unknown",
                playerHash, posBeforeX, posBeforeY, posBeforeZ,
                posAfterDx, posAfterDy, posAfterDz,
                velX, velY, velZ, velX, velY, velZ, baseSpeed,
                inputFlags & 0xff, 0, inputFlags & 0xff,
                supportBlockId, frictionBlockId, surfaceCategory,
                "", "", "", "", UNKNOWN_F32, UNKNOWN_F32,
                bodyInWater, eyeInWater, inLava,
                "", UNKNOWN_F32, UNKNOWN_F32, UNKNOWN_F32, UNKNOWN_F32, UNKNOWN_F32,
                "", "", "", baseSpeed, UNKNOWN_F32, (byte) 0, (byte) 0,
                false, "", 0L, 0,
                false, "", UNKNOWN_F32, UNKNOWN_F32, UNKNOWN_F32, 0L, 0,
                tickDurationMs, tickDurationMs, worldDimension, 0, 0L, simErrorPct);
    }

    /** Full v2 constructor used by Gateway/Fabric adapters. */
    public PacketPhysicsCaptureSample(
            long timestamp, long tickIndex, int serverProtocol, int clientProtocol,
            String serverVersion, String clientVersion, String serverBrand, String platform,
            String physicsFingerprint, String modPluginFingerprint, long playerHash,
            double posBeforeX, double posBeforeY, double posBeforeZ,
            float posAfterDx, float posAfterDy, float posAfterDz,
            float velBeforeX, float velBeforeY, float velBeforeZ,
            float velAfterX, float velAfterY, float velAfterZ, float baseSpeed,
            int inputFlags, int previousStateFlags, int stateFlags,
            int supportBlockId, int frictionBlockId, byte surfaceCategory,
            String blockId, String blockProperties, String blockStateId, String supportShapeId,
            float friction, float velocityMultiplier,
            boolean bodyInWater, boolean eyeInWater, boolean inLava,
            String fluidKind, float fluidLevel, float fluidHeight,
            float flowX, float flowY, float flowZ, String bodyFluid, String eyeFluid,
            String effectLevels, float attributeBaseSpeed, float movementSpeedModifier,
            byte jumpBoostLevel, byte slownessLevel,
            boolean vehicleMounted, String vehicleType, long vehicleId, int vehicleStateFlags,
            boolean externalForceActive, String externalForceKind,
            float externalForceX, float externalForceY, float externalForceZ,
            long externalForceSourceTick, int externalForceTimingTicks,
            float tickDurationMs, float mspt, byte worldDimension, int resyncFlags,
            long unknownMask, byte simErrorPct) {
        this(timestamp, tickIndex, serverProtocol, clientProtocol,
                serverVersion, clientVersion, serverBrand, platform,
                physicsFingerprint, modPluginFingerprint,
                "", "", "legacy", playerHash,
                posBeforeX, posBeforeY, posBeforeZ,
                posAfterDx, posAfterDy, posAfterDz,
                velBeforeX, velBeforeY, velBeforeZ,
                velAfterX, velAfterY, velAfterZ, baseSpeed,
                inputFlags, previousStateFlags, stateFlags,
                supportBlockId, frictionBlockId, surfaceCategory,
                blockId, blockProperties, blockStateId, supportShapeId,
                friction, velocityMultiplier,
                bodyInWater, eyeInWater, inLava,
                fluidKind, fluidLevel, fluidHeight,
                flowX, flowY, flowZ, bodyFluid, eyeFluid,
                effectLevels, attributeBaseSpeed, movementSpeedModifier,
                jumpBoostLevel, slownessLevel,
                vehicleMounted, vehicleType, vehicleId, vehicleStateFlags,
                externalForceActive, externalForceKind,
                externalForceX, externalForceY, externalForceZ,
                externalForceSourceTick, externalForceTimingTicks,
                tickDurationMs, mspt, worldDimension, resyncFlags,
                unknownMask, simErrorPct);
    }

    /** Full v2 constructor with the adapter identity bridge fields. */
    public PacketPhysicsCaptureSample(
            long timestamp, long tickIndex, int serverProtocol, int clientProtocol,
            String serverVersion, String clientVersion, String serverBrand, String platform,
            String physicsFingerprint, String modPluginFingerprint,
            String captureSubjectId, String translationBehaviorFingerprint, String identitySource,
            long playerHash,
            double posBeforeX, double posBeforeY, double posBeforeZ,
            float posAfterDx, float posAfterDy, float posAfterDz,
            float velBeforeX, float velBeforeY, float velBeforeZ,
            float velAfterX, float velAfterY, float velAfterZ, float baseSpeed,
            int inputFlags, int previousStateFlags, int stateFlags,
            int supportBlockId, int frictionBlockId, byte surfaceCategory,
            String blockId, String blockProperties, String blockStateId, String supportShapeId,
            float friction, float velocityMultiplier,
            boolean bodyInWater, boolean eyeInWater, boolean inLava,
            String fluidKind, float fluidLevel, float fluidHeight,
            float flowX, float flowY, float flowZ, String bodyFluid, String eyeFluid,
            String effectLevels, float attributeBaseSpeed, float movementSpeedModifier,
            byte jumpBoostLevel, byte slownessLevel,
            boolean vehicleMounted, String vehicleType, long vehicleId, int vehicleStateFlags,
            boolean externalForceActive, String externalForceKind,
            float externalForceX, float externalForceY, float externalForceZ,
            long externalForceSourceTick, int externalForceTimingTicks,
            float tickDurationMs, float mspt, byte worldDimension, int resyncFlags,
            long unknownMask, byte simErrorPct) {
        this.timestamp = timestamp;
        this.sampleSchemaVersion = SAMPLE_SCHEMA_VERSION;
        this.tickIndex = tickIndex;
        this.serverProtocol = serverProtocol;
        this.clientProtocol = clientProtocol;
        this.serverVersion = serverVersion;
        this.clientVersion = clientVersion;
        this.serverBrand = serverBrand;
        this.platform = platform;
        this.physicsFingerprint = physicsFingerprint;
        this.modPluginFingerprint = modPluginFingerprint;
        this.captureSubjectId = captureSubjectId == null ? "" : captureSubjectId;
        this.translationBehaviorFingerprint = translationBehaviorFingerprint == null
                ? "" : translationBehaviorFingerprint;
        this.identitySource = identitySource == null ? "unknown" : identitySource;
        this.playerHash = playerHash;
        this.posBeforeX = posBeforeX;
        this.posBeforeY = posBeforeY;
        this.posBeforeZ = posBeforeZ;
        this.posAfterDx = posAfterDx;
        this.posAfterDy = posAfterDy;
        this.posAfterDz = posAfterDz;
        this.velBeforeX = velBeforeX;
        this.velBeforeY = velBeforeY;
        this.velBeforeZ = velBeforeZ;
        this.velAfterX = velAfterX;
        this.velAfterY = velAfterY;
        this.velAfterZ = velAfterZ;
        this.baseSpeed = baseSpeed;
        this.inputFlags = inputFlags;
        this.previousStateFlags = previousStateFlags;
        this.stateFlags = stateFlags;
        this.supportBlockId = supportBlockId;
        this.frictionBlockId = frictionBlockId;
        this.surfaceCategory = surfaceCategory;
        this.blockId = blockId;
        this.blockProperties = blockProperties;
        this.blockStateId = blockStateId;
        this.supportShapeId = supportShapeId;
        this.friction = friction;
        this.velocityMultiplier = velocityMultiplier;
        this.bodyInWater = bodyInWater;
        this.eyeInWater = eyeInWater;
        this.inLava = inLava;
        this.fluidKind = fluidKind;
        this.fluidLevel = fluidLevel;
        this.fluidHeight = fluidHeight;
        this.flowX = flowX;
        this.flowY = flowY;
        this.flowZ = flowZ;
        this.bodyFluid = bodyFluid;
        this.eyeFluid = eyeFluid;
        this.effectLevels = effectLevels;
        this.attributeBaseSpeed = attributeBaseSpeed;
        this.movementSpeedModifier = movementSpeedModifier;
        this.jumpBoostLevel = jumpBoostLevel;
        this.slownessLevel = slownessLevel;
        this.vehicleMounted = vehicleMounted;
        this.vehicleType = vehicleType;
        this.vehicleId = vehicleId;
        this.vehicleStateFlags = vehicleStateFlags;
        this.externalForceActive = externalForceActive;
        this.externalForceKind = externalForceKind;
        this.externalForceX = externalForceX;
        this.externalForceY = externalForceY;
        this.externalForceZ = externalForceZ;
        this.externalForceSourceTick = externalForceSourceTick;
        this.externalForceTimingTicks = externalForceTimingTicks;
        this.tickDurationMs = tickDurationMs;
        this.mspt = mspt;
        this.worldDimension = worldDimension;
        this.resyncFlags = resyncFlags;
        this.unknownMask = unknownMask;
        this.simErrorPct = simErrorPct;
    }

    /** V3 inclusion-aware constructor for LOOK and no-position movement. */
    public PacketPhysicsCaptureSample(
            long timestamp, long tickIndex, int serverProtocol, int clientProtocol,
            String serverVersion, String clientVersion, String serverBrand, String platform,
            String physicsFingerprint, String modPluginFingerprint,
            String captureSubjectId, String translationBehaviorFingerprint, String identitySource,
            long playerHash,
            double posBeforeX, double posBeforeY, double posBeforeZ,
            float posAfterDx, float posAfterDy, float posAfterDz,
            float velBeforeX, float velBeforeY, float velBeforeZ,
            float velAfterX, float velAfterY, float velAfterZ, float baseSpeed,
            int inputFlags, int previousStateFlags, int stateFlags,
            int supportBlockId, int frictionBlockId, byte surfaceCategory,
            String blockId, String blockProperties, String blockStateId, String supportShapeId,
            float friction, float velocityMultiplier,
            boolean bodyInWater, boolean eyeInWater, boolean inLava,
            String fluidKind, float fluidLevel, float fluidHeight,
            float flowX, float flowY, float flowZ, String bodyFluid, String eyeFluid,
            String effectLevels, float attributeBaseSpeed, float movementSpeedModifier,
            byte jumpBoostLevel, byte slownessLevel,
            boolean vehicleMounted, String vehicleType, long vehicleId, int vehicleStateFlags,
            boolean externalForceActive, String externalForceKind,
            float externalForceX, float externalForceY, float externalForceZ,
            long externalForceSourceTick, int externalForceTimingTicks,
            float tickDurationMs, float mspt, byte worldDimension, int resyncFlags,
            long unknownMask, byte simErrorPct,
            boolean positionIncluded, boolean lookIncluded,
            float yaw, float pitch, float lookX, float lookY, float lookZ) {
        this(timestamp, tickIndex, serverProtocol, clientProtocol,
                serverVersion, clientVersion, serverBrand, platform,
                physicsFingerprint, modPluginFingerprint,
                captureSubjectId, translationBehaviorFingerprint, identitySource,
                playerHash, posBeforeX, posBeforeY, posBeforeZ,
                posAfterDx, posAfterDy, posAfterDz,
                velBeforeX, velBeforeY, velBeforeZ, velAfterX, velAfterY, velAfterZ, baseSpeed,
                inputFlags, previousStateFlags, stateFlags,
                supportBlockId, frictionBlockId, surfaceCategory,
                blockId, blockProperties, blockStateId, supportShapeId,
                friction, velocityMultiplier, bodyInWater, eyeInWater, inLava,
                fluidKind, fluidLevel, fluidHeight, flowX, flowY, flowZ, bodyFluid, eyeFluid,
                effectLevels, attributeBaseSpeed, movementSpeedModifier, jumpBoostLevel, slownessLevel,
                vehicleMounted, vehicleType, vehicleId, vehicleStateFlags,
                externalForceActive, externalForceKind, externalForceX, externalForceY, externalForceZ,
                externalForceSourceTick, externalForceTimingTicks,
                tickDurationMs, mspt, worldDimension, resyncFlags, unknownMask, simErrorPct);
        this.positionIncluded = positionIncluded;
        this.lookIncluded = lookIncluded;
        this.yaw = Float.isFinite(yaw) ? yaw : 0.0f;
        this.pitch = Float.isFinite(pitch) ? pitch : 0.0f;
        this.lookX = Float.isFinite(lookX) ? lookX : 0.0f;
        this.lookY = Float.isFinite(lookY) ? lookY : 0.0f;
        this.lookZ = Float.isFinite(lookZ) ? lookZ : 0.0f;
    }

    @Override public byte packetId() { return PacketId.PACKET_PHYSICS_CAPTURE_SAMPLE; }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        CaptureFrameV3.Builder frame = CaptureFrameV3.builder();
        frame.timestamp = timestamp;
        frame.adapterId = identitySource == null || identitySource.isEmpty() ? "gateway" : identitySource;
        frame.adapterVersion = CaptureFrameV3.configuredAdapterVersion();
        frame.adapterCapabilityBitmap = CaptureFrameV3.configuredCapabilityBitmap(identitySource);
        frame.behaviorBundleHash = CaptureFrameV3.configuredBehaviorBundleHash(serverVersion);
        frame.captureSessionId = System.getProperty("zeus.capture.session", "");
        frame.captureSubjectId = captureSubjectId;
        frame.playerHash = playerHash;
        frame.serverProtocol = normalizeProtocol(serverProtocol);
        frame.clientProtocol = normalizeProtocol(clientProtocol);
        frame.serverVersion = valueOrEmpty(serverVersion);
        frame.clientVersion = valueOrEmpty(clientVersion);
        frame.serverBrand = valueOrEmpty(serverBrand);
        frame.platform = valueOrEmpty(platform);
        frame.modPluginFingerprint = valueOrEmpty(modPluginFingerprint);
        frame.effectiveBehaviorFingerprint = valueOrEmpty(translationBehaviorFingerprint);
        frame.serverTick = tickIndex;
        frame.movementSequence = tickIndex;
        frame.inclusionFlags = positionIncluded && hasFinitePosition()
                ? CaptureFrameV3.INCLUSION_POSITION : CaptureFrameV3.INCLUSION_NO_POSITION_MOVEMENT;
        if (lookIncluded) frame.inclusionFlags |= CaptureFrameV3.INCLUSION_LOOK;
        frame.tickDurationMs = tickDurationMs;
        frame.mspt = mspt;
        frame.posBeforeX = posBeforeX; frame.posBeforeY = posBeforeY; frame.posBeforeZ = posBeforeZ;
        frame.posDeltaX = posAfterDx; frame.posDeltaY = posAfterDy; frame.posDeltaZ = posAfterDz;
        frame.velocityBeforeX = velBeforeX; frame.velocityBeforeY = velBeforeY; frame.velocityBeforeZ = velBeforeZ;
        frame.velocityAfterX = velAfterX; frame.velocityAfterY = velAfterY; frame.velocityAfterZ = velAfterZ;
        frame.yaw = yaw; frame.pitch = pitch;
        frame.lookX = lookX; frame.lookY = lookY; frame.lookZ = lookZ;
        frame.previousStateFlags = previousStateFlags;
        frame.stateFlags = stateFlags;
        frame.supportBlockId = valueOrEmpty(blockId);
        frame.bodyBlockId = valueOrEmpty(blockStateId);
        frame.supportShapeHash = valueOrEmpty(supportShapeId);
        frame.surfaceKind = surfaceKind(surfaceCategory);
        frame.friction = friction;
        frame.speedFactor = velocityMultiplier;
        frame.bodyFluid = !valueOrEmpty(bodyFluid).isEmpty() ? bodyFluid : (bodyInWater ? "water" : inLava ? "lava" : "air");
        frame.eyeFluid = !valueOrEmpty(eyeFluid).isEmpty() ? eyeFluid : (eyeInWater ? "water" : "air");
        frame.bodyFluidHeight = fluidHeight;
        frame.eyeFluidHeight = fluidHeight;
        frame.flowX = flowX; frame.flowY = flowY; frame.flowZ = flowZ;
        if (Float.isFinite(attributeBaseSpeed)) frame.attributes.add(new CaptureFrameV3.NamedFloat("minecraft:generic.movement_speed", attributeBaseSpeed));
        if (Float.isFinite(movementSpeedModifier)) frame.attributes.add(new CaptureFrameV3.NamedFloat("movement_speed_modifier", movementSpeedModifier));
        parseLevels(effectLevels, frame.effects);
        if (jumpBoostLevel != 0) frame.effects.add(new CaptureFrameV3.NamedLevel("minecraft:jump_boost", jumpBoostLevel));
        if (slownessLevel != 0) frame.effects.add(new CaptureFrameV3.NamedLevel("minecraft:slowness", slownessLevel));
        frame.vehicleType = vehicleMounted ? valueOrEmpty(vehicleType) : "";
        frame.vehicleId = vehicleId; frame.vehicleStateFlags = vehicleStateFlags;
        if (externalForceActive && externalForceKind != null && !externalForceKind.isEmpty()) {
            frame.externalForces.add(new CaptureFrameV3.Force(externalForceKind, externalForceX, externalForceY, externalForceZ, externalForceSourceTick, externalForceTimingTicks));
        }
        long presence = 0L;
        if (!frame.adapterId.isEmpty() && !frame.adapterVersion.isEmpty() && !frame.behaviorBundleHash.isEmpty()
                && !frame.captureSubjectId.isEmpty() && !frame.serverVersion.isEmpty() && !frame.platform.isEmpty()) presence |= CaptureFrameV3.PRESENCE_IDENTITY;
        if (positionIncluded && hasFinitePosition()) presence |= CaptureFrameV3.PRESENCE_POSITION;
        if (hasFiniteVelocity()) presence |= CaptureFrameV3.PRESENCE_VELOCITY;
        if (lookIncluded) presence |= CaptureFrameV3.PRESENCE_LOOK;
        boolean blockContextValid = !frame.supportBlockId.isEmpty()
                && Float.isFinite(frame.friction) && Float.isFinite(frame.speedFactor);
        if (blockContextValid) presence |= CaptureFrameV3.PRESENCE_BLOCK_CONTEXT;
        boolean fluidContextValid = !frame.bodyFluid.isEmpty()
                && Float.isFinite(frame.bodyFluidHeight) && Float.isFinite(frame.eyeFluidHeight)
                && Float.isFinite(frame.flowX) && Float.isFinite(frame.flowY) && Float.isFinite(frame.flowZ);
        if (fluidContextValid) presence |= CaptureFrameV3.PRESENCE_FLUID_CONTEXT;
        if (!frame.attributes.isEmpty()) presence |= CaptureFrameV3.PRESENCE_ATTRIBUTES;
        if (!frame.effects.isEmpty()) presence |= CaptureFrameV3.PRESENCE_EFFECTS;
        if (vehicleMounted) presence |= CaptureFrameV3.PRESENCE_VEHICLE;
        if (!frame.externalForces.isEmpty()) presence |= CaptureFrameV3.PRESENCE_EXTERNAL_FORCES;
        if (Float.isFinite(tickDurationMs) && Float.isFinite(mspt)) presence |= CaptureFrameV3.PRESENCE_TICK_TIMING;
        frame.presenceMask = presence;
        frame.validityMask = presence;
        frame.build().encode(out);
    }

    private boolean hasFinitePosition() {
        return Double.isFinite(posBeforeX) && Double.isFinite(posBeforeY) && Double.isFinite(posBeforeZ)
                && Float.isFinite(posAfterDx) && Float.isFinite(posAfterDy) && Float.isFinite(posAfterDz);
    }
    private boolean hasFiniteVelocity() {
        return Float.isFinite(velBeforeX) && Float.isFinite(velBeforeY) && Float.isFinite(velBeforeZ)
                && Float.isFinite(velAfterX) && Float.isFinite(velAfterY) && Float.isFinite(velAfterZ);
    }
    private static String valueOrEmpty(String value) { return value == null ? "" : value; }
    private static int normalizeProtocol(int value) { return value == UNKNOWN_U16 ? 0 : value; }
    private static void parseLevels(String values, java.util.List<CaptureFrameV3.NamedLevel> out) {
        if (values == null || values.isEmpty()) return;
        for (String entry : values.split(",")) {
            int split = entry.indexOf('=');
            if (split <= 0) continue;
            try { out.add(new CaptureFrameV3.NamedLevel(entry.substring(0, split).trim(), (byte) Integer.parseInt(entry.substring(split + 1).trim()))); }
            catch (NumberFormatException ignored) { }
        }
    }
    private static String surfaceKind(byte category) {
        switch (category) {
            case 1: return "ice";
            case 2: return "slime";
            case 3: return "honey";
            case 4: return "soul_sand";
            case 5: return "cobweb";
            default: return "";
        }
    }

    public long getTimestamp() { return timestamp; }
    public byte getSampleSchemaVersion() { return sampleSchemaVersion; }
    public long getTickIndex() { return tickIndex; }
    public int getServerProtocol() { return serverProtocol; }
    public int getClientProtocol() { return clientProtocol; }
    public String getServerVersion() { return serverVersion; }
    public String getClientVersion() { return clientVersion; }
    public String getServerBrand() { return serverBrand; }
    public String getPlatform() { return platform; }
    public String getPhysicsFingerprint() { return physicsFingerprint; }
    public String getModPluginFingerprint() { return modPluginFingerprint; }
    public String getCaptureSubjectId() { return captureSubjectId; }
    public String getTranslationBehaviorFingerprint() { return translationBehaviorFingerprint; }
    public String getIdentitySource() { return identitySource; }
    public long getPlayerHash() { return playerHash; }
    public double getPosBeforeX() { return posBeforeX; }
    public double getPosBeforeY() { return posBeforeY; }
    public double getPosBeforeZ() { return posBeforeZ; }
    public float getPosAfterDx() { return posAfterDx; }
    public float getPosAfterDy() { return posAfterDy; }
    public float getPosAfterDz() { return posAfterDz; }
    public float getVelBeforeX() { return velBeforeX; }
    public float getVelBeforeY() { return velBeforeY; }
    public float getVelBeforeZ() { return velBeforeZ; }
    public float getVelAfterX() { return velAfterX; }
    public float getVelAfterY() { return velAfterY; }
    public float getVelAfterZ() { return velAfterZ; }
    public float getBaseSpeed() { return baseSpeed; }
    public int getInputFlags() { return inputFlags; }
    public int getSupportBlockId() { return supportBlockId; }
    public int getFrictionBlockId() { return frictionBlockId; }
    public byte getSurfaceCategory() { return surfaceCategory; }
    public boolean isBodyInWater() { return bodyInWater; }
    public boolean isEyeInWater() { return eyeInWater; }
    public boolean isInLava() { return inLava; }
    public byte getEffectLevels() { return jumpBoostLevel; }
    public float getTickDurationMs() { return tickDurationMs; }
    public byte getWorldDimension() { return worldDimension; }
    public byte getSimErrorPct() { return simErrorPct; }
}
