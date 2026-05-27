package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Packet for window closing events
 */
public final class PacketPlayerCloseWindow extends PacketBaseInfo {

    private final byte windowId;

    public PacketPlayerCloseWindow(long timestamp, String uid, String username, byte windowId) {
        super(timestamp, uid, username);
        this.windowId = windowId;
    }

    public byte getWindowId() {
        return windowId;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_CLOSE_WINDOW;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // Write window ID (1 byte)
        out.write(windowId);
    }

    @Override
    public String toString() {
        return "PacketPlayerCloseWindow{" +
                "timestamp=" + getTimestamp() +
                ", uid='" + getUid() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", windowId=" + windowId +
                '}';
    }
}
