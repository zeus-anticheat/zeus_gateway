package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;
import org.vennv.utils.Hand;

/**
 * Packet for item use release events
 */
public final class PacketPlayerReleaseUseItem extends PacketBaseInfo {

    private final Hand hand;

    public PacketPlayerReleaseUseItem(
        long timestamp,
        String uid,
        String username,
        Hand hand
    ) {
        super(timestamp, uid, username);
        this.hand = hand;
    }

    public Hand getHand() {
        return hand;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_RELEASE_USE_ITEM;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // Write hand (1 byte)
        out.write(hand.getValue());
    }

    @Override
    public String toString() {
        return (
            "PacketPlayerReleaseUseItem{" +
            "timestamp=" +
            getTimestamp() +
            ", uid='" +
            getUid() +
            '\'' +
            ", username='" +
            getUsername() +
            '\'' +
            ", hand=" +
            hand +
            '}'
        );
    }
}
