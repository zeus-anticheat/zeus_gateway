package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public final class PacketEntityDestroy extends PacketBaseInfo {

    private final List<Integer> entityIds;

    public PacketEntityDestroy(long timestamp, String uid, String username,
                               List<Integer> entityIds) {
        super(timestamp, uid, username);
        this.entityIds = entityIds;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_ENTITY_DESTROY;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        ByteBufferUtil.putInt(out, entityIds.size());
        for (int id : entityIds) {
            ByteBufferUtil.putInt(out, id);
        }
    }
}
