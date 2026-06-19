package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketEntitySpawn extends PacketBaseInfo {

    private final int entityId;
    private final String uuid;
    private final String entityType;
    private final double x;
    private final double y;
    private final double z;
    private final float pitch;
    private final float yaw;

    public PacketEntitySpawn(long timestamp, String uid, String username,
                             int entityId, String uuid, String entityType,
                             double x, double y, double z,
                             float pitch, float yaw) {
        super(timestamp, uid, username);
        this.entityId = entityId;
        this.uuid = uuid;
        this.entityType = entityType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.pitch = pitch;
        this.yaw = yaw;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_ENTITY_SPAWN;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        ByteBufferUtil.putInt(out, entityId);
        ByteBufferUtil.putString(out, uuid);
        ByteBufferUtil.putString(out, entityType);
        
        ByteBufferUtil.putDouble(out, x);
        ByteBufferUtil.putDouble(out, y);
        ByteBufferUtil.putDouble(out, z);
        
        ByteBufferUtil.putFloat(out, pitch);
        ByteBufferUtil.putFloat(out, yaw);
    }
}
