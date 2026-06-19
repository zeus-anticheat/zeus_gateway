package org.vennv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class PacketBaseInfo implements PacketBase, PacketEncode {

    protected final long timestamp;
    protected final String uid;
    protected final String username;
    protected final int protocolVersion;

    public PacketBaseInfo(long timestamp, String uid, String username) {
        this(timestamp, uid, username, 0);
    }

    public PacketBaseInfo(long timestamp, String uid, String username, int protocolVersion) {
        this.timestamp = timestamp;
        this.uid = uid;
        this.username = username;
        this.protocolVersion = protocolVersion;
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

        // protocol_version: Option<u32> in Rust = u8 flag + u32 value
        if (protocolVersion > 0) {
            ByteBufferUtil.putByte(out, (byte) 1);
            ByteBufferUtil.putInt(out, protocolVersion);
        } else {
            ByteBufferUtil.putByte(out, (byte) 0);
        }
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

    public int getProtocolVersion() {
        return protocolVersion;
    }

    @Override
    public abstract byte packetId();
}
