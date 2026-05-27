package org.vennv.zeusGateway.task;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
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
import org.vennv.utils.Armor;
import org.vennv.utils.Armors;
import org.vennv.utils.EffectFlags;
import org.vennv.utils.EffectType;
import org.vennv.utils.Enchantment;
import org.vennv.utils.Item;
import org.vennv.utils.ItemStack;
import org.vennv.utils.RelativeBlock;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusGateway.compat.AttributeCompat;
import org.vennv.zeusGateway.compat.EffectCompat;
import org.vennv.zeusGateway.compat.EntityCompat;
import org.vennv.zeusGateway.platform.ServerCombatSettings;
import org.vennv.zeusGateway.platform.ServerVersion;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.utils.BlockUtil;
import org.vennv.zeusGateway.utils.ItemUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Emits the current Bukkit player state as Zeus protocol packets.
 *
 * Used by join handling, late plugin start/reload recovery, and periodic
 * resync so those paths stay behaviorally identical.
 */
public final class PlayerStateSnapshotService {
    private static final ConcurrentHashMap<UUID, String> HELD_HASH = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> ARMOR_HASH = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> ENCHANT_HASH = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> INVENTORY_HASH = new ConcurrentHashMap<>();

    private PlayerStateSnapshotService() {}

    public static void sendFullSnapshot(Player player) {
        sendSnapshot(player, true);
    }

    public static void sendResyncSnapshot(Player player) {
        sendSnapshot(player, false);
    }

    public static void sendMutableStateSnapshot(Player player) {
        long timestamp = System.currentTimeMillis();
        String uid = player.getUniqueId().toString();
        String name = player.getName();

        PacketQueue.push(serverConfig(timestamp, uid, name, player));
        sendCommands(timestamp, uid, name, player);
        sendHeldItem(timestamp, uid, name, player, false);
        sendArmor(timestamp, uid, name, player, false);
        sendEnchantments(timestamp, uid, name, player, false);
    }

    public static void sendCommandStateSnapshot(Player player) {
        long timestamp = System.currentTimeMillis();
        sendCommands(
                timestamp,
                player.getUniqueId().toString(),
                player.getName(),
                player);
    }

    public static void sendPositionAndBlocksSnapshot(Player player) {
        long timestamp = System.currentTimeMillis();
        sendPositionAndBlocks(
                timestamp,
                player.getUniqueId().toString(),
                player.getName(),
                player);
    }

