package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;
import org.vennv.utils.Hand;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Packet for item use events
 */
public final class PacketPlayerUseItem extends PacketBaseInfo {

    private final Hand hand;
    private final byte sequence;

    public PacketPlayerUseItem(long timestamp, String uid, String username,
                               Hand hand, byte sequence) {
        super(timestamp, uid, username);
        this.hand = hand;
        this.sequence = sequence;
    }

    public Hand getHand() {
        return hand;
    }

    public byte getSequence() {
        return sequence;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_USE_ITEM;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // Write hand (1 byte)
        out.write(hand.getValue());

        // Write sequence (1 byte)
        out.write(sequence);
    }

    @Override
    public String toString() {
        return "PacketPlayerUseItem{" +
                "timestamp=" + getTimestamp() +
                ", uid='" + getUid() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", hand=" + hand +
                ", sequence=" + sequence +
                '}';
    }
}
