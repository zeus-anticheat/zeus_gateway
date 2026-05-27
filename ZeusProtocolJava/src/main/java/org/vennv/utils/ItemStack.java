package org.vennv.utils;

import org.vennv.ByteBufferUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Represents an item stack with ID, metadata, and count
 */
public class ItemStack {
    public static final String EMPTY_ID = "";

    private String id;
    private int meta;
    private byte count;

    public ItemStack(String id, int meta, byte count) {
        this.id = id == null ? EMPTY_ID : id;
        this.meta = meta;
        this.count = count;
    }

    /**
     * Compatibility constructor for older call sites. New packet collectors
     * should pass a stable namespaced item key instead of a registry ordinal.
     */
    @Deprecated
    public ItemStack(short id, int meta, byte count) {
        this(id == -1 ? EMPTY_ID : Short.toString(id), meta, count);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null ? EMPTY_ID : id;
    }

    @Deprecated
    public void setId(short id) {
        this.id = id == -1 ? EMPTY_ID : Short.toString(id);
    }

    public boolean isEmpty() {
        return id == null || id.isEmpty();
    }

    public int getMeta() {
        return meta;
    }

    public void setMeta(int meta) {
        this.meta = meta;
    }

    public byte getCount() {
        return count;
    }

    public void setCount(byte count) {
        this.count = count;
    }

    /**
     * Encode the item stack to a byte array output stream
     */
    public void encode(ByteArrayOutputStream out) throws IOException {
        // Write stable item id as a UTF-8 string with u16 length.
        ByteBufferUtil.putString(out, id == null ? EMPTY_ID : id);

        // Write meta (4 bytes).
        ByteBufferUtil.putInt(out, meta);

        // Write count (1 byte), always present in the breaking v2 layout.
        out.write(count);
    }

    /**
     * Decode an item stack from a byte buffer
     */
    public static ItemStack decode(ByteBuffer buf) {
        int idLen = buf.getShort() & 0xFFFF;
        byte[] idBytes = new byte[idLen];
        buf.get(idBytes);
        String id = new String(idBytes, StandardCharsets.UTF_8);
        int meta = buf.getInt();
        byte count = buf.get();
        return new ItemStack(id, meta, count);
    }

    @Override
    public String toString() {
        return "ItemStack{" +
                "id=" + id +
                ", meta=" + meta +
                ", count=" + count +
                '}';
    }
}
