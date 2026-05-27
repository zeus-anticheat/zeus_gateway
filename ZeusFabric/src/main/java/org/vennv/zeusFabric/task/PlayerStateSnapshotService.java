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
import net.minecraft.util.math.Vec3d;
import org.vennv.Effect;
import org.vennv.packets.PacketPlayerArmorsEquipment;
import org.vennv.packets.PacketPlayerChangeMode;
import org.vennv.packets.PacketPlayerEffect;
import org.vennv.packets.PacketPlayerEnchantments;
import org.vennv.packets.PacketPlayerHeldItem;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.packets.PacketPlayerJoin;
import org.vennv.packets.PacketPlayerOpenWindow;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketPlayerSurroundingBlocks;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.packets.PacketServerConfig;
import org.vennv.utils.Armors;
import org.vennv.utils.EffectFlags;
import org.vennv.utils.EffectType;
import org.vennv.utils.Enchantment;
import org.vennv.utils.Item;
import org.vennv.utils.RelativeBlock;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusFabric.ServerCombatSettings;
import org.vennv.zeusFabric.provider.PacketQueue;
import org.vennv.zeusFabric.utils.BlockUtil;
import org.vennv.zeusFabric.utils.ItemUtil;
import org.vennv.zeusFabric.utils.MinecraftCompat;

import java.util.ArrayList;
import java.util.Comparator;
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

    private PlayerStateSnapshotService() {}

    public static void sendFullSnapshot(ServerPlayerEntity player) {
        sendSnapshot(player, true);
    }

    public static void sendResyncSnapshot(ServerPlayerEntity player) {
        sendSnapshot(player, false);
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

    public static void sendPositionAndBlocksSnapshot(ServerPlayerEntity player) {
        long timestamp = System.currentTimeMillis();
        sendPositionAndBlocks(
                timestamp,
                player.getUuidAsString(),
                player.getName().getString(),
                player);
    }

    public static void clear(ServerPlayerEntity player) {
        clear(player.getUuidAsString());
    }

    public static void clear(String uid) {
        HELD_HASH.remove(uid);
        ARMOR_HASH.remove(uid);
        ENCHANT_HASH.remove(uid);
        INVENTORY_HASH.remove(uid);
    }

    private static void sendSnapshot(ServerPlayerEntity player, boolean forceStableState) {
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

        sendPositionAndBlocks(timestamp, uid, name, player);
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

        return new PacketServerConfig(
                timestamp,
                uid,
                name,
                reach,
                cooldown,
                ServerCombatSettings.getMaxCps());
    }

    private static void sendPositionAndBlocks(
            long timestamp,
            String uid,
            String name,
            ServerPlayerEntity player) {
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
                onGround));

        List<RelativeBlock> blocks = BlockUtil.getRelativeBlocks(player);
        PacketQueue.push(new PacketPlayerSurroundingBlocks(timestamp, uid, name, blocks));
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
