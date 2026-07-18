package org.vennv.zeusGateway.compat;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.vennv.zeusGateway.platform.ServerVersion;

/**
 * Version-compatible accessors for block properties.
 * <p>
 * Provides safe alternatives for APIs that do not exist on older
 * Minecraft versions:
 * <ul>
 *   <li>{@code Block.getBoundingBox()} → 1.14+</li>
 *   <li>{@code Block.getBlockData().getAsString()} → 1.13+</li>
 *   <li>{@code Material.isAir()} → 1.15+</li>
 * </ul>
 */
public final class BlockCompat {

    private BlockCompat() {}

    // ──────────────────────────────────────────────────────────────────────
    //  Air detection
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Version-safe check for air blocks.
     * Uses {@code Material.isAir()} on 1.15+, falls back to name matching
     * on older versions (covers AIR, CAVE_AIR, VOID_AIR, LEGACY_AIR).
     */
    public static boolean isAir(Block block) {
        if (ServerVersion.HAS_MATERIAL_IS_AIR) {
            try {
                boolean air = block.getType().isAir();
                // Paper API may return AIR Material for post-1.15 blocks
                // (e.g. honey_block) that are NOT air. Validate against
                // the actual block-data string when getBlockData() is
                // available.
                if (air && ServerVersion.HAS_BLOCK_DATA) {
                    try {
                        String data = block.getBlockData().getAsString();
                        if (!data.endsWith(":air") && !data.contains(":cave_air")
                                && !data.contains(":void_air")) {
                            return false; // data says it's a real block, not air
                        }
                    } catch (NoSuchMethodError | NoClassDefFoundError ignored) {}
                }
                return air;
            } catch (NoSuchMethodError e) {
                // Fall through
            }
        }
        // Pre-1.15 fallback
        String name = block.getType().name();
        return name.equals("AIR") || name.equals("CAVE_AIR")
                || name.equals("VOID_AIR") || name.equals("LEGACY_AIR");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Block data string
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Gets a block-data string compatible with the Zeus binary protocol.
     * <p>
     * On 1.13+ (post-Flattening): uses {@code block.getBlockData().getAsString()}
     * which returns something like {@code "minecraft:stone_slab[type=bottom]"}.
     * <p>
     * On pre-1.13 (pre-Flattening): returns a synthetic namespaced ID like
     * {@code "minecraft:stone"} from the material name.
     */
    public static String getBlockDataString(Block block) {
        if (ServerVersion.HAS_BLOCK_DATA) {
            try {
                return block.getBlockData().getAsString();
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                // Fall through
            }
        }
        // Pre-1.13 fallback: construct from Material name
        return "minecraft:" + block.getType().name().toLowerCase(java.util.Locale.ROOT);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Block bounding box (as raw double array)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Gets the block's axis-aligned bounding box as a flat array
     * {@code [minX, minY, minZ, maxX, maxY, maxZ]}.
     * <p>
     * Using a raw array avoids direct references to {@code org.bukkit.util.BoundingBox}
     * in calling code, ensuring class-loading safety on pre-1.13.2 servers.
     * <p>
     * Returns {@code null} for non-solid / air blocks (callers should skip them).
     *
     * @param block the block to measure
     * @return 6-element double array, or {@code null} if the block has no collision
     */
    public static double[] getBlockBoundsArray(Block block) {
        Material mat = block.getType();
        if (!mat.isSolid()) return null;

        if (ServerVersion.HAS_BOUNDING_BOX) {
            try {
                org.bukkit.util.BoundingBox bb = block.getBoundingBox();
                return new double[] {
                    bb.getMinX(), bb.getMinY(), bb.getMinZ(),
                    bb.getMaxX(), bb.getMaxY(), bb.getMaxZ()
                };
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                // Fall through to estimation
            }
        }

        return estimateBlockBoundsArray(block);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Pre-1.14 block bounds estimation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Estimates block collision bounds from the material name when
     * {@code Block.getBoundingBox()} is unavailable (pre-1.14).
     */
    private static double[] estimateBlockBoundsArray(Block block) {
        Material mat = block.getType();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        String name = mat.name();

        // ── Slabs ──────────────────────────────────────────────────────
        if (name.contains("SLAB")) {
            // On 1.13+ we can check BlockData for top/bottom
            if (ServerVersion.HAS_BLOCK_DATA) {
                try {
                    String data = block.getBlockData().getAsString();
                    if (data.contains("type=top")) {
                        return new double[] {x, y + 0.5, z, x + 1.0, y + 1.0, z + 1.0};
                    }
                    if (data.contains("type=double")) {
                        return new double[] {x, y, z, x + 1.0, y + 1.0, z + 1.0};
                    }
                } catch (NoSuchMethodError | NoClassDefFoundError ignored) {}
            }
            // Bottom slab or pre-1.13
            return new double[] {x, y, z, x + 1.0, y + 0.5, z + 1.0};
        }

        // ── Stairs ─────────────────────────────────────────────────────
        if (name.contains("STAIRS")) {
            // Treat as full block for collision purposes (conservative)
            return new double[] {x, y, z, x + 1.0, y + 1.0, z + 1.0};
        }

        // ── Fences ─────────────────────────────────────────────────────
        if (name.contains("FENCE") && !name.contains("GATE")) {
            return new double[] {x + 0.375, y, z + 0.375, x + 0.625, y + 1.5, z + 0.625};
        }

        // ── Fence gates ────────────────────────────────────────────────
        if (name.contains("FENCE_GATE")) {
            if (ServerVersion.HAS_BLOCK_DATA) {
                try {
                    String data = block.getBlockData().getAsString();
                    if (data.contains("open=true")) {
                        return null; // open gate = no collision
                    }
                } catch (NoSuchMethodError | NoClassDefFoundError ignored) {}
            }
            return new double[] {x, y, z, x + 1.0, y + 1.5, z + 1.0};
        }

        // ── Walls ──────────────────────────────────────────────────────
        if (name.contains("WALL") && !name.contains("SIGN") && !name.contains("BANNER")
                && !name.contains("TORCH") && !name.contains("HEAD") && !name.contains("FAN")) {
            return new double[] {x + 0.25, y, z + 0.25, x + 0.75, y + 1.5, z + 0.75};
        }

        // ── Beds ───────────────────────────────────────────────────────
        if (name.contains("BED") && !name.contains("BEDROCK")) {
            return new double[] {x, y, z, x + 1.0, y + 0.5625, z + 1.0};
        }

        // ── Carpet ─────────────────────────────────────────────────────
        if (name.contains("CARPET")) {
            return new double[] {x, y, z, x + 1.0, y + 0.0625, z + 1.0};
        }

        // ── Pressure plates ────────────────────────────────────────────
        if (name.contains("PRESSURE_PLATE")) {
            return new double[] {x + 0.0625, y, z + 0.0625, x + 0.9375, y + 0.03125, z + 0.9375};
        }

        // ── Snow layer ─────────────────────────────────────────────────
        if (name.equals("SNOW")) {
            double height = 0.125;
            if (ServerVersion.HAS_BLOCK_DATA) {
                try {
                    String data = block.getBlockData().getAsString();
                    int marker = data.indexOf("layers=");
                    if (marker >= 0) {
                        int start = marker + "layers=".length();
                        int end = data.indexOf(']', start);
                        if (end < 0) {
                            end = data.length();
                        }
                        height = Integer.parseInt(data.substring(start, end)) * 0.125;
                    }
                } catch (NoSuchMethodError | NoClassDefFoundError | NumberFormatException ignored) {}
            }
            return new double[] {x, y, z, x + 1.0, y + height, z + 1.0};
        }

        // ── Soul sand ──────────────────────────────────────────────────
        if (name.equals("SOUL_SAND")) {
            return new double[] {x, y, z, x + 1.0, y + 0.875, z + 1.0};
        }

        // ── Enchantment table ──────────────────────────────────────────
        if (name.contains("ENCHANT")) {
            return new double[] {x, y, z, x + 1.0, y + 0.75, z + 1.0};
        }

        // ── End portal frame ───────────────────────────────────────────
        if (name.contains("END_PORTAL_FRAME")) {
            return new double[] {x, y, z, x + 1.0, y + 0.8125, z + 1.0};
        }

        // ── Trapdoors ──────────────────────────────────────────────────
        if (name.contains("TRAPDOOR")) {
            if (ServerVersion.HAS_BLOCK_DATA) {
                try {
                    String data = block.getBlockData().getAsString();
                    if (data.contains("open=true")) {
                        return null; // open trapdoor = passable
                    }
                    if (data.contains("half=top")) {
                        return new double[] {x, y + 0.8125, z, x + 1.0, y + 1.0, z + 1.0};
                    }
                } catch (NoSuchMethodError | NoClassDefFoundError ignored) {}
            }
            return new double[] {x, y, z, x + 1.0, y + 0.1875, z + 1.0};
        }

        // ── Doors ──────────────────────────────────────────────────────
        if (name.contains("DOOR") && !name.contains("TRAPDOOR")) {
            if (ServerVersion.HAS_BLOCK_DATA) {
                try {
                    String data = block.getBlockData().getAsString();
                    if (data.contains("open=true")) {
                        return null; // open door = passable
                    }
                } catch (NoSuchMethodError | NoClassDefFoundError ignored) {}
            }
            return new double[] {x, y, z, x + 1.0, y + 1.0, z + 1.0};
        }

        // ── Lily pad ───────────────────────────────────────────────────
        if (name.equals("LILY_PAD") || name.equals("WATER_LILY")) {
            return new double[] {x + 0.0625, y, z + 0.0625, x + 0.9375, y + 0.09375, z + 0.9375};
        }

        // ── Default: full solid block ──────────────────────────────────
        return new double[] {x, y, z, x + 1.0, y + 1.0, z + 1.0};
    }
}
