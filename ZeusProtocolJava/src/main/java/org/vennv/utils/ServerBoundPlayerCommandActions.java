package org.vennv.utils;

/**
 * Represents server-bound player command actions
 */
public enum ServerBoundPlayerCommandActions {
    OPEN_INVENTORY(1),
    PRESS_SHIFT_KEY(2),
    RELEASE_SHIFT_KEY(3),
    START_FALL_FLYING(4),
    START_RIDING_JUMP(5),
    STOP_RIDING_JUMP(6),
    START_SPRINTING(7),
    STOP_SPRINTING(8),
    START_SNEAKING(9),
    STOP_SNEAKING(10),
    STOP_SLEEPING(11),
    STOP_FALL_FLYING(12),
    START_RIDING_BOAT(13),
    STOP_RIDING_BOAT(14),
    START_RIDING_VEHICLE(15),
    STOP_RIDING_VEHICLE(16),
    START_RIPTIDE(17),
    STOP_RIPTIDE(18),
    START_SWIMMING(19),
    STOP_SWIMMING(20);

    private final byte value;

    ServerBoundPlayerCommandActions(int value) {
        this.value = (byte) value;
    }

    public byte getValue() {
        return value;
    }

    /**
     * Get action from byte value
     */
    public static ServerBoundPlayerCommandActions fromValue(byte value) {
        for (ServerBoundPlayerCommandActions action : ServerBoundPlayerCommandActions.values()) {
            if (action.value == value) {
                return action;
            }
        }
        throw new IllegalArgumentException("Invalid action value: " + value);
    }

    @Override
    public String toString() {
        return name() + "(" + value + ")";
    }
}
