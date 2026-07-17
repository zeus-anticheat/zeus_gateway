package org.vennv.zeusFabric.task;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.vennv.Effect;
import org.vennv.packets.PacketCollisionWindow;
import org.vennv.packets.PacketCollisionWindow.Cell;
import org.vennv.packets.PacketCollisionWindow.CellUpdate;
import org.vennv.packets.PacketCollisionWindow.CollisionWindowUpdate;
import org.vennv.packets.PacketPlayerArmorsEquipment;
import org.vennv.packets.PacketPlayerChangeMode;
import org.vennv.packets.PacketPlayerEffect;
import org.vennv.packets.PacketPlayerEnchantments;
import org.vennv.packets.PacketPlayerHeldItem;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.packets.PacketPlayerJoin;
import org.vennv.packets.PacketPlayerOpenWindow;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.packets.PacketServerConfig;
import org.vennv.packets.PacketUpdateAttributes;
import org.vennv.utils.Armors;
import org.vennv.utils.EffectFlags;
import org.vennv.utils.EffectType;
import org.vennv.utils.Enchantment;
import org.vennv.utils.Item;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusFabric.ServerCombatSettings;
import org.vennv.zeusFabric.provider.PacketQueue;
import org.vennv.zeusFabric.utils.BlockUtil;
import org.vennv.zeusFabric.utils.ItemUtil;
import org.vennv.zeusFabric.utils.MinecraftCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Emits the current Fabric player state as Zeus protocol packets.
 *
 * Join, late-start recovery and periodic resync use this class so those paths
 * stay behaviorally aligned with ZeusGateway.
 */
public final class PlayerStateSnapshotService {
    private static final Map<String, String> HELD_HASH = new ConcurrentHashMap<>();
    private static final Map<String, String> ARMOR_HASH = new ConcurrentHashMap<>();
    private static final Map<String, String> ENCHANT_HASH = new ConcurrentHashMap<>();
    private static final Map<String, String> INVENTORY_HASH = new ConcurrentHashMap<>();
    private static final Object COLLISION_LOCK = new Object();
    private static final Map<String, ChunkSnapshotSemantics.State> COLLISION_STATES = new HashMap<>();
    private static final CollisionGenerationAllocator COLLISION_GENERATIONS =
            CollisionGenerationAllocator.persistent();

    private PlayerStateSnapshotService() {}

    public static void sendFullSnapshot(ServerPlayerEntity player) {
        invalidate(player);
        sendSnapshot(player, true, PacketPlayerPosition.SOURCE_SNAPSHOT);
    }

    public static void sendResyncSnapshot(ServerPlayerEntity player) {
        clearMutableState(player.getUuidAsString());
        sendSnapshot(player, true, PacketPlayerPosition.SOURCE_RESYNC);
    }

    public static void sendMutableStateSnapshot(ServerPlayerEntity player) {
        long timestamp = System.currentTimeMillis();
        String uid = player.getUuidAsString();
        String name = player.getName().getString();

        PacketQueue.push(serverConfig(timestamp, uid, name, player));
        sendCommands(timestamp, uid, name, player);
        sendHeldItem(timestamp, uid, name, player, false);
        sendArmor(timestamp, uid, name, player, false);
        sendEnchantments(timestamp, uid, name, player, false);
    }

    public static void sendCommandStateSnapshot(ServerPlayerEntity player) {
        long timestamp = System.currentTimeMillis();
        sendCommands(timestamp, player.getUuidAsString(), player.getName().getString(), player);
    }

    public static void sendHeldItemSnapshot(ServerPlayerEntity player, boolean force) {
        long timestamp = System.currentTimeMillis();
        sendHeldItem(timestamp, player.getUuidAsString(), player.getName().getString(), player, force);
    }

    public static void sendArmorSnapshot(ServerPlayerEntity player, boolean force) {
        long timestamp = System.currentTimeMillis();
        sendArmor(timestamp, player.getUuidAsString(), player.getName().getString(), player, force);
    }

    public static void sendEnchantmentsSnapshot(ServerPlayerEntity player, boolean force) {
        long timestamp = System.currentTimeMillis();
        sendEnchantments(timestamp, player.getUuidAsString(), player.getName().getString(), player, force);
    }

