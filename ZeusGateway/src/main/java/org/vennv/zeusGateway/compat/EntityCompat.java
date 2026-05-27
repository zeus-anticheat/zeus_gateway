package org.vennv.zeusGateway.compat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.vennv.zeusGateway.compat.nms.NmsEntityAccess;
import org.vennv.zeusGateway.platform.ServerVersion;

/**
 * Version-compatible accessors for entity dimensions.
 * <p>
 * <strong>Tier 1 (1.14+):</strong> Uses Bukkit API directly
 * ({@code Entity.getBoundingBox()}, {@code Entity.getHeight()}).
 * <p>
 * <strong>Tier 2 (1.8–1.13):</strong> Falls back to NMS reflection via
 * {@link NmsEntityAccess}, then to hardcoded safe defaults.
 */
public final class EntityCompat {

    private EntityCompat() {}

    /**
     * Get the entity's bounding-box height.
     *
     * @param entity the target entity
     * @return the height in blocks
     */
    public static double getHeight(Entity entity) {
        if (ServerVersion.HAS_BOUNDING_BOX) {
            try {
                return entity.getBoundingBox().getHeight();
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                // Flag-detection was wrong; fall through to NMS
            }
        }
        return NmsEntityAccess.getHeight(entity);
    }

    /**
     * Get the entity's bounding-box width (X-axis).
     *
     * @param entity the target entity
     * @return the width in blocks
     */
    public static double getWidth(Entity entity) {
        if (ServerVersion.HAS_BOUNDING_BOX) {
            try {
                return entity.getBoundingBox().getWidthX();
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                // Fall through to NMS
            }
        }
        return NmsEntityAccess.getWidth(entity);
    }

    /**
     * Get the player's height for position packets.
     *
     * @param player the player
     * @return height in blocks (typically 1.8 standing, 1.5 sneaking)
     */
    public static float getPlayerHeight(Player player) {
        if (ServerVersion.HAS_ENTITY_HEIGHT) {
            try {
                return (float) player.getHeight();
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                // Fall through
            }
        }
        // Safe fallback: standard Minecraft player dimensions
        return player.isSneaking() ? 1.5f : 1.8f;
    }

    /**
     * Get the player's width for ground detection.
     *
     * @param player the player
     * @return width in blocks (0.6 for all Minecraft versions)
     */
    public static double getPlayerWidth(Player player) {
        if (ServerVersion.HAS_ENTITY_HEIGHT) {
            try {
                return player.getWidth();
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                // Fall through
            }
        }
        return 0.6; // constant across all MC versions
    }
}
