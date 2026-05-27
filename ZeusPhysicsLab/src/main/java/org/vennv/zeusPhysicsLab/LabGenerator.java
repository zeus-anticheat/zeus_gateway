package org.vennv.zeusPhysicsLab;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Barrel;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.EntityType;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class LabGenerator {
    private static final int WIDTH = 17;
    private static final int LENGTH = 34;
    private static final int GAP = 4;
    private static final int WALL_HEIGHT = 8;
    private static final Material WALL_MATERIAL = Material.POLISHED_BLACKSTONE_BRICKS;
    private static final Material WALL_TEXT_MATERIAL = Material.GOLD_BLOCK;
    private static final String[] ZEUS_PATTERN = {
        "ZZZ EEE U U SSS",
        "  Z E   U U S  ",
        " Z  EEE U U SSS",
        "Z   E   U U   S",
        "ZZZ EEE UUU SSS"
    };

    private final ZeusPhysicsLab plugin;
    private final List<Station> stations;

    private final Map<String, LabPlate> plates = new HashMap<>();
    private final Map<Station, List<StepHeightZone>> stepZones = new HashMap<>();
    private BukkitTask activeTask;

    public List<StepHeightZone> zonesFor(Station station) {
        return stepZones.getOrDefault(station, List.of());
    }

    public LabPlate plateAt(Location location) {
        return plates.get(key(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    public LabGenerator(ZeusPhysicsLab plugin, List<Station> stations) {
        this.plugin = plugin;
        this.stations = stations;
    }

    public void generate(CommandSender sender, World world, int originX, int originY, int originZ) {
        if (isBusy(sender)) {
            return;
        }
        plates.clear();
        stepZones.clear();
        ensureScoreboards();
        buildHub(world, originX, originY, originZ);
        sender.sendMessage("Zeus Physics Lab generation started for " + stations.size() + " stations.");
        final int[] index = {0};
        final BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int built = 0;
            while (index[0] < stations.size() && built < 1) {
                Station station = stations.get(index[0]++);
                buildStation(world, stationOrigin(world, originX, originY, originZ, station), station);
                built++;
            }
            if (index[0] >= stations.size()) {
                plugin.writeManifest(world.getName(), originX, originY, originZ);
                sender.sendMessage("Zeus Physics Lab generated as one long forward route at "
                    + world.getName() + " " + originX + " " + originY + " " + originZ + ".");
                finishActiveTask(taskRef[0]);
            }
        }, 1L, 1L);
        activeTask = taskRef[0];
    }

    public void reset(CommandSender sender, World world, int originX, int originY, int originZ) {
        if (isBusy(sender)) {
            return;
        }
        sender.sendMessage("Zeus Physics Lab reset started for " + stations.size() + " stations.");
        final int[] index = {0};
        final BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int cleared = 0;
            while (index[0] < stations.size() && cleared < 1) {
                Station station = stations.get(index[0]++);
                Location origin = stationOrigin(world, originX, originY, originZ, station);
                clearStation(world, origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
                cleared++;
            }
            if (index[0] >= stations.size()) {
                fill(world, originX - WIDTH / 2, originY - 1, originZ - 8, originX + WIDTH / 2, originY + 8, originZ + 16, Material.AIR);
                plates.clear();
                stepZones.clear();
                sender.sendMessage("Zeus Physics Lab route cleared.");
                finishActiveTask(taskRef[0]);
            }
        }, 1L, 1L);
        activeTask = taskRef[0];
    }

    public Location stationLocation(World world, int originX, int originY, int originZ, Station station) {
        Location origin = stationOrigin(world, originX, originY, originZ, station);
        return origin.clone().add(0.5, 1.0, 3.5);
    }

    public VerificationResult verify(World world, int originX, int originY, int originZ) {
        int foundSigns = 0;
        int foundCommandBlocks = 0;
        for (Station station : stations) {
            Location origin = stationOrigin(world, originX, originY, originZ, station);
            if (origin.clone().add(0, 0, 2).getBlock().getType() == Material.STONE_PRESSURE_PLATE) {
                foundSigns++;
            }
            if (origin.clone().add(0, 0, LENGTH - 2).getBlock().getType() == Material.STONE_PRESSURE_PLATE) {
                foundCommandBlocks++;
            }
        }
        return new VerificationResult(stations.size(), foundSigns, foundCommandBlocks);
    }

    private void buildHub(World world, int x, int y, int z) {
        fill(world, x - WIDTH / 2, y - 1, z - 6, x + WIDTH / 2, y - 1, z + 4, Material.POLISHED_ANDESITE);
        world.setBlockData(x, y - 1, z, Bukkit.createBlockData(Material.GOLD_BLOCK));
    }

    private boolean isBusy(CommandSender sender) {
        if (activeTask == null || activeTask.isCancelled()) {
            return false;
        }
        sender.sendMessage("Zeus Physics Lab operation already running; wait for completion before issuing another lab command.");
        return true;
    }

    private void finishActiveTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        if (activeTask == task) {
            activeTask = null;
        }
    }

    private void buildStation(World world, Location origin, Station station) {
        int cx = origin.getBlockX();
        int y = origin.getBlockY();
        int z = origin.getBlockZ();
        clearStation(world, cx, y, z);
        Material floor = floorFor(station.category());
        fill(world, cx - WIDTH / 2, y - 1, z, cx + WIDTH / 2, y - 1, z + LENGTH, floor);
        outline(world, cx - WIDTH / 2, y, z, cx + WIDTH / 2, z + LENGTH, Material.SMOOTH_STONE_SLAB);
        buildSideWalls(world, cx, y, z);
        buildCenterGuide(world, cx, y, z);
        placeStart(world, cx, y, z, station);
        buildCategoryGeometry(world, cx, y, z, station);
        world.setBlockData(cx, y - 1, z + LENGTH - 2, Bukkit.createBlockData(Material.LIME_CONCRETE));
        world.setBlockData(cx, y, z + LENGTH - 2, Bukkit.createBlockData(Material.STONE_PRESSURE_PLATE));
        registerPlate(cx, y, z + LENGTH - 2, station, true);
        placeCommand(world, cx, y - 2, z + LENGTH - 2, finishCommandFor(station));
        bridgeToNext(world, cx, y, z + LENGTH);
    }

    private void clearStation(World world, int cx, int y, int z) {
        fill(world, cx - WIDTH / 2 - 1, y, z - 1, cx + WIDTH / 2 + 1, y + WALL_HEIGHT, z + LENGTH + GAP, Material.AIR);
        fill(world, cx - WIDTH / 2 - 1, y - 3, z - 1, cx + WIDTH / 2 + 1, y - 1, z + LENGTH + GAP, Material.STONE);
    }

    private void placeStart(World world, int cx, int y, int z, Station station) {
        world.setBlockData(cx, y - 1, z + 2, Bukkit.createBlockData(Material.GOLD_BLOCK));
        world.setBlockData(cx, y, z + 2, Bukkit.createBlockData(Material.STONE_PRESSURE_PLATE));
        registerPlate(cx, y, z + 2, station, false);
        placeCommand(world, cx, y - 2, z + 2, startCommandFor(station));
    }

    private String startCommandFor(Station station) {
        String id = station.id();
        if (id.contains("LAVA")) return "effect give @p fire_resistance 60 0 true";
        if (id.contains("WATER_SUBMERGED") || id.contains("BUBBLE_COLUMN") || id.contains("RIPTIDE")) return "effect give @p water_breathing 60 0 true";
        if (id.contains("BREAK_FAST_TOOL")) return "effect give @p haste 60 1 true";
        if (id.contains("BREAK_FATIGUE")) return "effect give @p mining_fatigue 60 1 true";
        if (id.contains("KB_RESISTANCE")) return "effect give @p resistance 60 1 true";
        if (id.contains("RIPTIDE")) return "weather rain";
        if (id.contains("SPEAR_CHARGE") || id.contains("SPEAR_MULTI_ENTITY") || id.contains("SPEAR_DISMOUNT")) return "effect give @p speed 45 1 true";
        if (id.contains("MACE_SMASH") || id.contains("FREE_FALL") || id.contains("ELYTRA")) return "effect give @p slow_falling 45 0 true";
        return "say ZeusLab start " + id;
    }

    private String finishCommandFor(Station station) {
        return "effect clear @p";
    }

    private void registerPlate(int cx, int y, int z, Station station, boolean isFinish) {
        plates.put(key(cx, y, z), new LabPlate(station, isFinish));
    }

    private String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private void buildCategoryGeometry(World world, int cx, int y, int z, Station station) {
        switch (station.category()) {
            case VEHICLE -> buildVehicle(world, cx, y, z, station);
            case INTERACT -> buildInteract(world, cx, y, z, station);
            case COMBAT -> buildCombat(world, cx, y, z, station);
            case EXTERNAL_FORCE -> buildExternalForce(world, cx, y, z, station);
            case TRANSACTION -> buildTransaction(world, cx, y, z, station);
            case NETWORK -> buildNetwork(world, cx, y, z, station);
            case CROSS_FEATURE -> buildCrossFeature(world, cx, y, z, station);
            case ENVIRONMENT -> buildEnvironment(world, cx, y, z, station);
            case LIQUID_CLIMB_SPECIAL -> buildSpecialMovement(world, cx, y, z, station);
            case VERTICAL -> buildVertical(world, cx, y, z, station);
            default -> buildMovement(world, cx, y, z, station);
        }
    }

    private void buildMovement(World world, int cx, int y, int z, Station station) {
        if (station.id().contains("CIRCLE")) {
            for (int i = 0; i < 16; i++) {
                double angle = i * Math.PI / 8.0;
                int px = cx + (int) Math.round(Math.cos(angle) * 5);
                int pz = z + 16 + (int) Math.round(Math.sin(angle) * 5);
                world.setBlockData(px, y - 1, pz, Bukkit.createBlockData(Material.WHITE_CONCRETE));
            }
        } else if (station.id().contains("ZIGZAG") || station.id().contains("STRAFE")) {
            for (int step = 5; step < LENGTH - 4; step++) {
                int offset = ((step / 4) % 2 == 0) ? -3 : 3;
                world.setBlockData(cx + offset, y - 1, z + step, Bukkit.createBlockData(Material.WHITE_CONCRETE));
            }
        } else {
            straightLane(world, cx, y, z, Material.WHITE_CONCRETE);
        }
    }

    private void buildVertical(World world, int cx, int y, int z, Station station) {
        straightLane(world, cx, y, z, Material.WHITE_CONCRETE);
        if (station.id().contains("LOW_CEILING") || station.id().contains("HEAD_HIT")) {
            fill(world, cx - 1, y + 2, z + 10, cx + 1, y + 2, z + 24, Material.GLASS);
        } else if (station.id().contains("STEP_HEIGHT_VARIATION")) {
            straightLane(world, cx, y, z, Material.STONE);
            int dz = 5;
            // Group A (< 1.0)
            createZone(station, world, cx, y, z, dz, Material.SOUL_SAND, "Soul Sand", "0.875", "Sinks player", 1);
            dz += 4;
            createZone(station, world, cx, y, z, dz, Material.ENCHANTING_TABLE, "Enchant Table", "0.75", "3/4 block", 2);
            dz += 4;
            createZone(station, world, cx, y, z, dz, Material.DAYLIGHT_DETECTOR, "Daylight Det", "0.375", "3/8 block", 3);
            dz += 4;
            // Group B (> 0.6)
            createZone(station, world, cx, y, z, dz, Material.OAK_FENCE, "Oak Fence", "1.5", "Blocks jump", 4);
            dz += 4;
            createZone(station, world, cx, y, z, dz, Material.COBBLESTONE_WALL, "Cobble Wall", "1.5", "Blocks jump", 5);
            dz += 4;
            // Group C (Special)
            createZone(station, world, cx, y, z, dz, Material.SLIME_BLOCK, "Slime Block", "1.0", "Bounce effect", 6);
            dz += 4;
            createZone(station, world, cx, y, z, dz, Material.HONEY_BLOCK, "Honey Block", "1.0", "Prevents jump", 7);
            dz += 4;
            createZone(station, world, cx, y, z, dz, Material.MAGMA_BLOCK, "Magma Block", "1.0", "Damage on step", 8);
        } else if (station.id().equals("MV_STEP_UP_HALF")) {
            buildStepUpHalfCourse(world, cx, y, z, station);
        } else if (station.id().equals("MV_STEP_UP_FULL")) {
            buildStepUpFullCourse(world, cx, y, z, station);
        } else if (station.id().equals("MV_REVERSE_STEP")) {
            buildReverseStepCourse(world, cx, y, z, station);
        } else if (station.id().contains("FALL")) {
            fill(world, cx - 1, y, z + 12, cx + 1, y + 3, z + 14, Material.STONE);
            world.setBlockData(cx, y + 4, z + 14, Bukkit.createBlockData(Material.GOLD_BLOCK));
            Stairs stairs = (Stairs) Bukkit.createBlockData(Material.STONE_STAIRS);
            stairs.setFacing(BlockFace.SOUTH);
            world.setBlockData(cx, y, z + 11, stairs);
            for (int h = 1; h <= 4; h++) {
                Ladder ladder = (Ladder) Bukkit.createBlockData(Material.LADDER);
                ladder.setFacing(BlockFace.NORTH);
                world.setBlockData(cx, y + h, z + 11, ladder);
            }
            if (station.id().contains("WATER")) fill(world, cx - 2, y, z + 22, cx + 2, y, z + 24, Material.WATER);
        }
        
        if (station.id().contains("FREE_FALL_TOWER")) {
            fill(world, cx - 1, y, z + 12, cx + 1, y + 40, z + 14, Material.STONE);
            world.setBlockData(cx, y + 41, z + 14, Bukkit.createBlockData(Material.GOLD_BLOCK));
            for (int h = 0; h <= 41; h++) {
                Ladder ladder = (Ladder) Bukkit.createBlockData(Material.LADDER);
                ladder.setFacing(BlockFace.NORTH);
                world.setBlockData(cx, y + h, z + 11, ladder);
            }
            fill(world, cx - 1, y, z + 18, cx + 1, y, z + 20, Material.HAY_BLOCK);
            fill(world, cx - 1, y, z + 24, cx + 1, y, z + 26, Material.WATER);
        } else if (station.id().contains("SLIME_PISTON_LAUNCH")) {
            Piston piston = (Piston) Bukkit.createBlockData(Material.STICKY_PISTON);
            piston.setFacing(BlockFace.UP);
            world.setBlockData(cx, y - 2, z + 14, piston);
            world.setBlockData(cx, y - 1, z + 14, Bukkit.createBlockData(Material.SLIME_BLOCK));
            Switch btn = (Switch) Bukkit.createBlockData(Material.STONE_BUTTON);
            btn.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            ((Directional) btn).setFacing(BlockFace.NORTH);
            world.setBlockData(cx - 2, y - 1, z + 14, Bukkit.createBlockData(Material.OBSIDIAN));
            world.setBlockData(cx - 2, y, z + 14, btn);
            world.setBlockData(cx - 2, y - 2, z + 14, Bukkit.createBlockData(Material.REDSTONE_WIRE));
            world.setBlockData(cx - 1, y - 1, z + 14, Bukkit.createBlockData(Material.REDSTONE_WIRE));
            world.setBlockData(cx - 1, y - 2, z + 14, Bukkit.createBlockData(Material.REDSTONE_WIRE));
        }
    }


    private void buildStepUpHalfCourse(World world, int cx, int y, int z, Station station) {
        fill(world, cx - 5, y - 1, z + 5, cx + 5, y - 1, z + LENGTH - 4, Material.STONE);
        buildSlabUpDownLane(world, cx - 4, y, z);
        buildStairUpDownLane(world, cx, y, z);
        buildUpperHalfStateLane(world, cx + 4, y, z);
        placeSign(world, cx - 7, y, z + 7, "Slab ramp", "bottom -> full", "then step down", station.id());
        placeSign(world, cx - 2, y, z + 7, "Stair ramp", "south/north", "up and down", station.id());
        placeSign(world, cx + 6, y, z + 7, "Top states", "top slab/stair", "flush + raised", station.id());
    }

    private void buildStepUpFullCourse(World world, int cx, int y, int z, Station station) {
        straightLane(world, cx, y, z, Material.STONE);
        fill(world, cx - 1, y, z + 11, cx + 1, y, z + 13, Material.STONE);
        fill(world, cx - 1, y + 1, z + 17, cx + 1, y + 1, z + 19, Material.STONE);
        placeSign(world, cx + 3, y, z + 10, "Full steps", "jump up 1.0", "then 2.0 block", station.id());
    }

    private void buildReverseStepCourse(World world, int cx, int y, int z, Station station) {
        fill(world, cx - 4, y - 1, z + 5, cx + 4, y - 1, z + LENGTH - 4, Material.STONE);
        fill(world, cx - 1, y, z + 8, cx + 1, y, z + 12, Material.STONE);
        setSlab(world, cx, y, z + 13, Material.SMOOTH_STONE_SLAB, Slab.Type.BOTTOM);
        setStair(world, cx, y, z + 16, Material.STONE_STAIRS, Bisected.Half.BOTTOM, BlockFace.NORTH, Stairs.Shape.STRAIGHT);
        fill(world, cx - 4, y, z + 8, cx - 4, y, z + 11, Material.STONE);
        setSlab(world, cx - 4, y, z + 12, Material.SMOOTH_STONE_SLAB, Slab.Type.BOTTOM);
        fill(world, cx + 4, y, z + 8, cx + 4, y, z + 11, Material.STONE);
        setStair(world, cx + 4, y, z + 12, Material.STONE_STAIRS, Bisected.Half.BOTTOM, BlockFace.NORTH, Stairs.Shape.STRAIGHT);
        placeSign(world, cx + 3, y, z + 8, "Reverse step", "full -> half", "slab/stair drop", station.id());
    }

    private void buildSlabUpDownLane(World world, int x, int y, int z) {
        for (int dz = 5; dz < LENGTH - 4; dz++) {
            world.setBlockData(x, y - 1, z + dz, Bukkit.createBlockData(Material.STONE));
        }
        setSlab(world, x, y, z + 10, Material.OAK_SLAB, Slab.Type.BOTTOM);
        fill(world, x, y, z + 11, x, y, z + 14, Material.STONE);
        setSlab(world, x, y, z + 15, Material.OAK_SLAB, Slab.Type.BOTTOM);
        setSlab(world, x, y - 1, z + 19, Material.SMOOTH_STONE_SLAB, Slab.Type.TOP);
        setSlab(world, x, y - 1, z + 20, Material.SMOOTH_STONE_SLAB, Slab.Type.TOP);
        setSlab(world, x, y, z + 22, Material.OAK_SLAB, Slab.Type.BOTTOM);
    }

    private void buildStairUpDownLane(World world, int x, int y, int z) {
        for (int dz = 5; dz < LENGTH - 4; dz++) {
            world.setBlockData(x, y - 1, z + dz, Bukkit.createBlockData(Material.STONE));
        }
        setStair(world, x, y, z + 10, Material.STONE_STAIRS, Bisected.Half.BOTTOM, BlockFace.SOUTH, Stairs.Shape.STRAIGHT);
        fill(world, x, y, z + 11, x, y, z + 14, Material.STONE);
        setStair(world, x, y, z + 15, Material.STONE_STAIRS, Bisected.Half.BOTTOM, BlockFace.NORTH, Stairs.Shape.STRAIGHT);
        setStair(world, x, y, z + 19, Material.STONE_STAIRS, Bisected.Half.BOTTOM, BlockFace.EAST, Stairs.Shape.INNER_LEFT);
        setStair(world, x, y, z + 21, Material.STONE_STAIRS, Bisected.Half.BOTTOM, BlockFace.WEST, Stairs.Shape.OUTER_RIGHT);
    }

    private void buildUpperHalfStateLane(World world, int x, int y, int z) {
        for (int dz = 5; dz < LENGTH - 4; dz++) {
            world.setBlockData(x, y - 1, z + dz, Bukkit.createBlockData(Material.STONE));
        }
        setSlab(world, x, y - 1, z + 9, Material.OAK_SLAB, Slab.Type.TOP);
        setSlab(world, x, y - 1, z + 10, Material.OAK_SLAB, Slab.Type.TOP);
        setStair(world, x, y - 1, z + 13, Material.STONE_STAIRS, Bisected.Half.TOP, BlockFace.SOUTH, Stairs.Shape.STRAIGHT);
        setStair(world, x, y - 1, z + 15, Material.STONE_STAIRS, Bisected.Half.TOP, BlockFace.NORTH, Stairs.Shape.STRAIGHT);
        setSlab(world, x, y, z + 19, Material.OAK_SLAB, Slab.Type.BOTTOM);
        setStair(world, x, y, z + 22, Material.STONE_STAIRS, Bisected.Half.BOTTOM, BlockFace.SOUTH, Stairs.Shape.STRAIGHT);
    }

    private void createZone(Station station, World world, int cx, int y, int z, int dz, Material material, String name, String height, String instruction, int order) {
        fill(world, cx - 1, y, z + dz, cx + 1, y, z + dz + 2, material);
        placeSign(world, cx + 2, y, z + dz + 1, name, "Height: " + height, instruction, "");
        StepHeightZone zone = new StepHeightZone(name, height, instruction, cx - 2, cx + 2, y, z + dz, z + dz + 2, order);
        stepZones.computeIfAbsent(station, ignored -> new java.util.ArrayList<>()).add(zone);
    }
    private void buildEnvironment(World world, int cx, int y, int z, Station station) {
        Material material = switch (station.id()) {
            case "MV_ICE" -> Material.PACKED_ICE;
            case "MV_SLIME" -> Material.SLIME_BLOCK;
            case "MV_HONEY" -> Material.HONEY_BLOCK;
            case "MV_SOUL_SAND" -> Material.SOUL_SAND;
            case "MV_WEB", "MV_WEB_SOUL_SAND", "MV_WEB_BLUE_ICE" -> Material.COBWEB;
            case "MV_POWDER_SNOW" -> Material.POWDER_SNOW;
            case "MV_SNOW_LAYER" -> Material.SNOW;
            case "MV_MUD_SINK" -> Material.MUD;
            case "MV_BERRY_BUSH", "MV_CACTUS_CONTACT", "MV_POINTED_DRIPSTONE" -> Material.AIR;
            default -> Material.COBBLESTONE;
        };
        straightLane(world, cx, y, z, material);
        if (station.id().contains("WEB") || station.id().contains("POWDER_SNOW")) {
            Material base = station.id().contains("SOUL_SAND") ? Material.SOUL_SAND : (station.id().contains("ICE") ? Material.BLUE_ICE : Material.STONE);
            straightLane(world, cx, y, z, base);
            for (int dz = 5; dz < LENGTH - 15; dz++) {
                fill(world, cx - 1, y, z + dz, cx + 1, y, z + dz, material);
            }
        }
        if (station.id().contains("STAIRS_SLABS")) {
            buildStairsSlabsTerrain(world, cx, y, z, station);
        } else if (station.id().contains("FENCE_STEP_UP")) {
            straightLane(world, cx, y, z, Material.STONE);
            for (int i = 8; i < LENGTH - 8; i += 3) {
                world.setBlockData(cx, y - 1, z + i, Bukkit.createBlockData(Material.OAK_FENCE));
                world.setBlockData(cx - 2, y - 1, z + i, Bukkit.createBlockData(Material.YELLOW_CONCRETE));
                world.setBlockData(cx + 2, y - 1, z + i, Bukkit.createBlockData(Material.YELLOW_CONCRETE));
            }
        } else if (station.id().contains("STONE_WALL_STEP_UP")) {
            straightLane(world, cx, y, z, Material.STONE);
            for (int i = 8; i < LENGTH - 8; i += 3) {
                world.setBlockData(cx, y - 1, z + i, Bukkit.createBlockData(Material.COBBLESTONE_WALL));
                world.setBlockData(cx - 2, y - 1, z + i, Bukkit.createBlockData(Material.YELLOW_CONCRETE));
                world.setBlockData(cx + 2, y - 1, z + i, Bukkit.createBlockData(Material.YELLOW_CONCRETE));
            }
        } else if (station.id().contains("FENCE")) {
            for (int i = 6; i < LENGTH - 4; i++) world.setBlockData(cx + 2, y, z + i, Bukkit.createBlockData(Material.OAK_FENCE));
        } else if (station.id().contains("WALL")) {
            for (int i = 6; i < LENGTH - 4; i++) world.setBlockData(cx + 2, y, z + i, Bukkit.createBlockData(Material.COBBLESTONE_WALL));
        }
        
        if (station.id().contains("SNOW_LAYER_RAMP")) {
            straightLane(world, cx, y, z, Material.STONE);
            for (int i = 0; i < 7; i++) {
                Snow snow = (Snow) Bukkit.createBlockData(Material.SNOW);
                snow.setLayers(i + 1);
                world.setBlockData(cx, y, z + 8 + i, snow);
            }
        }

        if (station.id().contains("BERRY_BUSH")) {
            straightLane(world, cx, y, z, Material.GRASS_BLOCK);
            for (int i = 8; i < LENGTH - 8; i += 2) world.setBlockData(cx, y, z + i, Bukkit.createBlockData(Material.SWEET_BERRY_BUSH));
        } else if (station.id().contains("CACTUS_CONTACT")) {
            straightLane(world, cx, y, z, Material.SAND);
            for (int i = 8; i < LENGTH - 8; i += 3) {
                world.setBlockData(cx - 1, y, z + i, Bukkit.createBlockData(Material.CACTUS));
                world.setBlockData(cx + 1, y, z + i, Bukkit.createBlockData(Material.CACTUS));
            }
        } else if (station.id().contains("POINTED_DRIPSTONE")) {
            straightLane(world, cx, y, z, Material.STONE);
            for (int i = 8; i < LENGTH - 8; i += 3) world.setBlockData(cx, y, z + i, Bukkit.createBlockData(Material.POINTED_DRIPSTONE));
        } else if (station.id().contains("TRAPDOOR_CRAWL")) {
            straightLane(world, cx, y, z, Material.STONE);
            fill(world, cx - 1, y, z + 12, cx + 1, y + 1, z + 20, Material.STONE);
            fill(world, cx, y, z + 12, cx, y, z + 20, Material.AIR);
            world.setBlockData(cx, y + 1, z + 11, Bukkit.createBlockData(Material.OAK_TRAPDOOR));
        } else if (station.id().contains("BED_BOUNCE")) {
            straightLane(world, cx, y, z, Material.STONE);
            world.setBlockData(cx, y, z + 12, Bukkit.createBlockData(Material.RED_BED));
            world.setBlockData(cx, y, z + 13, Bukkit.createBlockData(Material.RED_BED));
        }
    }

    private void buildStairsSlabsTerrain(World world, int cx, int y, int z, Station station) {
        fill(world, cx - 5, y - 1, z + 5, cx + 5, y - 1, z + LENGTH - 4, Material.STONE);

        buildSlabUpDownLane(world, cx - 4, y, z);
        buildStairUpDownLane(world, cx, y, z);

        for (int dz = 5; dz < LENGTH - 4; dz++) {
            world.setBlockData(cx + 4, y - 1, z + dz, Bukkit.createBlockData(Material.STONE));
        }
        world.setBlockData(cx + 4, y, z + 8, Bukkit.createBlockData(Material.WHITE_CARPET));
        setSlab(world, cx + 4, y, z + 10, Material.SMOOTH_STONE_SLAB, Slab.Type.BOTTOM);
        setStair(world, cx + 4, y, z + 12, Material.BRICK_STAIRS, Bisected.Half.BOTTOM, BlockFace.SOUTH, Stairs.Shape.STRAIGHT);
        setSlab(world, cx + 4, y - 1, z + 15, Material.SMOOTH_STONE_SLAB, Slab.Type.TOP);
        setStair(world, cx + 4, y - 1, z + 17, Material.BRICK_STAIRS, Bisected.Half.TOP, BlockFace.NORTH, Stairs.Shape.STRAIGHT);
        world.setBlockData(cx + 4, y, z + 20, Bukkit.createBlockData(Material.OAK_TRAPDOOR));
        setStair(world, cx + 4, y, z + 23, Material.BRICK_STAIRS, Bisected.Half.BOTTOM, BlockFace.EAST, Stairs.Shape.OUTER_LEFT);
        setStair(world, cx + 4, y, z + 25, Material.BRICK_STAIRS, Bisected.Half.BOTTOM, BlockFace.WEST, Stairs.Shape.INNER_RIGHT);

        placeSign(world, cx - 7, y, z + 7, "Slabs", "bottom/top", "up/down lane", station.id());
        placeSign(world, cx - 2, y, z + 7, "Stairs", "facing/shape", "straight/corner", station.id());
        placeSign(world, cx + 6, y, z + 7, "Mixed", "carpet/trapdoor", "slab + stair", station.id());
    }

    private void buildSpecialMovement(World world, int cx, int y, int z, Station station) {
        if (station.id().contains("ELYTRA")) {
            fill(world, cx - 1, y, z + 8, cx + 1, y + 28, z + 10, Material.STONE);
            world.setBlockData(cx, y + 29, z + 10, Bukkit.createBlockData(Material.GOLD_BLOCK));
            for (int h = 0; h <= 29; h++) {
                Ladder ladder = (Ladder) Bukkit.createBlockData(Material.LADDER);
                ladder.setFacing(BlockFace.NORTH);
                world.setBlockData(cx, y + h, z + 7, ladder);
            }
            for (int dz = 14; dz <= 28; dz += 4) {
                fill(world, cx - 3, y + 12, z + dz, cx + 3, y + 12, z + dz, Material.GLASS);
                world.setBlockData(cx, y + 12, z + dz, Bukkit.createBlockData(Material.AIR));
            }
        } else if (station.id().contains("RIPTIDE")) {
            fill(world, cx - 3, y - 1, z + 6, cx + 3, y - 1, z + 28, Material.STONE);
            fill(world, cx - 3, y, z + 6, cx + 3, y + 5, z + 28, Material.WATER);
            fill(world, cx - 3, y + 6, z + 20, cx + 3, y + 6, z + 28, Material.GLASS);
        } else if (station.id().contains("WATER") || station.id().contains("BUBBLE")) {
            fill(world, cx - 3, y - 1, z + 6, cx + 3, y - 1, z + 28, Material.STONE);
            fill(world, cx - 3, y, z + 6, cx + 3, y + 2, z + 28, Material.WATER);
            if (station.id().contains("BUBBLE")) {
                for (int dz = 8; dz < 26; dz++) world.setBlockData(cx, y, z + dz, Bukkit.createBlockData(Material.SOUL_SAND));
            }
        } else if (station.id().contains("LAVA")) {
            fill(world, cx - 2, y - 1, z + 8, cx + 2, y - 1, z + 24, Material.STONE);
            fill(world, cx - 2, y, z + 8, cx + 2, y, z + 24, Material.LAVA);
        } else if (station.id().contains("FLOWING_WATER")) {
            fill(world, cx - 2, y - 1, z + 8, cx + 2, y - 1, z + 24, Material.STONE);
            fill(world, cx - 2, y, z + 8, cx + 2, y, z + 24, Material.WATER);
            fill(world, cx - 1, y, z + 8, cx + 1, y, z + 24, Material.AIR);
            world.setBlockData(cx - 2, y, z + 8, Bukkit.createBlockData(Material.WATER));
            world.setBlockData(cx + 2, y, z + 24, Bukkit.createBlockData(Material.WATER));
        } else if (station.id().contains("ENDER_PEARL")) {
            straightLane(world, cx, y, z, Material.WHITE_CONCRETE);
            fill(world, cx - 1, y, z + 12, cx + 1, y + 4, z + 20, Material.GLASS);
            fill(world, cx, y, z + 12, cx, y + 4, z + 20, Material.AIR);
            world.setBlockData(cx, y + 3, z + 12, Bukkit.createBlockData(Material.AIR));
        } else {
            straightLane(world, cx, y, z, Material.WHITE_CONCRETE);
            fill(world, cx + 3, y, z + 10, cx + 3, y + 5, z + 16, Material.OAK_PLANKS);
            for (int h = y; h <= y + 5; h++) {
                for (int dz = z + 10; dz <= z + 16; dz++) {
                    if (station.id().contains("SCAFFOLD")) {
                        world.setBlockData(cx + 2, h, dz, Bukkit.createBlockData(Material.SCAFFOLDING));
                    } else if (station.id().contains("VINES")) {
                        MultipleFacing vine = (MultipleFacing) Bukkit.createBlockData(Material.VINE);
                        vine.setFace(BlockFace.EAST, true);
                        world.setBlockData(cx + 2, h, dz, vine);
                    } else {
                        Ladder ladder = (Ladder) Bukkit.createBlockData(Material.LADDER);
                        ladder.setFacing(BlockFace.WEST);
                        world.setBlockData(cx + 2, h, dz, ladder);
                    }
                }
            }
        }
    }

    private void buildVehicle(World world, int cx, int y, int z, Station station) {
        if (station.id().contains("BOAT")) {
            Material lane = station.id().contains("ICE") ? Material.BLUE_ICE : station.id().contains("WATER") ? Material.WATER : Material.OAK_PLANKS;
            fill(world, cx - 2, y - 1, z + 6, cx + 2, y - 1, z + LENGTH - 4, lane == Material.WATER ? Material.STONE : lane);
            if (lane == Material.WATER) fill(world, cx - 2, y, z + 6, cx + 2, y, z + LENGTH - 4, Material.WATER);
            
            if (station.id().contains("TURNS")) {
                world.setBlockData(cx, y, z + 12, Bukkit.createBlockData(Material.STONE));
                world.setBlockData(cx - 1, y, z + 18, Bukkit.createBlockData(Material.STONE));
                world.setBlockData(cx + 1, y, z + 24, Bukkit.createBlockData(Material.STONE));
            }
            world.spawnEntity(new Location(world, cx + 0.5, y + 1, z + 7.5), EntityType.OAK_BOAT).addScoreboardTag("zeus_lab_vehicle");
        } else if (station.id().contains("HORSE")) {
            straightLane(world, cx, y, z, Material.GRASS_BLOCK);
            fill(world, cx - 3, y, z + 10, cx + 3, y, z + 12, Material.OAK_FENCE);
            fill(world, cx - 1, y, z + 10, cx + 1, y, z + 12, Material.AIR);
            world.spawnEntity(new Location(world, cx + 0.5, y + 1, z + 7.5), EntityType.HORSE).addScoreboardTag("zeus_lab_vehicle");
        } else if (station.id().contains("PIG_STRIDER")) {
            straightLane(world, cx, y, z, Material.CRIMSON_NYLIUM);
            fill(world, cx - 2, y, z + 18, cx + 2, y, z + 24, Material.LAVA);
            world.spawnEntity(new Location(world, cx - 1.5, y + 1, z + 7.5), EntityType.PIG).addScoreboardTag("zeus_lab_vehicle");
            world.spawnEntity(new Location(world, cx + 1.5, y + 1, z + 18.5), EntityType.STRIDER).addScoreboardTag("zeus_lab_vehicle");
        } else {
            fill(world, cx - 2, y - 1, z + 6, cx + 2, y - 1, z + LENGTH - 4, Material.IRON_BLOCK);
            if (station.id().contains("SLOPE") || station.id().contains("CURVE")) {
                for (int dz = 6; dz <= 11; dz++) world.getBlockAt(cx - 1, y, z + dz).setType(Material.RAIL, true);
                world.getBlockAt(cx, y, z + 11).setType(Material.RAIL, true);
                world.getBlockAt(cx + 1, y, z + 11).setType(Material.RAIL, true);
                world.getBlockAt(cx + 1, y, z + 12).setType(Material.RAIL, true);
                world.getBlockAt(cx - 1, y - 1, z + 8).setType(Material.REDSTONE_BLOCK, false);
                world.getBlockAt(cx - 1, y, z + 8).setType(Material.POWERED_RAIL, true);
                world.getBlockAt(cx + 1, y, z + 13).setType(Material.IRON_BLOCK, false);
                world.getBlockAt(cx + 1, y + 1, z + 13).setType(Material.RAIL, true); 
                world.getBlockAt(cx + 1, y + 1, z + 14).setType(Material.IRON_BLOCK, false);
                world.getBlockAt(cx + 1, y + 2, z + 14).setType(Material.RAIL, true); 
                world.getBlockAt(cx, y + 1, z + 14).setType(Material.IRON_BLOCK, false);
                world.getBlockAt(cx, y + 2, z + 14).setType(Material.RAIL, true); 
                world.getBlockAt(cx - 1, y + 1, z + 14).setType(Material.IRON_BLOCK, false);
                world.getBlockAt(cx - 1, y + 2, z + 14).setType(Material.RAIL, true); 
                world.getBlockAt(cx - 1, y, z + 15).setType(Material.IRON_BLOCK, false);
                world.getBlockAt(cx - 1, y + 1, z + 15).setType(Material.RAIL, true); 
                world.getBlockAt(cx - 1, y, z + 16).setType(Material.RAIL, true); 
                world.getBlockAt(cx, y, z + 16).setType(Material.RAIL, true); 
                world.getBlockAt(cx, y, z + 17).setType(Material.RAIL, true);
                for (int dz = 18; dz < LENGTH - 4; dz++) world.getBlockAt(cx, y, z + dz).setType(Material.RAIL, true);
                world.spawnEntity(new Location(world, cx - 0.5, y + 1, z + 6.5), EntityType.MINECART).addScoreboardTag("zeus_lab_vehicle");
            } else {
                for (int dz = 6; dz < LENGTH - 4; dz++) {
                    world.getBlockAt(cx, y, z + dz).setType(Material.RAIL, true);
                }
                world.spawnEntity(new Location(world, cx + 0.5, y + 1, z + 7.5), EntityType.MINECART).addScoreboardTag("zeus_lab_vehicle");
            }
        }
    }

    private void buildInteract(World world, int cx, int y, int z, Station station) {
        straightLane(world, cx, y, z, Material.GRASS_BLOCK);
        
        if (station.id().contains("PLACE_BELOW") || station.id().contains("MOVING")) {
            fill(world, cx - 1, y, z + 12, cx + 1, y, z + 24, Material.AIR);
            if (station.id().contains("BELOW")) {
                world.setBlockData(cx, y - 1, z + 11, Bukkit.createBlockData(Material.DIRT));
            }
        } else if (station.id().contains("ROTATION_SNAP")) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0;
                int px = cx + (int) Math.round(Math.cos(angle) * 3);
                int pz = z + 16 + (int) Math.round(Math.sin(angle) * 3);
                world.setBlockData(px, y, pz, Bukkit.createBlockData(Material.TARGET));
            }
        } else if (station.id().contains("DISTANCE")) {
            for (int i = 1; i <= 6; i++) {
                world.setBlockData(cx + 2, y + 1, z + 8 + (i * 2), Bukkit.createBlockData(Material.STONE));
            }
        } else if (station.id().contains("ROTATION")) {
            world.setBlockData(cx - 3, y + 1, z + 14, Bukkit.createBlockData(Material.STONE));
            world.setBlockData(cx - 2, y + 2, z + 16, Bukkit.createBlockData(Material.STONE));
            world.setBlockData(cx + 2, y, z + 16, Bukkit.createBlockData(Material.STONE));
            world.setBlockData(cx + 3, y + 3, z + 14, Bukkit.createBlockData(Material.STONE));
        } else if (station.id().contains("SUPPORT_MISSING")) {
            world.setBlockData(cx, y + 2, z + 14, Bukkit.createBlockData(Material.STONE));
            world.setBlockData(cx + 2, y + 1, z + 16, Bukkit.createBlockData(Material.STONE));
        } else {
            for (int dz = 8; dz <= 24; dz += 3) {
                world.setBlockData(cx - 2, y, z + dz, Bukkit.createBlockData(Material.OAK_PLANKS));
                world.setBlockData(cx + 2, y, z + dz, Bukkit.createBlockData(Material.STONE));
            }
        }
    }

    private void buildCombat(World world, int cx, int y, int z, Station station) {
        straightLane(world, cx, y, z, Material.RED_CONCRETE);
        
        if (station.id().contains("SPEAR_JAB_RANGE")) {
            world.spawnEntity(new Location(world, cx + 0.5, y, z + 13.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            world.spawnEntity(new Location(world, cx + 0.5, y, z + 18.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            world.spawnEntity(new Location(world, cx + 0.5, y, z + 22.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            placeSign(world, cx - 5, y, z + 12, "Spear reach", "2-4.5 blocks", "Too close fails", station.id());
        } else if (station.id().contains("SPEAR_CHARGE_ATTACK")) {
            fill(world, cx - 2, y - 1, z + 8, cx + 2, y - 1, z + 28, Material.BLUE_ICE);
            world.spawnEntity(new Location(world, cx + 0.5, y, z + 24.5), EntityType.ZOMBIE).addScoreboardTag("zeus_lab_target");
            placeSign(world, cx - 5, y, z + 10, "Spear charge", "Hold use", "Run into target", station.id());
        } else if (station.id().contains("SPEAR_MULTI_ENTITY")) {
            fill(world, cx - 2, y - 1, z + 8, cx + 2, y - 1, z + 28, Material.BLUE_ICE);
            for (int dz = 17; dz <= 25; dz += 2) world.spawnEntity(new Location(world, cx + 0.5, y, z + dz + 0.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            placeSign(world, cx - 5, y, z + 10, "Mob Kabob", "Charge through", "five targets", station.id());
        } else if (station.id().contains("SPEAR_NON_SOLID_REACH")) {
            fill(world, cx - 1, y, z + 14, cx + 1, y + 1, z + 14, Material.COBWEB);
            world.setBlockData(cx, y, z + 17, Bukkit.createBlockData(Material.SHORT_GRASS));
            world.spawnEntity(new Location(world, cx + 0.5, y, z + 18.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            placeSign(world, cx - 5, y, z + 12, "Spear reach", "Hit through", "non-solid blocks", station.id());
        } else if (station.id().contains("SPEAR_DISMOUNT")) {
            world.spawnEntity(new Location(world, cx + 0.5, y, z + 20.5), EntityType.HORSE).addScoreboardTag("zeus_lab_target");
            world.spawnEntity(new Location(world, cx + 0.5, y, z + 21.5), EntityType.ZOMBIE).addScoreboardTag("zeus_lab_target");
            fill(world, cx - 2, y - 1, z + 8, cx + 2, y - 1, z + 24, Material.BLUE_ICE);
            placeSign(world, cx - 5, y, z + 10, "Spear dismount", "Charge mounted", "target", station.id());
        } else if (station.id().contains("SPEAR_NO_CRIT") || station.id().contains("SPEAR_NO_SPRINT_KB")) {
            world.spawnEntity(new Location(world, cx + 0.5, y, z + 16.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            placeSign(world, cx - 5, y, z + 12, "Spear control", "No crit/sprint KB", "Compare packets", station.id());
        } else if (station.id().contains("STATIC_TARGET") || station.id().contains("SWING_NO_HIT") || station.id().contains("COMBO") || station.id().contains("CRITICAL_HITS") || station.id().contains("BLOCKING") || station.id().contains("EATING") || station.id().contains("FISHING") || station.id().contains("WEAPON") || station.id().contains("TRIDENT")) {
            world.spawnEntity(new Location(world, cx + 0.5, y, z + 16.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
        } else if (station.id().contains("MOVING_TARGET")) {
            fill(world, cx - 2, y - 1, z + 15, cx + 2, y - 1, z + 15, Material.PACKED_ICE);
            fill(world, cx - 2, y, z + 15, cx + 2, y, z + 15, Material.WATER);
            world.spawnEntity(new Location(world, cx + 1.5, y, z + 15.5), EntityType.ZOMBIE).addScoreboardTag("zeus_lab_target");
        } else if (station.id().contains("STRAFE_TARGET")) {
            fill(world, cx - 4, y - 1, z + 12, cx + 4, y - 1, z + 20, Material.RED_CONCRETE);
            world.spawnEntity(new Location(world, cx + 0.5, y, z + 16.5), EntityType.ZOMBIE).addScoreboardTag("zeus_lab_target");
        } else if (station.id().contains("DISTANCE_SWEEP")) {
            for (int i = 1; i <= 5; i++) {
                world.spawnEntity(new Location(world, cx + 0.5, y, z + 8.5 + (i * 2)), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            }
        } else if (station.id().contains("YAW_OFFSETS") || station.id().contains("TARGET_SWITCH")) {
            world.spawnEntity(new Location(world, cx - 3.5, y, z + 14.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            world.spawnEntity(new Location(world, cx - 1.5, y, z + 16.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            world.spawnEntity(new Location(world, cx + 1.5, y, z + 17.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            world.spawnEntity(new Location(world, cx + 3.5, y, z + 16.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
        } else if (station.id().contains("PITCH_OFFSETS")) {
            world.spawnEntity(new Location(world, cx + 0.5, y, z + 12.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            world.spawnEntity(new Location(world, cx + 0.5, y + 2, z + 16.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            world.setBlockData(cx, y, z + 16, Bukkit.createBlockData(Material.GLASS));
            world.setBlockData(cx, y + 1, z + 16, Bukkit.createBlockData(Material.GLASS));
            world.spawnEntity(new Location(world, cx + 0.5, y + 4, z + 20.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            world.setBlockData(cx, y, z + 20, Bukkit.createBlockData(Material.GLASS));
            world.setBlockData(cx, y + 1, z + 20, Bukkit.createBlockData(Material.GLASS));
            world.setBlockData(cx, y + 2, z + 20, Bukkit.createBlockData(Material.GLASS));
            world.setBlockData(cx, y + 3, z + 20, Bukkit.createBlockData(Material.GLASS));
        } else {
            for (int dz = 10; dz <= 24; dz += 7) {
                world.spawnEntity(new Location(world, cx + 0.5, y, z + dz + 0.5), EntityType.ARMOR_STAND).addScoreboardTag("zeus_lab_target");
            }
        }
    }
    private void buildExternalForce(World world, int cx, int y, int z, Station station) {
        if (station.id().contains("ENTITY_PUSH")) {
            straightLane(world, cx, y, z, Material.WHITE_CONCRETE);
            fill(world, cx - 1, y, z + 12, cx + 1, y + 2, z + 14, Material.OAK_PLANKS);
            fill(world, cx, y, z + 13, cx, y + 2, z + 13, Material.AIR);
            for (int i = 0; i < 25; i++) world.spawnEntity(new Location(world, cx + 0.5, y, z + 13.5), EntityType.COW).addScoreboardTag("zeus_lab_target");
        } else {
            fill(world, cx - 3, y - 1, z + 8, cx + 3, y - 1, z + 24, Material.OBSIDIAN);
            placeCommand(world, cx - 2, y - 2, z + 10, "summon tnt ~ ~1 ~ {Fuse:80}");
            placeCommand(world, cx + 2, y - 2, z + 10, "effect give @p resistance 10 1 true");
            Switch btn1 = (Switch) Bukkit.createBlockData(Material.STONE_BUTTON);
            btn1.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            ((Directional) btn1).setFacing(BlockFace.NORTH);
            world.setBlockData(cx - 2, y, z + 10, btn1);
            Switch btn2 = (Switch) Bukkit.createBlockData(Material.STONE_BUTTON);
            btn2.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            ((Directional) btn2).setFacing(BlockFace.NORTH);
            world.setBlockData(cx + 2, y, z + 10, btn2);
            placeSign(world, cx - 7, y, z + 10, "Force", "Press button", "then recover", station.id());
        }
    }

    private void buildTransaction(World world, int cx, int y, int z, Station station) {
        straightLane(world, cx, y, z, Material.YELLOW_CONCRETE);
        world.setBlockData(cx - 2, y, z + 10, Bukkit.createBlockData(Material.CHEST));
        if (world.getBlockAt(cx - 2, y, z + 10).getState() instanceof Container chest) {
            chest.getInventory().addItem(new ItemStack(Material.OAK_LOG, 64));
            chest.getInventory().setItem(10, new ItemStack(Material.DIAMOND, 10));
            chest.getInventory().setItem(0, new ItemStack(Material.STONE, 32));
            chest.getInventory().setItem(1, new ItemStack(Material.DIRT, 32));
            chest.getInventory().setItem(2, new ItemStack(Material.IRON_INGOT, 16));
            chest.getInventory().setItem(3, new ItemStack(Material.STICK, 32));
            chest.getInventory().setItem(4, new ItemStack(Material.BREAD, 16));
            chest.update();
        }
        world.setBlockData(cx + 2, y, z + 14, Bukkit.createBlockData(Material.BARREL));
        if (world.getBlockAt(cx + 2, y, z + 14).getState() instanceof Container barrel) {
            barrel.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 32));
            barrel.getInventory().setItem(0, new ItemStack(Material.REDSTONE, 32));
            barrel.getInventory().setItem(1, new ItemStack(Material.LAPIS_LAZULI, 32));
            barrel.getInventory().setItem(2, new ItemStack(Material.EMERALD, 16));
            barrel.getInventory().setItem(3, new ItemStack(Material.ARROW, 32));
            barrel.getInventory().setItem(4, new ItemStack(Material.COOKED_BEEF, 16));
            barrel.update();
        }
        world.setBlockData(cx, y, z + 18, Bukkit.createBlockData(Material.CRAFTING_TABLE));
        world.setBlockData(cx - 2, y, z + 18, Bukkit.createBlockData(Material.SHULKER_BOX));
        if (world.getBlockAt(cx - 2, y, z + 18).getState() instanceof Container shulker) {
            shulker.getInventory().setItem(0, new ItemStack(Material.COBBLESTONE, 64));
            shulker.getInventory().setItem(8, new ItemStack(Material.OAK_PLANKS, 64));
            shulker.update();
        }
    }

    private void buildNetwork(World world, int cx, int y, int z, Station station) {
        straightLane(world, cx, y, z, Material.LIGHT_BLUE_CONCRETE);
        if (station.id().contains("TELEPORT")) {
            placeCommand(world, cx - 2, y - 2, z + 10, "tp @p ~ ~ ~6");
            Switch btn3 = (Switch) Bukkit.createBlockData(Material.STONE_BUTTON);
            btn3.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            ((Directional) btn3).setFacing(BlockFace.NORTH);
            world.setBlockData(cx - 2, y, z + 10, btn3);
            placeCommand(world, cx - 2, y - 2, z + 18, "tp @p ~ ~ ~6");
            Switch btn4 = (Switch) Bukkit.createBlockData(Material.STONE_BUTTON);
            btn4.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            ((Directional) btn4).setFacing(BlockFace.NORTH);
            world.setBlockData(cx - 2, y, z + 18, btn4);
        } else if (station.id().contains("TPS")) {
            for (int dz = 10; dz <= 20; dz += 5) world.setBlockData(cx + 3, y, z + dz, Bukkit.createBlockData(Material.REDSTONE_LAMP));
            world.setBlockData(cx + 3, y, z + 9, Bukkit.createBlockData(Material.LEVER));
        }
    }

    private void buildCrossFeature(World world, int cx, int y, int z, Station station) {
        straightLane(world, cx, y, z, station.id().contains("ICE") ? Material.BLUE_ICE : Material.PURPLE_CONCRETE);
        buildInteract(world, cx, y, z, station);
        buildCombat(world, cx, y, z, station);
    }

    private void straightLane(World world, int cx, int y, int z, Material material) {
        for (int dz = 5; dz < LENGTH - 3; dz++) {
            fill(world, cx - 1, y - 1, z + dz, cx + 1, y - 1, z + dz, material);
        }
    }

    private void buildCenterGuide(World world, int cx, int y, int z) {
        for (int dz = 5; dz < LENGTH - 3; dz += 4) {
            world.setBlockData(cx, y - 1, z + dz, Bukkit.createBlockData(Material.YELLOW_CONCRETE));
        }
    }

    private void bridgeToNext(World world, int cx, int y, int z) {
        fill(world, cx - 2, y - 1, z + 1, cx + 2, y - 1, z + GAP, Material.POLISHED_ANDESITE);
    }

    private Location stationOrigin(World world, int originX, int originY, int originZ, Station station) {
        return new Location(world, originX, originY, originZ + 8 + (long) (station.number() - 1) * (LENGTH + GAP));
    }

    private void ensureScoreboards() {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "scoreboard objectives add zeus_station dummy");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "scoreboard objectives add zeus_run dummy");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "scoreboard objectives add zeus_phase dummy");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "scoreboard objectives setdisplay sidebar zeus_station");
    }

    private Material floorFor(StationCategory category) {
        return switch (category) {
            case VEHICLE -> Material.CYAN_TERRACOTTA;
            case INTERACT -> Material.GREEN_TERRACOTTA;
            case COMBAT -> Material.RED_TERRACOTTA;
            case EXTERNAL_FORCE -> Material.ORANGE_TERRACOTTA;
            case TRANSACTION -> Material.YELLOW_TERRACOTTA;
            case NETWORK -> Material.LIGHT_BLUE_TERRACOTTA;
            case CROSS_FEATURE -> Material.PURPLE_TERRACOTTA;
            default -> Material.GRAY_TERRACOTTA;
        };
    }

    private String equipmentCommand(StationCategory category) {
        return switch (category) {
            case COMBAT, CROSS_FEATURE -> "give @p diamond_sword 1";
            case INTERACT -> "give @p stone 64";
            case TRANSACTION -> "give @p oak_log 64";
            case EXTERNAL_FORCE -> "effect give @p resistance 15 1 true";
            default -> "effect clear @p";
        };
    }

    private void placeCommand(World world, int x, int y, int z, String command) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.COMMAND_BLOCK, false);
        if (block.getState() instanceof CommandBlock commandBlock) {
            commandBlock.setCommand(command);
            commandBlock.setName("ZeusLab");
            commandBlock.update(true, false);
        }
    }

    private void placeSign(World world, int x, int y, int z, String... lines) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.OAK_SIGN, false);
        if (block.getState() instanceof Sign sign) {
            for (int i = 0; i < Math.min(4, lines.length); i++) {
                sign.getSide(Side.FRONT).line(i, Component.text(lines[i]));
            }
            sign.update(true, false);
        }
    }

    private void outline(World world, int x1, int y, int z1, int x2, int z2, Material material) {
        for (int x = x1; x <= x2; x++) {
            world.setBlockData(x, y, z1, Bukkit.createBlockData(material));
            world.setBlockData(x, y, z2, Bukkit.createBlockData(material));
        }
        for (int z = z1; z <= z2; z++) {
            world.setBlockData(x1, y, z, Bukkit.createBlockData(material));
            world.setBlockData(x2, y, z, Bukkit.createBlockData(material));
        }
    }

    private void buildSideWalls(World world, int cx, int y, int z) {
        int leftX = cx - WIDTH / 2;
        int rightX = cx + WIDTH / 2;
        int wallStartZ = z - 1;
        int wallEndZ = z + LENGTH + GAP - 1;

        fill(world, leftX, y, wallStartZ, leftX, y + WALL_HEIGHT, wallEndZ, WALL_MATERIAL);
        fill(world, rightX, y, wallStartZ, rightX, y + WALL_HEIGHT, wallEndZ, WALL_MATERIAL);

        placeZeusWallText(world, leftX, y + 2, z + 9);
        placeZeusWallText(world, rightX, y + 2, z + 9);
    }

    private void placeZeusWallText(World world, int x, int baseY, int startZ) {
        for (int row = 0; row < ZEUS_PATTERN.length; row++) {
            String line = ZEUS_PATTERN[row];
            int y = baseY + (ZEUS_PATTERN.length - 1 - row);
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != ' ') {
                    world.setBlockData(x, y, startZ + col, Bukkit.createBlockData(WALL_TEXT_MATERIAL));
                }
            }
        }
    }

    private void fill(World world, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    world.setBlockData(x, y, z, Bukkit.createBlockData(material));
                }
            }
        }
    }

    private void setSlab(World world, int x, int y, int z, Material material, Slab.Type type) {
        Slab slab = (Slab) Bukkit.createBlockData(material);
        slab.setType(type);
        world.setBlockData(x, y, z, slab);
    }

    private void setStair(World world, int x, int y, int z, Material material, Bisected.Half half, BlockFace facing, Stairs.Shape shape) {
        Stairs stairs = (Stairs) Bukkit.createBlockData(material);
        stairs.setHalf(half);
        stairs.setFacing(facing);
        stairs.setShape(shape);
        world.setBlockData(x, y, z, stairs);
    }

    private String trim(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, max - 3) + "...";
    }

    public record VerificationResult(int expectedStations, int signsFound, int commandBlocksFound) {}
}
