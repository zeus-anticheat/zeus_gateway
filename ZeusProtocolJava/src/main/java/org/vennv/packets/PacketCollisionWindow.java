package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

public final class PacketCollisionWindow extends PacketBaseInfo {
    public static final byte SCHEMA_VERSION = 1;
    public static final int COLLISION_WINDOW_RADIUS = 4;
    public static final int COLLISION_WINDOW_EDGE = 9;
    public static final int COLLISION_WINDOW_CELLS = 729;
    public static final int COLLISION_WINDOW_MASK_BYTES = 92;
    public static final int MAX_DATAGRAM_LENGTH = 1200;
    public static final int MAX_FRAGMENT_COUNT = 64;
    public static final int MAX_PAYLOAD_LENGTH = 65_536;
    public static final int MAX_PALETTE_ENTRIES = 729;
    public static final int MAX_BLOCK_STATE_BYTES = 512;

    public enum Kind {
        FULL(0),
        DELTA(1);

        private final int wireValue;

        Kind(int wireValue) {
            this.wireValue = wireValue;
        }

        public int getWireValue() {
            return wireValue;
        }

        private static Kind fromWire(int value) throws IOException {
            if (value == 0) {
                return FULL;
            }
            if (value == 1) {
                return DELTA;
            }
            throw new IOException("invalid collision window kind " + value);
        }
    }

    public enum Encoding {
        DENSE(0),
        SPARSE(1);

        private final int wireValue;

        Encoding(int wireValue) {
            this.wireValue = wireValue;
        }

        public int getWireValue() {
            return wireValue;
        }

        private static Encoding fromWire(int value) throws IOException {
            if (value == 0) {
                return DENSE;
            }
            if (value == 1) {
                return SPARSE;
            }
            throw new IOException("invalid collision window encoding " + value);
        }
    }

    public enum CellType {
        UNKNOWN,
        KNOWN_AIR,
        KNOWN_BLOCK
    }

    public static final class Cell {
        private static final Cell UNKNOWN = new Cell(CellType.UNKNOWN, null);
        private static final Cell KNOWN_AIR = new Cell(CellType.KNOWN_AIR, null);

        private final CellType type;
        private final String blockState;

        private Cell(CellType type, String blockState) {
            this.type = Objects.requireNonNull(type, "type");
            this.blockState = blockState;
            if (type == CellType.KNOWN_BLOCK) {
                if (blockState == null) {
                    throw new IllegalArgumentException("known block requires a block state");
                }
                if (strictUtf8(blockState).length > MAX_BLOCK_STATE_BYTES) {
                    throw new IllegalArgumentException("collision block state exceeds byte limit");
                }
            } else if (blockState != null) {
                throw new IllegalArgumentException("non-block cell cannot contain a block state");
            }
        }

        public static Cell unknown() {
            return UNKNOWN;
        }

        public static Cell knownAir() {
            return KNOWN_AIR;
        }

        public static Cell knownBlock(String blockState) {
            return new Cell(CellType.KNOWN_BLOCK, blockState);
        }

        public CellType getType() {
            return type;
        }

