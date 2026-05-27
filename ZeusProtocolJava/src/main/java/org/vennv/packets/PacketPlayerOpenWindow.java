package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

/**
 * Packet for window opening events
 */
public final class PacketPlayerOpenWindow extends PacketBaseInfo {

    private final byte windowId;

    public PacketPlayerOpenWindow(
        long timestamp,
        String uid,
        String username,
        byte windowId
    ) {
        super(timestamp, uid, username);
        this.windowId = windowId;
    }

    public byte getWindowId() {
        return windowId;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_OPEN_WINDOW;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // Write window ID (1 byte)
        out.write(windowId);
    }

    @Override
    public String toString() {
        return (
            "PacketPlayerOpenWindow{" +
            "timestamp=" +
            getTimestamp() +
            ", uid='" +
            getUid() +
            '\'' +
            ", username='" +
            getUsername() +
            '\'' +
            ", windowId=" +
            windowId +
            '}'
        );
    }
}
