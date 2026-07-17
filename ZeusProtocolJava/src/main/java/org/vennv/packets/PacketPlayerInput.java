package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Serverbound player input flags (Minecraft 1.21.2+).
 *
 * Wire layout (after {@link PacketBaseInfo}):
 * <pre>
 *   flags: u8
 *     bit 0 = forward
 *     bit 1 = backward
 *     bit 2 = left
 *     bit 3 = right
 *     bit 4 = jump
 *     bit 5 = shift (sneak)
 *     bit 6 = sprint
 *     bit 7 = trusted packet-level capture
 * </pre>
 *
 * This packet enables version-specific jump / sneak / sprint inference
 * without depending on inferring from position deltas or guesswork.
 */
public final class PacketPlayerInput extends PacketBaseInfo {

    public static final int TRUSTED_CAPTURE = 0x80;

    private final byte flags;

    public PacketPlayerInput(long timestamp, String uid, String username, byte flags) {
        super(timestamp, uid, username);
        this.flags = flags;
    }

    public byte getFlags() { return flags; }
    public boolean isForward()  { return (flags & 0x01) != 0; }
    public boolean isBackward() { return (flags & 0x02) != 0; }
    public boolean isLeft()    { return (flags & 0x04) != 0; }
    public boolean isRight()   { return (flags & 0x08) != 0; }
    public boolean isJump()    { return (flags & 0x10) != 0; }
    public boolean isShift()   { return (flags & 0x20) != 0; }
    public boolean isSprint()  { return (flags & 0x40) != 0; }
    public boolean isTrustedCapture() { return (flags & TRUSTED_CAPTURE) != 0; }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_INPUT;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        out.write(flags & 0xFF);
    }

    @Override
    public String toString() {
        return "PacketPlayerInput{flags=" + flags + "}";
    }
}