        public String getBlockState() {
            return blockState;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Cell)) {
                return false;
            }
            Cell cell = (Cell) other;
            return type == cell.type && Objects.equals(blockState, cell.blockState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, blockState);
        }
    }

    public static final class CellUpdate {
        private final int index;
        private final Cell cell;

        public CellUpdate(int index, Cell cell) {
            if (index < 0 || index >= COLLISION_WINDOW_CELLS) {
                throw new IllegalArgumentException("collision cell index is out of range");
            }
            this.index = index;
            this.cell = Objects.requireNonNull(cell, "cell");
        }

        public int getIndex() {
            return index;
        }

        public Cell getCell() {
            return cell;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CellUpdate)) {
                return false;
            }
            CellUpdate update = (CellUpdate) other;
            return index == update.index && cell.equals(update.cell);
        }

        @Override
        public int hashCode() {
            return Objects.hash(index, cell);
        }
    }

    public static final class EncodedPayload {
        private final Encoding encoding;
        private final byte[] payload;
        private final long crc32;

        private EncodedPayload(Encoding encoding, byte[] payload) {
            this.encoding = Objects.requireNonNull(encoding, "encoding");
            this.payload = payload.clone();
            this.crc32 = collisionPayloadCrc32(this.payload);
        }

        public Encoding getEncoding() {
            return encoding;
        }

        public byte[] getPayload() {
            return payload.clone();
        }

        public int getPayloadLength() {
            return payload.length;
        }

        public long getCrc32() {
            return crc32;
        }
    }

    public static final class CollisionWindowUpdate {
        private final Kind kind;
        private final long generation;
        private final long sequence;
        private final long baseSequence;
        private final int baseCenterX;
        private final int baseCenterY;
        private final int baseCenterZ;
        private final int centerX;
        private final int centerY;
        private final int centerZ;
        private final List<CellUpdate> cells;

        private CollisionWindowUpdate(
            Kind kind,
            long generation,
            long sequence,
            long baseSequence,
            int baseCenterX,
            int baseCenterY,
            int baseCenterZ,
            int centerX,
            int centerY,
            int centerZ,
            List<CellUpdate> cells
        ) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.generation = generation;
            this.sequence = sequence;
            this.baseSequence = baseSequence;
            this.baseCenterX = baseCenterX;
            this.baseCenterY = baseCenterY;
            this.baseCenterZ = baseCenterZ;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.cells = normalizeUpdates(cells);
            validateUpdateSet(this);
        }

        public static CollisionWindowUpdate full(
            long generation,
            long sequence,
            int centerX,
            int centerY,
            int centerZ,
            List<Cell> cells
        ) {
            Objects.requireNonNull(cells, "cells");
            if (cells.size() != COLLISION_WINDOW_CELLS) {
                throw new IllegalArgumentException("full collision window must contain exactly 729 cells");
            }
            List<CellUpdate> updates = new ArrayList<CellUpdate>(COLLISION_WINDOW_CELLS);
            for (int index = 0; index < cells.size(); index++) {
                updates.add(new CellUpdate(index, Objects.requireNonNull(cells.get(index), "cell")));
            }
            return new CollisionWindowUpdate(
                Kind.FULL,
                generation,
                sequence,
                0,
                centerX,
                centerY,
                centerZ,
                centerX,
                centerY,
                centerZ,
                updates
            );
        }

        public static CollisionWindowUpdate delta(
            long generation,
            long sequence,
            long baseSequence,
            int baseCenterX,
            int baseCenterY,
            int baseCenterZ,
            int centerX,
            int centerY,
            int centerZ,
            List<CellUpdate> cells
        ) {
            return new CollisionWindowUpdate(
                Kind.DELTA,
                generation,
                sequence,
                baseSequence,
                baseCenterX,
                baseCenterY,
                baseCenterZ,
                centerX,
                centerY,
                centerZ,
                cells
            );
        }

        public EncodedPayload encodePayload() {
            return encodeCollisionPayload(this);
        }

        public List<PacketCollisionWindow> toFragments(
            long timestamp,
            String uid,
            String username
        ) {
            return createFragments(this, timestamp, uid, username, null);
        }

        public List<PacketCollisionWindow> toFragments(
            long timestamp,
            String uid,
            String username,
            int protocolVersion
        ) {
            Long version = protocolVersion > 0 ? Long.valueOf(protocolVersion) : null;
            return createFragments(this, timestamp, uid, username, version);
        }

        public List<PacketCollisionWindow> toFragments(
            long timestamp,
            String uid,
            String username,
            Long protocolVersion
        ) {
            return createFragments(this, timestamp, uid, username, protocolVersion);
        }

        public Kind getKind() {
            return kind;
        }

        public long getGeneration() {
            return generation;
        }

        public long getSequence() {
            return sequence;
        }

        public long getBaseSequence() {
            return baseSequence;
        }

        public int getBaseCenterX() {
            return baseCenterX;
        }

        public int getBaseCenterY() {
            return baseCenterY;
        }

        public int getBaseCenterZ() {
            return baseCenterZ;
        }

        public int getCenterX() {
            return centerX;
        }

        public int getCenterY() {
            return centerY;
        }

        public int getCenterZ() {
            return centerZ;
        }

        public List<CellUpdate> getCells() {
            return cells;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CollisionWindowUpdate)) {
                return false;
            }
            CollisionWindowUpdate update = (CollisionWindowUpdate) other;
            return generation == update.generation
                && sequence == update.sequence
                && baseSequence == update.baseSequence
                && baseCenterX == update.baseCenterX
                && baseCenterY == update.baseCenterY
                && baseCenterZ == update.baseCenterZ
                && centerX == update.centerX
                && centerY == update.centerY
                && centerZ == update.centerZ
                && kind == update.kind
                && cells.equals(update.cells);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                kind,
                generation,
                sequence,
                baseSequence,
                baseCenterX,
                baseCenterY,
                baseCenterZ,
                centerX,
                centerY,
                centerZ,
                cells
            );
        }
    }

    private final Long wireProtocolVersion;
    private final byte schemaVersion;
    private final Kind kind;
    private final Encoding encoding;
    private final long generation;
    private final long sequence;
    private final long baseSequence;
    private final int baseCenterX;
    private final int baseCenterY;
    private final int baseCenterZ;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int fragmentIndex;
    private final int fragmentCount;
    private final int totalPayloadLength;
    private final long payloadCrc32;
    private final byte[] fragmentPayload;

    private PacketCollisionWindow(
        long timestamp,
        String uid,
        String username,
        Long protocolVersion,
        byte schemaVersion,
        Kind kind,
        Encoding encoding,
        long generation,
        long sequence,
        long baseSequence,
        int baseCenterX,
        int baseCenterY,
        int baseCenterZ,
        int centerX,
        int centerY,
        int centerZ,
        int fragmentIndex,
        int fragmentCount,
        int totalPayloadLength,
        long payloadCrc32,
        byte[] fragmentPayload,
        boolean validate
    ) {
        super(timestamp, uid, username, protocolVersion == null ? 0 : (int) (long) protocolVersion);
        this.wireProtocolVersion = protocolVersion;
        this.schemaVersion = schemaVersion;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.encoding = Objects.requireNonNull(encoding, "encoding");
        this.generation = generation;
        this.sequence = sequence;
        this.baseSequence = baseSequence;
        this.baseCenterX = baseCenterX;
        this.baseCenterY = baseCenterY;
        this.baseCenterZ = baseCenterZ;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.fragmentIndex = fragmentIndex;
        this.fragmentCount = fragmentCount;
        this.totalPayloadLength = totalPayloadLength;
        this.payloadCrc32 = payloadCrc32;
        this.fragmentPayload = Objects.requireNonNull(fragmentPayload, "fragmentPayload").clone();
        validateBase(uid, username, protocolVersion);
        if (validate) {
            validateFragment();
        }
    }

    public static int collisionWindowIndex(int dx, int dy, int dz) {
        if (Math.abs((long) dx) > COLLISION_WINDOW_RADIUS
            || Math.abs((long) dy) > COLLISION_WINDOW_RADIUS
            || Math.abs((long) dz) > COLLISION_WINDOW_RADIUS) {
            throw new IllegalArgumentException("collision window offset is out of range");
        }
        return (dy + COLLISION_WINDOW_RADIUS) * COLLISION_WINDOW_EDGE * COLLISION_WINDOW_EDGE
            + (dz + COLLISION_WINDOW_RADIUS) * COLLISION_WINDOW_EDGE
            + dx
            + COLLISION_WINDOW_RADIUS;
    }

    public static int[] collisionWindowOffsets(int index) {
        if (index < 0 || index >= COLLISION_WINDOW_CELLS) {
            throw new IllegalArgumentException("collision cell index is out of range");
        }
        int dy = index / (COLLISION_WINDOW_EDGE * COLLISION_WINDOW_EDGE);
        int remainder = index % (COLLISION_WINDOW_EDGE * COLLISION_WINDOW_EDGE);
        int dz = remainder / COLLISION_WINDOW_EDGE;
        int dx = remainder % COLLISION_WINDOW_EDGE;
        return new int[] {
            dx - COLLISION_WINDOW_RADIUS,
            dy - COLLISION_WINDOW_RADIUS,
            dz - COLLISION_WINDOW_RADIUS
        };
    }

    public static int[] collisionWindowPosition(int centerX, int centerY, int centerZ, int index) {
        int[] offsets = collisionWindowOffsets(index);
        return new int[] {
            Math.addExact(centerX, offsets[0]),
            Math.addExact(centerY, offsets[1]),
            Math.addExact(centerZ, offsets[2])
        };
    }

    public static int[] collisionWindowCenter(double x, double y, double z) {
        return new int[] {floorCoordinate(x), floorCoordinate(y), floorCoordinate(z)};
    }

    public static List<Integer> enteringCellIndices(
        int baseCenterX,
        int baseCenterY,
        int baseCenterZ,
        int centerX,
        int centerY,
        int centerZ
    ) {
        long shiftX = (long) centerX - baseCenterX;
        long shiftY = (long) centerY - baseCenterY;
        long shiftZ = (long) centerZ - baseCenterZ;
        if (Math.abs(shiftX) >= COLLISION_WINDOW_EDGE
            || Math.abs(shiftY) >= COLLISION_WINDOW_EDGE
            || Math.abs(shiftZ) >= COLLISION_WINDOW_EDGE) {
            throw new IllegalArgumentException("collision window delta centers do not overlap");
        }
        List<Integer> entering = new ArrayList<Integer>();
        for (int index = 0; index < COLLISION_WINDOW_CELLS; index++) {
            int[] offsets = collisionWindowOffsets(index);
            long oldX = shiftX + offsets[0];
            long oldY = shiftY + offsets[1];
            long oldZ = shiftZ + offsets[2];
            if (Math.abs(oldX) > COLLISION_WINDOW_RADIUS
                || Math.abs(oldY) > COLLISION_WINDOW_RADIUS
                || Math.abs(oldZ) > COLLISION_WINDOW_RADIUS) {
                entering.add(index);
            }
        }
        return Collections.unmodifiableList(entering);
    }

    public static long collisionPayloadCrc32(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        CRC32 crc32 = new CRC32();
        crc32.update(payload, 0, payload.length);
        return crc32.getValue();
    }

    public static EncodedPayload encodeCollisionPayload(CollisionWindowUpdate update) {
        Objects.requireNonNull(update, "update");
        validateUpdateSet(update);
        List<CellUpdate> updates = normalizeUpdates(update.cells);
        List<String> palette = buildPalette(updates);
        byte[] dense = encodeDense(updates, palette);
        byte[] sparse = encodeSparse(updates, palette);
        EncodedPayload encoded = dense.length <= sparse.length
            ? new EncodedPayload(Encoding.DENSE, dense)
            : new EncodedPayload(Encoding.SPARSE, sparse);
        if (encoded.payload.length > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("collision logical payload exceeds byte limit");
        }
        return encoded;
    }

    public static List<CellUpdate> decodeCollisionPayload(
        Kind kind,
        Encoding encoding,
        int baseCenterX,
        int baseCenterY,
        int baseCenterZ,
        int centerX,
        int centerY,
        int centerZ,
        byte[] payload
    ) throws IOException {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(payload, "payload");
        if (payload.length > MAX_PAYLOAD_LENGTH) {
            throw new IOException("collision logical payload exceeds byte limit");
        }
        Reader reader = new Reader(payload);
        List<String> palette = decodePalette(reader);
        List<CellUpdate> updates = encoding == Encoding.DENSE
            ? decodeDense(reader, palette)
            : decodeSparse(reader, palette);
        if (reader.remaining() != 0) {
            throw new IOException("collision logical payload has trailing bytes");
        }
        try {
            new CollisionWindowUpdate(
                kind,
                0,
                0,
                kind == Kind.FULL ? 0 : 1,
                baseCenterX,
                baseCenterY,
                baseCenterZ,
                centerX,
                centerY,
                centerZ,
                updates
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        return Collections.unmodifiableList(new ArrayList<CellUpdate>(updates));
    }

    public static PacketCollisionWindow decodeDatagram(byte[] datagram) throws IOException {
        Objects.requireNonNull(datagram, "datagram");
        if (datagram.length > MAX_DATAGRAM_LENGTH) {
            throw new IOException("collision datagram exceeds byte limit");
        }
        Reader reader = new Reader(datagram);
        int packetId = reader.readUnsignedByte();
        if (packetId != (PacketId.PACKET_COLLISION_WINDOW & 0xff)) {
            throw new IOException("unexpected collision window packet id " + packetId);
        }
        long timestamp = reader.readLong();
        String uid = reader.readString("packet base");
        String username = reader.readString("packet base");
        int protocolFlag = reader.readUnsignedByte();
        Long protocolVersion;
        if (protocolFlag == 0) {
            protocolVersion = null;
        } else if (protocolFlag == 1) {
            protocolVersion = reader.readUnsignedInt();
        } else {
            throw new IOException("invalid packet base protocol flag " + protocolFlag);
        }
        int schemaVersion = reader.readUnsignedByte();
        if (schemaVersion != (SCHEMA_VERSION & 0xff)) {
            throw new IOException("unsupported collision window schema " + schemaVersion);
        }
        Kind kind = Kind.fromWire(reader.readUnsignedByte());
        Encoding encoding = Encoding.fromWire(reader.readUnsignedByte());
        long generation = reader.readLong();
        long sequence = reader.readLong();
        long baseSequence = reader.readLong();
        int baseCenterX = reader.readInt();
        int baseCenterY = reader.readInt();
        int baseCenterZ = reader.readInt();
        int centerX = reader.readInt();
        int centerY = reader.readInt();
        int centerZ = reader.readInt();
        int fragmentIndex = reader.readUnsignedShort();
        int fragmentCount = reader.readUnsignedShort();
        long totalPayloadLength = reader.readUnsignedInt();
        if (totalPayloadLength > MAX_PAYLOAD_LENGTH) {
            throw new IOException("collision total payload length exceeds byte limit");
        }
        long payloadCrc32 = reader.readUnsignedInt();
        int fragmentPayloadLength = reader.readUnsignedShort();
        if (fragmentPayloadLength != reader.remaining()) {
            throw new IOException("collision fragment payload length mismatch");
        }
        byte[] fragmentPayload = reader.readBytes(fragmentPayloadLength);
        try {
            return new PacketCollisionWindow(
                timestamp,
                uid,
                username,
                protocolVersion,
                (byte) schemaVersion,
                kind,
                encoding,
                generation,
                sequence,
                baseSequence,
                baseCenterX,
                baseCenterY,
                baseCenterZ,
                centerX,
                centerY,
                centerZ,
                fragmentIndex,
                fragmentCount,
                (int) totalPayloadLength,
                payloadCrc32,
                fragmentPayload,
                true
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
    }

    public static CollisionWindowUpdate reassemble(List<PacketCollisionWindow> fragments)
        throws IOException {
        Objects.requireNonNull(fragments, "fragments");
        if (fragments.isEmpty()) {
            throw new IOException("collision fragment set is empty");
        }
        List<PacketCollisionWindow> ordered = new ArrayList<PacketCollisionWindow>(fragments.size());
        for (PacketCollisionWindow fragment : fragments) {
            ordered.add(Objects.requireNonNull(fragment, "fragment"));
        }
        Collections.sort(ordered, new Comparator<PacketCollisionWindow>() {
            @Override
            public int compare(PacketCollisionWindow left, PacketCollisionWindow right) {
                return Integer.compare(left.fragmentIndex, right.fragmentIndex);
            }
        });
        PacketCollisionWindow first = ordered.get(0);
        if (ordered.size() != first.fragmentCount) {
            throw new IOException("collision fragment set has a gap");
        }
        int assembledLength = 0;
        for (int index = 0; index < ordered.size(); index++) {
            PacketCollisionWindow fragment = ordered.get(index);
            try {
                fragment.validateFragment();
            } catch (IllegalArgumentException exception) {
                throw new IOException(exception.getMessage(), exception);
            }
            if (!first.hasSameMetadata(fragment)) {
                throw new IOException("collision fragment metadata mismatch");
            }
            if (fragment.fragmentIndex != index) {
                throw new IOException("collision fragment duplicate or gap");
            }
            assembledLength = Math.addExact(assembledLength, fragment.fragmentPayload.length);
            if (assembledLength > first.totalPayloadLength) {
                throw new IOException("collision reassembled payload exceeds declared length");
            }
        }
        if (assembledLength != first.totalPayloadLength) {
            throw new IOException("collision reassembled payload length mismatch");
        }
        ByteArrayOutputStream payload = new ByteArrayOutputStream(assembledLength);
        for (PacketCollisionWindow fragment : ordered) {
            payload.write(fragment.fragmentPayload, 0, fragment.fragmentPayload.length);
        }
        byte[] bytes = payload.toByteArray();
        if (collisionPayloadCrc32(bytes) != first.payloadCrc32) {
            throw new IOException("collision payload CRC32 mismatch");
        }
        List<CellUpdate> cells = decodeCollisionPayload(
            first.kind,
            first.encoding,
            first.baseCenterX,
            first.baseCenterY,
            first.baseCenterZ,
            first.centerX,
            first.centerY,
            first.centerZ,
            bytes
        );
        try {
            return new CollisionWindowUpdate(
                first.kind,
                first.generation,
                first.sequence,
                first.baseSequence,
                first.baseCenterX,
                first.baseCenterY,
                first.baseCenterZ,
                first.centerX,
                first.centerY,
                first.centerZ,
                cells
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_COLLISION_WINDOW;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        Objects.requireNonNull(out, "out");
        validateFragment();
        ByteArrayOutputStream datagram = new ByteArrayOutputStream();
        encodeUnchecked(datagram);
        if (datagram.size() > MAX_DATAGRAM_LENGTH) {
            throw new IOException("collision datagram exceeds byte limit");
        }
        datagram.writeTo(out);
    }

    public byte[] encodeDatagram() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        encode(out);
        return out.toByteArray();
    }

    public int encodedDatagramLength() throws IOException {
        return encodeDatagram().length;
    }

    public Long getOptionalProtocolVersion() {
        return wireProtocolVersion;
    }

    public byte getSchemaVersion() {
        return schemaVersion;
    }

    public Kind getKind() {
        return kind;
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public long getGeneration() {
        return generation;
    }

    public long getSequence() {
        return sequence;
    }

    public long getBaseSequence() {
        return baseSequence;
    }

    public int getBaseCenterX() {
        return baseCenterX;
    }

    public int getBaseCenterY() {
        return baseCenterY;
    }

    public int getBaseCenterZ() {
        return baseCenterZ;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getFragmentIndex() {
        return fragmentIndex;
    }

    public int getFragmentCount() {
        return fragmentCount;
    }

    public int getTotalPayloadLength() {
        return totalPayloadLength;
    }

    public long getPayloadCrc32() {
        return payloadCrc32;
    }

    public byte[] getFragmentPayload() {
        return fragmentPayload.clone();
    }

    private static List<PacketCollisionWindow> createFragments(
        CollisionWindowUpdate update,
        long timestamp,
        String uid,
        String username,
        Long protocolVersion
    ) {
        Objects.requireNonNull(update, "update");
        EncodedPayload encoded = update.encodePayload();
        PacketCollisionWindow probe = new PacketCollisionWindow(
            timestamp,
            uid,
            username,
            protocolVersion,
            SCHEMA_VERSION,
            update.kind,
            encoded.encoding,
            update.generation,
            update.sequence,
            update.baseSequence,
            update.baseCenterX,
            update.baseCenterY,
            update.baseCenterZ,
            update.centerX,
            update.centerY,
            update.centerZ,
            0,
            1,
            encoded.payload.length,
            encoded.crc32,
            new byte[0],
            false
        );
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        probe.encodeUnchecked(header);
        if (header.size() >= MAX_DATAGRAM_LENGTH) {
            throw new IllegalArgumentException("collision packet base and header leave no payload capacity");
        }
        int capacity = MAX_DATAGRAM_LENGTH - header.size();
        int fragmentCount = (encoded.payload.length + capacity - 1) / capacity;
        if (fragmentCount < 1 || fragmentCount > MAX_FRAGMENT_COUNT) {
            throw new IllegalArgumentException("collision fragment count is out of range");
        }
        List<PacketCollisionWindow> fragments = new ArrayList<PacketCollisionWindow>(fragmentCount);
        for (int fragmentIndex = 0; fragmentIndex < fragmentCount; fragmentIndex++) {
            int from = fragmentIndex * capacity;
            int to = Math.min(from + capacity, encoded.payload.length);
            fragments.add(new PacketCollisionWindow(
                timestamp,
                uid,
                username,
                protocolVersion,
                SCHEMA_VERSION,
                update.kind,
                encoded.encoding,
                update.generation,
                update.sequence,
                update.baseSequence,
                update.baseCenterX,
                update.baseCenterY,
                update.baseCenterZ,
                update.centerX,
                update.centerY,
                update.centerZ,
                fragmentIndex,
                fragmentCount,
                encoded.payload.length,
                encoded.crc32,
                Arrays.copyOfRange(encoded.payload, from, to),
                true
            ));
        }
        return Collections.unmodifiableList(fragments);
    }

    private static int floorCoordinate(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("collision window coordinate is not finite");
        }
        double floor = Math.floor(value);
        if (floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("collision window coordinate is out of range");
        }
        return (int) floor;
    }

    private static List<CellUpdate> normalizeUpdates(List<CellUpdate> updates) {
        Objects.requireNonNull(updates, "cells");
        if (updates.size() > COLLISION_WINDOW_CELLS) {
            throw new IllegalArgumentException("collision update count exceeds cell count");
        }
        List<CellUpdate> normalized = new ArrayList<CellUpdate>(updates.size());
        for (CellUpdate update : updates) {
            normalized.add(Objects.requireNonNull(update, "cell update"));
        }
        Collections.sort(normalized, new Comparator<CellUpdate>() {
            @Override
            public int compare(CellUpdate left, CellUpdate right) {
                return Integer.compare(left.index, right.index);
            }
        });
        for (int index = 1; index < normalized.size(); index++) {
            if (normalized.get(index - 1).index == normalized.get(index).index) {
                throw new IllegalArgumentException("duplicate collision cell index");
            }
        }
        return Collections.unmodifiableList(normalized);
    }

    private static void validateUpdateSet(CollisionWindowUpdate update) {
        if (update.kind == Kind.FULL) {
            if (update.baseSequence != 0) {
                throw new IllegalArgumentException("full collision window base sequence must be zero");
            }
            if (update.baseCenterX != update.centerX
                || update.baseCenterY != update.centerY
                || update.baseCenterZ != update.centerZ) {
                throw new IllegalArgumentException("full collision window base center must equal center");
            }
            if (update.cells.size() != COLLISION_WINDOW_CELLS) {
                throw new IllegalArgumentException("collision update set does not match required cells");
            }
            for (int index = 0; index < COLLISION_WINDOW_CELLS; index++) {
                if (update.cells.get(index).index != index) {
                    throw new IllegalArgumentException("collision update set does not match required cells");
                }
            }
            return;
        }
        List<Integer> expected = enteringCellIndices(
            update.baseCenterX,
            update.baseCenterY,
            update.baseCenterZ,
            update.centerX,
            update.centerY,
            update.centerZ
        );
        if (expected.size() != update.cells.size()) {
            throw new IllegalArgumentException("collision update set does not match required cells");
        }
        for (int index = 0; index < expected.size(); index++) {
            if (expected.get(index).intValue() != update.cells.get(index).index) {
                throw new IllegalArgumentException("collision update set does not match required cells");
            }
        }
    }

    private static List<String> buildPalette(List<CellUpdate> updates) {
        List<String> palette = new ArrayList<String>();
        for (CellUpdate update : updates) {
            if (update.cell.type == CellType.KNOWN_BLOCK) {
                palette.add(update.cell.blockState);
            }
        }
        Collections.sort(palette, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return compareUnsigned(strictUtf8(left), strictUtf8(right));
            }
        });
        List<String> deduplicated = new ArrayList<String>(palette.size());
        for (String state : palette) {
            if (deduplicated.isEmpty()
                || compareUnsigned(
                    strictUtf8(deduplicated.get(deduplicated.size() - 1)),
                    strictUtf8(state)
                ) != 0) {
                deduplicated.add(state);
            }
        }
        if (deduplicated.size() > MAX_PALETTE_ENTRIES) {
            throw new IllegalArgumentException("collision palette exceeds entry limit");
        }
        return deduplicated;
    }

    private static byte[] encodeDense(List<CellUpdate> updates, List<String> palette) {
        byte[] present = new byte[COLLISION_WINDOW_MASK_BYTES];
        byte[] known = new byte[COLLISION_WINDOW_MASK_BYTES];
        byte[] block = new byte[COLLISION_WINDOW_MASK_BYTES];
        List<Integer> blockIndexes = new ArrayList<Integer>();
        for (CellUpdate update : updates) {
            maskSet(present, update.index);
            if (update.cell.type == CellType.KNOWN_AIR) {
                maskSet(known, update.index);
            } else if (update.cell.type == CellType.KNOWN_BLOCK) {
                maskSet(known, update.index);
                maskSet(block, update.index);
                blockIndexes.add(paletteIndex(palette, update.cell.blockState));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        encodePalette(out, palette);
        out.write(present, 0, present.length);
        out.write(known, 0, known.length);
        out.write(block, 0, block.length);
        for (Integer index : blockIndexes) {
            writeShort(out, index.intValue());
        }
        return out.toByteArray();
    }

    private static byte[] encodeSparse(List<CellUpdate> updates, List<String> palette) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        encodePalette(out, palette);
        writeShort(out, updates.size());
        for (CellUpdate update : updates) {
            writeShort(out, update.index);
            if (update.cell.type == CellType.UNKNOWN) {
                out.write(0);
            } else if (update.cell.type == CellType.KNOWN_AIR) {
                out.write(1);
            } else {
                out.write(2);
                writeShort(out, paletteIndex(palette, update.cell.blockState));
            }
        }
        return out.toByteArray();
    }

    private static void encodePalette(ByteArrayOutputStream out, List<String> palette) {
        writeShort(out, palette.size());
        for (String state : palette) {
            byte[] bytes = strictUtf8(state);
            if (bytes.length > MAX_BLOCK_STATE_BYTES) {
                throw new IllegalArgumentException("collision block state exceeds byte limit");
            }
            writeShort(out, bytes.length);
            out.write(bytes, 0, bytes.length);
        }
    }

    private static int paletteIndex(List<String> palette, String state) {
        byte[] target = strictUtf8(state);
        int low = 0;
        int high = palette.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int comparison = compareUnsigned(strictUtf8(palette.get(middle)), target);
            if (comparison < 0) {
                low = middle + 1;
            } else if (comparison > 0) {
                high = middle - 1;
            } else {
                return middle;
            }
        }
        throw new IllegalArgumentException("collision block state is missing from palette");
    }

    private static List<String> decodePalette(Reader reader) throws IOException {
        int count = reader.readUnsignedShort();
        if (count > MAX_PALETTE_ENTRIES) {
            throw new IOException("collision palette exceeds entry limit");
        }
        if (count > reader.remaining() / 2) {
            throw new IOException("collision palette count exceeds payload");
        }
        List<String> palette = new ArrayList<String>(count);
        byte[] previous = null;
        for (int index = 0; index < count; index++) {
            int length = reader.readUnsignedShort();
            if (length > MAX_BLOCK_STATE_BYTES) {
                throw new IOException("collision block state exceeds byte limit");
            }
            byte[] bytes = reader.readBytes(length);
            String state = decodeUtf8(bytes);
            if (previous != null && compareUnsigned(previous, bytes) >= 0) {
                throw new IOException("collision palette is not strictly UTF-8 sorted");
            }
            palette.add(state);
            previous = bytes;
        }
        return palette;
    }

    private static List<CellUpdate> decodeDense(Reader reader, List<String> palette)
        throws IOException {
        if (reader.remaining() < COLLISION_WINDOW_MASK_BYTES * 3) {
            throw new IOException("truncated collision dense masks");
        }
        byte[] present = reader.readBytes(COLLISION_WINDOW_MASK_BYTES);
        byte[] known = reader.readBytes(COLLISION_WINDOW_MASK_BYTES);
        byte[] block = reader.readBytes(COLLISION_WINDOW_MASK_BYTES);
        for (int index = 0; index < COLLISION_WINDOW_MASK_BYTES; index++) {
            if (((known[index] & 0xff) & ~(present[index] & 0xff)) != 0) {
                throw new IOException("collision known mask is not a subset of present mask");
            }
            if (((block[index] & 0xff) & ~(known[index] & 0xff)) != 0) {
                throw new IOException("collision block mask is not a subset of known mask");
            }
        }
        int last = COLLISION_WINDOW_MASK_BYTES - 1;
        if (((present[last] | known[last] | block[last]) & 0xfe) != 0) {
            throw new IOException("collision dense mask padding is not zero");
        }
        int blockCount = 0;
        for (int index = 0; index < COLLISION_WINDOW_CELLS; index++) {
            if (maskGet(block, index)) {
                blockCount++;
            }
        }
        if (reader.remaining() != blockCount * 2) {
            throw new IOException("collision dense palette indexes do not match block mask");
        }
        boolean[] used = new boolean[palette.size()];
        List<CellUpdate> updates = new ArrayList<CellUpdate>();
        for (int index = 0; index < COLLISION_WINDOW_CELLS; index++) {
            if (!maskGet(present, index)) {
                continue;
            }
            Cell cell;
            if (!maskGet(known, index)) {
                cell = Cell.unknown();
            } else if (!maskGet(block, index)) {
                cell = Cell.knownAir();
            } else {
                cell = paletteCell(palette, used, reader.readUnsignedShort());
            }
            updates.add(new CellUpdate(index, cell));
        }
        validatePaletteUsage(used);
        return updates;
    }

    private static List<CellUpdate> decodeSparse(Reader reader, List<String> palette)
        throws IOException {
        int count = reader.readUnsignedShort();
        if (count > COLLISION_WINDOW_CELLS) {
            throw new IOException("collision sparse update count exceeds cell count");
        }
        if (count > reader.remaining() / 3) {
            throw new IOException("collision sparse update count exceeds payload");
        }
        boolean[] used = new boolean[palette.size()];
        List<CellUpdate> updates = new ArrayList<CellUpdate>(count);
        int previous = -1;
        for (int position = 0; position < count; position++) {
            int index = reader.readUnsignedShort();
            if (index >= COLLISION_WINDOW_CELLS) {
                throw new IOException("collision cell index is out of range");
            }
            if (index <= previous) {
                throw new IOException("collision sparse indexes are not strictly ascending");
            }
            int tag = reader.readUnsignedByte();
            Cell cell;
            if (tag == 0) {
                cell = Cell.unknown();
            } else if (tag == 1) {
                cell = Cell.knownAir();
            } else if (tag == 2) {
                cell = paletteCell(palette, used, reader.readUnsignedShort());
            } else {
                throw new IOException("invalid collision cell tag " + tag);
            }
            updates.add(new CellUpdate(index, cell));
            previous = index;
        }
        if (reader.remaining() != 0) {
            throw new IOException("collision sparse payload has trailing bytes");
        }
        validatePaletteUsage(used);
        return updates;
    }

    private static Cell paletteCell(List<String> palette, boolean[] used, int index)
        throws IOException {
        if (index < 0 || index >= palette.size()) {
            throw new IOException("collision palette index is out of range");
        }
        used[index] = true;
        return Cell.knownBlock(palette.get(index));
    }

    private static void validatePaletteUsage(boolean[] used) throws IOException {
        for (boolean entry : used) {
            if (!entry) {
                throw new IOException("collision palette contains unused entries");
            }
        }
    }

    private void validateFragment() {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported collision window schema");
        }
        if (kind == Kind.FULL) {
            if (baseSequence != 0) {
                throw new IllegalArgumentException("full collision window base sequence must be zero");
            }
            if (baseCenterX != centerX || baseCenterY != centerY || baseCenterZ != centerZ) {
                throw new IllegalArgumentException("full collision window base center must equal center");
            }
        } else {
            enteringCellIndices(
                baseCenterX,
                baseCenterY,
                baseCenterZ,
                centerX,
                centerY,
                centerZ
            );
        }
        if (fragmentCount < 1 || fragmentCount > MAX_FRAGMENT_COUNT) {
            throw new IllegalArgumentException("collision fragment count is out of range");
        }
        if (fragmentIndex < 0 || fragmentIndex >= fragmentCount) {
            throw new IllegalArgumentException("collision fragment index is out of range");
        }
        if (totalPayloadLength < 1 || totalPayloadLength > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("collision total payload length is out of range");
        }
        if (fragmentCount > totalPayloadLength) {
            throw new IllegalArgumentException("collision fragment count exceeds payload length");
        }
        if (fragmentPayload.length == 0) {
            throw new IllegalArgumentException("collision fragment payload is empty");
        }
        if (fragmentPayload.length > totalPayloadLength) {
            throw new IllegalArgumentException("collision fragment exceeds total payload length");
        }
        if (fragmentCount == 1 && fragmentPayload.length != totalPayloadLength) {
            throw new IllegalArgumentException(
                "single collision fragment length does not match total payload"
            );
        }
        if (payloadCrc32 < 0 || payloadCrc32 > 0xffff_ffffL) {
            throw new IllegalArgumentException("collision payload CRC32 is out of range");
        }
    }

    private boolean hasSameMetadata(PacketCollisionWindow other) {
        return timestamp == other.timestamp
            && uid.equals(other.uid)
            && username.equals(other.username)
            && Objects.equals(wireProtocolVersion, other.wireProtocolVersion)
            && schemaVersion == other.schemaVersion
            && kind == other.kind
            && encoding == other.encoding
            && generation == other.generation
            && sequence == other.sequence
            && baseSequence == other.baseSequence
            && baseCenterX == other.baseCenterX
            && baseCenterY == other.baseCenterY
            && baseCenterZ == other.baseCenterZ
            && centerX == other.centerX
            && centerY == other.centerY
            && centerZ == other.centerZ
            && fragmentCount == other.fragmentCount
            && totalPayloadLength == other.totalPayloadLength
            && payloadCrc32 == other.payloadCrc32;
    }

    private void encodeUnchecked(ByteArrayOutputStream out) {
        out.write(packetId());
        writeLong(out, timestamp);
        writeString(out, uid);
        writeString(out, username);
        if (wireProtocolVersion == null) {
            out.write(0);
        } else {
            out.write(1);
            writeInt(out, (int) (long) wireProtocolVersion);
        }
        out.write(schemaVersion);
        out.write(kind.wireValue);
        out.write(encoding.wireValue);
        writeLong(out, generation);
        writeLong(out, sequence);
        writeLong(out, baseSequence);
        writeInt(out, baseCenterX);
        writeInt(out, baseCenterY);
        writeInt(out, baseCenterZ);
        writeInt(out, centerX);
        writeInt(out, centerY);
        writeInt(out, centerZ);
        writeShort(out, fragmentIndex);
        writeShort(out, fragmentCount);
        writeInt(out, totalPayloadLength);
        writeInt(out, (int) payloadCrc32);
        writeShort(out, fragmentPayload.length);
        out.write(fragmentPayload, 0, fragmentPayload.length);
    }

    private static void validateBase(String uid, String username, Long protocolVersion) {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(username, "username");
        if (strictUtf8(uid).length > 0xffff || strictUtf8(username).length > 0xffff) {
            throw new IllegalArgumentException("packet base string exceeds wire limit");
        }
        if (protocolVersion != null && (protocolVersion < 0 || protocolVersion > 0xffff_ffffL)) {
            throw new IllegalArgumentException("packet protocol version is out of range");
        }
    }

    private static byte[] strictUtf8(String value) {
        Objects.requireNonNull(value, "value");
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("string is not valid UTF-8", exception);
        }
    }

    private static String decodeUtf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("string is not valid UTF-8", exception);
        }
    }

    private static int compareUnsigned(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int index = 0; index < length; index++) {
            int comparison = Integer.compare(left[index] & 0xff, right[index] & 0xff);
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static void maskSet(byte[] mask, int index) {
        mask[index >>> 3] = (byte) (mask[index >>> 3] | (1 << (index & 7)));
    }

    private static boolean maskGet(byte[] mask, int index) {
        return ((mask[index >>> 3] & 0xff) & (1 << (index & 7))) != 0;
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = strictUtf8(value);
        writeShort(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        out.write((int) (value >>> 56) & 0xff);
        out.write((int) (value >>> 48) & 0xff);
        out.write((int) (value >>> 40) & 0xff);
        out.write((int) (value >>> 32) & 0xff);
        out.write((int) (value >>> 24) & 0xff);
        out.write((int) (value >>> 16) & 0xff);
        out.write((int) (value >>> 8) & 0xff);
        out.write((int) value & 0xff);
    }

    private static final class Reader {
        private final byte[] bytes;
        private int position;

        private Reader(byte[] bytes) {
            this.bytes = bytes.clone();
        }

        private int remaining() {
            return bytes.length - position;
        }

        private int readUnsignedByte() throws IOException {
            require(1);
            return bytes[position++] & 0xff;
        }

        private int readUnsignedShort() throws IOException {
            return (readUnsignedByte() << 8) | readUnsignedByte();
        }

        private int readInt() throws IOException {
            return (readUnsignedByte() << 24)
                | (readUnsignedByte() << 16)
                | (readUnsignedByte() << 8)
                | readUnsignedByte();
        }

        private long readUnsignedInt() throws IOException {
            return readInt() & 0xffff_ffffL;
        }

        private long readLong() throws IOException {
            return ((long) readUnsignedByte() << 56)
                | ((long) readUnsignedByte() << 48)
                | ((long) readUnsignedByte() << 40)
                | ((long) readUnsignedByte() << 32)
                | ((long) readUnsignedByte() << 24)
                | ((long) readUnsignedByte() << 16)
                | ((long) readUnsignedByte() << 8)
                | readUnsignedByte();
        }

        private String readString(String label) throws IOException {
            int length = readUnsignedShort();
            if (length > remaining()) {
                throw new IOException("truncated " + label + " string");
            }
            return decodeUtf8(readBytes(length));
        }

        private byte[] readBytes(int length) throws IOException {
            require(length);
            byte[] value = Arrays.copyOfRange(bytes, position, position + length);
            position += length;
            return value;
        }

        private void require(int length) throws IOException {
            if (length < 0 || length > remaining()) {
                throw new IOException("truncated collision window packet");
            }
        }
    }
}
