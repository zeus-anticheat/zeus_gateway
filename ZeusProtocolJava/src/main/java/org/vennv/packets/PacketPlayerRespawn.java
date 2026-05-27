package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

/**
 * Packet for player respawn events
 */
public final class PacketPlayerRespawn extends PacketBaseInfo {

    public PacketPlayerRespawn(long timestamp, String uid, String username) {
        super(timestamp, uid, username);
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_RESPAWN;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
    }

    @Override
    public String toString() {
        return (
            "PacketPlayerRespawn{" +
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
