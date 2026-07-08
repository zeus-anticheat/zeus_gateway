package org.vennv;

public class PacketId {

    public static final byte PACKET_PLAYER_JOIN = 0x01;
    public static final byte PACKET_PLAYER_LEAVE = 0x02;
    public static final byte PACKET_PLAYER_POSITION = 0x03;
    public static final byte PACKET_PLAYER_KEEP_ALIVE = 0x04;
    public static final byte PACKET_PLAYER_CHANGE_MODE = 0x05;
    public static final byte PACKET_PLAYER_SWING_HAND = 0x06;
    public static final byte PACKET_PLAYER_ATTACK_ENTITY = 0x09;
    public static final byte PACKET_PLAYER_TELEPORT = 0x0A;
    public static final byte PACKET_PLAYER_EFFECT = 0x0B;
    public static final byte PACKET_PLAYER_GOT_DAMAGE = 0x0C;
    public static final byte PACKET_PLAYER_BLOCK_FACE = 0x0D;
    public static final byte PACKET_PLAYER_BLOCK_RAY_TRACE = 0x0E;
    public static final byte PACKET_PLAYER_BLOCK_CHANGE_ACK = 0x0F;
    public static final byte PACKET_PLAYER_ATTACKED_BY_ENTITY = 0x10;
    public static final byte PACKET_PLAYER_ENTITY_INTERACTION = 0x11;
    public static final byte PACKET_TPS_SERVER = 0x12;
    public static final byte PACKET_PLAYER_HELD_ITEM = 0x14;
    public static final byte PACKET_PLAYER_ARMORS_EQUIPMENT = 0x15;
    public static final byte PACKET_PLAYER_CONFIRM_TRANSACTION = 0x16;
    public static final byte PACKET_PLAYER_OPEN_WINDOW = 0x17;
    public static final byte PACKET_PLAYER_CLICK_WINDOW = 0x18;
    public static final byte PACKET_PLAYER_CLOSE_WINDOW = 0x19;
    public static final byte PACKET_PLAYER_USE_ITEM = 0x1A;
    public static final byte PACKET_PLAYER_RELEASE_USE_ITEM = 0x1B;
    public static final byte PACKET_PLAYER_STEER_VEHICLE = 0x1C;
    public static final byte PACKET_PLAYER_VEHICLE_MOVE = 0x1D;
    public static final byte PACKET_SERVER_BOUND_PLAYER_COMMAND = 0x1E;
    public static final byte PACKET_PLAYER_DEATH = 0x1F;
    public static final byte PACKET_PLAYER_CUSTOM_FEATURE = 0x20;
    public static final byte PACKET_PLAYER_ATTACKED_BY_PLAYER = 0x21;
    public static final byte PACKET_PLAYER_VELOCITY = 0x22;
    public static final byte PACKET_PLAYER_ENCHANTMENTS = 0x23;
    public static final byte PACKET_PLAYER_RESPAWN = 0x24;
    public static final byte PACKET_SERVER_CONFIG = 0x25;
    public static final byte PACKET_PLAYER_INVENTORY_TRANSACTION = 0x26;
    public static final byte PACKET_PLAYER_EXTERNAL_FORCE = 0x27;
    public static final byte PACKET_ENTITY_SPAWN = 0x28;
    public static final byte PACKET_ENTITY_MOVE = 0x29;
    public static final byte PACKET_ENTITY_DESTROY = 0x2A;
    public static final byte PACKET_BLOCK_CHANGE_EVENT = 0x2B;
    public static final byte PACKET_PLAYER_INPUT = 0x2C;
    public static final byte PACKET_CHUNK_DATA = 0x2D;
    public static final byte PACKET_UPDATE_ATTRIBUTES = 0x2E;
    public static final byte PACKET_PHYSICS_CAPTURE_SAMPLE = 0x2F;
}
