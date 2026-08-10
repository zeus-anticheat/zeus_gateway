package org.vennv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.vennv.packets.PacketChunkData;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketMovementStateSnapshot;
import org.vennv.packets.PacketPlayerAttackEntity;
import org.vennv.packets.PacketPlayerAbilities;
import org.vennv.packets.PacketPlayerExternalForce;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.packets.PacketPhysicsCaptureSample;
import org.vennv.packets.PacketPlayerInput;
import org.vennv.packets.PacketPlayerVehicleMove;
import org.vennv.packets.PacketPlayerVelocity;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketPlayerTeleport;
import org.vennv.packets.PacketShulkerBoxAction;
import org.vennv.packets.PacketServerConfig;
import org.vennv.packets.PacketUpdateAttributes;
import org.vennv.utils.ExternalForceFlags;
import org.vennv.utils.ExternalForceType;
import org.vennv.utils.ItemStack;

/** Golden payloads for the packets consumed by the cross-platform physics path. */
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
        assertEquals(0x2C, PacketId.PACKET_PLAYER_INPUT & 0xff);
        assertEquals(0x2D, PacketId.PACKET_CHUNK_DATA & 0xff);
        assertEquals(0x2E, PacketId.PACKET_UPDATE_ATTRIBUTES & 0xff);
        assertEquals(0x2F, PacketId.PACKET_PHYSICS_CAPTURE_SAMPLE & 0xff);
        assertEquals(0x30, PacketId.PACKET_COLLISION_WINDOW & 0xff);
        assertEquals(0x31, PacketId.PACKET_SHULKER_BOX_ACTION & 0xff);
        assertEquals(0x32, PacketId.PACKET_MOVEMENT_STATE_SNAPSHOT & 0xff);
        assertEquals(0x33, PacketId.PACKET_PLAYER_ABILITIES & 0xff);
    }

    @Test
    void serverAbilitiesMatchRustSourceAwareWire() throws Exception {
        PacketPlayerAbilities packet = PacketPlayerAbilities.server(
                TIMESTAMP, UID, USERNAME, 11L, true, true, 0.08f);

        assertEquals(
                "33010203040506070800017500016e00000000000000000b00033da3d70a",
                hex(packet));
    }

    @Test
    void movementStateSnapshotRoundTripsFullReplacementWire() throws Exception {
        PacketMovementStateSnapshot.Snapshot snapshot = new PacketMovementStateSnapshot.Snapshot(
                2,
                new PacketMovementStateSnapshot.Attributes(
                        true, 0.13f, 0.07, 0.5, 0.75, 1.25, 0.4, 0.2, 0.3,
                        Arrays.asList(new PacketUpdateAttributes.Property(
                                "minecraft:movement_speed",
                                0.1,
                                Arrays.asList(new PacketUpdateAttributes.Modifier(
                                        "plugin-item",
                                        "plugin:item_speed",
                                        0.3,
                                        PacketUpdateAttributes.Operation.MULTIPLY_TOTAL))))),
                new PacketMovementStateSnapshot.Abilities(true, true, 0.08f),
                true,
                true,
                true,
                true,
                new PacketMovementStateSnapshot.UseItem(true, true, false, false, false),
                new PacketMovementStateSnapshot.Vehicle(
                        "minecraft:horse", 42, (byte) 5, 0.225, 0.7, true),
                Arrays.asList(new Effect((byte) 8, (byte) 1, 120, (byte) 0)));

        List<PacketMovementStateSnapshot> fragments = PacketMovementStateSnapshot.createFragments(
                TIMESTAMP, UID, USERNAME, 11, 13, snapshot);

        assertEquals(1, fragments.size());
        PacketMovementStateSnapshot fragment = fragments.get(0);
        assertEquals(0x32, fragment.encodeDatagram()[0] & 0xff);
        assertEquals(
                "32010203040506070800017500016e0002000000000000000b000000000000000d00000001000000c8"
                        + "e0cdbb4c00c800000002013e051eb83fb1eb851eb851ec3fe00000000000003fe80000000000003ff4"
                        + "0000000000003fd999999999999a3fc999999999999a3fd33333333333330100186d696e6563726166"
                        + "743a6d6f76656d656e745f73706565643fb999999999999a0001000b706c7567696e2d6974656d00"
                        + "11706c7567696e3a6974656d5f73706565643fd33333333333330201013da3d70a0f0301000f6d696e"
                        + "6563726166743a686f7273650000002a050f3fcccccccccccccd3fe666666666666600010801000000"
                        + "7800",
                hex(fragment));
        assertTrue(fragment.encodedDatagramLength() <= PacketMovementStateSnapshot.MAX_DATAGRAM_LENGTH);
        assertEquals(fragment, PacketMovementStateSnapshot.decodeDatagram(fragment.encodeDatagram()));
        PacketMovementStateSnapshot.Snapshot decoded = PacketMovementStateSnapshot.reassemble(fragments);
        assertEquals(snapshot, decoded);
        assertEquals("plugin-item", decoded.getAttributes().getProperties().get(0)
                .getModifiers().get(0).getStableId());
        assertThrows(
                java.io.IOException.class,
                () -> PacketMovementStateSnapshot.reassemble(Arrays.asList()));
    }

    @Test
    void sourceAwareGravityOnlyAttributeUpdateKeepsRawModifierWire() throws Exception {
        PacketUpdateAttributes packet = PacketUpdateAttributes.merge(
                TIMESTAMP,
                UID,
                USERNAME,
                Arrays.asList(new PacketUpdateAttributes.Property(
                        "minecraft:gravity",
                        0.08,
                        Arrays.asList(new PacketUpdateAttributes.Modifier(
                                "plugin-id",
                                "plugin:gravity_boost",
                                0.25,
                                PacketUpdateAttributes.Operation.MULTIPLY_BASE)))));

        assertEquals(
                "2e010203040506070800017500016e007fc0a55a01000100116d696e6563726166743a67726176697479"
                        + "3fb47ae147ae147b00010009706c7567696e2d69640014706c7567696e3a677261766974795f626f6f73"
                        + "743fd000000000000001",
                hex(packet));
    }

    @Test
    void shulkerBoxActionKeepsBlockEventFields() throws Exception {
        PacketShulkerBoxAction packet = new PacketShulkerBoxAction(
                TIMESTAMP, UID, USERNAME, -34, 4, 395, (byte) 1, (byte) 0);
        assertEquals(1, packet.getActionType());
        assertEquals(0, packet.getViewerCount());
        assertEquals(
                "31010203040506070800017500016e00ffffffde000000040000018b0100",
                hex(packet));
    }

    @Test
    void collisionWindowFixtureIsStableAcrossLanguages() throws Exception {
        char[] blockStateChars = new char[512];
        Arrays.fill(blockStateChars, 'x');
        String blockState = new String(blockStateChars);
        List<PacketCollisionWindow.Cell> cells =
                new ArrayList<PacketCollisionWindow.Cell>(PacketCollisionWindow.COLLISION_WINDOW_CELLS);
        for (int index = 0; index < PacketCollisionWindow.COLLISION_WINDOW_CELLS; index++) {
            if (index % 3 == 0) {
                cells.add(PacketCollisionWindow.Cell.unknown());
            } else if (index % 3 == 1) {
                cells.add(PacketCollisionWindow.Cell.knownAir());
            } else {
                cells.add(PacketCollisionWindow.Cell.knownBlock(blockState));
            }
        }
        PacketCollisionWindow.CollisionWindowUpdate update =
                PacketCollisionWindow.CollisionWindowUpdate.full(7, 13, -17, -64, -33, cells);
        PacketCollisionWindow.EncodedPayload payload = update.encodePayload();
        assertEquals(PacketCollisionWindow.Encoding.DENSE, payload.getEncoding());
        assertEquals(1278, payload.getPayloadLength());
        assertEquals(0xfab36c6fL, payload.getCrc32());

        List<PacketCollisionWindow> fragments = update.toFragments(17, "test-uid", "Tester");
        assertEquals(2, fragments.size());
        assertEquals(1107, fragments.get(0).getFragmentPayload().length);
        assertEquals(171, fragments.get(1).getFragmentPayload().length);
        byte[] firstDatagram = fragments.get(0).encodeDatagram();
        byte[] secondDatagram = fragments.get(1).encodeDatagram();
        assertEquals(1200, firstDatagram.length);
        assertEquals(264, secondDatagram.length);
        assertEquals(0x30, firstDatagram[0] & 0xff);
        assertEquals(0x30, secondDatagram[0] & 0xff);
        assertEquals(
                "aa95f906e7589b01d4bcb5192f9f8ea9978078ab98b0ab7e3b3a3bb8fef3aa82",
                sha256(firstDatagram));
        assertEquals(
                "4a72a14cec701ccf238fb88b003b3be39611cdf6105fb4fc0e9ae062852e39fc",
                sha256(secondDatagram));

        PacketCollisionWindow first = PacketCollisionWindow.decodeDatagram(firstDatagram);
        PacketCollisionWindow second = PacketCollisionWindow.decodeDatagram(secondDatagram);
        assertEquals(17, first.getTimestamp());
        assertEquals("test-uid", first.getUid());
        assertEquals("Tester", first.getUsername());
        assertEquals(null, first.getOptionalProtocolVersion());
        assertEquals(0, first.getFragmentIndex());
        assertEquals(1, second.getFragmentIndex());
        assertEquals(2, first.getFragmentCount());
        assertEquals(2, second.getFragmentCount());
        assertEquals(1278, first.getTotalPayloadLength());
        assertEquals(0xfab36c6fL, first.getPayloadCrc32());
        assertEquals(update, PacketCollisionWindow.reassemble(Arrays.asList(second, first)));
    }

    @Test
    void chunkEncodedSizeMatchesWireBytes() throws Exception {
        PacketChunkData.BlockData block = new PacketChunkData.BlockData(
                (byte) 1, 64, (byte) 2, "minecraft:oak_log[axis=y]");
        PacketChunkData packet = new PacketChunkData(
                TIMESTAMP, UID, USERNAME, 3, 4, true, Arrays.asList(block));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        packet.encode(out);
        assertEquals(
                PacketChunkData.encodedBaseSize(UID, USERNAME)
                        + PacketChunkData.encodedBlockSize(block.blockType),
                out.size());
        assertTrue(out.size() <= PacketChunkData.MAX_UDP_PAYLOAD);
    }

    @Test
    void chunkCompletionUsesSecondFlagBit() throws Exception {
        PacketChunkData marker = new PacketChunkData(
                TIMESTAMP, UID, USERNAME, 3, 4, false, true, java.util.Collections.emptyList());
        assertTrue(hex(marker).endsWith("0200000000"));
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
    void playerInputTrustMarkerKeepsWireShape() throws Exception {
        PacketPlayerInput trusted = new PacketPlayerInput(
                TIMESTAMP, UID, USERNAME, (byte) 0xD5);
        PacketPlayerInput fallback = new PacketPlayerInput(
                TIMESTAMP, UID, USERNAME, (byte) 0x55);

        assertEquals("2c010203040506070800017500016e00d5", hex(trusted));
        assertEquals("2c010203040506070800017500016e0055", hex(fallback));
        assertTrue(trusted.isTrustedCapture());
        assertTrue(!fallback.isTrustedCapture());
    }

    @Test
    void vehicleMoveCarriesServerMountIdentity() throws Exception {
        PacketPlayerVehicleMove packet = new PacketPlayerVehicleMove(
                TIMESTAMP, UID, USERNAME,
                1.0, 2.0, 3.0, 4.0f, 5.0f,
                "minecraft:acacia_boat", 42,
                PacketPlayerVehicleMove.FLAG_MOUNTED | PacketPlayerVehicleMove.FLAG_IN_WATER);

        assertEquals("minecraft:acacia_boat", packet.getVehicleType());
        assertEquals(42, packet.getVehicleId());
        assertEquals(3, packet.getVehicleFlags());
        assertTrue(hex(packet).endsWith("00156d696e6563726166743a6163616369615f626f61740000002a03"));
    }

    @Test
    void horseVehicleTelemetryUsesTrailingVersionedExtension() throws Exception {
        PacketPlayerVehicleMove packet = new PacketPlayerVehicleMove(
                TIMESTAMP, UID, USERNAME,
                1.0, 2.0, 3.0, 4.0f, 5.0f,
                "minecraft:horse", 20,
                PacketPlayerVehicleMove.FLAG_MOUNTED | PacketPlayerVehicleMove.FLAG_ON_GROUND,
                0.225f, 0.7, true, true);

        assertEquals(0.225f, packet.getHorseMovementSpeed());
        assertEquals(0.7, packet.getHorseJumpStrength());
        assertTrue(packet.isHorseSaddleKnown());
        assertTrue(packet.isHorseSaddled());
        assertTrue(hex(packet).endsWith("013e6666663fe666666666666603"));
    }

    @Test
    void legacyTeleportKeepsPositionOnlyWireShape() throws Exception {
        PacketPlayerTeleport packet = new PacketPlayerTeleport(
                TIMESTAMP, UID, USERNAME, 1.0, 2.0, 3.0);

        assertEquals(PacketPlayerTeleport.SOURCE_SERVER_EVENT, packet.getSource());
        assertTrue(!packet.awaitsClientConfirmation());
        assertEquals(
                "0a010203040506070800017500016e003ff0000000000000400000000000000040080000000000"
                        + "00",
                hex(packet));
    }

    @Test
    void outboundTeleportAppendsVersionedMetadata() throws Exception {
        PacketPlayerTeleport packet = PacketPlayerTeleport.outbound(
                TIMESTAMP, UID, USERNAME, 1.0, 2.0, 3.0, -7);

        assertEquals(PacketPlayerTeleport.SOURCE_OUTBOUND_PACKET, packet.getSource());
        assertEquals(-7, packet.getTeleportId());
        assertTrue(packet.awaitsClientConfirmation());
        assertTrue(hex(packet).endsWith("0101fffffff9"));
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
    void physicsCaptureFixtureUsesSchemaV3Contract() throws Exception {
        String encoded = hex(new PacketPhysicsCaptureSample(
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
                (byte) 1));
        assertTrue(encoded.startsWith("2f010203040506070803"));
        assertTrue(encoded.contains("1122334455667788"));
    }

    @Test
    void serverConfigPublishesIdentityOnlyV1Extension() throws Exception {
        assertEquals(
                "25010203040506070800017500016e004040000041200000003dcccccd0102ff0004312e3231"
                        + "0005706170657200057061706572000776616e696c6c6102ff0004312e32310000000767617465776179",
                hex(new PacketServerConfig(
                        TIMESTAMP, UID, USERNAME, 3.0f, 10.0f, (byte) 0, 0.1f,
                        767, "1.21", "paper", "paper", "vanilla", 767, "1.21",
                        "", "gateway")));
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

    private String sha256(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
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
