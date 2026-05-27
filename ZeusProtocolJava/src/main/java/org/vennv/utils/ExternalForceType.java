package org.vennv.utils;

/**
 * Typed external-force sources synchronized with Rust protocol's ExternalForceType enum.
 */
public enum ExternalForceType {
    GENERIC(0),
    PLAYER_ATTACK(1),
    ENTITY_ATTACK(2),
    EXPLOSION(3),
    WIND_CHARGE(4),
    PISTON(5),
    SLIME_PISTON(6),
    FISHING_HOOK(7),
    PROJECTILE(8),
    BUBBLE_COLUMN(9),
    ELYTRA_FIREWORK(10);

    private final int value;

    ExternalForceType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ExternalForceType fromValue(int value) {
        for (ExternalForceType type : ExternalForceType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid external force type: " + value);
    }
}
