package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.vennv.ByteBufferUtil;
import org.vennv.PacketBase;
import org.vennv.PacketEncode;
import org.vennv.PacketId;

/**
 * Hard-cut packet 0x2F capture contract.
 *
 * Unknown data is represented only by explicit presence/validity bits. Numeric
 * payload slots are always finite, including absent slots (encoded as zero).
 */
public final class CaptureFrameV3 implements PacketBase, PacketEncode {
    public static final byte CAPTURE_SCHEMA_VERSION = 3;

    public static final long PRESENCE_IDENTITY = 1L << 0;
    public static final long PRESENCE_POSITION = 1L << 1;
    public static final long PRESENCE_VELOCITY = 1L << 2;
    public static final long PRESENCE_LOOK = 1L << 3;
    public static final long PRESENCE_BOUNDING_BOX = 1L << 4;
    public static final long PRESENCE_DIRECTIONAL_INPUT = 1L << 5;
    public static final long PRESENCE_BLOCK_CONTEXT = 1L << 6;
    public static final long PRESENCE_FLUID_CONTEXT = 1L << 7;
    public static final long PRESENCE_ATTRIBUTES = 1L << 8;
    public static final long PRESENCE_EFFECTS = 1L << 9;
    public static final long PRESENCE_ENCHANTMENTS = 1L << 10;
    public static final long PRESENCE_VEHICLE = 1L << 11;
    public static final long PRESENCE_EXTERNAL_FORCES = 1L << 12;
    public static final long PRESENCE_TICK_TIMING = 1L << 13;
    public static final long PRESENCE_PING = 1L << 14;
    public static final long PRESENCE_COLLISION = 1L << 15;
    public static final long KNOWN_PRESENCE_MASK = (1L << 16) - 1;

    // Adapter capability bits describe optional capture inputs. The
    // adapter-specific defaults are deliberately conservative; deployments
    // can override them with -Dzeus.capture.capabilities when an optional
    // listener/source has been verified.
    public static final long CAPABILITY_POSITION = 1L << 0;
    public static final long CAPABILITY_NO_POSITION_MOVEMENT = 1L << 1;
    public static final long CAPABILITY_DIRECTIONAL_INPUT = 1L << 2;
    public static final long CAPABILITY_ITEM_USE = 1L << 3;
    public static final long CAPABILITY_FLUID_STATE = 1L << 4;
    public static final long CAPABILITY_EFFECTS = 1L << 5;
    public static final long CAPABILITY_ATTRIBUTES = 1L << 6;
    public static final long CAPABILITY_VEHICLES = 1L << 7;
    public static final long CAPABILITY_EXTERNAL_FORCES = 1L << 8;
    public static final long CAPABILITY_MSPT = 1L << 9;
    public static final long CAPABILITY_PREDICTION_RESIDUAL = 1L << 10;
    public static final long CAPABILITY_COLLISION_SHAPES = 1L << 11;
    public static final long CAPABILITY_SUPPORT_BLOCK = 1L << 12;
    public static final long CAPABILITY_FLUID_FLOW = 1L << 13;
    public static final long KNOWN_CAPABILITY_MASK = (1L << 14) - 1L;

    public static final byte INCLUSION_POSITION = 1;
    public static final byte INCLUSION_LOOK = 1 << 1;
    public static final byte INCLUSION_NO_POSITION_MOVEMENT = 1 << 2;
    public static final byte KNOWN_INCLUSION_MASK = INCLUSION_POSITION | INCLUSION_LOOK | INCLUSION_NO_POSITION_MOVEMENT;

    public static String configuredAdapterVersion() {
        return System.getProperty("zeus.capture.adapter.version", "3.0.0");
    }

    public static long configuredCapabilityBitmap() {
        return configuredCapabilityBitmap("");
    }