    public static void sendInventoryDetailSnapshot(
            ServerPlayerEntity player,
            byte windowId,
            int stateId,
            short clickedSlot,
            byte button,
            short mode,
            short transactionId,
            boolean force) {
        long timestamp = System.currentTimeMillis();
        sendInventoryDetailSnapshot(
                timestamp,
                player.getUuidAsString(),
                player.getName().getString(),
                player,
                windowId,
                stateId,
                clickedSlot,
                button,
                mode,
                transactionId,
                false,
                force);
    }

    public static void sendOpenInventorySnapshot(ServerPlayerEntity player, boolean force) {
        long timestamp = System.currentTimeMillis();
        sendOpenInventorySnapshot(timestamp, player.getUuidAsString(), player.getName().getString(), player, force);
    }

    public static void clear(ServerPlayerEntity player) {
        clear(player.getUuidAsString());
    }

    public static void clear(String uid) {
        clearMutableState(uid);
        remove(uid);
    }

    private static void clearMutableState(String uid) {
        HELD_HASH.remove(uid);
        ARMOR_HASH.remove(uid);
        ENCHANT_HASH.remove(uid);
        INVENTORY_HASH.remove(uid);
    }

    public static void invalidate(ServerPlayerEntity player) {
        invalidate(player.getUuidAsString());
    }

    public static void invalidate(String uid) {
        synchronized (COLLISION_LOCK) {
            COLLISION_STATES.put(uid, ChunkSnapshotSemantics.State.empty(nextCollisionGeneration()));
            PacketQueue.removeCollisionWindows(uid);
        }
    }

    public static void remove(ServerPlayerEntity player) {
        remove(player.getUuidAsString());
    }

    public static void remove(String uid) {
        synchronized (COLLISION_LOCK) {
            COLLISION_STATES.remove(uid);
            PacketQueue.removeCollisionWindows(uid);
        }
    }

    public static void clearAll() {
        synchronized (COLLISION_LOCK) {
            for (String uid : COLLISION_STATES.keySet()) {
                PacketQueue.removeCollisionWindows(uid);
            }
            COLLISION_STATES.clear();
        }
    }

    public static boolean contains(
            ServerPlayerEntity player,
            int x,
            int y,
            int z) {
        World world = MinecraftCompat.entityWorld(player);
        return world != null && contains(player.getUuidAsString(), worldIdentity(world), x, y, z);
    }

    public static boolean contains(
            String uid,
            String worldIdentity,
            int x,
            int y,
            int z) {
        synchronized (COLLISION_LOCK) {
            ChunkSnapshotSemantics.State state = COLLISION_STATES.get(uid);
            return state != null
                    && state.committed()
                    && worldIdentity.equals(state.worldIdentity())
                    && ChunkSnapshotSemantics.contains(state.center(), x, y, z);
        }
    }

    public static void onMovement(ServerPlayerEntity player, double x, double y, double z) {
        sendCollisionWindow(player, ChunkSnapshotSemantics.Center.floor(x, y, z), false);
    }

    private static void sendSnapshot(ServerPlayerEntity player, boolean forceStableState, byte positionSource) {
        long timestamp = System.currentTimeMillis();
        String uid = player.getUuidAsString();
        String name = player.getName().getString();

        PacketQueue.push(new PacketPlayerJoin(timestamp, uid, name));
        PacketQueue.push(serverConfig(timestamp, uid, name, player));
        PacketQueue.push(new PacketPlayerChangeMode(
                timestamp,
                uid,
                name,
                MinecraftCompat.gameModeIndex(player)));

        sendPositionAndBlocks(timestamp, uid, name, player, positionSource);
        sendMovementAttributes(timestamp, uid, name, player);
        sendHeldItem(timestamp, uid, name, player, forceStableState);
        sendArmor(timestamp, uid, name, player, forceStableState);
        sendEnchantments(timestamp, uid, name, player, forceStableState);
        sendEffects(timestamp, uid, name, player);
        sendCommands(timestamp, uid, name, player);
        sendOpenInventorySnapshot(timestamp, uid, name, player, forceStableState);
    }

