package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketUpdateAttributes extends PacketBaseInfo {

    // Flag bits — MUST match Rust protocol/src/packets/update_attributes.rs
    private static final byte FLAG_GRAVITY            = 1 << 0;
    private static final byte FLAG_JUMP_STRENGTH      = 1 << 1;
    private static final byte FLAG_STEP_HEIGHT        = 1 << 2;
    private static final byte FLAG_SCALE              = 1 << 3;
    private static final byte FLAG_SNEAKING_SPEED     = 1 << 4;
    private static final byte FLAG_MOVEMENT_EFFICIENCY = 1 << 5;
    private static final byte FLAG_WATER_MOVEMENT_EFFICIENCY = 1 << 6;

    private final float movementSpeed;

    // Entity attributes (null = not sent, backward-compatible with old plugins)
    private final Double gravity;
    private final Double jumpStrength;
    private final Double stepHeight;
    private final Double scale;
    private final Double sneakingSpeed;
    private final Double movementEfficiency;
    private final Double waterMovementEfficiency;

    /** Old constructor — all optional attributes default to null. */
    public PacketUpdateAttributes(long timestamp, String uid, String username, float movementSpeed) {
        this(timestamp, uid, username, movementSpeed, null, null, null, null, null, null, null);
    }

    /** Full constructor with all entity attributes. */
    public PacketUpdateAttributes(
            long timestamp, String uid, String username,
            float movementSpeed,
            Double gravity,
            Double jumpStrength,
            Double stepHeight,
            Double scale,
            Double sneakingSpeed,
            Double movementEfficiency,
            Double waterMovementEfficiency) {
        super(timestamp, uid, username);
        this.movementSpeed = movementSpeed;
        this.gravity = gravity;
        this.jumpStrength = jumpStrength;
        this.stepHeight = stepHeight;
        this.scale = scale;
        this.sneakingSpeed = sneakingSpeed;
        this.movementEfficiency = movementEfficiency;
        this.waterMovementEfficiency = waterMovementEfficiency;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_UPDATE_ATTRIBUTES;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putFloat(out, movementSpeed);

        // Build flags byte
        byte flags = 0;
        if (gravity != null)            flags |= FLAG_GRAVITY;
        if (jumpStrength != null)       flags |= FLAG_JUMP_STRENGTH;
        if (stepHeight != null)         flags |= FLAG_STEP_HEIGHT;
        if (scale != null)              flags |= FLAG_SCALE;
        if (sneakingSpeed != null)      flags |= FLAG_SNEAKING_SPEED;
        if (movementEfficiency != null) flags |= FLAG_MOVEMENT_EFFICIENCY;
        if (waterMovementEfficiency != null) flags |= FLAG_WATER_MOVEMENT_EFFICIENCY;
        ByteBufferUtil.putByte(out, flags);

        // Write present fields in flag-bit order (matched to Rust decode order)
        if (gravity != null)            ByteBufferUtil.putDouble(out, gravity);
        if (jumpStrength != null)       ByteBufferUtil.putDouble(out, jumpStrength);
        if (stepHeight != null)         ByteBufferUtil.putDouble(out, stepHeight);
        if (scale != null)              ByteBufferUtil.putDouble(out, scale);
        if (sneakingSpeed != null)      ByteBufferUtil.putDouble(out, sneakingSpeed);
        if (movementEfficiency != null) ByteBufferUtil.putDouble(out, movementEfficiency);
        if (waterMovementEfficiency != null) ByteBufferUtil.putDouble(out, waterMovementEfficiency);
    }

    // -- Getters ------------------------------------------------------------

    public float getMovementSpeed() {
        return movementSpeed;
    }

    public Double getGravity() {
        return gravity;
    }

    public Double getJumpStrength() {
        return jumpStrength;
    }

    public Double getStepHeight() {
        return stepHeight;
    }

    public Double getScale() {
        return scale;
    }

    public Double getSneakingSpeed() {
        return sneakingSpeed;
    }

    public Double getMovementEfficiency() {
        return movementEfficiency;
    }

    public Double getWaterMovementEfficiency() {
        return waterMovementEfficiency;
    }
}
