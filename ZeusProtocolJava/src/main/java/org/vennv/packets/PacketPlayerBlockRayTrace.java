package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerBlockRayTrace extends PacketBaseInfo {

    public static final byte ACTION_INTERACT = 0;
    public static final byte ACTION_DIG = 1;
    public static final byte ACTION_PLACE = 2;

    private final boolean hitBlock;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final float hitX;
    private final float hitY;
    private final float hitZ;
    private final byte action;

    /** Legacy constructor. Omitted action means generic interaction. */
    public PacketPlayerBlockRayTrace(long timestamp, String uid, String username,
                                     boolean hitBlock, int blockX, int blockY, int blockZ,
                                     float hitX, float hitY, float hitZ) {
        this(timestamp, uid, username, hitBlock, blockX, blockY, blockZ,
                hitX, hitY, hitZ, ACTION_INTERACT);
    }

    public PacketPlayerBlockRayTrace(long timestamp, String uid, String username,
                                     boolean hitBlock, int blockX, int blockY, int blockZ,
                                     float hitX, float hitY, float hitZ, byte action) {
        super(timestamp, uid, username);
        if (action < ACTION_INTERACT || action > ACTION_PLACE) {
            throw new IllegalArgumentException("invalid block ray trace action: " + action);
        }
        this.hitBlock = hitBlock;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.action = action;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_BLOCK_RAY_TRACE;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putByte(out, (byte) (hitBlock ? 1 : 0));
        ByteBufferUtil.putInt(out, blockX);
        ByteBufferUtil.putInt(out, blockY);
        ByteBufferUtil.putInt(out, blockZ);
        ByteBufferUtil.putFloat(out, hitX);
        ByteBufferUtil.putFloat(out, hitY);
        ByteBufferUtil.putFloat(out, hitZ);
        ByteBufferUtil.putByte(out, action);
    }

    public boolean isHitBlock() {
        return hitBlock;
    }

    public int getBlockX() {
        return blockX;
    }

    public int getBlockY() {
        return blockY;
    }

    public int getBlockZ() {
        return blockZ;
    }

    public float getHitX() {
        return hitX;
    }

    public float getHitY() {
        return hitY;
    }

    public float getHitZ() {
        return hitZ;
    }

    public byte getAction() {
        return action;
    }
}