    private static PacketServerConfig serverConfig(
            long timestamp,
            String uid,
            String name,
            ServerPlayerEntity player) {
        float reach = ServerCombatSettings.getServerReach();
        float cooldown = ServerCombatSettings.getAttackCooldownTicks();

        if (player.getAttributes().hasAttribute(EntityAttributes.ATTACK_SPEED)) {
            double attackSpeed = player.getAttributeValue(EntityAttributes.ATTACK_SPEED);
            if (attackSpeed > 0.0) {
                cooldown = (float) (20.0 / attackSpeed);
            }
        }

        if (player.getAttributes().hasAttribute(EntityAttributes.ENTITY_INTERACTION_RANGE)) {
            double interactionRange = player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE);
            if (interactionRange > 0.0) {
                reach = (float) interactionRange;
            }
        }

        float movementSpeed = 0.1f;
        if (player.getAttributes().hasAttribute(EntityAttributes.MOVEMENT_SPEED)) {
            double value = player.getAttributeBaseValue(EntityAttributes.MOVEMENT_SPEED);
            if (Double.isFinite(value) && value > 0.0) {
                movementSpeed = (float) value;
            }
        }

        int protocol = MinecraftCompat.serverProtocol(
                org.vennv.zeusFabric.ZeusFabricMod.getServer().getVersion());
        return new PacketServerConfig(
                timestamp,
                uid,
                name,
                reach,
                cooldown,
                ServerCombatSettings.getMaxCps(),
                movementSpeed,
                protocol,
                org.vennv.zeusFabric.ZeusFabricMod.getServer().getVersion(),
                "fabric",
                "fabric",
                System.getProperty("zeus.physics.fingerprint", "vanilla"),
                protocol,
                org.vennv.zeusFabric.ZeusFabricMod.getServer().getVersion(),
                System.getenv().getOrDefault("ZEUS_TRANSLATION_BEHAVIOR_FINGERPRINT", ""),
                "fabric");
    }

    private static void sendPositionAndBlocks(
            long timestamp,
            String uid,
            String name,
            ServerPlayerEntity player,
            byte positionSource) {
        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d eye = player.getEyePos();
        boolean onGround = BlockUtil.isOnGround(player, pos);

        PacketQueue.push(new PacketPlayerPosition(
                timestamp,
                uid,
                name,
                false,
                pos.x,
                pos.y,
                pos.z,
                eye.x,
                eye.y,
                eye.z,
                player.getYaw(),
                player.getPitch(),
                player.getHeight(),
                onGround,
                positionSource));

        sendCollisionWindow(player, ChunkSnapshotSemantics.Center.floor(pos.x, pos.y, pos.z), true);
    }

    private static boolean sendCollisionWindow(
            ServerPlayerEntity player,
            ChunkSnapshotSemantics.Center center,
            boolean forceFull) {
        World world = MinecraftCompat.entityWorld(player);
        if (world == null) {
            return false;
        }
        String uid = player.getUuidAsString();
        String name = player.getName().getString();
        String worldIdentity = worldIdentity(world);
        ChunkSnapshotSemantics.State previous;
        long generationAtSample;
        long sequenceAtSample;
        boolean full;
        List<Integer> sampleIndices;
        ChunkSnapshotSemantics.Center previousCenter;
        List<Cell> previousCells = null;
        synchronized (COLLISION_LOCK) {
            previous = COLLISION_STATES.get(uid);
            if (previous == null) {
                previous = ChunkSnapshotSemantics.State.empty(nextCollisionGeneration());
                COLLISION_STATES.put(uid, previous);
            } else if (previous.worldIdentity() != null
                    && !worldIdentity.equals(previous.worldIdentity())) {
                previous = ChunkSnapshotSemantics.State.empty(nextCollisionGeneration());
                COLLISION_STATES.put(uid, previous);
                PacketQueue.removeCollisionWindows(uid);
            }
            if (!forceFull
                    && previous.committed()
                    && center.equals(previous.center())) {
                return false;
            }
            full = forceFull
                    || !previous.committed()
                    || !ChunkSnapshotSemantics.overlaps(previous.center(), center);
            sampleIndices = full
                    ? ChunkSnapshotSemantics.allIndices()
                    : ChunkSnapshotSemantics.entering(previous.center(), center);
            previousCenter = previous.center();
            generationAtSample = previous.generation();
            sequenceAtSample = previous.sequence();
            if (!full && previous.committed()) {
                previousCells = previous.cells();
            }
        }
        List<Cell> cells = new ArrayList<>(full
                ? ChunkSnapshotSemantics.unknownCells()
                : ChunkSnapshotSemantics.reuse(previousCenter, previousCells, center));
        sampleCells(world, center, sampleIndices, cells);

        synchronized (COLLISION_LOCK) {
            ChunkSnapshotSemantics.State current = COLLISION_STATES.get(uid);
            if (current == null || current.generation() != generationAtSample
                    || current.sequence() != sequenceAtSample) {
                return false;
            }
            long sequence = Math.incrementExact(previous.sequence());
            CollisionWindowUpdate update;
            if (full) {
                update = CollisionWindowUpdate.full(
                        previous.generation(),
                        sequence,
                        center.x(),
                        center.y(),
                        center.z(),
                        cells);
            } else {
                List<CellUpdate> updates = new ArrayList<>(sampleIndices.size());
                for (int index : sampleIndices) {
                    updates.add(new CellUpdate(index, cells.get(index)));
                }
                update = CollisionWindowUpdate.delta(
                        previous.generation(),
                        sequence,
                        previous.sequence(),
                        previous.center().x(),
                        previous.center().y(),
                        previous.center().z(),
                        center.x(),
                        center.y(),
                        center.z(),
                        updates);
            }

            List<PacketCollisionWindow> fragments;
            try {
                fragments = update.toFragments(System.currentTimeMillis(), uid, name);
                for (PacketCollisionWindow fragment : fragments) {
                    if (fragment.encodedDatagramLength() > PacketCollisionWindow.MAX_DATAGRAM_LENGTH) {
                        return false;
                    }
                }
            } catch (IllegalArgumentException | IOException exception) {
                return false;
            }
            if (!PacketQueue.pushCollisionWindow(
                    uid, previous.generation(), sequence, fragments)) {
                return false;
            }
            COLLISION_STATES.put(uid, new ChunkSnapshotSemantics.State(
                    previous.generation(), sequence, worldIdentity, center, cells));
            return true;
        }
    }

    private static void sampleCells(
            World world,
            ChunkSnapshotSemantics.Center center,
            List<Integer> indices,
            List<Cell> cells) {
        int minY = world.getBottomY();
        int maxY = Math.addExact(minY, world.getHeight());
        for (int index : indices) {
            int[] position = ChunkSnapshotSemantics.position(center, index);
            int x = position[0];
            int y = position[1];
            int z = position[2];
            if (y < minY || y >= maxY || !world.isChunkLoaded(x >> 4, z >> 4)) {
                cells.set(index, Cell.unknown());
                continue;
            }
            try {
                var state = world.getBlockState(new BlockPos(x, y, z));
                cells.set(index, state.isAir()
                        ? Cell.knownAir()
                        : Cell.knownBlock(state.toString()));
            } catch (RuntimeException exception) {
                cells.set(index, Cell.unknown());
            }
        }
    }

    private static String worldIdentity(World world) {
        return world.getRegistryKey().getValue().toString();
    }

    private static long nextCollisionGeneration() {
        return COLLISION_GENERATIONS.next();
    }

    public static void sendMovementAttributes(ServerPlayerEntity player) {
        sendMovementAttributes(
                System.currentTimeMillis(),
                player.getUuidAsString(),
                player.getName().getString(),
                player);
    }

    private static void sendMovementAttributes(
            long timestamp,
            String uid,
            String name,
            ServerPlayerEntity player) {
        double movementSpeed = player.getAttributeBaseValue(EntityAttributes.MOVEMENT_SPEED);
        if (Double.isFinite(movementSpeed) && movementSpeed > 0.0) {
            PacketQueue.push(new PacketUpdateAttributes(timestamp, uid, name, (float) movementSpeed));
        }
    }

    private static void sendHeldItem(
            long timestamp,
            String uid,
            String name,
            ServerPlayerEntity player,
            boolean force) {
        Item item = ItemUtil.item(MinecraftCompat.selectedStack(player));
        String hash = item.toString();
        if (!force && hash.equals(HELD_HASH.get(uid))) {
            return;
        }
        HELD_HASH.put(uid, hash);
        PacketQueue.push(new PacketPlayerHeldItem(timestamp, uid, name, item));
    }

    private static void sendArmor(
            long timestamp,
            String uid,
            String name,
            ServerPlayerEntity player,
            boolean force) {
        Armors armors = currentArmors(player);
        String hash = armors.toString();
        if (!force && hash.equals(ARMOR_HASH.get(uid))) {
            return;
        }
        ARMOR_HASH.put(uid, hash);
        PacketQueue.push(new PacketPlayerArmorsEquipment(timestamp, uid, name, armors));
    }

    private static void sendEnchantments(
            long timestamp,
            String uid,
            String name,
            ServerPlayerEntity player,
            boolean force) {
        float range = interactionRange(player);
        List<Enchantment> enchantments = currentEnchantments(player, range);
        String hash = range + ":" + enchantments.stream()
                .map(enchantment -> enchantment.getName() + "=" + Byte.toUnsignedInt(enchantment.getLevel()))
                .collect(Collectors.joining(","));
        if (!force && hash.equals(ENCHANT_HASH.get(uid))) {
            return;
        }
        ENCHANT_HASH.put(uid, hash);
        PacketQueue.push(new PacketPlayerEnchantments(timestamp, uid, name, enchantments, range));
    }

    private static void sendEffects(long timestamp, String uid, String name, ServerPlayerEntity player) {
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            PacketQueue.push(new PacketPlayerEffect(
                    timestamp,
                    uid,
                    name,
                    new Effect(effectId(effect), (byte) effect.getAmplifier(), effect.getDuration(), EffectFlags.ADD)));
        }
    }

    private static void sendCommands(long timestamp, String uid, String name, ServerPlayerEntity player) {
        sendCommand(timestamp, uid, name,
                player.isSprinting()
                        ? ServerBoundPlayerCommandActions.START_SPRINTING
                        : ServerBoundPlayerCommandActions.STOP_SPRINTING);
        sendCommand(timestamp, uid, name,
                player.isSneaking()
                        ? ServerBoundPlayerCommandActions.START_SNEAKING
                        : ServerBoundPlayerCommandActions.STOP_SNEAKING);
        sendCommand(timestamp, uid, name,
                isSwimming(player)
                        ? ServerBoundPlayerCommandActions.START_SWIMMING
                        : ServerBoundPlayerCommandActions.STOP_SWIMMING);
        sendCommand(timestamp, uid, name,
                player.isGliding()
                        ? ServerBoundPlayerCommandActions.START_FALL_FLYING
                        : ServerBoundPlayerCommandActions.STOP_FALL_FLYING);

        Entity vehicle = player.getVehicle();
        boolean riding = vehicle != null;
        boolean boat = vehicle instanceof BoatEntity;
        sendCommand(timestamp, uid, name,
                riding
                        ? ServerBoundPlayerCommandActions.START_RIDING_VEHICLE
                        : ServerBoundPlayerCommandActions.STOP_RIDING_VEHICLE);
        sendCommand(timestamp, uid, name,
                boat
                        ? ServerBoundPlayerCommandActions.START_RIDING_BOAT
                        : ServerBoundPlayerCommandActions.STOP_RIDING_BOAT);
    }

    private static void sendCommand(
            long timestamp,
            String uid,
            String name,
            ServerBoundPlayerCommandActions action) {
        PacketQueue.push(new PacketServerBoundPlayerCommand(timestamp, uid, name, action));
    }

    private static void sendOpenInventorySnapshot(
            long timestamp,
            String uid,
            String name,
            ServerPlayerEntity player,
            boolean force) {
        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null || handler.syncId == 0) {
            return;
        }

        byte windowId = (byte) (handler.syncId & 0xFF);
        sendInventoryDetailSnapshot(
                timestamp,
                uid,
                name,
                player,
                windowId,
                handler.getRevision(),
                (short) -1,
                (byte) 0,
                (short) 0,
                (short) 0,
                true,
                force);
    }

    private static void sendInventoryDetailSnapshot(
            long timestamp,
            String uid,
            String name,
            ServerPlayerEntity player,
            byte windowId,
            int stateId,
            short clickedSlot,
            byte button,
            short mode,
            short transactionId,
            boolean includeOpenWindow,
            boolean force) {
        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null) {
            return;
        }

        List<PacketPlayerInventoryTransaction.ChangedSlot> changedSlots = new ArrayList<>();
        for (int slot = 0; slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.slots.get(slot).getStack();
            changedSlots.add(new PacketPlayerInventoryTransaction.ChangedSlot(
                    (short) slot,
                    ItemUtil.protocolStack(stack)));
        }

        org.vennv.utils.ItemStack cursor = ItemUtil.protocolStack(handler.getCursorStack());
        String hash = Byte.toUnsignedInt(windowId) + ":" + cursor + ":" + changedSlots;
        if (!force && hash.equals(INVENTORY_HASH.get(uid))) {
            return;
        }
        INVENTORY_HASH.put(uid, hash);

        if (includeOpenWindow) {
            PacketQueue.push(new PacketPlayerOpenWindow(timestamp, uid, name, windowId));
        }
        PacketQueue.push(new PacketPlayerInventoryTransaction(
                timestamp,
                uid,
                name,
                windowId,
                stateId,
                clickedSlot,
                button,
                mode,
                transactionId,
                cursor,
                changedSlots));
    }

    private static Armors currentArmors(ServerPlayerEntity player) {
        return new Armors(
                ItemUtil.armor(player.getEquippedStack(EquipmentSlot.HEAD)),
                ItemUtil.armor(player.getEquippedStack(EquipmentSlot.CHEST)),
                ItemUtil.armor(player.getEquippedStack(EquipmentSlot.LEGS)),
                ItemUtil.armor(player.getEquippedStack(EquipmentSlot.FEET)));
    }

    private static List<Enchantment> currentEnchantments(ServerPlayerEntity player, float range) {
        List<Enchantment> enchantments = new ArrayList<>();
        for (ItemStack stack : equippedItems(player)) {
            collectEnchantments(stack, enchantments);
        }

        double knockbackResistance = knockbackResistance(player);
        if (knockbackResistance > 0.0) {
            int level = Math.max(1, Math.min(255, (int) Math.round(knockbackResistance * 10.0)));
            enchantments.add(new Enchantment("generic.knockback_resistance", (byte) level));
        }
        if (range > 0.0f) {
            int level = Math.max(1, Math.min(255, Math.round(range * 10.0f)));
            enchantments.add(new Enchantment("entity_interaction_range", (byte) level));
        }

        enchantments.sort(Comparator
                .comparing(Enchantment::getName)
                .thenComparingInt(enchantment -> Byte.toUnsignedInt(enchantment.getLevel())));
        return enchantments;
    }

    private static void collectEnchantments(ItemStack stack, List<Enchantment> out) {
        if (stack == null || stack.isEmpty() || !stack.hasEnchantments()) {
            return;
        }

        var enchantments = stack.getEnchantments();
        for (var registryEntry : enchantments.getEnchantments()) {
            int level = enchantments.getLevel(registryEntry);
            String name = registryEntry.getKey()
                    .map(key -> key.getValue().getPath())
                    .orElse("unknown");
            out.add(new Enchantment(name, (byte) Math.max(0, Math.min(255, level))));
        }
    }

    private static List<ItemStack> equippedItems(ServerPlayerEntity player) {
        return List.of(
                player.getMainHandStack(),
                player.getOffHandStack(),
                player.getEquippedStack(EquipmentSlot.HEAD),
                player.getEquippedStack(EquipmentSlot.CHEST),
                player.getEquippedStack(EquipmentSlot.LEGS),
                player.getEquippedStack(EquipmentSlot.FEET));
    }

    private static float interactionRange(ServerPlayerEntity player) {
        if (player.getAttributes().hasAttribute(EntityAttributes.ENTITY_INTERACTION_RANGE)) {
            double value = player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE);
            if (value > 0.0) {
                return (float) value;
            }
        }
        return ServerCombatSettings.getServerReach();
    }

    private static double knockbackResistance(ServerPlayerEntity player) {
        if (player.getAttributes().hasAttribute(EntityAttributes.KNOCKBACK_RESISTANCE)) {
            return player.getAttributeValue(EntityAttributes.KNOCKBACK_RESISTANCE);
        }
        return 0.0;
    }

    private static boolean isSwimming(ServerPlayerEntity player) {
        return player.getPose() == net.minecraft.entity.EntityPose.SWIMMING;
    }

    private static byte effectId(StatusEffectInstance effect) {
        String key = Registries.STATUS_EFFECT
                .getId(effect.getEffectType().value())
                .getPath();
        return (byte) EffectType.fromKey(key).getValue();
    }
}
