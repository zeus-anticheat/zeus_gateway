package org.vennv.utils;

/**
 * Represents the category of a custom feature packet.
 * Synchronized with the backend analysis system for efficient transmission.
 */
public enum CustomFeatureCategory {
    COMBAT(1),
    MOVEMENT(2),
    INTERACT(3),
    TRANSACTION(4),
    OTHER(5);

    private final int id;

    CustomFeatureCategory(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    /**
     * Get CustomFeatureCategory from integer ID
     */
    public static CustomFeatureCategory fromId(int id) {
        for (CustomFeatureCategory category : CustomFeatureCategory.values()) {
            if (category.id == id) {
                return category;
            }
        }
        throw new IllegalArgumentException("Invalid custom feature category id: " + id);
    }

    @Override
    public String toString() {
        return name() + "(" + id + ")";
    }
}
