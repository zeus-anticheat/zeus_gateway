package org.vennv.zeusGateway.compat.nms;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.vennv.zeusGateway.platform.ServerVersion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Provides entity width/height information on pre-1.14 servers using
 * NMS reflection.
 * <p>
 * On NMS 1.8 – 1.13 the internal {@code Entity} class exposes two public
 * float fields:
 * <ul>
 *   <li>{@code width} — the entity's horizontal collision size</li>
 *   <li>{@code length} — the entity's vertical height (confusingly named)</li>
 * </ul>
 * This class reflectively accesses those fields via
 * {@code CraftEntity.getHandle()}.
 * <p>
 * On 1.14+ this class is <em>never used</em> because the Bukkit API
 * provides {@code Entity.getBoundingBox()}, {@code Entity.getHeight()},
 * and {@code Entity.getWidth()} directly.
 */
public final class NmsEntityAccess {

    // Cached reflection handles — resolved once at class-load time.
    private static final Method GET_HANDLE;
    private static final Field WIDTH_FIELD;
    private static final Field HEIGHT_FIELD;
    private static final boolean AVAILABLE;

    static {
        Method getHandle = null;
        Field widthField = null;
        Field heightField = null;
        boolean ok = false;

        String nmsVersion = ServerVersion.nmsVersion();
        if (nmsVersion != null && !ServerVersion.isAtLeast(1, 14)) {
            try {
                // CraftBukkit: org.bukkit.craftbukkit.v1_X_RY.entity.CraftEntity
                Class<?> craftEntity = Class.forName(
                        "org.bukkit.craftbukkit." + nmsVersion + ".entity.CraftEntity");
                getHandle = craftEntity.getMethod("getHandle");

                // NMS: net.minecraft.server.v1_X_RY.Entity
                Class<?> nmsEntity = Class.forName(
                        "net.minecraft.server." + nmsVersion + ".Entity");

                // Entity.width (float) — horizontal size
                widthField = nmsEntity.getDeclaredField("width");
                widthField.setAccessible(true);

                // Entity.length (float) — vertical height
                // Named "length" on 1.8–1.13; some forks label it "height"
                try {
                    heightField = nmsEntity.getDeclaredField("length");
                } catch (NoSuchFieldException e1) {
                    heightField = nmsEntity.getDeclaredField("height");
                }
                heightField.setAccessible(true);

                ok = true;
            } catch (Exception ignored) {
                // Falls through — AVAILABLE stays false, callers get safe defaults
            }
        }

        GET_HANDLE = getHandle;
        WIDTH_FIELD = widthField;
        HEIGHT_FIELD = heightField;
        AVAILABLE = ok;
    }

    private NmsEntityAccess() {}

    /**
     * Returns the entity's height via NMS reflection.
     * Falls back to {@code LivingEntity.getEyeHeight() / 0.85} or {@code 1.8}
     * if reflection is unavailable.
     */
    public static double getHeight(Entity entity) {
        if (AVAILABLE) {
            try {
                Object handle = GET_HANDLE.invoke(entity);
                return ((Number) HEIGHT_FIELD.get(handle)).doubleValue();
            } catch (Exception ignored) {
                // fall through
            }
        }
        // Safe fallback
        if (entity instanceof LivingEntity) {
            return ((LivingEntity) entity).getEyeHeight() / 0.85;
        }
        return 1.8;
    }

    /**
     * Returns the entity's width via NMS reflection.
     * Falls back to {@code 0.6} (standard for players and most mobs).
     */
    public static double getWidth(Entity entity) {
        if (AVAILABLE) {
            try {
                Object handle = GET_HANDLE.invoke(entity);
                return ((Number) WIDTH_FIELD.get(handle)).doubleValue();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return 0.6;
    }
}
