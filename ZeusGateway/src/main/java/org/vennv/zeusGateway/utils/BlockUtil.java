package org.vennv.zeusGateway.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.vennv.utils.RelativeBlock;
import org.vennv.zeusGateway.compat.BlockCompat;
import org.vennv.zeusGateway.compat.EntityCompat;

import java.util.ArrayList;
import java.util.List;

public class BlockUtil {

    public static boolean isOnGround(Player player, Vector pos) {
        World world = player.getWorld();
        final double epsilon = 0.0001;
        final double playerWidth = EntityCompat.getPlayerWidth(player);

        double footY = pos.getY() - epsilon;
        int blockY = MathUtil.floor(footY);

        double minX = pos.getX() - (playerWidth / 2);
        double maxX = pos.getX() + (playerWidth / 2);
        double minZ = pos.getZ() - (playerWidth / 2);
        double maxZ = pos.getZ() + (playerWidth / 2);

        int minBlockX = MathUtil.floor(minX);
        int maxBlockX = MathUtil.floor(maxX);
        int minBlockZ = MathUtil.floor(minZ);
        int maxBlockZ = MathUtil.floor(maxZ);

        for (int x = minBlockX; x <= maxBlockX; x++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
                Block block = world.getBlockAt(x, blockY, z);

                if (BlockCompat.isAir(block) || block.isLiquid()) {
                    continue;
                }

                double[] bounds = BlockCompat.getBlockBoundsArray(block);
                if (bounds == null) {
                    continue;
                }

                // bounds = [minX, minY, minZ, maxX, maxY, maxZ]
                if (footY >= bounds[1] - epsilon && footY <= bounds[4] + epsilon) {
                    if (BoxUtil.boxesOverlapXZ(minX, maxX, minZ, maxZ,
                            bounds[0], bounds[3], bounds[2], bounds[5])) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static List<RelativeBlock> getRelativeBlocks(Player player) {
        List<RelativeBlock> blocks = new ArrayList<>();

        Location loc = player.getLocation();
        World world = loc.getWorld();

        int baseX = loc.getBlockX();
        int baseY = (int) Math.floor(loc.getY());
        int baseZ = loc.getBlockZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = world.getBlockAt(
                            baseX + dx,
                            baseY + dy,
                            baseZ + dz
                    );

                    blocks.add(new RelativeBlock(
                            dx,
                            dy,
                            dz,
                            BlockCompat.getBlockDataString(b)
                    ));
                }
            }
        }
        return blocks;
    }
}
