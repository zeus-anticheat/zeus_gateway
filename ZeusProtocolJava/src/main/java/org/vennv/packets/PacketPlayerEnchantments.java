package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;
import org.vennv.utils.Enchantment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public final class PacketPlayerEnchantments extends PacketBaseInfo {

    private final List<Enchantment> enchantments;
    private final float entityInteractionRange;

    public PacketPlayerEnchantments(long timestamp, String uid, String username, List<Enchantment> enchantments, float entityInteractionRange) {
        super(timestamp, uid, username);
        this.enchantments = enchantments;
        this.entityInteractionRange = entityInteractionRange;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_ENCHANTMENTS;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putFloat(out, entityInteractionRange);
        
        ByteBufferUtil.putShort(out, (short) enchantments.size());
        for (Enchantment enchantment : enchantments) {
            ByteBufferUtil.putString(out, enchantment.getName());
            ByteBufferUtil.putByte(out, enchantment.getLevel());
        }
    }

    public List<Enchantment> getEnchantments() {
        return enchantments;
    }

    public float getEntityInteractionRange() {
        return entityInteractionRange;
    }
}
