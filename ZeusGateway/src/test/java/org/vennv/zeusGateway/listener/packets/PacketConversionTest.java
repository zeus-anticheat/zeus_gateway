package org.vennv.zeusGateway.listener.packets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.comphenix.protocol.wrappers.EnumWrappers;
import org.junit.jupiter.api.Test;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.zeusGateway.provider.PacketQueue;

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

    @Test
    void packetQueueOrdersEqualPriorityPacketsByInsertionOrder() {
        while (PacketQueue.poll() != null) {
        }
        PacketPlayerPosition newer = positionAt(200);
        PacketPlayerPosition older = positionAt(100);

        PacketQueue.push(newer);
        PacketQueue.push(older);

        assertSame(newer, PacketQueue.poll());
        assertSame(older, PacketQueue.poll());
        assertNull(PacketQueue.poll());
    }

    private static PacketPlayerPosition positionAt(long timestamp) {
        return new PacketPlayerPosition(
                timestamp, "u", "n", false,
                1.0, 2.0, 3.0,
                1.0, 3.62, 3.0,
                0.0f, 0.0f, 1.8f, true);
    }
}
