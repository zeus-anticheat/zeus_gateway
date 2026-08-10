package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;
import org.vennv.ByteBufferUtil;
import org.vennv.Effect;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

/** Versioned FULL_REPLACE movement-critical player state. */
public final class PacketMovementStateSnapshot extends PacketBaseInfo {
    public static final byte SCHEMA_VERSION = 2;
    private static final byte LEGACY_SCHEMA_VERSION = 1;
    public static final int MAX_DATAGRAM_LENGTH = 1200;
    public static final int MAX_FRAGMENT_COUNT = 64;
    public static final int MAX_PAYLOAD_LENGTH = 65_536;
    public static final int MAX_EFFECTS = 64;
    public static final int MAX_VEHICLE_TYPE_BYTES = 256;

    private static final int STATE_SPRINTING = 1;
    private static final int STATE_SNEAKING = 1 << 1;
    private static final int STATE_SWIMMING = 1 << 2;
    private static final int STATE_FALL_FLYING = 1 << 3;
    private static final int USE_ITEM_USING = 1;
    private static final int USE_ITEM_BLOCKING = 1 << 1;
    private static final int USE_ITEM_EATING = 1 << 2;
    private static final int USE_ITEM_DRAWING = 1 << 3;
    private static final int USE_ITEM_FISHING = 1 << 4;
    private static final int VEHICLE_MOVEMENT_SPEED = 1;
    private static final int VEHICLE_JUMP_STRENGTH = 1 << 1;
    private static final int VEHICLE_SADDLE_KNOWN = 1 << 2;
    private static final int VEHICLE_SADDLED = 1 << 3;

    public static final class Attributes {
        private final boolean complete;
        private final float movementSpeed;
        private final double gravity;
        private final double jumpStrength;
        private final double stepHeight;
        private final double scale;
        private final double sneakingSpeed;
        private final double movementEfficiency;
        private final double waterMovementEfficiency;
        private final List<PacketUpdateAttributes.Property> properties;

        public Attributes(
                boolean complete,
                float movementSpeed,
                double gravity,
                double jumpStrength,
                double stepHeight,
                double scale,
                double sneakingSpeed,
                double movementEfficiency,
                double waterMovementEfficiency) {
            this(complete, movementSpeed, gravity, jumpStrength, stepHeight, scale,
                    sneakingSpeed, movementEfficiency, waterMovementEfficiency,
                    Collections.<PacketUpdateAttributes.Property>emptyList());
        }

        public Attributes(
                boolean complete,
                float movementSpeed,
                double gravity,
                double jumpStrength,
                double stepHeight,
                double scale,
                double sneakingSpeed,
                double movementEfficiency,
                double waterMovementEfficiency,
                List<PacketUpdateAttributes.Property> properties) {
            this.complete = complete;
            this.movementSpeed = movementSpeed;
            this.gravity = gravity;
            this.jumpStrength = jumpStrength;
            this.stepHeight = stepHeight;
            this.scale = scale;
            this.sneakingSpeed = sneakingSpeed;
            this.movementEfficiency = movementEfficiency;
            this.waterMovementEfficiency = waterMovementEfficiency;
            Objects.requireNonNull(properties, "properties");
            require(properties.size() <= 8, "movement snapshot attribute property count exceeds limit");
            List<PacketUpdateAttributes.Property> copy =
                    new ArrayList<PacketUpdateAttributes.Property>(properties.size());
            for (PacketUpdateAttributes.Property property : properties) {
                copy.add(Objects.requireNonNull(property, "property"));
            }
            this.properties = Collections.unmodifiableList(copy);
            validate();
        }

        public static Attributes vanilla(boolean complete) {
            return new Attributes(complete, 0.1f, 0.08, 0.42, 0.6, 1.0, 0.3, 0.0, 0.0);
        }

        private void validate() {
            require(Float.isFinite(movementSpeed) && movementSpeed >= 0.0f && movementSpeed <= 1024.0f,
                    "movement snapshot speed is out of range");
            require(Double.isFinite(gravity) && gravity >= 0.0, "movement snapshot gravity is invalid");
            require(Double.isFinite(jumpStrength) && jumpStrength >= 0.0,
                    "movement snapshot jump strength is invalid");
            require(Double.isFinite(stepHeight) && stepHeight >= 0.0,
                    "movement snapshot step height is invalid");
            require(Double.isFinite(scale) && scale > 0.0, "movement snapshot scale is invalid");
            require(Double.isFinite(sneakingSpeed) && sneakingSpeed >= 0.0,
                    "movement snapshot sneaking speed is invalid");
            require(Double.isFinite(movementEfficiency) && movementEfficiency >= 0.0,
                    "movement snapshot movement efficiency is invalid");
            require(Double.isFinite(waterMovementEfficiency) && waterMovementEfficiency >= 0.0,
                    "movement snapshot water movement efficiency is invalid");
        }

