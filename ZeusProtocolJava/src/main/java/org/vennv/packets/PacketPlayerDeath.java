package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

/**
 * Packet for player death events
 */
public final class PacketPlayerDeath extends PacketBaseInfo {

    public PacketPlayerDeath(long timestamp, String uid, String username) {
        super(timestamp, uid, username);
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_DEATH;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
    }

    @Override
    public String toString() {
        return (
            "PacketPlayerDeath{" +
            "timestamp=" +
            getTimestamp() +
            ", uid='" +
            getUid() +
            '\'' +
            ", username='" +
            getUsername() +
            '\'' +
            '}'
        );
    }
}
