package org.vennv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.vennv.packets.PacketPlayerAttackEntity;
import org.vennv.packets.PacketPlayerExternalForce;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.packets.PacketPhysicsCaptureSample;
import org.vennv.packets.PacketPlayerVelocity;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.utils.ExternalForceFlags;
import org.vennv.utils.ExternalForceType;
import org.vennv.utils.ItemStack;

/**
 * Golden payloads for the packets consumed by the cross-platform physics path.
 * A change here requires a coordinated Rust protocol migration.
 */
class WireContractGoldenTest {
    private static final long TIMESTAMP = 0x0102030405060708L;
    private static final String UID = "u";
    private static final String USERNAME = "n";

    @Test
    void packetIdsRemainAssignedThroughExternalForce() {
        assertEquals(0x09, PacketId.PACKET_PLAYER_ATTACK_ENTITY & 0xff);
        assertEquals(0x22, PacketId.PACKET_PLAYER_VELOCITY & 0xff);
        assertEquals(0x26, PacketId.PACKET_PLAYER_INVENTORY_TRANSACTION & 0xff);
        assertEquals(0x27, PacketId.PACKET_PLAYER_EXTERNAL_FORCE & 0xff);
    }

    @Test
    void attackFixtureIsStable() throws Exception {
        assertEquals(
                "09010203040506070800017500016e0000016540100000000000004014000000000000401800000000000040e00000410000003fe666663f19999a3ff00000000000004000000000000000400800000000000001",
                hex(new PacketPlayerAttackEntity(
                        TIMESTAMP,
                        UID,
                        USERNAME,
                        new EntityState("e", 1, 2, 3, 4, 5, 6, 7f, 8f, 1.8f, .6f, true))));
    }

    @Test
    void velocityFixtureIsStable() throws Exception {
        assertEquals(
                "22010203040506070800017500016e003fd0000000000000bfe00000000000003ff0000000000000",
                hex(new PacketPlayerVelocity(TIMESTAMP, UID, USERNAME, .25, -.5, 1)));
    }

    @Test
    void positionFixtureIsStable() throws Exception {
        assertEquals(
                "03010203040506070800017500016e00003ff0000000000000400000000000000040080000000000003ff000000000000040000000000000004008000000000000000000000000000040000000010100000000000000020101",
                hex(new PacketPlayerPosition(
                        TIMESTAMP,
                        UID,
                        USERNAME,
                        false,
                        1.0, 2.0, 3.0,
                        1.0, 2.0, 3.0,
                        0f, 0f, 2.0f,
                        true,
                        (byte) 1,
                        2L,
                        true,
                        true)));
    }


    @Test
    void physicsCaptureFixtureHasNoPlayerBase() throws Exception {
        assertEquals(
                "2f0102030405060708010302030111223344556677883ff00000000000004050000000000000"
                        + "40000000000000003dcccccd000000003e4ccccd3dcccccd000000003e4ccccd3dcccccd"
                        + "0300010001000000320001",
                hex(new PacketPhysicsCaptureSample(
                        TIMESTAMP,
                        770,
                        769,
                        0x1122334455667788L,
                        1.0, 64.0, 2.0,
                        0.1f, 0.0f, 0.2f,
                        0.1f, 0.0f, 0.2f,
                        0.1f,
                        (byte) 0x03,
                        1,
                        1,
                        (byte) 0,
                        false,
                        false,
                        false,
                        (byte) 0,
                        (byte) 50,
                        (byte) 0,
                        (byte) 1)));
    }

    @Test
    void inventoryTransactionFixtureIsStable() throws Exception {
        assertEquals(
                "26010203040506070800017500016e0002000000030004050006000700106d696e6563726166743a"
                        + "637572736f72000000080900000001000a000f6d696e6563726166743a73746f6e650000000b0c",
                hex(new PacketPlayerInventoryTransaction(
                        TIMESTAMP,
                        UID,
                        USERNAME,
                        (byte) 2,
                        3,
                        (short) 4,
                        (byte) 5,
                        (short) 6,
                        (short) 7,
                        new ItemStack("minecraft:cursor", 8, (byte) 9),
                        Arrays.asList(new PacketPlayerInventoryTransaction.ChangedSlot(
                                (short) 10,
                                new ItemStack("minecraft:stone", 11, (byte) 12))))));
    }

    @Test
    void externalForceFixtureIsStable() throws Exception {
        assertEquals(
                "27010203040506070800017500016e00063ff000000000000040000000000000004008000000000000"
                        + "401000000000000040140000000000004018000000000000401c0000000000004020000000000000"
                        + "40220000000000004024000000000000000b00000041",
                hex(new PacketPlayerExternalForce(
                        TIMESTAMP,
                        UID,
                        USERNAME,
                        ExternalForceType.SLIME_PISTON,
                        1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                        (short) 11,
                        ExternalForceFlags.HAS_SLIME | ExternalForceFlags.ENVIRONMENT_BACKED)));
    }

    private String hex(PacketEncode packet) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        packet.encode(out);
        StringBuilder result = new StringBuilder();
        for (byte value : out.toByteArray()) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
