package org.vennv;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ByteBufferUtil {

    public static void putByte(ByteArrayOutputStream out, byte value) throws IOException {
        out.write(value);
    }

    public static void putInt(ByteArrayOutputStream out, int value) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(value);
        dos.flush();
    }

    public static void putLong(ByteArrayOutputStream out, long value) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeLong(value);
        dos.flush();
    }

    public static void putFloat(ByteArrayOutputStream out, float value) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeFloat(value);
        dos.flush();
    }

    public static void putDouble(ByteArrayOutputStream out, double value) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeDouble(value);
        dos.flush();
    }

    public static void putShort(ByteArrayOutputStream out, short value) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeShort(value);
        dos.flush();
    }

    public static void putBytes(ByteArrayOutputStream out, byte[] bytes) throws IOException {
        out.write(bytes);
    }

    public static void putString(ByteArrayOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        putShort(out, (short) bytes.length);
        putBytes(out, bytes);
    }
}
