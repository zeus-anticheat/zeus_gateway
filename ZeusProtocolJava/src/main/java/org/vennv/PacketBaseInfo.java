package org.vennv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class PacketBaseInfo implements PacketBase, PacketEncode {

    protected final long timestamp;
    protected final String uid;
    protected final String username;

    public PacketBaseInfo(long timestamp, String uid, String username) {
        this.timestamp = timestamp;
        this.uid = uid;
        this.username = username;
    }

    protected void encodePlayerInfo(ByteArrayOutputStream out)
        throws IOException {
        ByteBufferUtil.putByte(out, packetId());

        // timestamp
        ByteBufferUtil.putLong(out, timestamp);

        // uid
        byte[] uidBytes = uid.getBytes(StandardCharsets.UTF_8);
        ByteBufferUtil.putShort(out, (short) uidBytes.length);
        ByteBufferUtil.putBytes(out, uidBytes);

        // username
        byte[] nameBytes = username.getBytes(StandardCharsets.UTF_8);
        ByteBufferUtil.putShort(out, (short) nameBytes.length);
        ByteBufferUtil.putBytes(out, nameBytes);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getUid() {
        return uid;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public abstract byte packetId();
}
