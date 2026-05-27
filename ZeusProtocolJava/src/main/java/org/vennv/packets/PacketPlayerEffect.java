package org.vennv.packets;

import org.vennv.Effect;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerEffect extends PacketBaseInfo {

    private final Effect effect;

    public PacketPlayerEffect(long timestamp, String uid, String username, Effect effect) {
        super(timestamp, uid, username);
        this.effect = effect;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_EFFECT;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        effect.encode(out);
    }

    public Effect getEffect() {
        return effect;
    }
}
