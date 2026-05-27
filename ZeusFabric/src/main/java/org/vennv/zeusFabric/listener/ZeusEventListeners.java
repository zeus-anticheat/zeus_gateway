package org.vennv.zeusFabric.listener;

import java.lang.reflect.Field;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vennv.Effect;
import org.vennv.EntityState;
import org.vennv.packets.*;
import org.vennv.utils.*;
import org.vennv.zeusFabric.provider.PacketQueue;
import org.vennv.zeusFabric.task.PlayerStateSnapshotService;
import org.vennv.zeusFabric.utils.ItemUtil;
import org.vennv.zeusFabric.utils.MinecraftCompat;

/**
 * Registers all Fabric event callbacks that correspond to the Zeus protocol packets.
 * <p>
 * Each callback creates the matching ZeusProtocolJava packet and pushes it into the
 * {@link PacketQueue} for batched UDP transmission to the Zeus anti-cheat server.
 * <p>
 * Covers all 30 packet types defined in {@link org.vennv.PacketId}:
 * <ol>
 *   <li>PacketPlayerJoin</li>
 *   <li>PacketPlayerLeave</li>
 *   <li>PacketPlayerPosition</li>
 *   <li>PacketPlayerKeepAlive</li>
 *   <li>PacketPlayerChangeMode</li>
 *   <li>PacketPlayerSwingHand</li>
 *   <li>PacketPlayerPlaceBlock</li>
 *   <li>PacketPlayerDiggingBlock</li>
 *   <li>PacketPlayerAttackEntity</li>
 *   <li>PacketPlayerTeleport</li>
 *   <li>PacketPlayerEffect</li>
 *   <li>PacketPlayerGotDamage</li>
 *   <li>PacketPlayerBlockFace</li>
 *   <li>PacketPlayerBlockRayTrace</li>
 *   <li>PacketPlayerBlockChangeAck</li>
 *   <li>PacketPlayerAttackedByEntity</li>
 *   <li>PacketPlayerEntityInteraction</li>
 *   <li>PacketTPSServer (handled in ZeusFabricMod tick callback)</li>
 *   <li>PacketPlayerSurroundingBlocks</li>
 *   <li>PacketPlayerHeldItem</li>
 *   <li>PacketPlayerArmorsEquipment</li>
 *   <li>PacketPlayerConfirmTransaction</li>
 *   <li>PacketPlayerOpenWindow</li>
 *   <li>PacketPlayerClickWindow</li>
 *   <li>PacketPlayerCloseWindow</li>
 *   <li>PacketPlayerUseItem</li>
 *   <li>PacketPlayerReleaseUseItem</li>
 *   <li>PacketPlayerSteerVehicle</li>
 *   <li>PacketPlayerVehicleMove</li>
 *   <li>PacketServerBoundPlayerCommand</li>
 * </ol>
 */
public final class ZeusEventListeners {

    private static final Logger LOGGER = LoggerFactory.getLogger("zeusfabric");

    private ZeusEventListeners() {}

    // ─────────────────────────────────────────────────────────────────────
    //  Tracking maps / caches for detecting state changes per-player
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Tracks previous held item per player UUID so we can emit
     * PacketPlayerHeldItem on change.
     */
    private static final java.util.Map<String, String> LAST_HELD_HASH =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks previous armor hash per player so we can emit
     * PacketPlayerArmorsEquipment only on change.
     */
    private static final java.util.Map<String, Integer> LAST_ARMOR_HASH =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks previous gamemode ordinal per player.
     */
    private static final java.util.Map<String, Integer> LAST_GAMEMODE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks previous effects list per player.
     */
    private static final java.util.Map<String, List<Effect>> LAST_EFFECTS =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Integer> LAST_ENCHANTMENTS_HASH =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks previous position per player for detecting teleports vs moves.
     */
    private static final java.util.Map<String, Vec3d> LAST_POSITION =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks sneaking state per player.
     */
    private static final java.util.Map<String, Boolean> LAST_SNEAKING =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks sprinting state per player.
     */
    private static final java.util.Map<String, Boolean> LAST_SPRINTING =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks whether player was in a vehicle last tick.
     */
    private static final java.util.Map<String, Boolean> LAST_IN_VEHICLE =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Boolean> LAST_IN_BOAT =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks the vehicle position from last tick for vehicle-move detection.
     */
    private static final java.util.Map<String, Vec3d> LAST_VEHICLE_POS =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks last open screen handler id per player for open/close window.
     */
    private static final java.util.Map<String, Integer> LAST_SCREEN_HANDLER =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks flying state (elytra) per player.
     */
    private static final java.util.Map<String, Boolean> LAST_GLIDING =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks riptide state per player.
     */
    private static final java.util.Map<String, Boolean> LAST_USING_RIPTIDE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks swimming/crawling pose (Pose.SWIMMING) per player.
     */
    private static final java.util.Map<String, Boolean> LAST_SWIMMING =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks the last recorded velocity per player.
     */
    private static final java.util.Map<String, Vec3d> LAST_VELOCITY =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Suppresses repeat piston force packets while the same moving block
     * continues intersecting the player over consecutive ticks.
     */
    private static final java.util.Map<String, String> LAST_PISTON_SIGNATURE =
        new java.util.concurrent.ConcurrentHashMap<>();

    // ─── Reflection fields for independent KeepAlive RTT measurement ────

    private static Field FIELD_WAITING_FOR_KEEP_ALIVE;
    private static Field FIELD_LAST_KEEP_ALIVE_TIME;
    private static boolean keepAliveReflectionFailed = false;

    /**
     * Tracks the last observed "waitingForKeepAlive" state per player.
     * When it transitions from true → false, the client responded.
     */
    private static final java.util.Map<String, Boolean> LAST_WAITING_STATE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks the sentTime when we first observed waitingForKeepAlive=true.
     */
    private static final java.util.Map<String, Long> KA_SENT_TIME =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Last measured RTT per player (ms), updated when waitingForKeepAlive
     * transitions from true → false.
     */
    private static final java.util.Map<String, Long> MEASURED_RTT =
        new java.util.concurrent.ConcurrentHashMap<>();