    public static void sendInventoryDetailSnapshot(
            Player player,
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
                player.getUniqueId().toString(),
                player.getName(),
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

    public static void clear(Player player) {
        UUID uuid = player.getUniqueId();
        HELD_HASH.remove(uuid);
        ARMOR_HASH.remove(uuid);
        ENCHANT_HASH.remove(uuid);
        INVENTORY_HASH.remove(uuid);
    }

    private static void sendSnapshot(Player player, boolean forceStableState) {
        long timestamp = System.currentTimeMillis();
        String uid = player.getUniqueId().toString();
        String name = player.getName();

        PacketQueue.push(new PacketPlayerJoin(timestamp, uid, name));
        PacketQueue.push(serverConfig(timestamp, uid, name, player));
        PacketQueue.push(new PacketPlayerChangeMode(
                timestamp,
                uid,
                name,
                gameModeToProtocolId(player.getGameMode())));

        sendPositionAndBlocks(timestamp, uid, name, player);
        sendHeldItem(timestamp, uid, name, player, forceStableState);
        sendArmor(timestamp, uid, name, player, forceStableState);
        sendEnchantments(timestamp, uid, name, player, forceStableState);
        sendEffects(timestamp, uid, name, player);
        sendCommands(timestamp, uid, name, player);
        sendOpenInventorySnapshot(timestamp, uid, name, player, forceStableState);
    }

    static PacketServerConfig serverConfig(long timestamp, String uid, String name, Player player) {
        float reach = ServerCombatSettings.getServerReach();
        float cooldown = ServerCombatSettings.getAttackCooldownTicks();

        Double attackSpeed = AttributeCompat.getAttackSpeed(player);
        if (attackSpeed != null && attackSpeed > 0.0) {
            cooldown = (float) (20.0 / attackSpeed);
        }

        Double interactionRange = AttributeCompat.getInteractionRange(player);
        if (interactionRange != null && interactionRange > 0.0) {
            reach = interactionRange.floatValue();
        }

        return new PacketServerConfig(
                timestamp,
                uid,
                name,
                reach,
                cooldown,
                ServerCombatSettings.getMaxCps());
    }

    public static int gameModeToProtocolId(GameMode mode) {
        switch (mode) {
            case CREATIVE:
                return 1;
            case ADVENTURE:
                return 2;
            case SPECTATOR:
                return 3;
            case SURVIVAL:
            default:
                return 0;
        }
    }

    private static void sendPositionAndBlocks(long timestamp, String uid, String name, Player player) {
        Location loc = player.getLocation();
        Location eye = player.getEyeLocation();
        Vector pos = loc.toVector();
        float height = EntityCompat.getPlayerHeight(player);
        boolean onGround = BlockUtil.isOnGround(player, pos);

        PacketQueue.push(new PacketPlayerPosition(
                timestamp,
                uid,
                name,
                false,
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                eye.getX(),
                eye.getY(),
                eye.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                height,
                onGround));

        List<RelativeBlock> blocks = BlockUtil.getRelativeBlocks(player);
        PacketQueue.push(new PacketPlayerSurroundingBlocks(timestamp, uid, name, blocks));
    }

    private static void sendHeldItem(
            long timestamp,
            String uid,
            String name,
            Player player,
            boolean force) {
        Item item = ItemUtil.protocolItem(mainHand(player));
        String hash = item.toString();
        if (!force && hash.equals(HELD_HASH.get(player.getUniqueId()))) {
            return;
        }
        HELD_HASH.put(player.getUniqueId(), hash);
        PacketQueue.push(new PacketPlayerHeldItem(timestamp, uid, name, item));
    }

    private static void sendArmor(
            long timestamp,
            String uid,
            String name,
            Player player,
            boolean force) {
        Armors armors = currentArmors(player);
        String hash = armors.toString();
        if (!force && hash.equals(ARMOR_HASH.get(player.getUniqueId()))) {
            return;
        }
        ARMOR_HASH.put(player.getUniqueId(), hash);
        PacketQueue.push(new PacketPlayerArmorsEquipment(timestamp, uid, name, armors));
    }

    private static void sendEnchantments(
            long timestamp,
            String uid,
            String name,
            Player player,
            boolean force) {
        float range = ServerCombatSettings.getServerReach();
        Double interactionRange = AttributeCompat.getInteractionRange(player);
        if (interactionRange != null && interactionRange > 0.0) {
            range = interactionRange.floatValue();
        }

        List<Enchantment> enchantments = currentEnchantments(player, range);
        String hash = range + ":" + enchantments.stream()
                .map(enchantment -> enchantment.getName() + "=" + Byte.toUnsignedInt(enchantment.getLevel()))
                .collect(Collectors.joining(","));
        if (!force && hash.equals(ENCHANT_HASH.get(player.getUniqueId()))) {
            return;
        }
        ENCHANT_HASH.put(player.getUniqueId(), hash);
        PacketQueue.push(new PacketPlayerEnchantments(timestamp, uid, name, enchantments, range));
    }

    private static void sendEffects(long timestamp, String uid, String name, Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect == null || effect.getType() == null) {
                continue;
            }

            byte effectId = (byte) EffectType.fromKey(
                    EffectCompat.getEffectKey(effect.getType())
            ).getValue();
            byte amplifier = (byte) effect.getAmplifier();
            int duration = effect.getDuration();

            PacketQueue.push(new PacketPlayerEffect(
                    timestamp,
                    uid,
                    name,
                    new Effect(effectId, amplifier, duration, EffectFlags.ADD)));
        }
    }

    private static void sendCommands(long timestamp, String uid, String name, Player player) {
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
                isFallFlying(player)
                        ? ServerBoundPlayerCommandActions.START_FALL_FLYING
                        : ServerBoundPlayerCommandActions.STOP_FALL_FLYING);

        boolean riding = player.isInsideVehicle();
        boolean boat = riding && player.getVehicle() instanceof Boat;
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
            Player player,
            boolean force) {
        Object view = org.vennv.zeusGateway.compat.InventoryViewCompat.getOpenInventory(player);
        if (view == null) {
            return;
        }
        org.bukkit.inventory.Inventory top = org.vennv.zeusGateway.compat.InventoryViewCompat.getTopInventory(view);
        if (top == null) {
            return;
        }
        InventoryType topType = org.vennv.zeusGateway.compat.InventoryViewCompat.topInventoryType(view);
        if (topType == InventoryType.CRAFTING) {
            return;
        }

        byte windowId = (byte) (org.vennv.zeusGateway.compat.InventoryViewCompat.viewHashCode(view) & 0xFF);
        sendInventoryDetailSnapshot(
                timestamp,
                uid,
                name,
                player,
                windowId,
                -1,
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
            Player player,
            byte windowId,
            int stateId,
            short clickedSlot,
            byte button,
            short mode,
            short transactionId,
            boolean includeOpenWindow,
            boolean force) {
        Object view = org.vennv.zeusGateway.compat.InventoryViewCompat.getOpenInventory(player);
        if (view == null) {
            return;
        }

        List<PacketPlayerInventoryTransaction.ChangedSlot> changedSlots = new ArrayList<>();
        int slotCount = org.vennv.zeusGateway.compat.InventoryViewCompat.countSlots(view);
        for (int slot = 0; slot < slotCount; slot++) {
            try {
                changedSlots.add(new PacketPlayerInventoryTransaction.ChangedSlot(
                        (short) slot,
                        ItemUtil.protocolStack(org.vennv.zeusGateway.compat.InventoryViewCompat.getItem(view, slot))));
            } catch (Exception ignored) {
                // Some platforms reject virtual raw slots; keep the snapshot best-effort.
            }
        }

        ItemStack cursor = ItemUtil.protocolStack(player.getItemOnCursor());
        String hash = Byte.toUnsignedInt(windowId) + ":" + cursor + ":" + changedSlots;
        if (!force && hash.equals(INVENTORY_HASH.get(player.getUniqueId()))) {
            return;
        }
        INVENTORY_HASH.put(player.getUniqueId(), hash);

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

    private static org.bukkit.inventory.ItemStack mainHand(Player player) {
        try {
            return player.getInventory().getItemInMainHand();
        } catch (NoSuchMethodError ignored) {
            try {
                return (org.bukkit.inventory.ItemStack) player.getClass()
                        .getMethod("getItemInHand")
                        .invoke(player);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private static List<Enchantment> currentEnchantments(Player player, float range) {
        List<Enchantment> enchantments = new ArrayList<>();
        collectEnchantments(mainHand(player), enchantments);
        collectEnchantments(offHand(player), enchantments);
        collectEnchantments(player.getInventory().getHelmet(), enchantments);
        collectEnchantments(player.getInventory().getChestplate(), enchantments);
        collectEnchantments(player.getInventory().getLeggings(), enchantments);
        collectEnchantments(player.getInventory().getBoots(), enchantments);

        Double knockbackResistance = AttributeCompat.getKnockbackResistance(player);
        if (knockbackResistance != null && knockbackResistance > 0.0) {
            int level = Math.max(1, Math.min(255, (int) Math.round(knockbackResistance * 10.0)));
            enchantments.add(new Enchantment("generic.knockback_resistance", (byte) level));
        }
        if (range > 0.0f) {
            int level = Math.max(1, Math.min(255, Math.round(range * 10.0f)));
            enchantments.add(new Enchantment("entity_interaction_range", (byte) level));
        }

        enchantments.sort(Comparator.comparing(Enchantment::getName)
                .thenComparingInt(enchantment -> Byte.toUnsignedInt(enchantment.getLevel())));
        return enchantments;
    }

    private static void collectEnchantments(
            org.bukkit.inventory.ItemStack item,
            List<Enchantment> enchantments) {
        if (item == null || isAir(item)) {
            return;
        }
        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry
                : item.getEnchantments().entrySet()) {
            int level = Math.max(0, Math.min(255, entry.getValue()));
            if (level <= 0) {
                continue;
            }
            enchantments.add(new Enchantment(enchantmentKey(entry.getKey()), (byte) level));
        }
    }

    private static String enchantmentKey(org.bukkit.enchantments.Enchantment enchantment) {
        try {
            return enchantment.getKey().getKey();
        } catch (Exception | NoSuchMethodError ignored) {
            return enchantment.getName().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static boolean isAir(org.bukkit.inventory.ItemStack item) {
        try {
            return item.getType().isAir();
        } catch (NoSuchMethodError ignored) {
            return item.getType() == org.bukkit.Material.AIR;
        }
    }

    private static org.bukkit.inventory.ItemStack offHand(Player player) {
        try {
            return player.getInventory().getItemInOffHand();
        } catch (NoSuchMethodError ignored) {
            return null;
        }
    }

    private static Armors currentArmors(Player player) {
        Armor helmet = ItemUtil.protocolArmor(player.getInventory().getHelmet());
        Armor chestplate = ItemUtil.protocolArmor(player.getInventory().getChestplate());
        Armor leggings = ItemUtil.protocolArmor(player.getInventory().getLeggings());
        Armor boots = ItemUtil.protocolArmor(player.getInventory().getBoots());
        return new Armors(helmet, chestplate, leggings, boots);
    }

    private static boolean isSwimming(Player player) {
        if (!ServerVersion.HAS_ENTITY_POSE) {
            return false;
        }
        try {
            return player.getPose() == org.bukkit.entity.Pose.SWIMMING;
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
            return false;
        }
    }

    private static boolean isFallFlying(Player player) {
        try {
            return player.isGliding();
        } catch (NoSuchMethodError ignored) {
            return false;
        }
    }
}
