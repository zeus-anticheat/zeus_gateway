package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;
import org.vennv.utils.CustomFeatureCategory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PacketPlayerCustomFeature extends PacketBaseInfo {

    private final int categoryId;
    private final int featureId;
    private final double featureValue;

    public PacketPlayerCustomFeature(long timestamp, String uid, String username, int categoryId, int featureId, double featureValue) {
        super(timestamp, uid, username);
        this.categoryId = categoryId;
        this.featureId = featureId;
        this.featureValue = featureValue;
    }

    public PacketPlayerCustomFeature(long timestamp, String uid, String username, CustomFeatureCategory category, int featureId, double featureValue) {
        this(timestamp, uid, username, category.getId(), featureId, featureValue);
    }

    public int getCategoryId() {
        return categoryId;
    }

    public int getFeatureId() {
        return featureId;
    }

    public double getFeatureValue() {
        return featureValue;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_CUSTOM_FEATURE;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putInt(out, this.categoryId);
        ByteBufferUtil.putInt(out, this.featureId);
        ByteBufferUtil.putDouble(out, this.featureValue);
    }
}
