package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

public final class PacketBlockChangeEvent extends PacketBaseInfo {

    private final int worldX;
    private final int worldY;
    private final int worldZ;
    private final String blockType;
    private final byte action;

    public PacketBlockChangeEvent(
        long timestamp,
        String uid,
        String username,
        int worldX,
        int worldY,
        int worldZ,
        String blockType,
        byte action
    ) {
        super(timestamp, uid, username);
        this.worldX = worldX;
        this.worldY = worldY;
        this.worldZ = worldZ;
        this.blockType = blockType;
        this.action = action;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_BLOCK_CHANGE_EVENT;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // world coordinates
        ByteBufferUtil.putInt(out, worldX);
        ByteBufferUtil.putInt(out, worldY);
        ByteBufferUtil.putInt(out, worldZ);

        // block type (length-prefixed string)
        byte[] typeBytes = blockType.getBytes(StandardCharsets.UTF_8);
        ByteBufferUtil.putShort(out, (short) typeBytes.length);
        ByteBufferUtil.putBytes(out, typeBytes);

        // action: 0=PLACE/BREAK, 1=PISTON_PUSH, 2=PISTON_PULL, 3=FLUID_FLOW, 4=REDSTONE, 5=GRAVITY
        ByteBufferUtil.putByte(out, action);
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

    public String getBlockType() {
        return blockType;
    }

    public byte getAction() {
        return action;
    }
}