        public boolean isComplete() { return complete; }
        public float getMovementSpeed() { return movementSpeed; }
        public double getGravity() { return gravity; }
        public double getJumpStrength() { return jumpStrength; }
        public double getStepHeight() { return stepHeight; }
        public double getScale() { return scale; }
        public double getSneakingSpeed() { return sneakingSpeed; }
        public double getMovementEfficiency() { return movementEfficiency; }
        public double getWaterMovementEfficiency() { return waterMovementEfficiency; }
        public List<PacketUpdateAttributes.Property> getProperties() { return properties; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Attributes)) return false;
            Attributes value = (Attributes) other;
            return complete == value.complete
                    && Float.compare(movementSpeed, value.movementSpeed) == 0
                    && Double.compare(gravity, value.gravity) == 0
                    && Double.compare(jumpStrength, value.jumpStrength) == 0
                    && Double.compare(stepHeight, value.stepHeight) == 0
                    && Double.compare(scale, value.scale) == 0
                    && Double.compare(sneakingSpeed, value.sneakingSpeed) == 0
                    && Double.compare(movementEfficiency, value.movementEfficiency) == 0
                    && Double.compare(waterMovementEfficiency, value.waterMovementEfficiency) == 0
                    && properties.equals(value.properties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(complete, movementSpeed, gravity, jumpStrength, stepHeight, scale,
                    sneakingSpeed, movementEfficiency, waterMovementEfficiency, properties);
        }
    }

    public static final class Abilities {
        private final boolean canFly;
        private final boolean flying;
        private final float flySpeed;

        public Abilities(boolean canFly, boolean flying, float flySpeed) {
            require(Float.isFinite(flySpeed) && flySpeed >= 0.0f && flySpeed <= 1.0f,
                    "movement snapshot fly speed is out of range");
            require(!flying || canFly, "movement snapshot cannot fly without permission");
            this.canFly = canFly;
            this.flying = flying;
            this.flySpeed = flySpeed;
        }

        public static Abilities vanilla() {
            return new Abilities(false, false, 0.05f);
        }

        public boolean canFly() { return canFly; }
        public boolean isFlying() { return flying; }
        public float getFlySpeed() { return flySpeed; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Abilities)) return false;
            Abilities value = (Abilities) other;
            return canFly == value.canFly && flying == value.flying
                    && Float.compare(flySpeed, value.flySpeed) == 0;
        }

        @Override
        public int hashCode() { return Objects.hash(canFly, flying, flySpeed); }
    }

    public static final class UseItem {
        private final boolean using;
        private final boolean blocking;
        private final boolean eating;
        private final boolean drawing;
        private final boolean fishing;

        public UseItem(boolean using, boolean blocking, boolean eating, boolean drawing, boolean fishing) {
            require(using || !blocking && !eating && !drawing && !fishing,
                    "movement snapshot item-use detail lacks active use");
            this.using = using;
            this.blocking = blocking;
            this.eating = eating;
            this.drawing = drawing;
            this.fishing = fishing;
        }

        public static UseItem none() { return new UseItem(false, false, false, false, false); }
        public boolean isUsing() { return using; }
        public boolean isBlocking() { return blocking; }
        public boolean isEating() { return eating; }
        public boolean isDrawing() { return drawing; }
        public boolean isFishing() { return fishing; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof UseItem)) return false;
            UseItem value = (UseItem) other;
            return using == value.using && blocking == value.blocking && eating == value.eating
                    && drawing == value.drawing && fishing == value.fishing;
        }

        @Override
        public int hashCode() { return Objects.hash(using, blocking, eating, drawing, fishing); }
    }

    public static final class Vehicle {
        private final String vehicleType;
        private final int vehicleId;
        private final byte vehicleFlags;
        private final Double movementSpeed;
        private final Double jumpStrength;
        private final Boolean saddled;

        public Vehicle(
                String vehicleType,
                int vehicleId,
                byte vehicleFlags,
                Double movementSpeed,
                Double jumpStrength,
                Boolean saddled) {
            byte[] typeBytes = strictUtf8(vehicleType);
            require(typeBytes.length > 0 && typeBytes.length <= MAX_VEHICLE_TYPE_BYTES,
                    "movement snapshot vehicle type is invalid");
            require(vehicleId >= 0, "movement snapshot vehicle id is invalid");
            require(movementSpeed == null || Double.isFinite(movementSpeed) && movementSpeed >= 0.0,
                    "movement snapshot vehicle movement speed is invalid");
            require(jumpStrength == null || Double.isFinite(jumpStrength) && jumpStrength >= 0.0,
                    "movement snapshot vehicle jump strength is invalid");
            this.vehicleType = vehicleType;
            this.vehicleId = vehicleId;
            this.vehicleFlags = vehicleFlags;
            this.movementSpeed = movementSpeed;
            this.jumpStrength = jumpStrength;
            this.saddled = saddled;
        }

        public String getVehicleType() { return vehicleType; }
        public int getVehicleId() { return vehicleId; }
        public byte getVehicleFlags() { return vehicleFlags; }
        public Double getMovementSpeed() { return movementSpeed; }
        public Double getJumpStrength() { return jumpStrength; }
        public Boolean getSaddled() { return saddled; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Vehicle)) return false;
            Vehicle value = (Vehicle) other;
            return vehicleId == value.vehicleId && vehicleFlags == value.vehicleFlags
                    && vehicleType.equals(value.vehicleType)
                    && Objects.equals(movementSpeed, value.movementSpeed)
                    && Objects.equals(jumpStrength, value.jumpStrength)
                    && Objects.equals(saddled, value.saddled);
        }

        @Override
        public int hashCode() {
            return Objects.hash(vehicleType, vehicleId, vehicleFlags, movementSpeed, jumpStrength, saddled);
        }
    }

    public static final class Snapshot {
        private final int gamemode;
        private final Attributes attributes;
        private final Abilities abilities;
        private final boolean sprinting;
        private final boolean sneaking;
        private final boolean swimming;
        private final boolean fallFlying;
        private final UseItem useItem;
        private final Vehicle vehicle;
        private final List<Effect> effects;

        public Snapshot(
                int gamemode,
                Attributes attributes,
                Abilities abilities,
                boolean sprinting,
                boolean sneaking,
                boolean swimming,
                boolean fallFlying,
                UseItem useItem,
                Vehicle vehicle,
                List<Effect> effects) {
            require(gamemode >= 0 && gamemode <= 3, "movement snapshot gamemode is invalid");
            this.gamemode = gamemode;
            this.attributes = Objects.requireNonNull(attributes, "attributes");
            this.abilities = Objects.requireNonNull(abilities, "abilities");
            this.sprinting = sprinting;
            this.sneaking = sneaking;
            this.swimming = swimming;
            this.fallFlying = fallFlying;
            this.useItem = Objects.requireNonNull(useItem, "useItem");
            this.vehicle = vehicle;
            Objects.requireNonNull(effects, "effects");
            require(effects.size() <= MAX_EFFECTS, "movement snapshot effect count exceeds limit");
            List<Effect> copy = new ArrayList<Effect>(effects.size());
            Set<Integer> seen = new HashSet<Integer>();
            for (Effect effect : effects) {
                Effect value = Objects.requireNonNull(effect, "effect");
                require((value.getFlags() & 0xff) == 0,
                        "movement snapshot effect must use replacement value semantics");
                require(seen.add(value.getEffectId() & 0xff),
                        "movement snapshot contains duplicate effect");
                copy.add(value);
            }
            this.effects = Collections.unmodifiableList(copy);
        }

        public static Snapshot vanilla(boolean attributesComplete) {
            return new Snapshot(0, Attributes.vanilla(attributesComplete), Abilities.vanilla(),
                    false, false, false, false, UseItem.none(), null, Collections.<Effect>emptyList());
        }

        public int getGamemode() { return gamemode; }
        public Attributes getAttributes() { return attributes; }
        public Abilities getAbilities() { return abilities; }
        public boolean isSprinting() { return sprinting; }
        public boolean isSneaking() { return sneaking; }
        public boolean isSwimming() { return swimming; }
        public boolean isFallFlying() { return fallFlying; }
        public UseItem getUseItem() { return useItem; }
        public Vehicle getVehicle() { return vehicle; }
        public List<Effect> getEffects() { return effects; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Snapshot)) return false;
            Snapshot value = (Snapshot) other;
            return gamemode == value.gamemode && sprinting == value.sprinting
                    && sneaking == value.sneaking && swimming == value.swimming
                    && fallFlying == value.fallFlying && attributes.equals(value.attributes)
                    && abilities.equals(value.abilities) && useItem.equals(value.useItem)
                    && Objects.equals(vehicle, value.vehicle) && effects.equals(value.effects);
        }

        @Override
        public int hashCode() {
            return Objects.hash(gamemode, attributes, abilities, sprinting, sneaking, swimming,
                    fallFlying, useItem, vehicle, effects);
        }
    }

    private final byte schemaVersion;
    private final long generation;
    private final long sequence;
    private final int fragmentIndex;
    private final int fragmentCount;
    private final int totalPayloadLength;
    private final long payloadCrc32;
    private final byte[] fragmentPayload;

    private PacketMovementStateSnapshot(
            long timestamp,
            String uid,
            String username,
            int protocolVersion,
            byte schemaVersion,
            long generation,
            long sequence,
            int fragmentIndex,
            int fragmentCount,
            int totalPayloadLength,
            long payloadCrc32,
            byte[] fragmentPayload) {
        super(timestamp, uid, username, protocolVersion);
        this.schemaVersion = schemaVersion;
        this.generation = generation;
        this.sequence = sequence;
        this.fragmentIndex = fragmentIndex;
        this.fragmentCount = fragmentCount;
        this.totalPayloadLength = totalPayloadLength;
        this.payloadCrc32 = payloadCrc32;
        this.fragmentPayload = Objects.requireNonNull(fragmentPayload, "fragmentPayload").clone();
        validateFragment();
    }

    public static List<PacketMovementStateSnapshot> createFragments(
            long timestamp,
            String uid,
            String username,
            long generation,
            long sequence,
            Snapshot snapshot) {
        return createFragments(timestamp, uid, username, 0, generation, sequence, snapshot);
    }

    public static List<PacketMovementStateSnapshot> createFragments(
            long timestamp,
            String uid,
            String username,
            int protocolVersion,
            long generation,
            long sequence,
            Snapshot snapshot) {
        require(generation > 0 && sequence > 0, "movement snapshot version is invalid");
        byte[] payload = encodePayload(Objects.requireNonNull(snapshot, "snapshot"));
        long crc32 = crc32(payload);
        int headerLength;
        try {
            PacketMovementStateSnapshot probe = new PacketMovementStateSnapshot(
                    timestamp, uid, username, protocolVersion, SCHEMA_VERSION, generation, sequence,
                    0, 2, payload.length, crc32, new byte[] {0});
            headerLength = probe.encodedDatagramLength() - 1;
        } catch (IOException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
        int capacity = MAX_DATAGRAM_LENGTH - headerLength;
        require(capacity > 0, "movement snapshot header leaves no payload capacity");
        int count = (payload.length + capacity - 1) / capacity;
        require(count > 0 && count <= MAX_FRAGMENT_COUNT,
                "movement snapshot fragment count is out of range");
        List<PacketMovementStateSnapshot> result = new ArrayList<PacketMovementStateSnapshot>(count);
        for (int index = 0; index < count; index++) {
            int start = index * capacity;
            int end = Math.min(payload.length, start + capacity);
            byte[] part = new byte[end - start];
            System.arraycopy(payload, start, part, 0, part.length);
            result.add(new PacketMovementStateSnapshot(
                    timestamp, uid, username, protocolVersion, SCHEMA_VERSION, generation, sequence,
                    index, count, payload.length, crc32, part));
        }
        return Collections.unmodifiableList(result);
    }

    public static Snapshot reassemble(List<PacketMovementStateSnapshot> fragments) throws IOException {
        Objects.requireNonNull(fragments, "fragments");
        if (fragments.isEmpty()) throw new IOException("movement snapshot fragment set is empty");
        List<PacketMovementStateSnapshot> ordered = new ArrayList<PacketMovementStateSnapshot>(fragments);
        Collections.sort(ordered, new Comparator<PacketMovementStateSnapshot>() {
            @Override
            public int compare(PacketMovementStateSnapshot left, PacketMovementStateSnapshot right) {
                return Integer.compare(left.fragmentIndex, right.fragmentIndex);
            }
        });
        PacketMovementStateSnapshot first = ordered.get(0);
        if (ordered.size() != first.fragmentCount) {
            throw new IOException("movement snapshot fragment set has a gap");
        }
        ByteArrayOutputStream payload = new ByteArrayOutputStream(first.totalPayloadLength);
        for (int index = 0; index < ordered.size(); index++) {
            PacketMovementStateSnapshot fragment = ordered.get(index);
            try {
                fragment.validateFragment();
            } catch (IllegalArgumentException failure) {
                throw new IOException(failure.getMessage(), failure);
            }
            if (!first.sameFragmentSet(fragment)) {
                throw new IOException("movement snapshot metadata mismatch");
            }
            if (fragment.fragmentIndex != index) {
                throw new IOException("movement snapshot fragment duplicate or gap");
            }
            if (payload.size() + fragment.fragmentPayload.length > first.totalPayloadLength) {
                throw new IOException("movement snapshot reassembled payload exceeds declared length");
            }
            payload.write(fragment.fragmentPayload, 0, fragment.fragmentPayload.length);
        }
        byte[] bytes = payload.toByteArray();
        if (bytes.length != first.totalPayloadLength) {
            throw new IOException("movement snapshot reassembled payload length mismatch");
        }
        if (crc32(bytes) != first.payloadCrc32) {
            throw new IOException("movement snapshot payload CRC32 mismatch");
        }
        return decodePayload(bytes, first.schemaVersion);
    }

    public static PacketMovementStateSnapshot decodeDatagram(byte[] datagram) throws IOException {
        Objects.requireNonNull(datagram, "datagram");
        if (datagram.length > MAX_DATAGRAM_LENGTH) {
            throw new IOException("movement snapshot datagram exceeds byte limit");
        }
        Reader reader = new Reader(datagram);
        int id = reader.readUnsignedByte();
        if (id != (PacketId.PACKET_MOVEMENT_STATE_SNAPSHOT & 0xff)) {
            throw new IOException("unexpected movement snapshot packet id " + id);
        }
        long timestamp = reader.readLong();
        String uid = reader.readString();
        String username = reader.readString();
        int protocolFlag = reader.readUnsignedByte();
        int protocolVersion;
        if (protocolFlag == 0) {
            protocolVersion = 0;
        } else if (protocolFlag == 1) {
            long value = reader.readUnsignedInt();
            if (value > Integer.MAX_VALUE) throw new IOException("protocol version exceeds Java range");
            protocolVersion = (int) value;
        } else {
            throw new IOException("invalid packet base protocol flag " + protocolFlag);
        }
        int schemaVersion = reader.readUnsignedByte();
        long generation = reader.readLong();
        long sequence = reader.readLong();
        int fragmentIndex = reader.readUnsignedShort();
        int fragmentCount = reader.readUnsignedShort();
        long totalPayloadLength = reader.readUnsignedInt();
        long payloadCrc32 = reader.readUnsignedInt();
        int fragmentLength = reader.readUnsignedShort();
        if (fragmentLength != reader.remaining()) {
            throw new IOException("movement snapshot fragment payload length mismatch");
        }
        byte[] fragmentPayload = reader.readBytes(fragmentLength);
        try {
            return new PacketMovementStateSnapshot(
                    timestamp, uid, username, protocolVersion, (byte) schemaVersion, generation,
                    sequence, fragmentIndex, fragmentCount, (int) totalPayloadLength,
                    payloadCrc32, fragmentPayload);
        } catch (IllegalArgumentException failure) {
            throw new IOException(failure.getMessage(), failure);
        }
    }

    @Override
    public byte packetId() { return PacketId.PACKET_MOVEMENT_STATE_SNAPSHOT; }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        validateFragment();
        ByteArrayOutputStream datagram = new ByteArrayOutputStream();
        encodePlayerInfo(datagram);
        ByteBufferUtil.putByte(datagram, schemaVersion);
        ByteBufferUtil.putLong(datagram, generation);
        ByteBufferUtil.putLong(datagram, sequence);
        ByteBufferUtil.putShort(datagram, (short) fragmentIndex);
        ByteBufferUtil.putShort(datagram, (short) fragmentCount);
        ByteBufferUtil.putInt(datagram, totalPayloadLength);
        ByteBufferUtil.putInt(datagram, (int) payloadCrc32);
        ByteBufferUtil.putShort(datagram, (short) fragmentPayload.length);
        ByteBufferUtil.putBytes(datagram, fragmentPayload);
        if (datagram.size() > MAX_DATAGRAM_LENGTH) {
            throw new IOException("movement snapshot datagram exceeds byte limit");
        }
        datagram.writeTo(out);
    }

    public byte[] encodeDatagram() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        encode(out);
        return out.toByteArray();
    }

    public int encodedDatagramLength() throws IOException { return encodeDatagram().length; }
    public byte getSchemaVersion() { return schemaVersion; }
    public long getGeneration() { return generation; }
    public long getSequence() { return sequence; }
    public int getFragmentIndex() { return fragmentIndex; }
    public int getFragmentCount() { return fragmentCount; }
    public int getTotalPayloadLength() { return totalPayloadLength; }
    public long getPayloadCrc32() { return payloadCrc32; }
    public byte[] getFragmentPayload() { return fragmentPayload.clone(); }

    private void validateFragment() {
        require(schemaVersion == LEGACY_SCHEMA_VERSION || schemaVersion == SCHEMA_VERSION,
                "unsupported movement snapshot schema");
        require(generation > 0 && sequence > 0, "movement snapshot version is invalid");
        require(fragmentCount > 0 && fragmentCount <= MAX_FRAGMENT_COUNT,
                "movement snapshot fragment count is out of range");
        require(fragmentIndex >= 0 && fragmentIndex < fragmentCount,
                "movement snapshot fragment index is out of range");
        require(totalPayloadLength > 0 && totalPayloadLength <= MAX_PAYLOAD_LENGTH,
                "movement snapshot total payload length is out of range");
        require(fragmentCount <= totalPayloadLength && fragmentPayload.length > 0
                        && fragmentPayload.length <= totalPayloadLength,
                "movement snapshot fragment payload length is invalid");
        require(fragmentCount != 1 || fragmentPayload.length == totalPayloadLength,
                "movement snapshot single fragment length mismatch");
    }

    private boolean sameFragmentSet(PacketMovementStateSnapshot other) {
        return timestamp == other.timestamp && uid.equals(other.uid) && username.equals(other.username)
                && protocolVersion == other.protocolVersion && schemaVersion == other.schemaVersion
                && generation == other.generation && sequence == other.sequence
                && fragmentCount == other.fragmentCount
                && totalPayloadLength == other.totalPayloadLength && payloadCrc32 == other.payloadCrc32;
    }

    private static byte[] encodePayload(Snapshot snapshot) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteBufferUtil.putInt(out, snapshot.gamemode);
            ByteBufferUtil.putByte(out, bool(snapshot.attributes.complete));
            ByteBufferUtil.putFloat(out, snapshot.attributes.movementSpeed);
            ByteBufferUtil.putDouble(out, snapshot.attributes.gravity);
            ByteBufferUtil.putDouble(out, snapshot.attributes.jumpStrength);
            ByteBufferUtil.putDouble(out, snapshot.attributes.stepHeight);
            ByteBufferUtil.putDouble(out, snapshot.attributes.scale);
            ByteBufferUtil.putDouble(out, snapshot.attributes.sneakingSpeed);
            ByteBufferUtil.putDouble(out, snapshot.attributes.movementEfficiency);
            ByteBufferUtil.putDouble(out, snapshot.attributes.waterMovementEfficiency);
            ByteBufferUtil.putByte(out, (byte) snapshot.attributes.properties.size());
            for (PacketUpdateAttributes.Property property : snapshot.attributes.properties) {
                ByteBufferUtil.putString(out, property.getKey());
                ByteBufferUtil.putDouble(out, property.getBaseValue());
                ByteBufferUtil.putShort(out, (short) property.getModifiers().size());
                for (PacketUpdateAttributes.Modifier modifier : property.getModifiers()) {
                    ByteBufferUtil.putString(out, modifier.getStableId());
                    ByteBufferUtil.putString(out, modifier.getName());
                    ByteBufferUtil.putDouble(out, modifier.getAmount());
                    ByteBufferUtil.putByte(out, (byte) modifier.getOperation().ordinal());
                }
            }
            ByteBufferUtil.putByte(out, bool(snapshot.abilities.canFly));
            ByteBufferUtil.putByte(out, bool(snapshot.abilities.flying));
            ByteBufferUtil.putFloat(out, snapshot.abilities.flySpeed);
            int stateFlags = (snapshot.sprinting ? STATE_SPRINTING : 0)
                    | (snapshot.sneaking ? STATE_SNEAKING : 0)
                    | (snapshot.swimming ? STATE_SWIMMING : 0)
                    | (snapshot.fallFlying ? STATE_FALL_FLYING : 0);
            ByteBufferUtil.putByte(out, (byte) stateFlags);
            int useFlags = (snapshot.useItem.using ? USE_ITEM_USING : 0)
                    | (snapshot.useItem.blocking ? USE_ITEM_BLOCKING : 0)
                    | (snapshot.useItem.eating ? USE_ITEM_EATING : 0)
                    | (snapshot.useItem.drawing ? USE_ITEM_DRAWING : 0)
                    | (snapshot.useItem.fishing ? USE_ITEM_FISHING : 0);
            ByteBufferUtil.putByte(out, (byte) useFlags);
            if (snapshot.vehicle == null) {
                ByteBufferUtil.putByte(out, (byte) 0);
            } else {
                Vehicle vehicle = snapshot.vehicle;
                ByteBufferUtil.putByte(out, (byte) 1);
                ByteBufferUtil.putString(out, vehicle.vehicleType);
                ByteBufferUtil.putInt(out, vehicle.vehicleId);
                ByteBufferUtil.putByte(out, vehicle.vehicleFlags);
                int vehicleFlags = (vehicle.movementSpeed != null ? VEHICLE_MOVEMENT_SPEED : 0)
                        | (vehicle.jumpStrength != null ? VEHICLE_JUMP_STRENGTH : 0)
                        | (vehicle.saddled != null ? VEHICLE_SADDLE_KNOWN : 0)
                        | (Boolean.TRUE.equals(vehicle.saddled) ? VEHICLE_SADDLED : 0);
                ByteBufferUtil.putByte(out, (byte) vehicleFlags);
                if (vehicle.movementSpeed != null) ByteBufferUtil.putDouble(out, vehicle.movementSpeed);
                if (vehicle.jumpStrength != null) ByteBufferUtil.putDouble(out, vehicle.jumpStrength);
            }
            ByteBufferUtil.putShort(out, (short) snapshot.effects.size());
            for (Effect effect : snapshot.effects) effect.encode(out);
            byte[] payload = out.toByteArray();
            require(payload.length > 0 && payload.length <= MAX_PAYLOAD_LENGTH,
                    "movement snapshot payload exceeds limit");
            return payload;
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Snapshot decodePayload(byte[] payload, byte schemaVersion) throws IOException {
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_LENGTH) {
            throw new IOException("movement snapshot payload length is invalid");
        }
        Reader reader = new Reader(payload);
        int gamemode = reader.readInt();
        boolean complete = reader.readBoolean();
        float attributeMovementSpeed = reader.readFloat();
        double gravity = reader.readDouble();
        double attributeJumpStrength = reader.readDouble();
        double stepHeight = reader.readDouble();
        double scale = reader.readDouble();
        double sneakingSpeed = reader.readDouble();
        double movementEfficiency = reader.readDouble();
        double waterMovementEfficiency = reader.readDouble();
        int propertyCount = schemaVersion >= SCHEMA_VERSION ? reader.readUnsignedByte() : 0;
        if (propertyCount > 8) {
            throw new IOException("movement snapshot attribute property count exceeds limit");
        }
        List<PacketUpdateAttributes.Property> properties =
                new ArrayList<PacketUpdateAttributes.Property>(propertyCount);
        for (int propertyIndex = 0; propertyIndex < propertyCount; propertyIndex++) {
            String key = reader.readString();
            double baseValue = reader.readDouble();
            int modifierCount = reader.readUnsignedShort();
            if (modifierCount > 64) {
                throw new IOException("movement snapshot attribute modifier count exceeds limit");
            }
            List<PacketUpdateAttributes.Modifier> modifiers =
                    new ArrayList<PacketUpdateAttributes.Modifier>(modifierCount);
            for (int modifierIndex = 0; modifierIndex < modifierCount; modifierIndex++) {
                String stableId = reader.readString();
                String name = reader.readString();
                double amount = reader.readDouble();
                int operation = reader.readUnsignedByte();
                if (operation >= PacketUpdateAttributes.Operation.values().length) {
                    throw new IOException("movement snapshot attribute modifier operation is invalid");
                }
                modifiers.add(new PacketUpdateAttributes.Modifier(stableId, name, amount,
                        PacketUpdateAttributes.Operation.values()[operation]));
            }
            properties.add(new PacketUpdateAttributes.Property(key, baseValue, modifiers));
        }
        Attributes attributes = new Attributes(complete, attributeMovementSpeed, gravity,
                attributeJumpStrength,
                stepHeight, scale, sneakingSpeed, movementEfficiency,
                waterMovementEfficiency, properties);
        Abilities abilities = new Abilities(reader.readBoolean(), reader.readBoolean(), reader.readFloat());
        int stateFlags = reader.readUnsignedByte();
        if ((stateFlags & ~(STATE_SPRINTING | STATE_SNEAKING | STATE_SWIMMING | STATE_FALL_FLYING)) != 0) {
            throw new IOException("movement snapshot state flags are invalid");
        }
        int useFlags = reader.readUnsignedByte();
        if ((useFlags & ~(USE_ITEM_USING | USE_ITEM_BLOCKING | USE_ITEM_EATING
                | USE_ITEM_DRAWING | USE_ITEM_FISHING)) != 0) {
            throw new IOException("movement snapshot item-use flags are invalid");
        }
        UseItem useItem = new UseItem(
                (useFlags & USE_ITEM_USING) != 0,
                (useFlags & USE_ITEM_BLOCKING) != 0,
                (useFlags & USE_ITEM_EATING) != 0,
                (useFlags & USE_ITEM_DRAWING) != 0,
                (useFlags & USE_ITEM_FISHING) != 0);
        int vehiclePresence = reader.readUnsignedByte();
        Vehicle vehicle;
        if (vehiclePresence == 0) {
            vehicle = null;
        } else if (vehiclePresence == 1) {
            String type = reader.readString();
            int id = reader.readInt();
            byte flags = (byte) reader.readUnsignedByte();
            int presence = reader.readUnsignedByte();
            if ((presence & ~(VEHICLE_MOVEMENT_SPEED | VEHICLE_JUMP_STRENGTH
                    | VEHICLE_SADDLE_KNOWN | VEHICLE_SADDLED)) != 0
                    || (presence & VEHICLE_SADDLED) != 0 && (presence & VEHICLE_SADDLE_KNOWN) == 0) {
                throw new IOException("movement snapshot vehicle flags are invalid");
            }
            Double movementSpeed = (presence & VEHICLE_MOVEMENT_SPEED) != 0 ? reader.readDouble() : null;
            Double jumpStrength = (presence & VEHICLE_JUMP_STRENGTH) != 0 ? reader.readDouble() : null;
            Boolean saddled = (presence & VEHICLE_SADDLE_KNOWN) != 0
                    ? Boolean.valueOf((presence & VEHICLE_SADDLED) != 0) : null;
            vehicle = new Vehicle(type, id, flags, movementSpeed, jumpStrength, saddled);
        } else {
            throw new IOException("movement snapshot vehicle presence is invalid");
        }
        int effectCount = reader.readUnsignedShort();
        if (effectCount > MAX_EFFECTS) throw new IOException("movement snapshot effect count exceeds limit");
        List<Effect> effects = new ArrayList<Effect>(effectCount);
        for (int index = 0; index < effectCount; index++) {
            effects.add(new Effect((byte) reader.readUnsignedByte(), (byte) reader.readUnsignedByte(),
                    reader.readInt(), (byte) reader.readUnsignedByte()));
        }
        if (reader.remaining() != 0) throw new IOException("movement snapshot payload has trailing bytes");
        try {
            return new Snapshot(gamemode, attributes, abilities,
                    (stateFlags & STATE_SPRINTING) != 0,
                    (stateFlags & STATE_SNEAKING) != 0,
                    (stateFlags & STATE_SWIMMING) != 0,
                    (stateFlags & STATE_FALL_FLYING) != 0,
                    useItem, vehicle, effects);
        } catch (IllegalArgumentException failure) {
            throw new IOException(failure.getMessage(), failure);
        }
    }

    private static byte bool(boolean value) { return (byte) (value ? 1 : 0); }

    private static long crc32(byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload, 0, payload.length);
        return crc.getValue();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static byte[] strictUtf8(String value) {
        Objects.requireNonNull(value, "value");
        try {
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value));
            byte[] result = new byte[bytes.remaining()];
            bytes.get(result);
            return result;
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("string is not valid UTF-8", failure);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PacketMovementStateSnapshot)) return false;
        PacketMovementStateSnapshot value = (PacketMovementStateSnapshot) other;
        return timestamp == value.timestamp && protocolVersion == value.protocolVersion
                && schemaVersion == value.schemaVersion && generation == value.generation
                && sequence == value.sequence && fragmentIndex == value.fragmentIndex
                && fragmentCount == value.fragmentCount && totalPayloadLength == value.totalPayloadLength
                && payloadCrc32 == value.payloadCrc32 && uid.equals(value.uid)
                && username.equals(value.username)
                && java.util.Arrays.equals(fragmentPayload, value.fragmentPayload);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(timestamp, uid, username, protocolVersion, schemaVersion, generation,
                sequence, fragmentIndex, fragmentCount, totalPayloadLength, payloadCrc32)
                + java.util.Arrays.hashCode(fragmentPayload);
    }

    private static final class Reader {
        private final ByteBuffer input;

        private Reader(byte[] bytes) { this.input = ByteBuffer.wrap(bytes); }
        private int remaining() { return input.remaining(); }
        private void requireBytes(int count) throws IOException {
            if (count < 0 || input.remaining() < count) throw new IOException("truncated movement snapshot");
        }
        private int readUnsignedByte() throws IOException { requireBytes(1); return input.get() & 0xff; }
        private boolean readBoolean() throws IOException {
            int value = readUnsignedByte();
            if (value == 0) return false;
            if (value == 1) return true;
            throw new IOException("movement snapshot boolean is invalid");
        }
        private int readUnsignedShort() throws IOException { requireBytes(2); return input.getShort() & 0xffff; }
        private int readInt() throws IOException { requireBytes(4); return input.getInt(); }
        private long readUnsignedInt() throws IOException { return Integer.toUnsignedLong(readInt()); }
        private long readLong() throws IOException { requireBytes(8); return input.getLong(); }
        private float readFloat() throws IOException { requireBytes(4); return input.getFloat(); }
        private double readDouble() throws IOException { requireBytes(8); return input.getDouble(); }
        private byte[] readBytes(int count) throws IOException {
            requireBytes(count);
            byte[] result = new byte[count];
            input.get(result);
            return result;
        }
        private String readString() throws IOException {
            int length = readUnsignedShort();
            byte[] bytes = readBytes(length);
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException failure) {
                throw new IOException("invalid UTF-8 string", failure);
            }
        }
    }
}