    static {
        try {
            // Yarn-mapped field names for Fabric 1.21+
            FIELD_WAITING_FOR_KEEP_ALIVE =
                ServerPlayNetworkHandler.class.getDeclaredField("waitingForKeepAlive");
            FIELD_WAITING_FOR_KEEP_ALIVE.setAccessible(true);

            FIELD_LAST_KEEP_ALIVE_TIME =
                ServerPlayNetworkHandler.class.getDeclaredField("lastKeepAliveTime");
            FIELD_LAST_KEEP_ALIVE_TIME.setAccessible(true);

            LoggerFactory.getLogger("zeusfabric").info(
                "[ZeusFabric] KeepAlive reflection fields resolved successfully"
            );
        } catch (NoSuchFieldException e) {
            keepAliveReflectionFailed = true;
            LoggerFactory.getLogger("zeusfabric").warn(
                "[ZeusFabric] Could not resolve KeepAlive reflection fields: {}. "
              + "PingSpoof detection will use getLatency() fallback.",
                e.getMessage()
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Registration entry point
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Registers all Fabric event callbacks. Called once from the mod initialiser.
     */
    public static void registerAll() {
        registerJoinLeave();
        registerAttackEntity();
        registerAttackBlock();
        registerUseBlock();
        registerUseEntity();
        registerUseItem();
        registerBlockBreak();
        registerDamage();
        registerDeath();
        registerRespawn();
        registerWorldChange();
        registerTickListeners();

        LOGGER.info("[ZeusFabric] All event listeners registered.");
    }

    // ─────────────────────── Join / Leave ────────────────────────────────

    private static void registerJoinLeave() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            String uid = player.getUuidAsString();
            String name = player.getName().getString();

            PlayerStateSnapshotService.sendFullSnapshot(player);
            LOGGER.debug("[ZeusFabric] Player joined: {}", name);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            String uid = player.getUuidAsString();
            String name = player.getName().getString();
            long timestamp = System.currentTimeMillis();

            PacketQueue.push(new PacketPlayerLeave(timestamp, uid, name));
            clearTracking(uid);
            PlayerStateSnapshotService.clear(uid);

            LOGGER.debug("[ZeusFabric] Player left: {}", name);
        });
    }

    // ─────────────────────── World Change ────────────────────────────────
    
