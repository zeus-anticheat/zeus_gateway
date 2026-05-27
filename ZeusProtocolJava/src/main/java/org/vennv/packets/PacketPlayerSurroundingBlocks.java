package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;
import org.vennv.utils.RelativeBlock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PacketPlayerSurroundingBlocks extends PacketBaseInfo {

    private final List<RelativeBlock> blocks;

    public PacketPlayerSurroundingBlocks(
            long timestamp,
            String uid,
            String username,
            List<RelativeBlock> blocks
    ) {
        super(timestamp, uid, username);
        this.blocks = blocks;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_SURROUNDING_BLOCKS;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        ByteBufferUtil.putByte(out, (byte) blocks.size());

        for (RelativeBlock block : blocks) {
            ByteBufferUtil.putByte(out, (byte) block.dx);
            ByteBufferUtil.putByte(out, (byte) block.dy);
            ByteBufferUtil.putByte(out, (byte) block.dz);

            byte[] typeBytes = block.type.getBytes(StandardCharsets.UTF_8);
            ByteBufferUtil.putShort(out, (short) typeBytes.length);
            ByteBufferUtil.putBytes(out, typeBytes);
        }
    }

    public List<RelativeBlock> getBlocks() {
        return blocks;
    }
}
