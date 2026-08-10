package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PacketUpdateAttributes extends PacketBaseInfo {

    private static final int SOURCE_AWARE_MARKER = 0x7fc0a55a;
    private static final byte SOURCE_AWARE_SCHEMA_VERSION = 1;
    private static final int MAX_PROPERTIES = 8;
    private static final int MAX_MODIFIERS = 64;
    private static final int MAX_TEXT_BYTES = 256;

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
    private final List<Property> properties;
    private final boolean fullReplace;

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
        this.properties = null;
        this.fullReplace = false;
    }

    private PacketUpdateAttributes(
            long timestamp,
            String uid,
            String username,
            List<Property> properties,
            boolean fullReplace) {
        super(timestamp, uid, username);
        require(properties != null && !properties.isEmpty() && properties.size() <= MAX_PROPERTIES,
                "attribute property count is invalid");
        this.movementSpeed = Float.NaN;
        this.gravity = null;
        this.jumpStrength = null;
        this.stepHeight = null;
        this.scale = null;
        this.sneakingSpeed = null;
        this.movementEfficiency = null;
        this.waterMovementEfficiency = null;
        this.properties = Collections.unmodifiableList(new ArrayList<Property>(properties));
        this.fullReplace = fullReplace;
    }

    public static PacketUpdateAttributes merge(
            long timestamp, String uid, String username, List<Property> properties) {
        return new PacketUpdateAttributes(timestamp, uid, username, properties, false);
    }

    public static PacketUpdateAttributes fullReplace(
            long timestamp, String uid, String username, List<Property> properties) {
        return new PacketUpdateAttributes(timestamp, uid, username, properties, true);
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_UPDATE_ATTRIBUTES;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        if (properties != null) {
            encodeProperties(out);
            return;
        }
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

    private void encodeProperties(ByteArrayOutputStream out) throws IOException {
        ByteBufferUtil.putInt(out, SOURCE_AWARE_MARKER);
        ByteBufferUtil.putByte(out, SOURCE_AWARE_SCHEMA_VERSION);
        ByteBufferUtil.putByte(out, fullReplace ? (byte) 1 : (byte) 0);
        ByteBufferUtil.putByte(out, (byte) properties.size());
        for (Property property : properties) {
            ByteBufferUtil.putString(out, property.key);
            ByteBufferUtil.putDouble(out, property.baseValue);
            ByteBufferUtil.putShort(out, (short) property.modifiers.size());
            for (Modifier modifier : property.modifiers) {
                ByteBufferUtil.putString(out, modifier.stableId);
                ByteBufferUtil.putString(out, modifier.name);
                ByteBufferUtil.putDouble(out, modifier.amount);
                ByteBufferUtil.putByte(out, (byte) modifier.operation.ordinal());
            }
        }
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

    public enum Operation { ADDITION, MULTIPLY_BASE, MULTIPLY_TOTAL }

    public static final class Modifier {
        private final String stableId;
        private final String name;
        private final double amount;
        private final Operation operation;

        public Modifier(String stableId, String name, double amount, Operation operation) {
            require(validText(stableId) && validText(name) && Double.isFinite(amount),
                    "attribute modifier is invalid");
            this.stableId = stableId;
            this.name = name;
            this.amount = amount;
            this.operation = Objects.requireNonNull(operation, "operation");
        }

        public String getStableId() { return stableId; }
        public String getName() { return name; }
        public double getAmount() { return amount; }
        public Operation getOperation() { return operation; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Modifier)) return false;
            Modifier value = (Modifier) other;
            return Double.compare(amount, value.amount) == 0
                    && stableId.equals(value.stableId)
                    && name.equals(value.name)
                    && operation == value.operation;
        }

        @Override
        public int hashCode() { return Objects.hash(stableId, name, amount, operation); }
    }

    public static final class Property {
        private final String key;
        private final double baseValue;
        private final List<Modifier> modifiers;

        public Property(String key, double baseValue, List<Modifier> modifiers) {
            require(validText(key) && Double.isFinite(baseValue)
                            && modifiers != null && modifiers.size() <= MAX_MODIFIERS,
                    "attribute property is invalid");
            this.key = key;
            this.baseValue = baseValue;
            this.modifiers = Collections.unmodifiableList(new ArrayList<Modifier>(modifiers));
            for (Modifier modifier : this.modifiers) Objects.requireNonNull(modifier, "modifier");
        }

        public String getKey() { return key; }
        public double getBaseValue() { return baseValue; }
        public List<Modifier> getModifiers() { return modifiers; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Property)) return false;
            Property value = (Property) other;
            return Double.compare(baseValue, value.baseValue) == 0
                    && key.equals(value.key)
                    && modifiers.equals(value.modifiers);
        }

        @Override
        public int hashCode() { return Objects.hash(key, baseValue, modifiers); }
    }

    private static boolean validText(String value) {
        return value != null && !value.isEmpty()
                && value.getBytes(StandardCharsets.UTF_8).length <= MAX_TEXT_BYTES;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
