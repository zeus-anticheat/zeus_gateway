package org.vennv.zeusGateway.listener;

/**
 * Bukkit fallbacks that can be replaced by a successfully registered raw listener.
 */
public enum RawCaptureCapability {
    PLACE_BLOCK,
    DIGGING_BLOCK,
    BLOCK_FACE,
    HELD_ITEM,
    CLICK_WINDOW,
    USE_ITEM,
    SWING_HAND,
    ATTACK_ENTITY,
    VEHICLE_MOVE,
    PLAYER_COMMAND
}
