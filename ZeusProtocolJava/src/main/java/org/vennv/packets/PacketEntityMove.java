package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketEntityMove extends PacketBaseInfo {

    private final int entityId;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final boolean onGround;

    public PacketEntityMove(long timestamp, String uid, String username,
                            int entityId, double x, double y, double z,
                            float yaw, float pitch, boolean onGround) {
        super(timestamp, uid, username);
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_ENTITY_MOVE;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        ByteBufferUtil.putInt(out, entityId);
        
        ByteBufferUtil.putDouble(out, x);
        ByteBufferUtil.putDouble(out, y);
        ByteBufferUtil.putDouble(out, z);
        
        ByteBufferUtil.putFloat(out, yaw);
        ByteBufferUtil.putFloat(out, pitch);
        
        out.write(onGround ? 1 : 0);
    }
}
