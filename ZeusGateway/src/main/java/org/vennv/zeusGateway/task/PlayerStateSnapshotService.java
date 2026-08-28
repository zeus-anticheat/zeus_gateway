package org.vennv.zeusGateway.task;

import java.util.Arrays;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
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
import org.vennv.packets.PacketMovementStateSnapshot;
import org.vennv.packets.PacketUpdateAttributes;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.packets.PacketServerConfig;
import org.bukkit.event.inventory.InventoryType;
import org.vennv.utils.Armor;
import org.vennv.utils.Armors;
import org.vennv.utils.EffectFlags;
import org.vennv.utils.EffectType;
import org.vennv.utils.Enchantment;
import org.vennv.utils.Item;
import org.vennv.utils.ItemStack;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusGateway.compat.AttributeCompat;
import org.vennv.zeusGateway.compat.EffectCompat;
import org.vennv.zeusGateway.compat.EntityCompat;
import org.vennv.zeusGateway.listener.packets.PacketVehicleMoveListener;
import org.vennv.zeusGateway.platform.ServerIdentity;
import org.vennv.zeusGateway.platform.ServerVersion;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.utils.ItemUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * Emits the current Bukkit player state as Zeus protocol packets.
 *
 * Used by join handling, late plugin start/reload recovery, and periodic
 * resync so those paths stay behaviorally identical.
 */
public final class PlayerStateSnapshotService {
    private static final String RIPTIDE_ENCHANTMENT = "riptide";
    private static final String RIPTIDE_TRIDENT = "minecraft:trident";
    private static final ConcurrentHashMap<UUID, String> HELD_HASH = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> ARMOR_HASH = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> ENCHANT_HASH = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> INVENTORY_HASH = new ConcurrentHashMap<>();

    private PlayerStateSnapshotService() {}

    public static void sendFullSnapshot(Player player) {
        sendSnapshot(player, true, true);
    }

