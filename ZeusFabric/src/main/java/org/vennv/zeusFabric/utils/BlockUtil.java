package org.vennv.zeusFabric.utils;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.vennv.utils.RelativeBlock;

import java.util.ArrayList;
import java.util.List;

public class BlockUtil {

    public static boolean isOnGround(ServerPlayerEntity player, Vec3d pos) {
        World world = MinecraftCompat.entityWorld(player);
        final double epsilon = 0.0001;
        final double playerWidth = player.getWidth();

        double footY = pos.getY() - epsilon;
        int minBlockY = MathHelper.floor(pos.getY() - 0.5001);
        int maxBlockY = MathHelper.floor(pos.getY() + 0.5001);

        double minX = pos.getX() - (playerWidth / 2);
        double maxX = pos.getX() + (playerWidth / 2);
        double minZ = pos.getZ() - (playerWidth / 2);
        double maxZ = pos.getZ() + (playerWidth / 2);

        int minBlockX = MathHelper.floor(minX);
        int maxBlockX = MathHelper.floor(maxX);
        int minBlockZ = MathHelper.floor(minZ);
        int maxBlockZ = MathHelper.floor(maxZ);

        for (int x = minBlockX; x <= maxBlockX; x++) {
            for (int y = minBlockY; y <= maxBlockY; y++) {
                for (int z = minBlockZ; z <= maxBlockZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(blockPos);

                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }

                    VoxelShape shape = state.getCollisionShape(world, blockPos);
                    if (shape.isEmpty()) {
                        continue;
                    }

                    Box box = shape.getBoundingBox().offset(blockPos);

                    if (footY >= box.minY - epsilon && footY <= box.maxY + epsilon) {
                        if (boxesOverlapXZ(minX, maxX, minZ, maxZ, box)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean boxesOverlapXZ(double playerMinX, double playerMaxX,
                                         double playerMinZ, double playerMaxZ,
                                         Box blockBox) {
        return playerMaxX >= blockBox.minX - 0.0001 &&
                playerMinX <= blockBox.maxX + 0.0001 &&
                playerMaxZ >= blockBox.minZ - 0.0001 &&
                playerMinZ <= blockBox.maxZ + 0.0001;
    }

    public static List<RelativeBlock> getRelativeBlocks(ServerPlayerEntity player, Vec3d pos) {
        List<RelativeBlock> blocks = new ArrayList<>();

        World world = MinecraftCompat.entityWorld(player);

        int baseX = MathHelper.floor(pos.getX());
        int baseY = MathHelper.floor(pos.getY());
        int baseZ = MathHelper.floor(pos.getZ());

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos targetPos = new BlockPos(
                            baseX + dx,
                            baseY + dy,
                            baseZ + dz
                    );
                    BlockState state = world.getBlockState(targetPos);

                    blocks.add(new RelativeBlock(
                            dx,
                            dy,
                            dz,
                            state.toString() // Equivalent to getBlockData().getAsString() in Bukkit
                    ));
                }
            }
        }
        return blocks;
    }

    public static List<RelativeBlock> getRelativeBlocks(ServerPlayerEntity player) {
        return getRelativeBlocks(player, new Vec3d(player.getX(), player.getY(), player.getZ()));
    }
}