    private static void registerWorldChange() {
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            String uid = player.getUuidAsString();
            String name = player.getName().getString();
            long timestamp = System.currentTimeMillis();

            net.minecraft.util.math.Vec3d pos = positionOf(player);

            PacketQueue.push(
                new PacketPlayerTeleport(
                    timestamp,
                    uid,
                    name,
                    pos.x,
                    pos.y,
                    pos.z
                )
            );
        });
    }

    // ─────────────────── Attack Entity ───────────────────────────────────

    private static void registerAttackEntity() {
        AttackEntityCallback.EVENT.register(
            (player, world, hand, entity, hitResult) -> {
                if (
                    world.isClient() ||
                    !(player instanceof ServerPlayerEntity serverPlayer)
                ) {
                    return ActionResult.PASS;
                }

                String uid = serverPlayer.getUuidAsString();
                String name = serverPlayer.getName().getString();
                long timestamp = System.currentTimeMillis();

                EntityState entityState = buildEntityState(entity);

                PacketQueue.push(
                    new PacketPlayerAttackEntity(
                        timestamp,
                        uid,
                        name,
                        entityState
                    )
                );

                // Also emit swing hand
                PacketQueue.push(
                    new PacketPlayerSwingHand(timestamp, uid, name, false)
                );

                return ActionResult.PASS;
            }
        );
    }

    // ──────────────────── Attack Block (Digging) ────────────────────────

    private static void registerAttackBlock() {
        AttackBlockCallback.EVENT.register(
            (player, world, hand, pos, direction) -> {
                if (
                    world.isClient() ||
                    !(player instanceof ServerPlayerEntity serverPlayer)
                ) {
                    return ActionResult.PASS;
                }

                String uid = serverPlayer.getUuidAsString();
                String name = serverPlayer.getName().getString();
                long timestamp = System.currentTimeMillis();

                // PacketPlayerDiggingBlock
                PacketQueue.push(
                    new PacketPlayerDiggingBlock(
                        timestamp,
                        uid,
                        name,
                        false,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()
                    )
                );

                // PacketPlayerBlockFace
                byte face = mapDirection(direction);
                PacketQueue.push(
                    new PacketPlayerBlockFace(timestamp, uid, name, face)
                );

                // PacketPlayerSwingHand
                PacketQueue.push(
                    new PacketPlayerSwingHand(timestamp, uid, name, false)
                );

                return ActionResult.PASS;
            }
        );
    }

    // ──────────────────── Use Block (Place Block) ───────────────────────

    private static void registerUseBlock() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (
                world.isClient() ||
                !(player instanceof ServerPlayerEntity serverPlayer)
            ) {
                return ActionResult.PASS;
            }

            String uid = serverPlayer.getUuidAsString();
            String name = serverPlayer.getName().getString();
            long timestamp = System.currentTimeMillis();

            BlockPos pos = hitResult.getBlockPos();

            // PacketPlayerPlaceBlock
            PacketQueue.push(
                new PacketPlayerPlaceBlock(
                    timestamp,
                    uid,
                    name,
                    false,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()
                )
            );

            // PacketPlayerBlockFace
            byte face = mapDirection(hitResult.getSide());
            PacketQueue.push(
                new PacketPlayerBlockFace(timestamp, uid, name, face)
            );

            // PacketPlayerBlockRayTrace
            Vec3d hitPos = hitResult.getPos();
            PacketQueue.push(
                new PacketPlayerBlockRayTrace(
                    timestamp,
                    uid,
                    name,
                    true,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    (float) hitPos.x,
                    (float) hitPos.y,
                    (float) hitPos.z
                )
            );

            // PacketPlayerBlockChangeAck
            PacketQueue.push(
                new PacketPlayerBlockChangeAck(timestamp, uid, name)
            );

            return ActionResult.PASS;
        });
    }

    // ──────────────────── Use Entity (Interaction) ──────────────────────

    private static void registerUseEntity() {
        UseEntityCallback.EVENT.register(
            (player, world, hand, entity, hitResult) -> {
                if (
                    world.isClient() ||
                    !(player instanceof ServerPlayerEntity serverPlayer)
                ) {
                    return ActionResult.PASS;
                }

                String uid = serverPlayer.getUuidAsString();
                String name = serverPlayer.getName().getString();
                long timestamp = System.currentTimeMillis();

                EntityState entityState = buildEntityState(entity);

                PacketQueue.push(
                    new PacketPlayerEntityInteraction(
                        timestamp,
                        uid,
                        name,
                        entityState
                    )
                );

                return ActionResult.PASS;
            }
        );
    }

    // ──────────────────────── Use Item ──────────────────────────────────

    private static void registerUseItem() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (
                world.isClient() ||
                !(player instanceof ServerPlayerEntity serverPlayer)
            ) {
                return ActionResult.PASS;
            }

            String uid = serverPlayer.getUuidAsString();
            String name = serverPlayer.getName().getString();
            long timestamp = System.currentTimeMillis();

            org.vennv.utils.Hand zeusHand = (hand == Hand.OFF_HAND)
                ? org.vennv.utils.Hand.OFF_HAND
                : org.vennv.utils.Hand.MAIN_HAND;

            PacketQueue.push(
                new PacketPlayerUseItem(
                    timestamp,
                    uid,
                    name,
                    zeusHand,
                    (byte) 0
                )
            );

            net.minecraft.item.ItemStack used = serverPlayer.getStackInHand(hand);
            String itemKey = Registries.ITEM.getId(used.getItem()).getPath();
            if (serverPlayer.isGliding() && "firework_rocket".equals(itemKey)) {
                Vec3d velocity = serverPlayer.getVelocity();
                emitExternalForce(
                    serverPlayer,
                    ExternalForceType.ELYTRA_FIREWORK,
                    positionOf(serverPlayer),
                    serverPlayer.getRotationVec(1.0f),
                    velocity,
                    Math.max(1.0, velocity.length()),
                    (short) 40,
                    ExternalForceFlags.ENVIRONMENT_BACKED
                );
            }

            return ActionResult.PASS;
        });
    }

    // ─────────────────────── Block Break ────────────────────────────────

    private static void registerBlockBreak() {
        PlayerBlockBreakEvents.AFTER.register(
            (world, player, pos, state, blockEntity) -> {
                if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                    return;
                }

                String uid = serverPlayer.getUuidAsString();
                String name = serverPlayer.getName().getString();
                long timestamp = System.currentTimeMillis();

                // PacketPlayerBlockChangeAck — block has changed
                PacketQueue.push(
                    new PacketPlayerBlockChangeAck(timestamp, uid, name)
                );
            }
        );

        PlayerBlockBreakEvents.CANCELED.register(
            (world, player, pos, state, blockEntity) -> {
                if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                    return;
                }

                String uid = serverPlayer.getUuidAsString();
                String name = serverPlayer.getName().getString();
                long timestamp = System.currentTimeMillis();

                PacketQueue.push(
                    new PacketPlayerDiggingBlock(
                        timestamp,
                        uid,
                        name,
                        true,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()
                    )
                );
            }
        );
    }

    // ───────────────────── Damage events ────────────────────────────────

    private static void registerDamage() {
        // Player got damage
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(
            (entity, source, amount) -> {
                if (entity instanceof ServerPlayerEntity player) {
                    String uid = player.getUuidAsString();
                    String name = player.getName().getString();
                    long timestamp = System.currentTimeMillis();

                    DamageCause cause = mapDamageSource(source);
                    PacketQueue.push(
                        new PacketPlayerGotDamage(timestamp, uid, name, cause)
                    );

                    // If attacked by an entity, determine if it's a player or mob
                    Entity attacker = source.getAttacker();
                    if (attacker != null) {
                        EntityState attackerState = buildEntityState(attacker);
                        if (attacker instanceof ServerPlayerEntity) {
                            // Attacked by another player
                            PacketQueue.push(
                                new PacketPlayerAttackedByPlayer(
                                    timestamp,
                                    uid,
                                    name,
                                    attackerState
                                )
                            );
                        } else {
                            // Attacked by a non-player entity (mob, etc.)
                            PacketQueue.push(
                                new PacketPlayerAttackedByEntity(
                                    timestamp,
                                    uid,
                                    name,
                                    attackerState
                                )
                            );
                        }
                    }

                    Entity sourceEntity = attacker;
                    Vec3d sourcePos = sourceEntity != null ? positionOf(sourceEntity) : positionOf(player);
                    Vec3d velocity = player.getVelocity();
                    emitExternalForce(
                        player,
                        classifyDamageForce(source, attacker),
                        sourcePos,
                        positionOf(player).subtract(sourcePos),
                        velocity,
                        Math.max(amount, velocity.length()),
                        (short) 12,
                        ExternalForceFlags.DAMAGE_BACKED
                    );
                }
                return true; // don't cancel the damage
            }
        );
    }

    // ───────────────────── Death events ─────────────────────────────────

    private static void registerDeath() {
        // Player died
        ServerLivingEntityEvents.AFTER_DEATH.register(
            (entity, damageSource) -> {
                if (entity instanceof ServerPlayerEntity player) {
                    String uid = player.getUuidAsString();
                    String name = player.getName().getString();
                    long timestamp = System.currentTimeMillis();

                    PacketQueue.push(
                        new PacketPlayerDeath(timestamp, uid, name)
                    );
                }
            }
        );
    }

    // ───────────────────── Respawn events ───────────────────────────────

    private static void registerRespawn() {
        ServerPlayerEvents.AFTER_RESPAWN.register(
            (oldPlayer, newPlayer, alive) -> {
                String uid = newPlayer.getUuidAsString();
                String name = newPlayer.getName().getString();
                long timestamp = System.currentTimeMillis();

                PacketQueue.push(
                    new PacketPlayerRespawn(timestamp, uid, name)
                );
            }
        );
    }

    // ────────────────────── Tick-based listeners ────────────────────────
    // Some state changes (position, held item, armor, gamemode, effects,
    // sneaking, sprinting, vehicles, screen handlers) don't have dedicated
    // Fabric events, so we poll them every tick.

    private static void registerTickListeners() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
            server -> {
                for (ServerPlayerEntity player : server
                    .getPlayerManager()
                    .getPlayerList()) {
                    try {
                        tickPlayer(player);
                    } catch (Exception e) {
                        LOGGER.warn(
                            "[ZeusFabric] Error ticking player {}: {}",
                            player.getName().getString(),
                            e.getMessage()
                        );
                    }
                }
            }
        );
    }

    /**
     * Per-player per-tick state polling. Detects changes that Fabric
     * doesn't expose through dedicated events.
     */
    private static void tickPlayer(ServerPlayerEntity player) {
        String uid = player.getUuidAsString();
        String name = player.getName().getString();
        long timestamp = System.currentTimeMillis();

        // ── Keep Alive (piggyback on tick) ──
        tickKeepAlive(player, uid, name, timestamp);

        // ── Held Item ──
        PlayerStateSnapshotService.sendHeldItemSnapshot(player, false);

        // ── Armor Equipment ──
        PlayerStateSnapshotService.sendArmorSnapshot(player, false);

        // ── Game Mode ──
        tickGameMode(player, uid, name, timestamp);

        // ── Potion Effects ──
        tickEffects(player, uid, name, timestamp);

        // ── Enchantments & Attributes ──
        PlayerStateSnapshotService.sendEnchantmentsSnapshot(player, false);

        // ── Sneaking / Sprinting / Commands ──
        tickPlayerCommands(player, uid, name, timestamp);

        // ── Vehicle ──
        tickVehicle(player, uid, name, timestamp);

        // ── Screen handler (Open/Close Window) ──
        tickScreenHandler(player, uid, name, timestamp);

        // ── Block Ray Trace ──
        tickBlockRayTrace(player, uid, name, timestamp);

        // ── Velocity ──
        tickVelocity(player, uid, name, timestamp);

        // ── Environmental external force ──
        tickEnvironmentForces(player, uid, name, timestamp);
    }

    // ─────────────────────── Position ───────────────────────────────────

    private static void tickPosition(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        Vec3d pos = positionOf(player);
        Vec3d lastPos = LAST_POSITION.get(uid);

        // Only send when position actually changed
        if (lastPos != null && lastPos.squaredDistanceTo(pos) < 0.000001) {
            return;
        }

        LAST_POSITION.put(uid, pos);

        double eyeX = pos.x;
        double eyeY = pos.y + (double) player.getStandingEyeHeight();
        double eyeZ = pos.z;
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        float height = player.getHeight();
        boolean onGround = player.isOnGround();

        // Detect teleports: if distance > 8 blocks in a single tick
        boolean isTeleport =
            lastPos != null && lastPos.squaredDistanceTo(pos) > 64.0;

        if (isTeleport) {
            PacketQueue.push(
                new PacketPlayerTeleport(
                    timestamp,
                    uid,
                    name,
                    pos.x,
                    pos.y,
                    pos.z
                )
            );
        }

        PacketQueue.push(
            new PacketPlayerPosition(
                timestamp,
                uid,
                name,
                false,
                pos.x,
                pos.y,
                pos.z,
                eyeX,
                eyeY,
                eyeZ,
                yaw,
                pitch,
                height,
                onGround
            )
        );

        // Surrounding blocks
        List<RelativeBlock> blocks = getSurroundingBlocks(player);
        PacketQueue.push(
            new PacketPlayerSurroundingBlocks(timestamp, uid, name, blocks)
        );
    }

    // ─────────────────────── Velocity ─────────────────────────────────────

    private static void tickVelocity(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        Vec3d vel = player.getVelocity();
        Vec3d lastVel = LAST_VELOCITY.get(uid);

        if (lastVel != null && lastVel.squaredDistanceTo(vel) < 1e-6) {
            return;
        }

        LAST_VELOCITY.put(uid, vel);

        PacketQueue.push(
            new PacketPlayerVelocity(
                timestamp,
                uid,
                name,
                vel.x,
                vel.y,
                vel.z
            )
        );
    }

    private static void tickEnvironmentForces(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        if (emitPistonForce(player, uid)) {
            PlayerStateSnapshotService.sendPositionAndBlocksSnapshot(player);
        }

        BlockState feet = MinecraftCompat.entityWorld(player).getBlockState(player.getBlockPos());
        String state = feet.toString();
        Vec3d velocity = player.getVelocity();
        if (state.contains("bubble_column") && Math.abs(velocity.y) > 0.02) {
            emitExternalForce(
                player,
                ExternalForceType.BUBBLE_COLUMN,
                positionOf(player),
                new Vec3d(0.0, Math.signum(velocity.y), 0.0),
                velocity,
                Math.max(0.1, Math.abs(velocity.y)),
                (short) 20,
                ExternalForceFlags.ENVIRONMENT_BACKED
            );
        }
    }

    private static boolean emitPistonForce(ServerPlayerEntity player, String uid) {
        Box playerBox = player.getBoundingBox();
        Box searchBox = playerBox.expand(1.25);

        for (BlockPos pos : BlockPos.iterate(searchBox)) {
            BlockState state = MinecraftCompat.entityWorld(player).getBlockState(pos);
            if (!state.isOf(Blocks.MOVING_PISTON)) {
                continue;
            }
            if (!(MinecraftCompat.entityWorld(player).getBlockEntity(pos) instanceof PistonBlockEntity piston)) {
                continue;
            }

            Direction movement = piston.getMovementDirection();
            Box sweptBox = new Box(pos)
                .union(new Box(pos.offset(movement.getOpposite())))
                .expand(0.05);
            if (!sweptBox.intersects(playerBox)) {
                continue;
            }

            BlockState pushedBlock = piston.getPushedBlock();
            ExternalForceType type = ExternalForceType.PISTON;
            int flags = ExternalForceFlags.DIRECT_INTERSECT | ExternalForceFlags.ENVIRONMENT_BACKED;
            if (pushedBlock.isOf(Blocks.SLIME_BLOCK)) {
                type = ExternalForceType.SLIME_PISTON;
                flags |= ExternalForceFlags.HAS_SLIME;
            } else if (pushedBlock.isOf(Blocks.HONEY_BLOCK)) {
                flags |= ExternalForceFlags.HAS_HONEY;
            }
            if (!piston.isExtending()) {
                flags |= ExternalForceFlags.RETRACTING;
            }

            String signature = pos.asLong() + ":" + movement + ":" + flags;
            if (signature.equals(LAST_PISTON_SIGNATURE.put(uid, signature))) {
                return false;
            }

            Vec3d direction = new Vec3d(
                movement.getOffsetX(),
                movement.getOffsetY(),
                movement.getOffsetZ()
            );
            emitExternalForce(
                player,
                type,
                Vec3d.ofCenter(pos),
                direction,
                player.getVelocity(),
                type == ExternalForceType.SLIME_PISTON ? 1.0 : 0.51,
                (short) (type == ExternalForceType.SLIME_PISTON ? 30 : 15),
                flags
            );
            return true;
        }

        LAST_PISTON_SIGNATURE.remove(uid);
        return false;
    }

    // ─────────────────────── Keep Alive ─────────────────────────────────

    /** We send keep-alive packets once every second (20 ticks). */
    private static int keepAliveCounter = 0;

    private static void tickKeepAlive(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        // ── Independent RTT measurement via reflection ──
        // Every tick: check if "waitingForKeepAlive" transitioned true→false
        // which means the client just responded. At that moment, calculate
        // the real RTT = now - lastKeepAliveTime (when server sent the probe).
        if (!keepAliveReflectionFailed) {
            try {
                ServerPlayNetworkHandler handler = player.networkHandler;
                boolean waiting = FIELD_WAITING_FOR_KEEP_ALIVE.getBoolean(handler);
                long sentTime = FIELD_LAST_KEEP_ALIVE_TIME.getLong(handler);

                Boolean wasWaiting = LAST_WAITING_STATE.get(uid);

                if (waiting && (wasWaiting == null || !wasWaiting)) {
                    // Server just sent a new KeepAlive probe → record the send time
                    KA_SENT_TIME.put(uid, sentTime);
                } else if (!waiting && wasWaiting != null && wasWaiting) {
                    // Client just responded → calculate real RTT
                    Long recordedSentTime = KA_SENT_TIME.get(uid);
                    if (recordedSentTime != null) {
                        long now = Util.getMeasuringTimeMs();
                        long measuredRtt = now - recordedSentTime;
                        if (measuredRtt >= 0 && measuredRtt < 30_000) {
                            MEASURED_RTT.put(uid, measuredRtt);
                        }
                    }
                }

                LAST_WAITING_STATE.put(uid, waiting);
            } catch (Exception e) {
                // Silently ignore — fallback to getLatency()
            }
        }

        // ── Send KeepAlive packet every 20 ticks (1 second) ──
        keepAliveCounter++;
        if (keepAliveCounter % 20 != 0) {
            return;
        }

        int serverPing = player.networkHandler.getLatency();
        long measuredPing = MEASURED_RTT.getOrDefault(uid, (long) serverPing);

        // Send the independently measured RTT (or server ping as fallback)
        PacketQueue.push(new PacketPlayerKeepAlive(timestamp, uid, name, measuredPing));
    }

    // ─────────────────────── Held Item ──────────────────────────────────

    private static void tickHeldItem(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        ItemStack stack = MinecraftCompat.selectedStack(player);
        Item item = buildItem(stack);
        String currentHash = item.toString();
        String lastHash = LAST_HELD_HASH.get(uid);

        if (currentHash.equals(lastHash)) {
            return;
        }

        LAST_HELD_HASH.put(uid, currentHash);

        PacketQueue.push(new PacketPlayerHeldItem(timestamp, uid, name, item));
    }

    // ─────────────────────── Armor ──────────────────────────────────────

    private static void tickArmor(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        int currentHash = computeArmorHash(player);
        Integer lastHash = LAST_ARMOR_HASH.get(uid);

        if (lastHash != null && lastHash == currentHash) {
            return;
        }

        LAST_ARMOR_HASH.put(uid, currentHash);

        Armor helmet = buildArmor(player.getEquippedStack(EquipmentSlot.HEAD));
        Armor chestplate = buildArmor(
            player.getEquippedStack(EquipmentSlot.CHEST)
        );
        Armor leggings = buildArmor(
            player.getEquippedStack(EquipmentSlot.LEGS)
        );
        Armor boots = buildArmor(player.getEquippedStack(EquipmentSlot.FEET));

        Armors armors = new Armors(helmet, chestplate, leggings, boots);
        PacketQueue.push(
            new PacketPlayerArmorsEquipment(timestamp, uid, name, armors)
        );
    }

    // ─────────────────────── Game Mode ──────────────────────────────────

    private static void tickGameMode(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        int currentMode = MinecraftCompat.gameModeIndex(player);
        Integer lastMode = LAST_GAMEMODE.get(uid);

        if (lastMode != null && lastMode == currentMode) {
            return;
        }

        LAST_GAMEMODE.put(uid, currentMode);
        PacketQueue.push(
            new PacketPlayerChangeMode(timestamp, uid, name, currentMode)
        );
    }

    // ─────────────────────── Effects ────────────────────────────────────

    private static void tickEffects(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        Collection<StatusEffectInstance> effects = player.getStatusEffects();
        List<Effect> currentEffects = new ArrayList<>();

        for (StatusEffectInstance effectInstance : effects) {
            String effectKey = net.minecraft.registry.Registries.STATUS_EFFECT
                .getId(effectInstance.getEffectType().value())
                .getPath();
            byte effectId = (byte) EffectType.fromKey(effectKey).getValue();
            byte amplifier = (byte) effectInstance.getAmplifier();
            int duration = effectInstance.getDuration();
            currentEffects.add(new Effect(effectId, amplifier, duration, EffectFlags.ADD));
        }

        List<Effect> lastEffects = LAST_EFFECTS.getOrDefault(uid, new ArrayList<>());

        if (effectsAreEqual(currentEffects, lastEffects)) {
            return;
        }

        // Find removed effects (in last but not in current)
        for (Effect oldEff : lastEffects) {
            boolean found = false;
            for (Effect newEff : currentEffects) {
                if (oldEff.getEffectId() == newEff.getEffectId()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                PacketQueue.push(
                    new PacketPlayerEffect(timestamp, uid, name, new Effect(oldEff.getEffectId(), oldEff.getAmplifier(), oldEff.getDuration(), EffectFlags.REMOVE))
                );
            }
        }

        // Find added or modified effects
        for (Effect newEff : currentEffects) {
            boolean found = false;
            boolean modified = false;
            for (Effect oldEff : lastEffects) {
                if (oldEff.getEffectId() == newEff.getEffectId()) {
                    found = true;
                    // Because duration ticks down naturally, we only consider it 'modified' if there's a large jump (e.g. effect was renewed). 
                    // A jump of > 5 ticks means it was reassigned or the amplifier changed.
                    if (oldEff.getAmplifier() != newEff.getAmplifier() || Math.abs(oldEff.getDuration() - newEff.getDuration()) > 5) {
                        modified = true;
                    }
                    break;
                }
            }
            if (!found) {
                PacketQueue.push(
                    new PacketPlayerEffect(timestamp, uid, name, new Effect(newEff.getEffectId(), newEff.getAmplifier(), newEff.getDuration(), EffectFlags.ADD))
                );
            } else if (modified) {
                PacketQueue.push(
                    new PacketPlayerEffect(timestamp, uid, name, new Effect(newEff.getEffectId(), newEff.getAmplifier(), newEff.getDuration(), EffectFlags.MODIFY))
                );
            }
        }

        LAST_EFFECTS.put(uid, currentEffects);
    }

    private static boolean effectsAreEqual(List<Effect> e1, List<Effect> e2) {
        if (e1.size() != e2.size()) return false;
        for (Effect eff1 : e1) {
            boolean matched = false;
            for (Effect eff2 : e2) {
                if (eff1.getEffectId() == eff2.getEffectId() && eff1.getAmplifier() == eff2.getAmplifier() && Math.abs(eff1.getDuration() - eff2.getDuration()) <= 5) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    private static void tickEnchantments(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        java.util.List<org.vennv.utils.Enchantment> currentEnchantments = new java.util.ArrayList<>();
        
        // Loop through all equipped items (main hand, off hand, armor)
        for (net.minecraft.item.ItemStack itemStack : equippedItems(player)) {
            if (itemStack.isEmpty() || !itemStack.hasEnchantments()) continue;
            
            net.minecraft.component.type.ItemEnchantmentsComponent enchantmentsComponent = itemStack.getEnchantments();
            java.util.Set<net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>> enchantmentsSet = enchantmentsComponent.getEnchantments();
            for (net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> registryEntry : enchantmentsSet) {
                int level = enchantmentsComponent.getLevel(registryEntry);
                String enchantName = "unknown";
                if (registryEntry.getKey().isPresent()) {
                    enchantName = registryEntry.getKey().get().getValue().getPath();
                }
                currentEnchantments.add(new org.vennv.utils.Enchantment(enchantName, (byte) level));
            }
        }
        
        // Get entity interaction range (new attribute in Minecraft 1.21.2+)
        float entityInteractionRange = 3.0f; // Default vanilla
        if (player.getAttributes().hasAttribute(net.minecraft.entity.attribute.EntityAttributes.ENTITY_INTERACTION_RANGE)) {
            entityInteractionRange = (float) player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ENTITY_INTERACTION_RANGE);
        }

        // Include knockback resistance attribute as an enchantment entry
        if (player.getAttributes().hasAttribute(net.minecraft.entity.attribute.EntityAttributes.KNOCKBACK_RESISTANCE)) {
            double kbResistance = player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.KNOCKBACK_RESISTANCE);
            if (kbResistance > 0.0) {
                currentEnchantments.add(new org.vennv.utils.Enchantment(
                    "generic.knockback_resistance",
                    (byte) Math.round(kbResistance * 10) // 0.1 per Netherite piece → level 1-4
                ));
            }
        }

        // Hash both the enchantments and the reach value 
        int currentHash = currentEnchantments.hashCode() ^ Float.hashCode(entityInteractionRange);

        Integer lastHash = LAST_ENCHANTMENTS_HASH.get(uid);

        if (lastHash == null || !lastHash.equals(currentHash)) {
            PacketQueue.push(
                new org.vennv.packets.PacketPlayerEnchantments(timestamp, uid, name, currentEnchantments, entityInteractionRange)
            );

            LAST_ENCHANTMENTS_HASH.put(uid, currentHash);

            org.vennv.zeusFabric.ZeusFabricMod.LOGGER.debug(
                "[ZeusFabric] {} change enchantments/reach -> hash:{}",
                name,
                currentHash
            );
        }
    }

    // ──────────────── Sneaking / Sprinting / Commands ──────────────────

    private static void tickPlayerCommands(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        // Sneaking
        boolean sneaking = player.isSneaking();
        Boolean wasSneaking = LAST_SNEAKING.get(uid);
        if (wasSneaking == null || wasSneaking != sneaking) {
            LAST_SNEAKING.put(uid, sneaking);
            ServerBoundPlayerCommandActions action = sneaking
                ? ServerBoundPlayerCommandActions.START_SNEAKING
                : ServerBoundPlayerCommandActions.STOP_SNEAKING;
            PacketQueue.push(
                new PacketServerBoundPlayerCommand(timestamp, uid, name, action)
            );
        }

        // Sprinting
        boolean sprinting = player.isSprinting();
        Boolean wasSprinting = LAST_SPRINTING.get(uid);
        if (wasSprinting == null || wasSprinting != sprinting) {
            LAST_SPRINTING.put(uid, sprinting);
            ServerBoundPlayerCommandActions action = sprinting
                ? ServerBoundPlayerCommandActions.START_SPRINTING
                : ServerBoundPlayerCommandActions.STOP_SPRINTING;
            PacketQueue.push(
                new PacketServerBoundPlayerCommand(timestamp, uid, name, action)
            );
        }

        // Elytra flying
        boolean gliding = player.isGliding();
        Boolean wasGliding = LAST_GLIDING.get(uid);
        if (wasGliding == null || wasGliding != gliding) {
            LAST_GLIDING.put(uid, gliding);
            ServerBoundPlayerCommandActions action = gliding
                ? ServerBoundPlayerCommandActions.START_FALL_FLYING
                : ServerBoundPlayerCommandActions.STOP_FALL_FLYING;
            PacketQueue.push(
                new PacketServerBoundPlayerCommand(timestamp, uid, name, action)
            );
        }

        // Riptide
        boolean usingRiptide = player.isUsingRiptide();
        Boolean wasUsingRiptide = LAST_USING_RIPTIDE.get(uid);
        if (wasUsingRiptide == null || wasUsingRiptide != usingRiptide) {
            LAST_USING_RIPTIDE.put(uid, usingRiptide);
            ServerBoundPlayerCommandActions action = usingRiptide
                ? ServerBoundPlayerCommandActions.START_RIPTIDE
                : ServerBoundPlayerCommandActions.STOP_RIPTIDE;
            PacketQueue.push(
                new PacketServerBoundPlayerCommand(timestamp, uid, name, action)
            );
        }

        // Swimming/Crawling pose (Pose.SWIMMING covers both swimming in water and crawling on land)
        boolean swimming = (player.getPose() == net.minecraft.entity.EntityPose.SWIMMING);
        Boolean wasSwimming = LAST_SWIMMING.get(uid);
        if (wasSwimming == null || wasSwimming != swimming) {
            LAST_SWIMMING.put(uid, swimming);
            ServerBoundPlayerCommandActions action = swimming
                ? ServerBoundPlayerCommandActions.START_SWIMMING
                : ServerBoundPlayerCommandActions.STOP_SWIMMING;
            PacketQueue.push(
                new PacketServerBoundPlayerCommand(timestamp, uid, name, action)
            );
        }

        // Sleeping
        if (player.isSleeping()) {
            // Sleeping is tracked but we only care about STOP_SLEEPING
            // which we'll pick up when the player is no longer sleeping.
        }
    }

    // ─────────────────────── Vehicle ────────────────────────────────────

    private static void tickVehicle(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        Entity vehicle = player.getVehicle();
        boolean inVehicle = vehicle != null;
        boolean inBoat = vehicle instanceof net.minecraft.entity.vehicle.BoatEntity;
        Boolean wasInVehicle = LAST_IN_VEHICLE.getOrDefault(uid, false);
        Boolean wasInBoat = LAST_IN_BOAT.getOrDefault(uid, false);

        LAST_IN_VEHICLE.put(uid, inVehicle);
        LAST_IN_BOAT.put(uid, inBoat);

        if (!inVehicle) {
            if (wasInVehicle) {
                PacketQueue.push(
                    new PacketServerBoundPlayerCommand(timestamp, uid, name, ServerBoundPlayerCommandActions.STOP_RIDING_VEHICLE)
                );
            }
            if (wasInBoat) {
                PacketQueue.push(
                    new PacketServerBoundPlayerCommand(timestamp, uid, name, ServerBoundPlayerCommandActions.STOP_RIDING_BOAT)
                );
            }
            LAST_VEHICLE_POS.remove(uid);
            return;
        } else if (!wasInVehicle || wasInBoat != inBoat) {
            if (wasInBoat && !inBoat) {
                PacketQueue.push(
                    new PacketServerBoundPlayerCommand(timestamp, uid, name, ServerBoundPlayerCommandActions.STOP_RIDING_BOAT)
                );
            }
            ServerBoundPlayerCommandActions action = inBoat
                ? ServerBoundPlayerCommandActions.START_RIDING_BOAT
                : ServerBoundPlayerCommandActions.START_RIDING_VEHICLE;
            PacketQueue.push(
                new PacketServerBoundPlayerCommand(timestamp, uid, name, action)
            );
        }

        Vec3d vehiclePos = positionOf(vehicle);
        Vec3d lastVehiclePos = LAST_VEHICLE_POS.get(uid);

        if (
            lastVehiclePos != null &&
            lastVehiclePos.squaredDistanceTo(vehiclePos) > 0.000001
        ) {
            PacketQueue.push(
                new PacketPlayerVehicleMove(
                    timestamp,
                    uid,
                    name,
                    vehiclePos.x,
                    vehiclePos.y,
                    vehiclePos.z,
                    vehicle.getYaw(),
                    vehicle.getPitch()
                )
            );

            // Steer vehicle — derive from movement delta (best-effort)
            double dx = vehiclePos.x - lastVehiclePos.x;
            double dz = vehiclePos.z - lastVehiclePos.z;

            float yawRad = (float) Math.toRadians(player.getYaw());
            float sinYaw = (float) Math.sin(yawRad);
            float cosYaw = (float) Math.cos(yawRad);

            // Project world-space delta onto player-local forward/strafe axes
            float forward = (float) (-dx * sinYaw + dz * cosYaw);
            float sideway = (float) (dx * cosYaw + dz * sinYaw);

            // Clamp to [-1, 1] range
            forward = Math.max(-1f, Math.min(1f, forward * 5f));
            sideway = Math.max(-1f, Math.min(1f, sideway * 5f));

            boolean jump =
                vehicle.isOnGround() && vehiclePos.y > lastVehiclePos.y + 0.1;
            boolean unmount = false;

            PacketQueue.push(
                new PacketPlayerSteerVehicle(
                    timestamp,
                    uid,
                    name,
                    sideway,
                    forward,
                    jump,
                    unmount
                )
            );
        }

        LAST_VEHICLE_POS.put(uid, vehiclePos);
    }

    // ─────────────────── Screen Handler ─────────────────────────────────

    private static void tickScreenHandler(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        int currentHandlerId = player.currentScreenHandler.syncId;
        Integer lastHandlerId = LAST_SCREEN_HANDLER.get(uid);

        if (lastHandlerId == null) {
            // First tick — initialise
            LAST_SCREEN_HANDLER.put(uid, currentHandlerId);
            return;
        }

        if (lastHandlerId != currentHandlerId) {
            // Screen handler changed
            if (currentHandlerId != 0) {
                // Opened a new window (syncId 0 is the player inventory, always open)
                PlayerStateSnapshotService.sendOpenInventorySnapshot(player, true);
            } else {
                // Closed back to player inventory
                byte windowId = (byte) (lastHandlerId & 0xFF);
                PacketQueue.push(
                    new PacketPlayerCloseWindow(timestamp, uid, name, windowId)
                );
            }

            LAST_SCREEN_HANDLER.put(uid, currentHandlerId);
        }

        // Confirm transaction — synthesize for any click in a container
        // This is handled implicitly since modern MC (1.17+) uses state IDs
        // rather than transaction confirmation packets.
    }

    // ──────────────────── Block Ray Trace ───────────────────────────────

    /** Only emit ray-trace packets every 4 ticks to reduce spam. */
    private static int rayTraceCounter = 0;

    private static void tickBlockRayTrace(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        rayTraceCounter++;
        if (rayTraceCounter % 4 != 0) {
            return;
        }

        try {
            Vec3d eyePos = player.getEyePos();
            Vec3d lookVec = player.getRotationVec(1.0f);
            Vec3d endPos = eyePos.add(lookVec.multiply(5.0));

            ServerWorld world = (ServerWorld) MinecraftCompat.entityWorld(player);
            BlockHitResult hitResult = world.raycast(
                new RaycastContext(
                    eyePos,
                    endPos,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    player
                )
            );

            boolean hitBlock = hitResult.getType() == HitResult.Type.BLOCK;
            int blockX = 0,
                blockY = 0,
                blockZ = 0;
            float hitX = 0,
                hitY = 0,
                hitZ = 0;

            if (hitBlock) {
                BlockPos blockPos = hitResult.getBlockPos();
                blockX = blockPos.getX();
                blockY = blockPos.getY();
                blockZ = blockPos.getZ();

                Vec3d hitPos = hitResult.getPos();
                hitX = (float) hitPos.x;
                hitY = (float) hitPos.y;
                hitZ = (float) hitPos.z;
            }

            PacketQueue.push(
                new PacketPlayerBlockRayTrace(
                    timestamp,
                    uid,
                    name,
                    hitBlock,
                    blockX,
                    blockY,
                    blockZ,
                    hitX,
                    hitY,
                    hitZ
                )
            );
        } catch (Exception ignored) {
            // Raycasting can fail if the world/chunk is not loaded
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helper methods
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds an {@link EntityState} from any Minecraft entity.
     */
    private static EntityState buildEntityState(Entity entity) {
        Vec3d pos = positionOf(entity);
        Box box = entity.getBoundingBox();
        float height = (float) (box.maxY - box.minY);
        float width = (float) (box.maxX - box.minX);

        double eyeY = pos.y + (height * 0.85f);
        if (entity instanceof LivingEntity living) {
            eyeY = pos.y + living.getStandingEyeHeight();
        }

        return new EntityState(
            entity.getUuidAsString(),
            pos.x,
            pos.y,
            pos.z,
            pos.x,
            eyeY,
            pos.z,
            entity.getYaw(),
            entity.getPitch(),
            height,
            width,
            entity.isOnGround()
        );
    }

    private static Vec3d positionOf(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    private static ExternalForceType classifyDamageForce(DamageSource source, Entity attacker) {
        String name = source.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("wind_charge") || name.contains("windcharge")) {
            return ExternalForceType.WIND_CHARGE;
        }
        if (name.contains("explosion")) {
            return ExternalForceType.EXPLOSION;
        }
        if (name.equals("arrow") || name.equals("trident") || name.equals("fireball")) {
            return ExternalForceType.PROJECTILE;
        }
        if (attacker instanceof ServerPlayerEntity) {
            return ExternalForceType.PLAYER_ATTACK;
        }
        if (attacker != null) {
            return ExternalForceType.ENTITY_ATTACK;
        }
        return ExternalForceType.GENERIC;
    }

    private static void emitExternalForce(
        ServerPlayerEntity player,
        ExternalForceType type,
        Vec3d source,
        Vec3d direction,
        Vec3d velocity,
        double strength,
        short durationTicks,
        int flags
    ) {
        Vec3d sourcePos = source == null ? positionOf(player) : source;
        Vec3d dir = direction == null ? Vec3d.ZERO : direction;
        if (dir.lengthSquared() < 1.0e-9) {
            dir = positionOf(player).subtract(sourcePos);
        }
        if (dir.lengthSquared() >= 1.0e-9) {
            dir = dir.normalize();
        }
        Vec3d vel = velocity == null ? Vec3d.ZERO : velocity;
        PacketQueue.push(new PacketPlayerExternalForce(
            System.currentTimeMillis(),
            player.getUuidAsString(),
            player.getName().getString(),
            type,
            sourcePos.x,
            sourcePos.y,
            sourcePos.z,
            dir.x,
            dir.y,
            dir.z,
            vel.x,
            vel.y,
            vel.z,
            strength,
            durationTicks,
            flags
        ));
    }

    private static void clearTracking(String uid) {
        LAST_HELD_HASH.remove(uid);
        LAST_ARMOR_HASH.remove(uid);
        LAST_GAMEMODE.remove(uid);
        LAST_EFFECTS.remove(uid);
        LAST_ENCHANTMENTS_HASH.remove(uid);
        LAST_POSITION.remove(uid);
        LAST_SNEAKING.remove(uid);
        LAST_SPRINTING.remove(uid);
        LAST_IN_VEHICLE.remove(uid);
        LAST_IN_BOAT.remove(uid);
        LAST_VEHICLE_POS.remove(uid);
        LAST_SCREEN_HANDLER.remove(uid);
        LAST_GLIDING.remove(uid);
        LAST_USING_RIPTIDE.remove(uid);
        LAST_SWIMMING.remove(uid);
        LAST_VELOCITY.remove(uid);
        LAST_PISTON_SIGNATURE.remove(uid);
        LAST_WAITING_STATE.remove(uid);
        KA_SENT_TIME.remove(uid);
        MEASURED_RTT.remove(uid);
    }

    private static List<net.minecraft.item.ItemStack> equippedItems(ServerPlayerEntity player) {
        return List.of(
            player.getMainHandStack(),
            player.getOffHandStack(),
            player.getEquippedStack(EquipmentSlot.HEAD),
            player.getEquippedStack(EquipmentSlot.CHEST),
            player.getEquippedStack(EquipmentSlot.LEGS),
            player.getEquippedStack(EquipmentSlot.FEET)
        );
    }

    /**
     * Builds a Zeus {@link Item} from a Minecraft {@link ItemStack}.
     */
    private static Item buildItem(ItemStack stack) {
        return ItemUtil.item(stack);
    }

    /**
     * Builds a Zeus {@link Armor} from a Minecraft armor {@link ItemStack}.
     *
     * @return the Armor object, or {@code null} if the slot is empty.
     */
    private static Armor buildArmor(ItemStack stack) {
        return ItemUtil.armor(stack);
    }

    /**
     * Computes a hash of the player's current armor for change detection.
     */
    private static int computeArmorHash(ServerPlayerEntity player) {
        int hash = 17;
        for (EquipmentSlot slot : new EquipmentSlot[] {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
        }) {
            ItemStack stack = player.getEquippedStack(slot);
            hash =
                31 * hash + (stack.isEmpty() ? 0 : ItemStack.hashCode(stack));
        }
        return hash;
    }

    /**
     * Gets the surrounding blocks around a player (3x5x3 cube centred on the
     * player's feet), matching the Bukkit implementation in ZeusGateway.
     */
    private static List<RelativeBlock> getSurroundingBlocks(
        ServerPlayerEntity player
    ) {
        List<RelativeBlock> blocks = new ArrayList<>();

        BlockPos basePos = player.getBlockPos();
        World world = MinecraftCompat.entityWorld(player);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos bp = basePos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(bp);
                    blocks.add(new RelativeBlock(dx, dy, dz, state.toString()));
                }
            }
        }

        return blocks;
    }

    /**
     * Maps a Minecraft {@link Direction} to the protocol face byte.
     * 0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST
     */
    private static byte mapDirection(Direction direction) {
        if (direction == null) {
            return 0;
        }
        return switch (direction) {
            case DOWN -> (byte) 0;
            case UP -> (byte) 1;
            case NORTH -> (byte) 2;
            case SOUTH -> (byte) 3;
            case WEST -> (byte) 4;
            case EAST -> (byte) 5;
        };
    }

    /**
     * Maps a Minecraft {@link DamageSource} to the Zeus {@link DamageCause}.
     */
    private static DamageCause mapDamageSource(DamageSource source) {
        String name = source.getName();
        return switch (name) {
            case "inWall" -> DamageCause.SUFFOCATION;
            case "fall" -> DamageCause.FALL;
            case "inFire", "onFire" -> DamageCause.FIRE;
            case "lava" -> DamageCause.LAVA;
            case "drown" -> DamageCause.DROWNING;
            case
                "explosion",
                "explosion.player" -> DamageCause.ENTITY_EXPLOSION;
            case "outOfWorld" -> DamageCause.VOID;
            case "magic", "indirectMagic" -> DamageCause.MAGIC;
            case "wither" -> DamageCause.MAGIC;
            case "starve" -> DamageCause.STARVATION;
            case "anvil", "fallingBlock" -> DamageCause.FALLING_BLOCK;
            case "mob", "player" -> DamageCause.ENTITY_ATTACK;
            case "arrow", "trident", "fireball" -> DamageCause.PROJECTILE;
            case
                "cactus",
                "sweetBerryBush",
                "stalagmite" -> DamageCause.CONTACT;
            case "fireTick" -> DamageCause.FIRE_TICK;
            default -> {
                if (source.getAttacker() != null) {
                    yield DamageCause.ENTITY_ATTACK;
                }
                yield DamageCause.CUSTOM;
            }
        };
    }
}
