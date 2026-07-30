package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

/**
 * Clientbound shulker-box block-event state.
 * actionType 1 is the vanilla shulker animation; viewerCount is its event data.
 */
public final class PacketShulkerBoxAction extends PacketBaseInfo {
    private final int worldX;
    private final int worldY;
    private final int worldZ;
    private final byte actionType;
    private final byte viewerCount;

    public PacketShulkerBoxAction(
            long timestamp,
            String uid,
            String username,
            int worldX,
            int worldY,
            int worldZ,
            byte actionType,
            byte viewerCount) {
        super(timestamp, uid, username);
        this.worldX = worldX;
        this.worldY = worldY;
        this.worldZ = worldZ;
        this.actionType = actionType;
        this.viewerCount = viewerCount;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_SHULKER_BOX_ACTION;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putInt(out, worldX);
        ByteBufferUtil.putInt(out, worldY);
        ByteBufferUtil.putInt(out, worldZ);
        ByteBufferUtil.putByte(out, actionType);
        ByteBufferUtil.putByte(out, viewerCount);
    }

    public int getWorldX() {
        return worldX;
    }

    public int getWorldY() {
        return worldY;
    }

    public int getWorldZ() {
        return worldZ;
    }

    public byte getActionType() {
        return actionType;
    }

    public byte getViewerCount() {
        return viewerCount;
    }
}
