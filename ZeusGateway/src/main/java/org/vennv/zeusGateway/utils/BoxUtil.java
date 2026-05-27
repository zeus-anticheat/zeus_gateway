package org.vennv.zeusGateway.utils;

/**
 * Axis-aligned bounding box collision utilities.
 * <p>
 * Uses raw doubles instead of {@code org.bukkit.util.BoundingBox} to avoid
 * class-loading issues on pre-1.13.2 servers where BoundingBox does not exist.
 */
public class BoxUtil {

    /**
     * Checks whether a player's XZ footprint overlaps with a block's XZ bounds.
     *
     * @param playerMinX player bounding box min X
     * @param playerMaxX player bounding box max X
     * @param playerMinZ player bounding box min Z
     * @param playerMaxZ player bounding box max Z
     * @param blockMinX  block bounding box min X
     * @param blockMaxX  block bounding box max X
     * @param blockMinZ  block bounding box min Z
     * @param blockMaxZ  block bounding box max Z
     * @return {@code true} if the two boxes overlap on the XZ plane
     */
    public static boolean boxesOverlapXZ(double playerMinX, double playerMaxX,
                                   double playerMinZ, double playerMaxZ,
                                   double blockMinX, double blockMaxX,
                                   double blockMinZ, double blockMaxZ) {
        return playerMaxX >= blockMinX - 0.0001 &&
                playerMinX <= blockMaxX + 0.0001 &&
                playerMaxZ >= blockMinZ - 0.0001 &&
                playerMinZ <= blockMaxZ + 0.0001;
    }
}
