package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;
import org.vennv.utils.DamageCause;

/**
 * Packet for player damage events with damage cause
 */
public final class PacketPlayerGotDamage extends PacketBaseInfo {

    private final DamageCause cause;

    public PacketPlayerGotDamage(
        long timestamp,
        String uid,
        String username,
        DamageCause cause
    ) {
        super(timestamp, uid, username);
        this.cause = cause;
    }

    public DamageCause getCause() {
        return cause;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_GOT_DAMAGE;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // Write cause as u32 (4 bytes, big-endian)
        int causeValue = cause.getValue();
        out.write((causeValue >> 24) & 0xFF);
        out.write((causeValue >> 16) & 0xFF);
        out.write((causeValue >> 8) & 0xFF);
        out.write(causeValue & 0xFF);
    }

    @Override
    public String toString() {
        return (
            "PacketPlayerGotDamage{" +
            "timestamp=" +
            getTimestamp() +
            ", uid='" +
            getUid() +
            '\'' +
            ", username='" +
            getUsername() +
            '\'' +
            ", cause=" +
            cause +
            '}'
        );
    }
}
