package org.vennv.utils;

/**
 * Represents the cause of damage to a player
 * Synchronized with Rust protocol's DamageCause enum
 */
public enum DamageCause {
    CONTACT(0),
    ENTITY_ATTACK(1),
    PROJECTILE(2),
    SUFFOCATION(3),
    FALL(4),
    FIRE(5),
    FIRE_TICK(6),
    LAVA(7),
    DROWNING(8),
    BLOCK_EXPLOSION(9),
    ENTITY_EXPLOSION(10),
    VOID(11),
    SUICIDE(12),
    MAGIC(13),
    CUSTOM(14),
    STARVATION(15),
    FALLING_BLOCK(16);

    private final int value;

    DamageCause(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * Get DamageCause from integer value
     */
    public static DamageCause fromValue(int value) {
        for (DamageCause cause : DamageCause.values()) {
            if (cause.value == value) {
                return cause;
            }
        }
        throw new IllegalArgumentException("Invalid damage cause value: " + value);
    }

    @Override
    public String toString() {
        return name() + "(" + value + ")";
    }
}
