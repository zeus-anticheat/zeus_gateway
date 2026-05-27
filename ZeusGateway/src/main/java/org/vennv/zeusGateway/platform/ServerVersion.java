package org.vennv.zeusGateway.platform;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Detects the runtime Minecraft server version and sets feature flags used by
 * the modern adapter. This is capability detection, not a support declaration;
 * published target status is maintained in {@code support-matrix.json}.
 * <p>
 * Feature flags are resolved once during {@link #init(Logger)} and cached
 * for the lifetime of the JVM. All flags are safe to read from any thread
 * after initialisation.
 */
public final class ServerVersion {

    // ── Parsed version components ─────────────────────────────────────────
    private static int MAJOR = 1;
    private static int MINOR = 21;
    private static int PATCH = 0;

    // ── NMS versioned package suffix (e.g. "v1_8_R3"). Null on 1.17+ ─────
    private static String NMS_VERSION;

    // ── Feature flags (resolved once at startup) ──────────────────────────
    /** {@code true} when Entity/Block {@code .getBoundingBox()} exists (1.14+). */
    public static boolean HAS_BOUNDING_BOX;

    /** {@code true} when the Flattening / BlockData API exists (1.13+). */
    public static boolean HAS_BLOCK_DATA;

    /** {@code true} when {@code Player.rayTraceBlocks()} exists (1.13.1+). */
    public static boolean HAS_RAY_TRACE;

    /** {@code true} when {@code Material.isAir()} method exists (1.15+). */
    public static boolean HAS_MATERIAL_IS_AIR;

    /** {@code true} when {@code ItemMeta.getCustomModelData()} exists (1.14+). */
    public static boolean HAS_CUSTOM_MODEL_DATA;

    /** {@code true} when {@code PotionEffectType.getKey()} exists (1.18+). */
    public static boolean HAS_POTION_KEY;

    /** {@code true} when {@code Entity.getHeight()/getWidth()} exists (1.14+). */
    public static boolean HAS_ENTITY_HEIGHT;

    /** {@code true} when {@code Entity.getPose()} exists (1.14+). */
    public static boolean HAS_ENTITY_POSE;

    public static boolean HAS_GENERIC_ATTACK_SPEED;
    public static boolean HAS_ENTITY_INTERACTION_RANGE;

    // ── ProtocolLib info ──────────────────────────────────────────────────
    public static boolean PROTOCOL_LIB_AVAILABLE;
    public static int PROTOCOL_LIB_MAJOR;   // 4 or 5

    private static boolean initialised;

    private ServerVersion() {}

    // ──────────────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Initialises the version detection system. Must be called once during
     * plugin load, before any compat classes are used.
     *
     * @param logger the plugin logger for diagnostic output
     */
    public static synchronized void init(Logger logger) {
        if (initialised) return;
        initialised = true;

        parseVersion();
        detectNmsVersion();
        resolveFeatureFlags();
        detectProtocolLib();

        logger.info("[ZeusGateway] Server version: " + MAJOR + "." + MINOR + "." + PATCH
                + " (NMS: " + (NMS_VERSION != null ? NMS_VERSION : "mojang-mapped") + ")");
        logger.info("[ZeusGateway] Feature flags: "
                + "BoundingBox=" + HAS_BOUNDING_BOX + ", "
                + "BlockData=" + HAS_BLOCK_DATA + ", "
                + "RayTrace=" + HAS_RAY_TRACE + ", "
                + "MaterialIsAir=" + HAS_MATERIAL_IS_AIR + ", "
                + "CustomModelData=" + HAS_CUSTOM_MODEL_DATA + ", "
                + "PotionKey=" + HAS_POTION_KEY + ", "
                + "EntityHeight=" + HAS_ENTITY_HEIGHT + ", "
                + "EntityPose=" + HAS_ENTITY_POSE);
        if (PROTOCOL_LIB_AVAILABLE) {
            logger.info("[ZeusGateway] ProtocolLib v" + PROTOCOL_LIB_MAJOR + ".x detected.");
        }
    }

    /** @return {@code true} if the server version is ≥ {@code major.minor}. */
    public static boolean isAtLeast(int major, int minor) {
        return MAJOR > major || (MAJOR == major && MINOR >= minor);
    }

    /** @return {@code true} if the server version is ≥ {@code major.minor.patch}. */
    public static boolean isAtLeast(int major, int minor, int patch) {
        if (MAJOR != major) return MAJOR > major;
        if (MINOR != minor) return MINOR > minor;
        return PATCH >= patch;
    }

    /**
     * @return the NMS versioned package suffix (e.g. {@code "v1_8_R3"}),
     *         or {@code null} on 1.17+ (Mojang-mapped CraftBukkit).
     */
    public static String nmsVersion() {
        return NMS_VERSION;
    }

    public static int major() { return MAJOR; }
    public static int minor() { return MINOR; }
    public static int patch() { return PATCH; }

    // ──────────────────────────────────────────────────────────────────────
    //  Internal parsing
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Parses the server version from {@code Bukkit.getBukkitVersion()}.
     * Format examples: "1.21.11-R0.1-SNAPSHOT", "1.8.8-R0.1-SNAPSHOT".
     */
    private static void parseVersion() {
        try {
            String raw = Bukkit.getBukkitVersion(); // e.g. "1.21.11-R0.1-SNAPSHOT"
            String version = raw.split("-")[0];      // "1.21.11"
            String[] parts = version.split("\\.");
            if (parts.length >= 1) MAJOR = Integer.parseInt(parts[0]);
            if (parts.length >= 2) MINOR = Integer.parseInt(parts[1]);
            if (parts.length >= 3) PATCH = Integer.parseInt(parts[2]);
        } catch (Exception ignored) {
            // Keep defaults (1.21.0)
        }
    }

    /**
     * Extracts the NMS version suffix from the CraftServer package path.
     * On 1.17+ Paper (or Mojang-mapped Spigot), the suffix is absent.
     */
    private static void detectNmsVersion() {
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            // org.bukkit.craftbukkit.v1_8_R3 → parts[3] = "v1_8_R3"
            String[] parts = pkg.split("\\.");
            if (parts.length >= 4 && parts[3].startsWith("v")) {
                NMS_VERSION = parts[3];
            }
        } catch (Exception ignored) {
            // NMS_VERSION stays null → Mojang-mapped
        }
    }

    /**
     * Resolves feature flags using version comparison combined with
     * class / method existence checks for double-safety.
     */
    private static void resolveFeatureFlags() {
        // 1.13+: BlockData API ("The Flattening")
        HAS_BLOCK_DATA = isAtLeast(1, 13) && classExists("org.bukkit.block.data.BlockData");

        // 1.13.1+: Player.rayTraceBlocks()
        HAS_RAY_TRACE = isAtLeast(1, 13, 1) && methodExists(
                org.bukkit.entity.Player.class, "rayTraceBlocks", double.class);

        // 1.14+: Entity.getBoundingBox(), Entity.getHeight()/getWidth()
        HAS_BOUNDING_BOX = isAtLeast(1, 14) && methodExists(
                org.bukkit.entity.Entity.class, "getBoundingBox");
        HAS_ENTITY_HEIGHT = isAtLeast(1, 14) && methodExists(
                org.bukkit.entity.Entity.class, "getHeight");
        HAS_ENTITY_POSE = isAtLeast(1, 14) && classExists("org.bukkit.entity.Pose");
        HAS_CUSTOM_MODEL_DATA = isAtLeast(1, 14);

        // 1.15+: Material.isAir()
        HAS_MATERIAL_IS_AIR = isAtLeast(1, 15) && methodExists(
                org.bukkit.Material.class, "isAir");

        // 1.18+: PotionEffectType.getKey()
        HAS_POTION_KEY = isAtLeast(1, 18) && methodExists(
                org.bukkit.potion.PotionEffectType.class, "getKey");

        // 1.9+: Attribute.GENERIC_ATTACK_SPEED
        HAS_GENERIC_ATTACK_SPEED = isAtLeast(1, 9) && classExists("org.bukkit.attribute.Attribute") 
            && enumExists("org.bukkit.attribute.Attribute", "GENERIC_ATTACK_SPEED");

        // 1.21.2+: Attribute.PLAYER_ENTITY_INTERACTION_RANGE
        HAS_ENTITY_INTERACTION_RANGE = isAtLeast(1, 21, 2) && classExists("org.bukkit.attribute.Attribute") 
            && enumExists("org.bukkit.attribute.Attribute", "PLAYER_ENTITY_INTERACTION_RANGE");
    }

    /**
     * Detects whether ProtocolLib is available and determines its major version.

     */
    private static void detectProtocolLib() {
        try {
            Class.forName("com.comphenix.protocol.ProtocolLibrary");
            PROTOCOL_LIB_AVAILABLE = true;

            org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("ProtocolLib");
            if (pl != null) {
                String ver = pl.getDescription().getVersion();
                // "5.3.0" → major 5; "4.8.0" → major 4
                PROTOCOL_LIB_MAJOR = ver.startsWith("5") ? 5
                        : ver.startsWith("4") ? 4
                        : 5; // default to 5 for unknown/future versions
            } else {
                PROTOCOL_LIB_MAJOR = 5;
            }
        } catch (ClassNotFoundException e) {
            PROTOCOL_LIB_AVAILABLE = false;
            PROTOCOL_LIB_MAJOR = 0;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Reflection helpers
    // ──────────────────────────────────────────────────────────────────────

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean methodExists(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            clazz.getMethod(methodName, parameterTypes);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static boolean enumExists(String className, String enumName) {
        try {
            Class<?> clazz = Class.forName(className);
            for (Object constant : clazz.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(enumName)) {
                    return true;
                }
            }
        } catch (Exception e) {}
        return false;
    }
}
