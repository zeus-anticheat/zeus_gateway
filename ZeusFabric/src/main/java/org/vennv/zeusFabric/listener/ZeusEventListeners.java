package org.vennv.zeusFabric.listener;

import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vennv.Effect;
import org.vennv.EntityState;
import org.vennv.packets.*;
import org.vennv.utils.*;
import org.vennv.zeusFabric.mixins.ServerCommonNetworkHandlerAccessor;
import org.vennv.zeusFabric.provider.PacketQueue;
import org.vennv.zeusFabric.provider.PollingPolicy;
import org.vennv.zeusFabric.task.PlayerStateSnapshotService;
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
 *   <li>PacketPlayerHeldItem</li>
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
    private static final AuthoritativeTeleportDedupe TELEPORT_DEDUPE = new AuthoritativeTeleportDedupe();

    private ZeusEventListeners() {}

    // ─────────────────────────────────────────────────────────────────────
    //  Tracking maps / caches for detecting state changes per-player
    // ─────────────────────────────────────────────────────────────────────

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
            PlayerStateSnapshotService.remove(uid);
            PlayerStateSnapshotService.clear(uid);
            TELEPORT_DEDUPE.remove(uid);
            clearTracking(uid);

            LOGGER.debug("[ZeusFabric] Player left: {}", name);
        });
    }

    // ─────────────────────── World Change ────────────────────────────────
    
    private static void registerWorldChange() {
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
            authoritativeTeleport(player, AuthoritativeTeleportDedupe.Source.WORLD_CHANGE, player.age));
    }

    public static void authoritativeTeleport(
            ServerPlayerEntity player, double destX, double destY, double destZ, long lifecycleKey) {
        authoritativeTeleport(player, destX, destY, destZ,
                AuthoritativeTeleportDedupe.Source.OUTBOUND, lifecycleKey);
    }

    private static void authoritativeTeleport(
            ServerPlayerEntity player,
            AuthoritativeTeleportDedupe.Source source,
            long lifecycleKey) {
        authoritativeTeleport(player, player.getX(), player.getY(), player.getZ(), source, lifecycleKey);
    }

    private static void authoritativeTeleport(
            ServerPlayerEntity player,
            double destX,
            double destY,
            double destZ,
            AuthoritativeTeleportDedupe.Source source,
            long lifecycleKey) {
        World world = MinecraftCompat.entityWorld(player);
        if (world == null) {
            return;
        }
        if (!TELEPORT_DEDUPE.shouldEmit(
                player.getUuidAsString(),
                world.getRegistryKey().getValue().toString(),
                destX,
                destY,
                destZ,
                player.age,
                source,
                lifecycleKey)) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        String uid = player.getUuidAsString();
        String name = player.getName().getString();
        // An outbound PLAYER_POSITION_AND_LOOK only becomes authoritative once the
        // client echoes it back, so it is tagged for the engine's pending-teleport
        // queue. A world change is already applied server-side.
        PacketQueue.push(source == AuthoritativeTeleportDedupe.Source.OUTBOUND
                ? PacketPlayerTeleport.outbound(
                        timestamp, uid, name, destX, destY, destZ, (int) lifecycleKey)
                : new PacketPlayerTeleport(timestamp, uid, name, destX, destY, destZ));
        PlayerStateSnapshotService.invalidate(player);
        PlayerStateSnapshotService.sendResyncSnapshot(player);
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
        // Raw START/STOP/ABORT and arm animation packets are captured by
        // ServerPlayNetworkHandler mixins. Callback emission would duplicate them.
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
                    (float) hitPos.z,
                    PacketPlayerBlockRayTrace.ACTION_INTERACT
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

                PacketQueue.push(new PacketBlockChangeEvent(
                    timestamp, uid, name,
                    pos.getX(), pos.getY(), pos.getZ(),
                    "minecraft:air", PacketBlockChangeEvent.ACTION_BREAK
                ));
                PacketQueue.push(
                    new PacketPlayerBlockChangeAck(timestamp, uid, name)
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
                PlayerStateSnapshotService.invalidate(newPlayer);
                PlayerStateSnapshotService.sendResyncSnapshot(newPlayer);
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

        // ── Game Mode ──
        tickGameMode(player, uid, name, timestamp);

        // ── Potion Effects ──
        tickEffects(player, uid, name, timestamp);

        // ── Vehicle ──
        tickVehicle(player, uid, name, timestamp);

        // ── Screen handler (Open/Close Window) ──
        tickScreenHandler(player, uid, name, timestamp);
    }

    // ─────────────────────── Keep Alive ─────────────────────────────────

    private static void tickKeepAlive(
        ServerPlayerEntity player,
        String uid,
        String name,
        long timestamp
    ) {
        ServerCommonNetworkHandlerAccessor handler =
            (ServerCommonNetworkHandlerAccessor) player.networkHandler;
        boolean waiting = handler.zeus$isWaitingForKeepAlive();
        long sentTime = handler.zeus$getLastKeepAliveTime();
        Boolean wasWaiting = LAST_WAITING_STATE.get(uid);
        if (waiting && (wasWaiting == null || !wasWaiting)) {
            KA_SENT_TIME.put(uid, sentTime);
        } else if (!waiting && Boolean.TRUE.equals(wasWaiting)) {
            Long recordedSentTime = KA_SENT_TIME.get(uid);
            if (recordedSentTime != null) {
                long measuredRtt = Util.getMeasuringTimeMs() - recordedSentTime;
                if (measuredRtt >= 0 && measuredRtt < 30_000) {
                    MEASURED_RTT.put(uid, measuredRtt);
                }
            }
        }
        LAST_WAITING_STATE.put(uid, waiting);

        // ── Send KeepAlive packet every 20 ticks (1 second) ──
        if (!PollingPolicy.shouldSendKeepAlive(player.age)) {
            return;
        }

        int serverPing = player.networkHandler.getLatency();
        long measuredPing = MEASURED_RTT.getOrDefault(uid, (long) serverPing);

        // Send the independently measured RTT (or server ping as fallback)
        PacketQueue.push(new PacketPlayerKeepAlive(timestamp, uid, name, measuredPing));
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
            PlayerStateSnapshotService.onMovement(player, vehiclePos.x, vehiclePos.y, vehiclePos.z);
            PacketQueue.push(
                new PacketPlayerVehicleMove(
                    timestamp,
                    uid,
                    name,
                    vehiclePos.x,
                    vehiclePos.y,
                    vehiclePos.z,
                    vehicle.getYaw(),
                    vehicle.getPitch(),
                    Registries.ENTITY_TYPE.getId(vehicle.getType()).toString(),
                    vehicle.getId(),
                    vehicleFlags(vehicle),
                    horseMovementSpeed(vehicle),
                    horseJumpStrength(vehicle),
                    horseSaddleKnown(vehicle),
                    horseSaddled(vehicle)
                )
            );
        }

        LAST_VEHICLE_POS.put(uid, vehiclePos);
    }

    private static int vehicleFlags(Entity vehicle) {
        int flags = PacketPlayerVehicleMove.FLAG_MOUNTED;
        if (vehicle.isTouchingWater()) flags |= PacketPlayerVehicleMove.FLAG_IN_WATER;
        if (vehicle.isOnGround()) flags |= PacketPlayerVehicleMove.FLAG_ON_GROUND;
        return flags;
    }

    private static Float horseMovementSpeed(Entity vehicle) {
        if (!(vehicle instanceof AbstractHorseEntity horse)) return null;
        double value = horse.getAttributeValue(EntityAttributes.MOVEMENT_SPEED);
        return Double.isFinite(value) && value > 0.0 && value <= 1024.0 ? (float) value : null;
    }

    private static Double horseJumpStrength(Entity vehicle) {
        if (!(vehicle instanceof AbstractHorseEntity horse)) return null;
        double value = horse.getAttributeValue(EntityAttributes.JUMP_STRENGTH);
        return Double.isFinite(value) && value >= 0.0 && value <= 32.0 ? value : null;
    }

    private static boolean horseSaddleKnown(Entity vehicle) {
        return vehicle instanceof AbstractHorseEntity;
    }

    private static boolean horseSaddled(Entity vehicle) {
        return MinecraftCompat.isHorseSaddled(vehicle);
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
        LAST_GAMEMODE.remove(uid);
        LAST_EFFECTS.remove(uid);
        LAST_IN_VEHICLE.remove(uid);
        LAST_IN_BOAT.remove(uid);
        LAST_VEHICLE_POS.remove(uid);
        LAST_SCREEN_HANDLER.remove(uid);
        LAST_WAITING_STATE.remove(uid);
        KA_SENT_TIME.remove(uid);
        MEASURED_RTT.remove(uid);
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