    public static void sendResyncSnapshot(Player player) {
        sendSnapshot(player, false, false);
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

    /**
     * Sends one server-confirmed Riptide activation as an ordered queue group.
     * The event item, not mutable inventory, is authoritative for launch state.
     */
    public static void sendRiptideActivation(Player player, org.bukkit.inventory.ItemStack item) {
        long timestamp = System.currentTimeMillis();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        List<Enchantment> enchantments = riptideEnchantments(item);
        Item protocolItem = riptideActivationItem(ItemUtil.protocolItem(item), enchantments);
        String details = riptideDebugDetails(player, item, protocolItem, enchantments);

        boolean queued = PacketQueue.pushAll(Arrays.asList(
                new PacketPlayerHeldItem(timestamp, uid, name, protocolItem),
                new PacketPlayerEnchantments(
                        timestamp, uid, name, enchantments, 3.0f),
                new PacketServerBoundPlayerCommand(
                        timestamp, uid, name, ServerBoundPlayerCommandActions.START_RIPTIDE)));
        Logger.getLogger("ZeusGateway").info(
                "[Riptide] player=" + name + " queued=" + queued + details);
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

    private static void sendSnapshot(
            Player player,
            boolean forceStableState,
            boolean includeJoin) {
        long timestamp = System.currentTimeMillis();
        String uid = player.getUniqueId().toString();
        String name = player.getName();

        if (includeJoin) {
            PacketQueue.push(new PacketPlayerJoin(
                    timestamp, uid, name, ServerIdentity.serverProtocol()));
        }
        PacketQueue.push(serverConfig(timestamp, uid, name, player));

        sendPositionAndBlocks(timestamp, uid, name, player);
        sendHeldItem(timestamp, uid, name, player, forceStableState);
        sendArmor(timestamp, uid, name, player, forceStableState);
        sendEnchantments(timestamp, uid, name, player, forceStableState);
        sendOpenInventorySnapshot(timestamp, uid, name, player, forceStableState);
    }

    static List<PacketMovementStateSnapshot> movementStateFragments(
            long timestamp,
            String uid,
            String name,
            long generation,
            long sequence,
            Player player) {
        return PacketMovementStateSnapshot.createFragments(
                timestamp,
                uid,
                name,
                generation,
                sequence,
                movementStateSnapshot(player));
    }

    static PacketMovementStateSnapshot.Snapshot movementStateSnapshot(Player player) {
        List<PacketUpdateAttributes.Property> properties = AttributeCompat.getMovementProperties(player);
        Double movementSpeed = AttributeCompat.getMovementSpeed(player);
        Double gravity = AttributeCompat.getGravity(player);
        Double jumpStrength = AttributeCompat.getJumpStrength(player);
        Double stepHeight = AttributeCompat.getStepHeight(player);
        Double scale = AttributeCompat.getScale(player);
        Double sneakingSpeed = AttributeCompat.getSneakingSpeed(player);
        Double movementEfficiency = AttributeCompat.getMovementEfficiency(player);
        Double waterMovementEfficiency = AttributeCompat.getWaterMovementEfficiency(player);
        boolean attributesComplete = properties != null
                && validNonNegative(movementSpeed)
                && validNonNegative(gravity)
                && validNonNegative(jumpStrength)
                && validNonNegative(stepHeight)
                && validPositive(scale)
                && validNonNegative(sneakingSpeed)
                && validNonNegative(movementEfficiency)
                && validNonNegative(waterMovementEfficiency);
        if (!attributesComplete) {
            java.util.logging.Logger.getLogger("ZeusGateway").warning(
                    "[ZEUS-SNAP] incomplete attrs properties="
                            + (properties == null ? "null" : properties.size())
                            + " speed=" + movementSpeed
                            + " gravity=" + gravity
                            + " jump=" + jumpStrength
                            + " step=" + stepHeight
                            + " scale=" + scale
                            + " sneak=" + sneakingSpeed
                            + " eff=" + movementEfficiency
                            + " water=" + waterMovementEfficiency);
        }
        PacketMovementStateSnapshot.Attributes attributes = new PacketMovementStateSnapshot.Attributes(
                attributesComplete,
                validNonNegative(movementSpeed) ? movementSpeed.floatValue() : 0.1f,
                validNonNegative(gravity) ? gravity : 0.08,
                validNonNegative(jumpStrength) ? jumpStrength : 0.42,
                validNonNegative(stepHeight) ? stepHeight : 0.6,
                validPositive(scale) ? scale : 1.0,
                validNonNegative(sneakingSpeed) ? sneakingSpeed : 0.3,
                validNonNegative(movementEfficiency) ? movementEfficiency : 0.0,
                validNonNegative(waterMovementEfficiency) ? waterMovementEfficiency : 0.0,
                properties == null
                        ? java.util.Collections.<PacketUpdateAttributes.Property>emptyList()
                        : properties);

        boolean canFly = false;
        boolean flying = false;
        float flySpeed = 0.05f;
        try {
            canFly = player.getAllowFlight();
            flying = canFly && player.isFlying();
            float captured = player.getFlySpeed();
            if (Float.isFinite(captured) && captured >= 0.0f && captured <= 1.0f) {
                flySpeed = captured;
            }
        } catch (RuntimeException | LinkageError ignored) {
            canFly = false;
            flying = false;
        }

        boolean blocking = isBlocking(player);
        boolean fishing = hasFishHook(player);
        boolean usingItem = blocking || fishing || isHandRaised(player);
        org.bukkit.inventory.ItemStack held = mainHand(player);
        boolean eating = usingItem && held != null && isEdible(held);
        boolean drawing = usingItem && held != null && isDrawingItem(held);
        PacketMovementStateSnapshot.UseItem useItem = new PacketMovementStateSnapshot.UseItem(
                usingItem, blocking, eating, drawing, fishing);

        PacketMovementStateSnapshot.Vehicle vehicle = movementVehicle(player.getVehicle());
        List<Effect> effects = replacementEffects(player);
        GameMode mode = player.getGameMode();
        return new PacketMovementStateSnapshot.Snapshot(
                gameModeToProtocolId(mode == null ? GameMode.SURVIVAL : mode),
                attributes,
                new PacketMovementStateSnapshot.Abilities(canFly, flying, flySpeed),
                player.isSprinting(),
                player.isSneaking(),
                isSwimming(player),
                isFallFlying(player),
                useItem,
                vehicle,
                effects);
    }

    private static List<Effect> replacementEffects(Player player) {
        List<Effect> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect == null || effect.getType() == null) continue;
            int effectId = EffectType.fromKey(
                    EffectCompat.getEffectKey(effect.getType())).getValue();
            if (effectId < 0 || effectId >= 255) continue;
            effects.add(new Effect(
                    (byte) effectId,
                    (byte) effect.getAmplifier(),
                    effect.getDuration(),
                    EffectFlags.ADD));
        }
        effects.sort(Comparator.comparingInt(effect -> Byte.toUnsignedInt(effect.getEffectId())));
        return effects;
    }

