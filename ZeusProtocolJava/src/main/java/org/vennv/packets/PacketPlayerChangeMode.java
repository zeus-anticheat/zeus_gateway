package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerChangeMode extends PacketBaseInfo {

    private final int gamemode;

    public PacketPlayerChangeMode(long timestamp, String uid, String username, int gamemode) {
        super(timestamp, uid, username);
        this.gamemode = gamemode;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_CHANGE_MODE;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        ByteBufferUtil.putInt(out, gamemode);
    }

    public int getGamemode() {
        return gamemode;
    }
}