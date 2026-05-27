package org.vennv.utils;

/**
 * Constants for effect operation flags
 * Synchronized with Rust protocol's EffectFlags
 */
public final class EffectFlags {

    /**
     * Add a new effect
     */
    public static final byte ADD = 0;

    /**
     * Modify an existing effect
     */
    public static final byte MODIFY = 1;

    /**
     * Remove an effect
     */
    public static final byte REMOVE = 2;

    // Private constructor to prevent instantiation
    private EffectFlags() {
        throw new AssertionError("EffectFlags is a utility class and cannot be instantiated");
    }

    /**
     * Get a human-readable name for the flag value
     */
    public static String getFlagName(byte flag) {
        switch (flag) {
            case ADD:
                return "ADD";
            case MODIFY:
                return "MODIFY";
            case REMOVE:
                return "REMOVE";
            default:
                return "UNKNOWN(" + flag + ")";
        }
    }

    /**
     * Check if a flag value is valid
     */
    public static boolean isValidFlag(byte flag) {
        return flag >= ADD && flag <= REMOVE;
    }
}
