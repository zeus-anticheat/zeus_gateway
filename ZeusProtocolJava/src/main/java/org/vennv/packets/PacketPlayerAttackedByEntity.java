package org.vennv.packets;

import org.vennv.EntityState;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerAttackedByEntity extends PacketBaseInfo {

    private final EntityState entityState;

    public PacketPlayerAttackedByEntity(long timestamp, String uid, String username, EntityState entityState) {
        super(timestamp, uid, username);
        this.entityState = entityState;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_ATTACKED_BY_ENTITY;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        entityState.encode(out);
    }

    public EntityState getEntityState() {
        return entityState;
    }
}
