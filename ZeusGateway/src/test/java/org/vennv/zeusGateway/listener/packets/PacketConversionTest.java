package org.vennv.zeusGateway.listener.packets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.comphenix.protocol.wrappers.EnumWrappers;
import org.junit.jupiter.api.Test;

class PacketConversionTest {
    @Test
    void modernClickWindowDoesNotReuseButtonAsTransactionId() {
        assertEquals((short) 0,
                PacketClickWindowListener.transactionIdForVersion(true, (short) 4));
    }

    @Test
    void legacyClickWindowKeepsTheActionNumber() {
        assertEquals((short) 37,
                PacketClickWindowListener.transactionIdForVersion(false, (short) 37));
    }

    @Test
    void nullOrInvalidBlockFaceIsNotConvertedToDown() {
        assertNull(PacketBlockFaceListener.mapDirectionToFace(null));
        assertNull(PacketBlockFaceListener.validFace(7));
        assertEquals((byte) 0,
                PacketBlockFaceListener.mapDirectionToFace(EnumWrappers.Direction.DOWN));
    }
}