    private static PacketMovementStateSnapshot.Vehicle movementVehicle(Entity vehicle) {
        if (vehicle == null) return null;
        PacketVehicleMoveListener.HorseTelemetry horse =
                PacketVehicleMoveListener.horseTelemetry(vehicle);
        Float speed = horse.getMovementSpeed();
        return new PacketMovementStateSnapshot.Vehicle(
                PacketVehicleMoveListener.vehicleType(vehicle),
                vehicle.getEntityId(),
                (byte) PacketVehicleMoveListener.vehicleFlags(vehicle),
                speed == null ? null : speed.doubleValue(),
                horse.getJumpStrength(),
                horse.isSaddleKnown() ? Boolean.valueOf(horse.isSaddled()) : null);
    }

    private static boolean validNonNegative(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 && value <= 1024.0;
    }

    private static boolean validPositive(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0 && value <= 1024.0;
    }

    private static boolean isHandRaised(Player player) {
        try {
            return player.isHandRaised();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isBlocking(Player player) {
        try {
            return player.isBlocking();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean hasFishHook(Player player) {
        try {
            return player.getClass().getMethod("getFishHook").invoke(player) != null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isEdible(org.bukkit.inventory.ItemStack item) {
        try {
            return item.getType().isEdible();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isDrawingItem(org.bukkit.inventory.ItemStack item) {
        String material = item.getType().name();
        return "BOW".equals(material)
                || "CROSSBOW".equals(material)
                || "TRIDENT".equals(material);
    }

    static PacketServerConfig serverConfig(long timestamp, String uid, String name, Player player) {

        // Read current movement speed attribute (includes Soul Speed, Speed potion, etc.)
        float movementSpeed = 0.1f; // Vanilla default
        Double speedAttr = AttributeCompat.getMovementSpeed(player);
        if (speedAttr != null && speedAttr > 0.0) {
            movementSpeed = speedAttr.floatValue();
        }

        return new PacketServerConfig(
                timestamp,
                uid,
                name,
                movementSpeed,
                ServerIdentity.serverProtocol(),
                ServerIdentity.serverVersion(),
                ServerIdentity.serverBrand(),
                ServerIdentity.platform(),
                ServerIdentity.physicsFingerprint(),
                ServerIdentity.clientProtocol(player),
                ServerIdentity.clientVersion(ServerIdentity.clientProtocol(player)),
                ServerIdentity.translationBehaviorFingerprint(
                        player.getUniqueId(),
                        ServerIdentity.clientProtocol(player)),
                "gateway");
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
        float height = EntityCompat.getPlayerHeight(player);
        boolean onGround = player.isOnGround();

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
                onGround,
                PacketPlayerPosition.SOURCE_SNAPSHOT));
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
        float range = 3.0f;
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

    private static List<Enchantment> riptideEnchantments(org.bukkit.inventory.ItemStack item) {
        List<Enchantment> enchantments = new ArrayList<>();
        if (item == null) {
            return enchantments;
        }
        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry
                : item.getEnchantments().entrySet()) {
            int level = Math.max(0, Math.min(255, entry.getValue()));
            if (level > 0) {
                enchantments.add(new Enchantment(enchantmentKey(entry.getKey()), (byte) level));
            }
        }
        return enchantments;
    }

    static Item riptideActivationItem(Item eventItem, List<Enchantment> enchantments) {
        if (!eventItem.getItemStack().isEmpty()
                || enchantments.stream().noneMatch(enchantment -> RIPTIDE_ENCHANTMENT.equals(enchantment.getName()))) {
            return eventItem;
        }
        // Paper can expose an AIR ItemStack with the authoritative Riptide enchantment here.
        return new Item(RIPTIDE_TRIDENT, "", new ItemStack(RIPTIDE_TRIDENT, 0, (byte) 1));
    }

    private static String riptideDebugDetails(
            Player player,
            org.bukkit.inventory.ItemStack eventItem,
            Item protocolItem,
            List<Enchantment> enchantments) {
        return " riptideEvent=" + debugItem(eventItem)
                + " main=" + debugItem(mainHand(player))
                + " off=" + debugItem(offHand(player))
                + " wire=" + protocolItem
                + " enchants=" + enchantments.stream()
                        .map(enchantment -> enchantment.getName() + "="
                                + Byte.toUnsignedInt(enchantment.getLevel()))
                        .collect(Collectors.joining("|"));
    }

    private static String debugItem(org.bukkit.inventory.ItemStack item) {
        if (item == null) {
            return "null";
        }
        return item.getType().name() + "x" + item.getAmount()
                + " enchants=" + item.getEnchantments();
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