    public static long configuredCapabilityBitmap(String adapterId) {
        String raw = System.getProperty("zeus.capture.capabilities", "0");
        if (!"0".equals(raw)) {
            try {
                return Long.decode(raw);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        String adapter = adapterId == null ? "" : adapterId.toLowerCase(java.util.Locale.ROOT);
        long conservative = CAPABILITY_POSITION | CAPABILITY_EFFECTS
                | CAPABILITY_ATTRIBUTES | CAPABILITY_VEHICLES | CAPABILITY_SUPPORT_BLOCK;
        // Only the modern ProtocolLib path receives look-only/flying packets
        // and collision shape hashes by default. Legacy/Fabric can opt in
        // after their version-specific adapters prove those sources.
        if (adapter.contains("gateway") || adapter.contains("modern")) {
            conservative |= CAPABILITY_NO_POSITION_MOVEMENT | CAPABILITY_COLLISION_SHAPES;
        }
        return conservative;
    }

    /** Legacy wire field; runtime no longer consumes behavior bundles. */
    public static String configuredBehaviorBundleHash(String ignoredServerVersion) {
        return "";
    }

    public static final class NamedFloat {
        public final String id;
        public final float value;
        public NamedFloat(String id, float value) { this.id = string(id); this.value = finite(value); }
    }
    public static final class NamedLevel {
        public final String id;
        public final byte level;
        public NamedLevel(String id, byte level) { this.id = string(id); this.level = level; }
    }
    public static final class Force {
        public final String kind;
        public final float x, y, z;
        public final long sourceTick;
        public final int durationTicks;
        public Force(String kind, float x, float y, float z, long sourceTick, int durationTicks) {
            this.kind = string(kind); this.x = finite(x); this.y = finite(y); this.z = finite(z);
            this.sourceTick = sourceTick; this.durationTicks = durationTicks;
        }
    }

    /** Public builder keeps adapter code readable across multiple Minecraft APIs. */
    public static final class Builder {
        public long timestamp;
        public long presenceMask;
        public long validityMask;
        public String adapterId = "";
        public String adapterVersion = "";
        public long adapterCapabilityBitmap;
        public String behaviorBundleHash = "";
        public String captureSessionId = "";
        public String captureSubjectId = "";
        public long playerHash;
        public int serverProtocol;
        public int clientProtocol;
        public String serverVersion = "";
        public String clientVersion = "";
        public String serverBrand = "";
        public String platform = "";
        public String modPluginFingerprint = "";
        public String effectiveBehaviorFingerprint = "";
        public long serverTick;
        public long movementSequence;
        public byte inclusionFlags;
        public float tickDurationMs, mspt;
        public int pingMs;
        public double posBeforeX, posBeforeY, posBeforeZ;
        public float posDeltaX, posDeltaY, posDeltaZ;
        public float velocityBeforeX, velocityBeforeY, velocityBeforeZ;
        public float velocityAfterX, velocityAfterY, velocityAfterZ;
        public float yaw, pitch, lookX, lookY, lookZ;
        public String pose = "";
        public float boundingWidth, boundingHeight, eyeHeight;
        public int collisionFlags;
        public float forwardInput, sidewaysInput, vehicleForwardInput, vehicleSidewaysInput;
        public int inputFlags, previousStateFlags, stateFlags;
        public String supportBlockId = "", bodyBlockId = "", supportShapeHash = "", surfaceKind = "";
        public List<String> nearbyBlockIds = new ArrayList<>();
        public float friction, speedFactor, jumpFactor;
        public String bodyFluid = "", eyeFluid = "", bubbleColumn = "", dimension = "";
        public float bodyFluidHeight, eyeFluidHeight, flowX, flowY, flowZ;
        public List<NamedFloat> attributes = new ArrayList<>();
        public List<NamedLevel> effects = new ArrayList<>();
        public List<NamedLevel> enchantments = new ArrayList<>();
        public String vehicleType = "", vehicleStatus = "";
        public long vehicleId;
        public int vehicleStateFlags;
        public List<Force> externalForces = new ArrayList<>();
        public CaptureFrameV3 build() { return new CaptureFrameV3(this); }
    }

    private final Builder value;
    private CaptureFrameV3(Builder source) { this.value = normalize(source); validate(); }
    public static Builder builder() { return new Builder(); }

    @Override public byte packetId() { return PacketId.PACKET_PHYSICS_CAPTURE_SAMPLE; }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        validate();
        ByteBufferUtil.putByte(out, packetId());
        ByteBufferUtil.putLong(out, value.timestamp);
        ByteBufferUtil.putByte(out, CAPTURE_SCHEMA_VERSION);
        ByteBufferUtil.putLong(out, value.presenceMask);
        ByteBufferUtil.putLong(out, value.validityMask);
        putString(out, value.adapterId); putString(out, value.adapterVersion);
        ByteBufferUtil.putLong(out, value.adapterCapabilityBitmap); putString(out, value.behaviorBundleHash);
        putString(out, value.captureSessionId); putString(out, value.captureSubjectId);
        ByteBufferUtil.putLong(out, value.playerHash); putShort(out, value.serverProtocol); putShort(out, value.clientProtocol);
        putString(out, value.serverVersion); putString(out, value.clientVersion); putString(out, value.serverBrand);
        putString(out, value.platform); putString(out, value.modPluginFingerprint); putString(out, value.effectiveBehaviorFingerprint);
        ByteBufferUtil.putLong(out, value.serverTick); ByteBufferUtil.putLong(out, value.movementSequence);
        ByteBufferUtil.putByte(out, value.inclusionFlags); putFloat(out, value.tickDurationMs); putFloat(out, value.mspt); ByteBufferUtil.putInt(out, value.pingMs);
        putDouble(out, value.posBeforeX); putDouble(out, value.posBeforeY); putDouble(out, value.posBeforeZ);
        putFloat(out, value.posDeltaX); putFloat(out, value.posDeltaY); putFloat(out, value.posDeltaZ);
        putFloat(out, value.velocityBeforeX); putFloat(out, value.velocityBeforeY); putFloat(out, value.velocityBeforeZ);
        putFloat(out, value.velocityAfterX); putFloat(out, value.velocityAfterY); putFloat(out, value.velocityAfterZ);
        putFloat(out, value.yaw); putFloat(out, value.pitch); putFloat(out, value.lookX); putFloat(out, value.lookY); putFloat(out, value.lookZ);
        putString(out, value.pose); putFloat(out, value.boundingWidth); putFloat(out, value.boundingHeight); putFloat(out, value.eyeHeight); ByteBufferUtil.putInt(out, value.collisionFlags);
        putFloat(out, value.forwardInput); putFloat(out, value.sidewaysInput); putFloat(out, value.vehicleForwardInput); putFloat(out, value.vehicleSidewaysInput);
        ByteBufferUtil.putInt(out, value.inputFlags); ByteBufferUtil.putInt(out, value.previousStateFlags); ByteBufferUtil.putInt(out, value.stateFlags);
        putString(out, value.supportBlockId); putString(out, value.bodyBlockId); putStrings(out, value.nearbyBlockIds); putString(out, value.supportShapeHash);
        putFloat(out, value.friction); putFloat(out, value.speedFactor); putFloat(out, value.jumpFactor); putString(out, value.surfaceKind);
        putString(out, value.bodyFluid); putString(out, value.eyeFluid); putFloat(out, value.bodyFluidHeight); putFloat(out, value.eyeFluidHeight);
        putFloat(out, value.flowX); putFloat(out, value.flowY); putFloat(out, value.flowZ); putString(out, value.bubbleColumn); putString(out, value.dimension);
        putNamedFloats(out, value.attributes); putNamedLevels(out, value.effects); putNamedLevels(out, value.enchantments);
        putString(out, value.vehicleType); ByteBufferUtil.putLong(out, value.vehicleId); putString(out, value.vehicleStatus); ByteBufferUtil.putInt(out, value.vehicleStateFlags);
        putForces(out, value.externalForces);
    }

    private void validate() {
        if ((value.presenceMask & ~KNOWN_PRESENCE_MASK) != 0L || (value.validityMask & ~KNOWN_PRESENCE_MASK) != 0L) {
            throw new IllegalArgumentException("unknown CaptureFrameV3 presence/validity bit");
        }
        if ((value.adapterCapabilityBitmap & ~KNOWN_CAPABILITY_MASK) != 0L) {
            throw new IllegalArgumentException("unknown CaptureFrameV3 capability bit");
        }
        if ((value.validityMask & ~value.presenceMask) != 0L) {
            throw new IllegalArgumentException("CaptureFrameV3 marks absent group valid");
        }
        if ((value.inclusionFlags & ~KNOWN_INCLUSION_MASK) != 0) {
            throw new IllegalArgumentException("unknown CaptureFrameV3 inclusion bit");
        }
        if ((value.inclusionFlags & INCLUSION_POSITION) != 0
                && (value.inclusionFlags & INCLUSION_NO_POSITION_MOVEMENT) != 0) {
            throw new IllegalArgumentException("CaptureFrameV3 marks both position and no-position movement");
        }
        if ((value.inclusionFlags & INCLUSION_POSITION) != 0
                && (value.validityMask & PRESENCE_POSITION) == 0L) {
            throw new IllegalArgumentException("position inclusion requires a valid position group");
        }
        if ((value.validityMask & PRESENCE_IDENTITY) != 0L
                && (value.adapterId.isEmpty() || value.adapterVersion.isEmpty() || value.behaviorBundleHash.isEmpty()
                    || value.captureSubjectId.isEmpty() || value.serverVersion.isEmpty() || value.platform.isEmpty())) {
            throw new IllegalArgumentException("CaptureFrameV3 identity is incomplete");
        }
    }

    private static Builder normalize(Builder b) {
        // Do not mutate caller-owned lists.  All absent numeric slots are zero;
        // a mask, never NaN, determines whether they are meaningful.
        b.adapterId = string(b.adapterId); b.adapterVersion = string(b.adapterVersion);
        b.behaviorBundleHash = string(b.behaviorBundleHash); b.captureSessionId = string(b.captureSessionId);
        b.captureSubjectId = string(b.captureSubjectId); b.serverVersion = string(b.serverVersion);
        b.clientVersion = string(b.clientVersion); b.serverBrand = string(b.serverBrand); b.platform = string(b.platform);
        b.modPluginFingerprint = string(b.modPluginFingerprint); b.effectiveBehaviorFingerprint = string(b.effectiveBehaviorFingerprint);
        b.pose = string(b.pose); b.supportBlockId = string(b.supportBlockId); b.bodyBlockId = string(b.bodyBlockId);
        b.supportShapeHash = string(b.supportShapeHash); b.surfaceKind = string(b.surfaceKind);
        b.bodyFluid = string(b.bodyFluid); b.eyeFluid = string(b.eyeFluid); b.bubbleColumn = string(b.bubbleColumn); b.dimension = string(b.dimension);
        b.vehicleType = string(b.vehicleType); b.vehicleStatus = string(b.vehicleStatus);
        b.tickDurationMs = finite(b.tickDurationMs); b.mspt = finite(b.mspt);
        b.posBeforeX = finite(b.posBeforeX); b.posBeforeY = finite(b.posBeforeY); b.posBeforeZ = finite(b.posBeforeZ);
        b.posDeltaX = finite(b.posDeltaX); b.posDeltaY = finite(b.posDeltaY); b.posDeltaZ = finite(b.posDeltaZ);
        b.velocityBeforeX = finite(b.velocityBeforeX); b.velocityBeforeY = finite(b.velocityBeforeY); b.velocityBeforeZ = finite(b.velocityBeforeZ);
        b.velocityAfterX = finite(b.velocityAfterX); b.velocityAfterY = finite(b.velocityAfterY); b.velocityAfterZ = finite(b.velocityAfterZ);
        b.yaw = finite(b.yaw); b.pitch = finite(b.pitch); b.lookX = finite(b.lookX); b.lookY = finite(b.lookY); b.lookZ = finite(b.lookZ);
        b.boundingWidth = finite(b.boundingWidth); b.boundingHeight = finite(b.boundingHeight); b.eyeHeight = finite(b.eyeHeight);
        b.forwardInput = finite(b.forwardInput); b.sidewaysInput = finite(b.sidewaysInput); b.vehicleForwardInput = finite(b.vehicleForwardInput); b.vehicleSidewaysInput = finite(b.vehicleSidewaysInput);
        b.friction = finite(b.friction); b.speedFactor = finite(b.speedFactor); b.jumpFactor = finite(b.jumpFactor);
        b.bodyFluidHeight = finite(b.bodyFluidHeight); b.eyeFluidHeight = finite(b.eyeFluidHeight); b.flowX = finite(b.flowX); b.flowY = finite(b.flowY); b.flowZ = finite(b.flowZ);
        return b;
    }
    private static String string(String value) { return value == null ? "" : value; }
    private static float finite(float value) { return Float.isFinite(value) ? value : 0.0f; }
    private static double finite(double value) { return Double.isFinite(value) ? value : 0.0; }
    private static void putString(ByteArrayOutputStream out, String value) throws IOException { ByteBufferUtil.putString(out, string(value)); }
    private static void putShort(ByteArrayOutputStream out, int value) throws IOException { ByteBufferUtil.putShort(out, (short) value); }
    private static void putFloat(ByteArrayOutputStream out, float value) throws IOException { ByteBufferUtil.putFloat(out, finite(value)); }
    private static void putDouble(ByteArrayOutputStream out, double value) throws IOException { ByteBufferUtil.putDouble(out, finite(value)); }
    private static void putStrings(ByteArrayOutputStream out, List<String> values) throws IOException { ByteBufferUtil.putShort(out, (short) values.size()); for (String value : values) putString(out, value); }
    private static void putNamedFloats(ByteArrayOutputStream out, List<NamedFloat> values) throws IOException { ByteBufferUtil.putShort(out, (short) values.size()); for (NamedFloat value : values) { putString(out, value.id); putFloat(out, value.value); } }
    private static void putNamedLevels(ByteArrayOutputStream out, List<NamedLevel> values) throws IOException { ByteBufferUtil.putShort(out, (short) values.size()); for (NamedLevel value : values) { putString(out, value.id); ByteBufferUtil.putByte(out, value.level); } }
    private static void putForces(ByteArrayOutputStream out, List<Force> values) throws IOException { ByteBufferUtil.putShort(out, (short) values.size()); for (Force value : values) { putString(out, value.kind); putFloat(out, value.x); putFloat(out, value.y); putFloat(out, value.z); ByteBufferUtil.putLong(out, value.sourceTick); putShort(out, value.durationTicks); } }

    public long getMovementSequence() { return value.movementSequence; }
    public long getPresenceMask() { return value.presenceMask; }
    public long getValidityMask() { return value.validityMask; }
}
