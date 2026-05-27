package org.vennv.zeusGateway.utils;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import java.util.List;

public final class EntityMetadataReader {
    private EntityMetadataReader() {}

    public static Object fetchRaw(PacketContainer packet, int requiredIndex) {
        Object modern = fetchModernDataValue(packet, requiredIndex);
        if (modern != null) {
            return modern;
        }
        return fetchLegacyWatchable(packet, requiredIndex);
    }

    private static Object fetchModernDataValue(PacketContainer packet, int requiredIndex) {
        try {
            List<WrappedDataValue> values = packet.getDataValueCollectionModifier().readSafely(0);
            if (values == null || values.isEmpty()) {
                return null;
            }
            for (WrappedDataValue value : values) {
                if (value != null && value.getIndex() == requiredIndex) {
                    return value.getRawValue();
                }
            }
        } catch (Exception | NoSuchMethodError ignored) {
            // Older ProtocolLib/server versions expose metadata as watchables.
        }
        return null;
    }

    private static Object fetchLegacyWatchable(PacketContainer packet, int requiredIndex) {
        try {
            List<WrappedWatchableObject> values = packet.getWatchableCollectionModifier().readSafely(0);
            if (values == null || values.isEmpty()) {
                return null;
            }
            for (WrappedWatchableObject value : values) {
                if (value != null && value.getIndex() == requiredIndex) {
                    return value.getRawValue();
                }
            }
        } catch (Exception ignored) {
            // Metadata is unreadable for this packet/version.
        }
        return null;
    }
}
