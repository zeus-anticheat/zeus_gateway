package org.vennv.utils;

/**
 * Represents which hand is being used
 */
public enum Hand {
    MAIN_HAND(0),
    OFF_HAND(1);

    private final byte value;

    Hand(int value) {
        this.value = (byte) value;
    }

    public byte getValue() {
        return value;
    }

    /**
     * Get Hand from byte value
     */
    public static Hand fromValue(byte value) {
        for (Hand hand : Hand.values()) {
            if (hand.value == value) {
                return hand;
            }
        }
        throw new IllegalArgumentException("Invalid hand value: " + value);
    }

    @Override
    public String toString() {
        return name() + "(" + value + ")";
    }
}
