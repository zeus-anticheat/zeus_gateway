package org.vennv.zeusPhysicsLab;

import java.util.ArrayList;
import java.util.List;

public final class StationCatalog {
    private StationCatalog() {}

    public static List<Station> all() {
        List<Station> stations = new ArrayList<>();
        add(stations, StationCategory.MOVEMENT, "Movement", "Position", 35,
            "MV_FLAT_WALK:walk straight 32 blocks, no sprint",
            "MV_FLAT_SPRINT:sprint straight 64 blocks",
            "MV_SNEAK_WALK:sneak walk across flat lane",
            "MV_STOP_START:repeated start/stop every 5 blocks",
            "MV_STRAFE_LEFT_RIGHT:alternate A/D strafe while moving forward",
            "MV_CIRCLE_PATH:circle markers for curvature and yaw correlation",
            "MV_ZIGZAG_PATH:zigzag route for direction changes",
            "MV_MOUSE_MICRO_CORRECTION:narrow marker path for small yaw corrections");
        add(stations, StationCategory.VERTICAL, "Movement", "Position", 35,
            "MV_SINGLE_JUMP:repeat isolated flat-ground jumps",
            "MV_FREE_FALL_TOWER:long free-fall tower with landing zones",
            "MV_SLIME_PISTON_LAUNCH:piston launches player upward from slime block",
            "MV_SPRINT_JUMP:sprint-jump chain over a long lane",
            "MV_LOW_CEILING_JUMP:jump/sprint under low ceiling sections",
            "MV_STEP_UP_HALF:slab and stair step-up/down lanes with top and bottom block states",
            "MV_STEP_UP_FULL:legal step-up block transition lane",
            "STEP_HEIGHT_VARIATION:step height variation obstacle course",
            "MV_REVERSE_STEP:controlled reverse steps and drops across slab/stair transitions",
            "MV_FALL_SHORT:short falls onto flat ground",
            "MV_FALL_WATER_BUCKET:water landing/drop column",
            "MV_AIR_STRAFE:ledge jump with midair strafing",
            "MV_HEAD_HIT_JUMP:ceiling collision jump lane");
        add(stations, StationCategory.ENVIRONMENT, "Movement", "Position", 35,
            "MV_ICE:ice, packed ice, and blue ice lanes",
            "MV_SLIME:slime bounce and horizontal lane",
            "MV_HONEY:honey wall and ground movement lane",
            "MV_SOUL_SAND:soul sand and soul soil lane",
            "MV_WEB:cobweb entry/exit and falling section",
            "MV_WEB_SOUL_SAND:cobweb over soul sand base",
            "MV_WEB_BLUE_ICE:cobweb over blue ice base",
            "MV_POWDER_SNOW:powder snow sink/walk/jump lane",
            "MV_MUD_SINK:mud and partial-height muddy surface lane",
            "MV_BERRY_BUSH:sweet berry bush slow and damage lane",
            "MV_CACTUS_CONTACT:cactus side-contact damage and knockback lane",
            "MV_POINTED_DRIPSTONE:pointed dripstone collision and damage lane",
            "MV_SNOW_LAYER:snow layer ground-state lane",
            "MV_SNOW_LAYER_RAMP:walk over gradually increasing snow layers",
            "MV_STAIRS_SLABS:mixed stairs, slabs, carpet, trapdoor terrain with multiple orientations",
            "MV_FENCE_WALL_EDGE:fence and wall collision graze lane",
            "MV_FENCE_STEP_UP:step up and walk on top of oak fences",
            "MV_STONE_WALL_STEP_UP:step up and walk on cobblestone walls",
            "MV_BLOCK_EDGE:narrow edge path for edge proximity",
            "MV_TRAPDOOR_CRAWL:trapdoor-triggered crawling through one-block gap",
            "MV_BED_BOUNCE:bed bounce and landing behavior lane");
        add(stations, StationCategory.LIQUID_CLIMB_SPECIAL, "Movement", "Position", 40,
            "MV_WATER_SURFACE:swim and surface transitions",
            "MV_WATER_SUBMERGED:submerged swim lane with vertical movement",
            "MV_LAVA:lava movement with fire resistance",
            "MV_BUBBLE_COLUMN_UP:upward bubble column",
            "MV_BUBBLE_COLUMN_DOWN:downward bubble column",
            "MV_LADDER:climb, stop, and jump off ladder",
            "MV_VINES:vine climb and lateral movement",
            "MV_SCAFFOLDING:climb/drop scaffolding lane",
            "MV_RIPTIDE:water/rain riptide activation lane",
            "MV_FLOWING_WATER:flowing water side-current lane",
            "MV_ELYTRA_GLIDE:elytra glide, dive, and pull-up",
            "MV_ELYTRA_FIREWORK:elytra with firework boost",
            "MV_WIND_CHARGE:wind-charge impulse movement station",
            "MV_MACE_SMASH:mace smash context station",
            "MV_ENDER_PEARL:ender pearl teleport movement test");
        add(stations, StationCategory.VEHICLE, "Movement", "VehicleMove", 40,
            "VH_BOAT_WATER_STRAIGHT:boat on water straight lane",
            "VH_BOAT_WATER_TURNS:boat water turn course",
            "VH_BOAT_LAND:boat on land baseline",
            "VH_BOAT_ICE:boat on ice and blue ice speed lane",
            "VH_MINECART_FLAT:minecart flat rail",
            "VH_MINECART_SLOPE:minecart slope and curve rail",
            "VH_HORSE_WALK_JUMP:horse walk, sprint, and jump lane",
            "VH_PIG_STRIDER:pig/strider control station",
            "VH_MOUNT_DISMOUNT:repeat mount/dismount and exit impulse",
            "VH_VEHICLE_TO_PLAYER_TRANSITION:exit vehicle then move immediately");
        add(stations, StationCategory.INTERACT, "Interact", "PlaceBlock/DiggingBlock", 35,
            "IN_BREAK_SLOW:break stone, wood, and dirt at normal cadence",
            "IN_BREAK_FAST_TOOL:efficiency/haste break lane",
            "IN_BREAK_FATIGUE:mining fatigue break lane",
            "IN_BREAK_ROTATION:break blocks at varied yaw/pitch offsets",
            "IN_BREAK_DISTANCE:break near and far reachable blocks",
            "IN_PLACE_STATIC:place blocks while standing still",
            "IN_PLACE_MOVING:place blocks while walking and sprinting",
            "IN_PLACE_BELOW_MOVING:bridge/place-below while moving",
            "IN_PLACE_SUPPORT_MISSING:edge and unsupported placement attempts",
            "IN_PLACE_ROTATION_SNAP:place around player following markers",
            "IN_BREAK_PLACE_ALTERNATE:alternate break/place cadence",
            "IN_MULTI_BLOCK_SAME_TICK:rapid multi-block interaction station",
            "IN_INTERACT_COMBAT:place/break while near combat target");
        add(stations, StationCategory.COMBAT, "Combat", "SwingHand/Attack", 35,
            "CB_STATIC_TARGET:attack stationary armor stand target",
            "CB_MOVING_TARGET:attack moving target on command path",
            "CB_STRAFE_TARGET:strafe while attacking target",
            "CB_DISTANCE_SWEEP:targets at multiple legal distances",
            "CB_YAW_OFFSETS:targets arranged around player yaw offsets",
            "CB_PITCH_OFFSETS:targets at height differences",
            "CB_TARGET_SWITCH:alternate attacks between targets",
            "CB_SPRINT_RESET:sprint-hit-reset lane",
            "CB_CRITICAL_HITS:jump critical hit station",
            "CB_BLOCKING_ATTACK:attack in shield/blocking context",
            "CB_EATING_ATTACK:attempt attacks while eating/use-item context",
            "CB_FISHING_ATTACK:fishing rod cast then attack",
            "CB_WEAPON_FAMILIES:fist, sword, axe, trident, spear, mace, bow, crossbow",
            "CB_TRIDENT_THROW:trident spear-style throw and melee station",
            "CB_SPEAR_JAB_RANGE:spear minimum and maximum reach test",
            "CB_SPEAR_CHARGE_ATTACK:spear charge attack velocity lane",
            "CB_SPEAR_MULTI_ENTITY:spear charge multi-entity hit line",
            "CB_SPEAR_NON_SOLID_REACH:spear reach through cobweb and grass",
            "CB_SPEAR_NO_CRIT:spear jump attack no-critical-hit check",
            "CB_SPEAR_NO_SPRINT_KB:spear sprint hit no-sprint-knockback check",
            "CB_SPEAR_DISMOUNT:spear charge dismounts mounted target",
            "CB_SWING_NO_HIT:swings without hit for swing ratio",
            "CB_COMBO:repeated combo hits on target");
        add(stations, StationCategory.EXTERNAL_FORCE, "ExternalForce", "Velocity/AttackedByEntity", 35,
            "EF_KNOCKBACK_PLAYER:controlled incoming player/entity hit",
            "EF_KNOCKBACK_PROJECTILE:projectile knockback-like station",
            "EF_EXPLOSION_TNT:TNT knockback with safe reset",
            "EF_EXPLOSION_CRYSTAL_OR_BED:optional explosion station",
            "EF_PISTON_PUSH:piston push impulse station",
            "EF_WIND_CHARGE_FORCE:wind charge external force response",
            "EF_KB_RESISTANCE:same knockback with resistance modifiers",
            "EF_MULTI_FORCE:two force events in one analysis window",
            "EF_ENTITY_PUSH:entity collision and push recovery station");
        add(stations, StationCategory.TRANSACTION, "Transaction", "HeldItem/ClickWindow/OpenWindow/CloseWindow", 35,
            "TX_HOTBAR_SWITCH:controlled hotbar switching stationary and moving",
            "TX_CONTAINER_OPEN_CLOSE:chest/barrel open-close cadence",
            "TX_CONTAINER_DURATION:short and long container open durations",
            "TX_SLOT_TRAVERSAL:move items across sequential slots",
            "TX_BULK_MOVE:shift-click/bulk move items",
            "TX_DROP_ITEMS:drop single and stack items",
            "TX_CRAFTING:craft simple recipes",
            "TX_SORTING:manual inventory sorting route",
            "TX_WHILE_MOVING:inventory actions while walking",
            "TX_DURING_COMBAT:inventory/hotbar actions between combat hits");
        add(stations, StationCategory.NETWORK, "Network", "KeepAlive/packet_timing", 45,
            "NW_IDLE_BASELINE:stand still with normal packet cadence",
            "NW_MOVEMENT_CADENCE:steady movement route for IPD distribution",
            "NW_BURST_ACTIONS:controlled rapid actions for legitimate bursts",
            "NW_CONTAINER_BURSTS:inventory burst station",
            "NW_TELEPORT_SEQUENCE:command-block teleports with confirmations",
            "NW_CHUNK_BOUNDARY:movement across chunk boundaries",
            "NW_TPS_STRESS_LIGHT:safe low-intensity redstone/entity activity",
            "NW_PING_BUCKETS:repeat key routes under latency profiles");
        add(stations, StationCategory.CROSS_FEATURE, "Movement,Interact,Combat,Transaction,ExternalForce", "mixed", 45,
            "XR_MOVEMENT_INTERACT:sprint/jump while placing and breaking",
            "XR_MOVEMENT_COMBAT:sprint/strafe/jump while attacking moving target",
            "XR_COMBAT_TRANSACTION:hotbar/inventory actions during combat",
            "XR_VEHICLE_COMBAT:mount/dismount and attack around vehicle",
            "XR_LIQUID_COMBAT:water movement while attacking target",
            "XR_ICE_COMBAT:ice movement while attacking target",
            "XR_EXTERNAL_FORCE_COMBAT:knockback while combat is ongoing");
        return List.copyOf(stations);
    }

    private static void add(List<Station> stations, StationCategory category, String groups, String events, int duration, String... entries) {
        for (String entry : entries) {
            String[] parts = entry.split(":", 2);
            String id = parts[0];
            String description = parts[1];
            stations.add(new Station(
                stations.size() + 1,
                id,
                description,
                category,
                List.of(groups.split(",")),
                List.of(category.name().toLowerCase(), id.toLowerCase()),
                List.of(
                    "Press the start button or pressure plate.",
                    "Follow the lane markers and signs.",
                    description,
                    "Stop at the finish marker and save the replay segment with this station id."
                ),
                List.of(events.split("/")),
                duration
            ));
        }
    }
}
