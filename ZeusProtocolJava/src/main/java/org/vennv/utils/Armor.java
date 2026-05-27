package org.vennv.utils;

import org.vennv.ByteBufferUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Represents a single armor piece with name, custom name, and metadata
 */
public class Armor {
    private String name;
    private String customName;
    private int meta;

    public Armor(String name, String customName, int meta) {
        this.name = name;
        this.customName = customName;
        this.meta = meta;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public int getMeta() {
        return meta;
    }

    public void setMeta(int meta) {
        this.meta = meta;
    }

    /**
     * Encode the armor piece to a byte array output stream
     */
    public void encode(ByteArrayOutputStream out) throws IOException {
        ByteBufferUtil.putString(out, name == null ? "" : name);
        ByteBufferUtil.putString(out, customName == null ? "" : customName);

        // Write meta (4 bytes)
        ByteBufferUtil.putInt(out, meta);
    }

    /**
     * Decode an armor piece from a byte buffer
     */
    public static Armor decode(ByteBuffer buf) {
        String name = readString(buf);
        String customName = readString(buf);

        // Read meta
        int meta = buf.getInt();

        return new Armor(name, customName, meta);
    }

    private static String readString(ByteBuffer buf) {
        int len = buf.getShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "Armor{" +
                "name='" + name + '\'' +
                ", customName='" + customName + '\'' +
                ", meta=" + meta +
                '}';
    }
}
