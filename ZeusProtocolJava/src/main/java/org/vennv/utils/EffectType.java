package org.vennv.utils;

/**
 * Represents potion effect types
 * Synchronized with Rust protocol's EffectType enum
 */
public enum EffectType {
    SPEED(1),
    SLOWNESS(2),
    HASTE(3),
    MINING_FATIGUE(4),
    STRENGTH(5),
    INSTANT_HEALTH(6),
    INSTANT_DAMAGE(7),
    JUMP_BOOST(8),
    NAUSEA(9),
    REGENERATION(10),
    RESISTANCE(11),
    FIRE_RESISTANCE(12),
    WATER_BREATHING(13),
    INVISIBILITY(14),
    BLINDNESS(15),
    NIGHT_VISION(16),
    HUNGER(17),
    WEAKNESS(18),
    POISON(19),
    WITHER(20),
    HEALTH_BOOST(21),
    ABSORPTION(22),
    SATURATION(23),
    GLOWING(24),
    LEVITATION(25),
    LUCK(26),
    UNLUCK(27),
    SLOW_FALLING(28),
    CONDUIT_POWER(29),
    DOLPHINS_GRACE(30),
    BAD_OMEN(31),
    HERO_OF_THE_VILLAGE(32),
    MEMORY(33),
    INCREASE_DAMAGE(34),
    DECREASE_DAMAGE(35),
    FAST_DIGGING(36),
    SLOW_DIGGING(37),
    FAST_HEALING(38),
    HARMING(39),
    JUMP(40),
    CONFUSION(41),
    UNDEFINED(255);

    private final int value;

    EffectType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * Get EffectType from integer value.
     *
     * @deprecated Minecraft numeric potion IDs are deprecated since 1.6.2 and
     *             marked for removal. Prefer {@link #fromKey(String)} instead.
     */
    @Deprecated
    public static EffectType fromValue(int value) {
        for (EffectType type : EffectType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        return UNDEFINED;
    }

    /**
     * Get EffectType from a Minecraft {@code NamespacedKey} key string
     * (e.g. {@code "jump_boost"}, {@code "speed"}).
     * <p>
     * The key is uppercased and matched against enum constant names, so
     * {@code "jump_boost"} maps to {@link #JUMP_BOOST}, etc.
     */
    public static EffectType fromKey(String key) {
        if (key == null) {
            return UNDEFINED;
        }
        try {
            return EffectType.valueOf(key.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNDEFINED;
        }
    }

    @Override
    public String toString() {
        return name() + "(" + value + ")";
    }
}
