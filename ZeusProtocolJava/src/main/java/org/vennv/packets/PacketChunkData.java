package org.vennv.packets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

public final class PacketChunkData extends PacketBaseInfo {
    public static final int MAX_UDP_PAYLOAD = 65_507;

    public static class BlockData {
        public final byte localX;
        public final int y;
        public final byte localZ;
        public final String blockType;

        public BlockData(byte localX, int y, byte localZ, String blockType) {
            this.localX = localX;
            this.y = y;
            this.localZ = localZ;
            this.blockType = blockType;
        }
    }

    private final int chunkX;
    private final int chunkZ;
    private final boolean isFullChunk;
    private final boolean isComplete;
    private final List<BlockData> blocks;

    public PacketChunkData(
        long timestamp,
        String uid,
        String username,
        int chunkX,
        int chunkZ,
        boolean isFullChunk,
        List<BlockData> blocks
    ) {
        this(timestamp, uid, username, chunkX, chunkZ, isFullChunk, false, blocks);
    }

    public PacketChunkData(
        long timestamp,
        String uid,
        String username,
        int chunkX,
        int chunkZ,
        boolean isFullChunk,
        boolean isComplete,
        List<BlockData> blocks
    ) {
        super(timestamp, uid, username);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.isFullChunk = isFullChunk;
        this.isComplete = isComplete;
        this.blocks = blocks;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_CHUNK_DATA;
    }

    public static int encodedBaseSize(String uid, String username) {
        return 27
                + uid.getBytes(StandardCharsets.UTF_8).length
                + username.getBytes(StandardCharsets.UTF_8).length;
    }

    public static int encodedBlockSize(String blockType) {
        return 8 + blockType.getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        ByteBufferUtil.putInt(out, chunkX);
        ByteBufferUtil.putInt(out, chunkZ);
        int flags = (isFullChunk ? 1 : 0) | (isComplete ? 2 : 0);
        ByteBufferUtil.putByte(out, (byte) flags);

        // number of blocks
        ByteBufferUtil.putInt(out, blocks.size());

        for (BlockData block : blocks) {
            ByteBufferUtil.putByte(out, block.localX);
            ByteBufferUtil.putInt(out, block.y);
            ByteBufferUtil.putByte(out, block.localZ);
            
            byte[] typeBytes = block.blockType.getBytes(StandardCharsets.UTF_8);
            ByteBufferUtil.putShort(out, (short) typeBytes.length);
            ByteBufferUtil.putBytes(out, typeBytes);
        }
    }
}
