package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

/**
 * Packet for confirming inventory transactions
 */
public final class PacketPlayerConfirmTransaction extends PacketBaseInfo {

    private final byte windowId;
    private final int actionNumber;
    private final boolean accepted;

    public PacketPlayerConfirmTransaction(
        long timestamp,
        String uid,
        String username,
        byte windowId,
        int actionNumber,
        boolean accepted
    ) {
        super(timestamp, uid, username);
        this.windowId = windowId;
        this.actionNumber = actionNumber;
        this.accepted = accepted;
    }

    public byte getWindowId() {
        return windowId;
    }

    public int getActionNumber() {
        return actionNumber;
    }

    public boolean isAccepted() {
        return accepted;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_CONFIRM_TRANSACTION;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // Write window ID (1 byte)
        out.write(windowId);

        // Write action number (2 bytes, unsigned u16)
        out.write((actionNumber >> 8) & 0xFF);
        out.write(actionNumber & 0xFF);

        // Write accepted flag (1 byte)
        out.write(accepted ? 1 : 0);
    }

    @Override
    public String toString() {
        return (
            "PacketPlayerConfirmTransaction{" +
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
            ", actionNumber=" +
            actionNumber +
            ", accepted=" +
            accepted +
            '}'
        );
    }
}
