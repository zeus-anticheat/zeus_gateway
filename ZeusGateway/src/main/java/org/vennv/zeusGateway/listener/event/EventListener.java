package org.vennv.zeusGateway.listener.event;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import org.vennv.Effect;
import org.vennv.EntityState;
import org.vennv.packets.*;
import org.vennv.utils.*;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.compat.EntityCompat;
import org.vennv.zeusGateway.compat.EffectCompat;
import org.vennv.zeusGateway.listener.RawCaptureCapability;
import org.vennv.zeusGateway.debug.PacketDebugEnvelope;
import org.vennv.zeusGateway.platform.PlatformDetector;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.task.PlayerStateSnapshotService;
import org.vennv.zeusGateway.utils.ItemUtil;

/**
 * Unified Bukkit event listener that works across Paper, Spigot, and Folia.
 * <p>
 * Paper-specific events (e.g. PrePlayerAttackEntityEvent) are handled in
 * {@link PaperEventListener} and registered only when Paper is detected.
 * This class uses only standard Bukkit/Spigot API so it compiles and runs
 * on every platform.
 */
public class EventListener implements Listener {
    private final ZeusGateway plugin;
    private final EnumSet<RawCaptureCapability> rawCapabilities;

    /**
     * Monotonically increasing transaction counter shared between the click
     * and confirm handlers for the same InventoryClickEvent.
     */
    private short transactionCounter = 0;

    public EventListener() {
        this(null, EnumSet.noneOf(RawCaptureCapability.class));
    }

    /**
     * Compatibility constructor retained for integrations that previously toggled every fallback.
     */
    public EventListener(boolean packetFallbacksEnabled) {
        this(null, packetFallbacksEnabled
                ? EnumSet.noneOf(RawCaptureCapability.class)
                : EnumSet.allOf(RawCaptureCapability.class));
    }

    public EventListener(Set<RawCaptureCapability> rawCapabilities) {
        this(null, rawCapabilities);
    }

    public EventListener(ZeusGateway plugin, Set<RawCaptureCapability> rawCapabilities) {
        this.plugin = plugin;
        this.rawCapabilities = rawCapabilities.isEmpty()
                ? EnumSet.noneOf(RawCaptureCapability.class)
                : EnumSet.copyOf(rawCapabilities);
    }

    boolean isFallbackEnabled(RawCaptureCapability capability) {
        return !rawCapabilities.contains(capability);
    }

    private synchronized short nextTransactionId() {
        return ++transactionCounter;
    }

    // ─────────────────────────── Join / Leave ───────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        PlayerStateSnapshotService.sendFullSnapshot(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        PacketPlayerLeave packet = new PacketPlayerLeave(timestamp, uid, name);
        PacketQueue.push(packet);
        PlayerStateSnapshotService.clear(player);
        clearRawPositionState(player);
    }

    private void clearRawPositionState(Player player) {
        if (plugin == null || !plugin.isProtocolLibAvailable()) {
            return;
        }
        try {
            Class<?> listener = Class.forName(
                    "org.vennv.zeusGateway.listener.packets.PacketPositionListener");
            listener.getMethod("removePlayer", java.util.UUID.class)
                    .invoke(null, player.getUniqueId());
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().fine(
                    "[ZeusGateway] Unable to clear raw position capture state: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Vector velocity = event.getVelocity();
        PacketPlayerVelocity packet = new PacketPlayerVelocity(
                timestamp, uid, name,
                velocity.getX(), velocity.getY(), velocity.getZ());
        PacketQueue.push(packet);
    }

    // ─────────────────────────── Player Death ───────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        PacketPlayerDeath packet = new PacketPlayerDeath(timestamp, uid, name);
        PacketQueue.push(packet);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        PacketPlayerRespawn packet = new PacketPlayerRespawn(timestamp, uid, name);
        PacketQueue.push(packet);
    }

    // ────────────────────────── Attack Entity ──────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isFallbackEnabled(RawCaptureCapability.ATTACK_ENTITY)) {
            return;
        }

        // On Paper, PrePlayerAttackEntityEvent is preferred (handled in
        // PaperEventListener).
        // On Spigot/Folia we fall back to this standard Bukkit event.
        if (PlatformDetector.isPaper()) {
            return; // let PaperEventListener handle it
        }

        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getDamager();

        Entity entity = event.getEntity();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Location eLoc = entity.getLocation();
        double height = EntityCompat.getHeight(entity);
        double width = EntityCompat.getWidth(entity);
        double eyeX = eLoc.getX();
        double eyeY = eLoc.getY() +
                (entity instanceof org.bukkit.entity.LivingEntity
                        ? ((org.bukkit.entity.LivingEntity) entity).getEyeHeight()
                        : height * 0.85);
        double eyeZ = eLoc.getZ();

        PacketPlayerAttackEntity packet = new PacketPlayerAttackEntity(
                timestamp,
                uid,
                name,
                new EntityState(
                        entity.getUniqueId().toString(),
                        eLoc.getX(),
                        eLoc.getY(),
                        eLoc.getZ(),
                        eyeX,
                        eyeY,
                        eyeZ,
                        eLoc.getYaw(),
                        eLoc.getPitch(),
                        (float) height,
                        (float) width,
                        entity.isOnGround()));
        PacketQueue.push(packet);
    }

    // ────────────────────── Attacked BY Entity ─────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    @SuppressWarnings("unused")
    public void onPlayerDamagedByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        Entity attacker = event.getDamager();

        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Location aLoc = attacker.getLocation();
        double height = EntityCompat.getHeight(attacker);
        double width = EntityCompat.getWidth(attacker);
        double eyeY = aLoc.getY() +
                (attacker instanceof org.bukkit.entity.LivingEntity
                        ? ((org.bukkit.entity.LivingEntity) attacker).getEyeHeight()
                        : height * 0.85);

        EntityState attackerState = new EntityState(
                attacker.getUniqueId().toString(),
                aLoc.getX(),
                aLoc.getY(),
                aLoc.getZ(),
                aLoc.getX(),
                eyeY,
                aLoc.getZ(),
                aLoc.getYaw(),
                aLoc.getPitch(),
                (float) height,
                (float) width,
                attacker.isOnGround());

        if (attacker instanceof Player) {
            // Attacked by another player
            PacketQueue.push(new PacketPlayerAttackedByPlayer(timestamp, uid, name, attackerState));
        } else {
            // Attacked by a non-player entity (mob, etc.)
            PacketQueue.push(new PacketPlayerAttackedByEntity(timestamp, uid, name, attackerState));
        }

        Vector velocity = player.getVelocity();
        Location playerLoc = player.getLocation();
        Vector direction = playerLoc.toVector().subtract(aLoc.toVector());
        emitExternalForce(
                player,
                classifyDamageForce(event, attacker),
                aLoc,
                direction,
                velocity,
                Math.max(event.getFinalDamage(), velocity.length()),
                (short) 10,
                ExternalForceFlags.DAMAGE_BACKED);
    }

    // ──────────────────────── Player Got Damage ────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();

        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        DamageCause cause = mapDamageCause(event.getCause());

        PacketPlayerGotDamage packet = new PacketPlayerGotDamage(
                timestamp,
                uid,
                name,
                cause);
        PacketQueue.push(packet);

        if (!(event instanceof EntityDamageByEntityEvent)
                && (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)) {
            Vector velocity = player.getVelocity();
            emitExternalForce(
                    player,
                    ExternalForceType.EXPLOSION,
                    player.getLocation(),
                    velocity,
                    velocity,
                    Math.max(event.getFinalDamage(), velocity.length()),
                    (short) 20,
                    ExternalForceFlags.DAMAGE_BACKED);
        }
    }

    // ──────────────────────── Entity Interaction ───────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Location eLoc = entity.getLocation();
        double height = EntityCompat.getHeight(entity);
        double width = EntityCompat.getWidth(entity);
        double eyeY = eLoc.getY() +
                (entity instanceof org.bukkit.entity.LivingEntity
                        ? ((org.bukkit.entity.LivingEntity) entity).getEyeHeight()
                        : height * 0.85);

        PacketPlayerEntityInteraction packet = new PacketPlayerEntityInteraction(
                timestamp,
                uid,
                name,
                new EntityState(
                        entity.getUniqueId().toString(),
                        eLoc.getX(),
                        eLoc.getY(),
                        eLoc.getZ(),
                        eLoc.getX(),
                        eyeY,
                        eLoc.getZ(),
                        eLoc.getYaw(),
                        eLoc.getPitch(),
                        (float) height,
                        (float) width,
                        entity.isOnGround()));
        PacketQueue.push(packet);
    }

    // ─────────────────────── Game Mode Change ─────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        int gamemode = org.vennv.zeusGateway.task.ResyncTask.gameModeToProtocolId(event.getNewGameMode());

        PacketPlayerChangeMode packet = new PacketPlayerChangeMode(
                timestamp,
                uid,
                name,
                gamemode);
        PacketQueue.push(packet);
    }

    // ──────────────────────────── Teleport ─────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        PacketPlayerTeleport packet = new PacketPlayerTeleport(
                timestamp,
                uid,
                name,
                to.getX(),
                to.getY(),
                to.getZ());
        PacketQueue.push(packet);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Location to = player.getLocation();

        PacketPlayerTeleport packet = new PacketPlayerTeleport(
                timestamp,
                uid,
                name,
                to.getX(),
                to.getY(),
                to.getZ());
        PacketQueue.push(packet);
    }

    // ──────────────────────── Potion Effects ──────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();

        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        byte flags;
        switch (event.getAction()) {
            case ADDED:
                flags = EffectFlags.ADD;
                break;
            case CHANGED:
                flags = EffectFlags.MODIFY;
                break;
            case REMOVED:
            case CLEARED:
                flags = EffectFlags.REMOVE;
                break;
            default:
                flags = EffectFlags.ADD;
                break;
        }

        PotionEffect potionEffect = (flags == EffectFlags.REMOVE)
                ? event.getOldEffect()
                : event.getNewEffect();

        if (potionEffect == null) {
            return;
        }

        byte effectId = (byte) EffectType.fromKey(
                EffectCompat.getEffectKey(potionEffect.getType())).getValue();
        byte amplifier = (byte) potionEffect.getAmplifier();
        int duration = potionEffect.getDuration();

        Effect effect = new Effect(effectId, amplifier, duration, flags);

        PacketPlayerEffect packet = new PacketPlayerEffect(
                timestamp,
                uid,
                name,
                effect);
        PacketQueue.push(packet);
    }

    // ──────────────────── Block Place (Bukkit Event) ──────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isFallbackEnabled(RawCaptureCapability.PLACE_BLOCK)) {
            return;
        }

        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Location loc = event.getBlockPlaced().getLocation();

        // Emit PacketBlockChangeEvent so CompensatedWorld tracks this block
        String blockType = event.getBlockPlaced().getType().name();
        PacketQueue.push(new PacketBlockChangeEvent(
                timestamp,
                uid,
                name,
                (int) loc.getX(),
                (int) loc.getY(),
                (int) loc.getZ(),
                blockType,
                (byte) 0x00));
    }

    // ─────────────────── Block Break (Bukkit Event) ──────────────────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isFallbackEnabled(RawCaptureCapability.DIGGING_BLOCK)) {
            return;
        }

        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Location loc = event.getBlock().getLocation();

        // Emit PacketBlockChangeEvent with AIR (block was removed)
        PacketQueue.push(new PacketBlockChangeEvent(
                timestamp,
                uid,
                name,
                (int) loc.getX(),
                (int) loc.getY(),
                (int) loc.getZ(),
                "AIR",
                (byte) 0x00));
    }

    // ────────────────────────── Held Item ─────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (!isFallbackEnabled(RawCaptureCapability.HELD_ITEM)) {
            return;
        }

        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        int newSlot = event.getNewSlot();
        ItemStack bukkitItem = player.getInventory().getItem(newSlot);

        Item item = ItemUtil.protocolItem(bukkitItem);

        PacketPlayerHeldItem packet = new PacketPlayerHeldItem(
                timestamp,
                uid,
                name,
                item);
        PacketQueue.push(packet);
        scheduleMutableStateSnapshot(player);
    }

    // ────────────────────── Armor Equipment ───────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerArmorChange(PlayerItemHeldEvent event) {
        // We piggyback on slot changes to re-check armor.
        // A dedicated armor-change listener is registered via PaperEventListener
        // on Paper. On Spigot/Folia, we check armor on every item held change
        // and also in an inventory close event so we don't miss updates.
        if (PlatformDetector.isPaper()) {
            return;
        }
        sendArmorPacket(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryCloseArmorCheck(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        if (PlatformDetector.isPaper()) {
            return;
        }
        scheduleMutableStateSnapshot(player);
    }

    private void sendArmorPacket(Player player) {
        scheduleMutableStateSnapshot(player);
    }

    private Armor armorFromBukkitItem(ItemStack item) {
        return ItemUtil.protocolArmor(item);
    }

    // ────────────────── Inventory Open / Click / Close ────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();

        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        // Bukkit does not expose the raw window ID reliably, so we use
        // the hash-code truncated to a byte as a best-effort identifier.
        byte windowId = (byte) (org.vennv.zeusGateway.compat.InventoryViewCompat.viewHashCode(org.vennv.zeusGateway.compat.InventoryViewCompat.getView(event)) & 0xFF);

        PacketPlayerOpenWindow packet = new PacketPlayerOpenWindow(
                timestamp,
                uid,
                name,
                windowId);
        PacketQueue.push(packet);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        byte windowId = (byte) (org.vennv.zeusGateway.compat.InventoryViewCompat.viewHashCode(org.vennv.zeusGateway.compat.InventoryViewCompat.getView(event)) & 0xFF);
        short slotId = (short) event.getRawSlot();
        byte button = (byte) event.getHotbarButton();
        short mode = (short) event.getClick().ordinal();

        // Generate a unique, monotonically increasing transaction ID so the
        // matching ConfirmTransaction packet can remove this click from the
        // server-side item_clicks queue. Wraps around at Short.MAX_VALUE.
        short transactionId = nextTransactionId();

        ItemStack bukkitItem = event.getCurrentItem();
        org.vennv.utils.ItemStack protocolStack = ItemUtil.protocolStack(bukkitItem);

        if (isFallbackEnabled(RawCaptureCapability.CLICK_WINDOW)) {
            PacketPlayerClickWindow packet = new PacketPlayerClickWindow(
                    timestamp,
                    uid,
                    name,
                    windowId,
                    slotId,
                    button,
                    mode,
                    protocolStack,
                    transactionId);
            org.vennv.utils.ItemStack cursor = ItemUtil.protocolStack(event.getCursor());
            PacketQueue.push(new PacketDebugEnvelope(packet,
                    " bukkitCursor=" + debugStack(cursor)));
        }

        org.vennv.utils.ItemStack cursor = ItemUtil.protocolStack(event.getCursor());
        PacketQueue.push(new PacketPlayerInventoryTransaction(
                timestamp,
                uid,
                name,
                windowId,
                -1,
                slotId,
                button,
                mode,
                transactionId,
                cursor,
                java.util.Collections.singletonList(new PacketPlayerInventoryTransaction.ChangedSlot(
                        slotId,
                        protocolStack))));
        scheduleInventoryStateSnapshot(player, windowId, slotId, button, mode, transactionId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();

        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        byte windowId = (byte) (org.vennv.zeusGateway.compat.InventoryViewCompat.viewHashCode(org.vennv.zeusGateway.compat.InventoryViewCompat.getView(event)) & 0xFF);

        PacketPlayerCloseWindow packet = new PacketPlayerCloseWindow(
                timestamp,
                uid,
                name,
                windowId);
        PacketQueue.push(packet);
        scheduleMutableStateSnapshot(player);
    }

    // ────────────────────── Block Ray Trace ───────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteractBlockRayTrace(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        boolean hitBlock = false;
        int blockX = 0,
                blockY = 0,
                blockZ = 0;
        float hitX = 0f,
                hitY = 0f,
                hitZ = 0f;

        if (event.getClickedBlock() != null) {
            hitBlock = true;
            Location bLoc = event.getClickedBlock().getLocation();
            blockX = bLoc.getBlockX();
            blockY = bLoc.getBlockY();
            blockZ = bLoc.getBlockZ();

            // The click position relative to the block can be derived from
            // the player's eye ray; Bukkit does not expose the exact sub-block
            // hit position, so we approximate with the block centre offset.
            try {
                org.bukkit.util.RayTraceResult rayResult = player.rayTraceBlocks(5.0);
                if (rayResult != null && rayResult.getHitPosition() != null) {
                    hitX = (float) rayResult.getHitPosition().getX();
                    hitY = (float) rayResult.getHitPosition().getY();
                    hitZ = (float) rayResult.getHitPosition().getZ();
                }
            } catch (Exception | NoSuchMethodError e) {
                hitX = (float) bLoc.getX() + 0.5f;
                hitY = (float) bLoc.getY() + 0.5f;
                hitZ = (float) bLoc.getZ() + 0.5f;
            }
        }

        PacketPlayerBlockRayTrace packet = new PacketPlayerBlockRayTrace(
                timestamp,
                uid,
                name,
                hitBlock,
                blockX,
                blockY,
                blockZ,
                hitX,
                hitY,
                hitZ);
        PacketQueue.push(packet);
    }

    // ──────────────────── Vehicle Move (Bukkit) ──────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleMove(org.bukkit.event.vehicle.VehicleMoveEvent event) {
        if (!isFallbackEnabled(RawCaptureCapability.VEHICLE_MOVE)) {
            return;
        }

        Entity vehicle = event.getVehicle();
        if (vehicle.getPassengers().isEmpty()) {
            return;
        }

        // Only care about the first passenger that is a player
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player) {
                Player player = (Player) passenger;
                String uid = player.getUniqueId().toString();
                String name = player.getName();
                long timestamp = System.currentTimeMillis();

                Location to = event.getTo();

                PacketPlayerVehicleMove packet = new PacketPlayerVehicleMove(
                        timestamp,
                        uid,
                        name,
                        to.getX(),
                        to.getY(),
                        to.getZ(),
                        to.getYaw(),
                        to.getPitch());
                PacketQueue.push(packet);
                break; // only first player passenger
            }
        }
    }

    // ──────────────── Use / Release Use Item (Bukkit) ────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerInteractUseItem(PlayerInteractEvent event) {
        if (!isFallbackEnabled(RawCaptureCapability.USE_ITEM)) {
            return;
        }

        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
                event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Hand hand;
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
            hand = Hand.OFF_HAND;
        } else {
            hand = Hand.MAIN_HAND;
        }

        PacketPlayerUseItem packet = new PacketPlayerUseItem(
                timestamp,
                uid,
                name,
                hand,
                (byte) 0);
        PacketQueue.push(packet);

        ItemStack usedItem = event.getItem();
        if (usedItem != null
                && player.isGliding()
                && usedItem.getType().name().contains("FIREWORK_ROCKET")) {
            Vector velocity = player.getVelocity();
            emitExternalForce(
                    player,
                    ExternalForceType.ELYTRA_FIREWORK,
                    player.getLocation(),
                    player.getLocation().getDirection(),
                    velocity,
                    Math.max(1.0, velocity.length()),
                    (short) 40,
                    ExternalForceFlags.ENVIRONMENT_BACKED);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerReleaseUseItem(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Hand hand;
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
            hand = Hand.OFF_HAND;
        } else {
            hand = Hand.MAIN_HAND;
        }

        PacketPlayerReleaseUseItem packet = new PacketPlayerReleaseUseItem(
                timestamp,
                uid,
                name,
                hand);
        PacketQueue.push(packet);
    }

    // ─────────────────── Toggle Sneak / Sprint ───────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        ServerBoundPlayerCommandActions action = event.isSneaking()
                ? ServerBoundPlayerCommandActions.START_SNEAKING
                : ServerBoundPlayerCommandActions.STOP_SNEAKING;

        PacketServerBoundPlayerCommand packet = new PacketServerBoundPlayerCommand(timestamp, uid, name, action);
        PacketQueue.push(packet);
        scheduleCommandStateSnapshot(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        ServerBoundPlayerCommandActions action = event.isSprinting()
                ? ServerBoundPlayerCommandActions.START_SPRINTING
                : ServerBoundPlayerCommandActions.STOP_SPRINTING;

        PacketServerBoundPlayerCommand packet = new PacketServerBoundPlayerCommand(timestamp, uid, name, action);
        PacketQueue.push(packet);
        scheduleCommandStateSnapshot(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        if (!isFallbackEnabled(RawCaptureCapability.PLAYER_COMMAND)) {
            return;
        }

        PlayerStateSnapshotService.sendCommandStateSnapshot(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();

        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();
        ServerBoundPlayerCommandActions action = event.isGliding()
                ? ServerBoundPlayerCommandActions.START_FALL_FLYING
                : ServerBoundPlayerCommandActions.STOP_FALL_FLYING;

        PacketServerBoundPlayerCommand packet = new PacketServerBoundPlayerCommand(timestamp, uid, name, action);
        PacketQueue.push(packet);
    }

    private void scheduleMutableStateSnapshot(Player player) {
        if (plugin == null || plugin.getSchedulerAdapter() == null) {
            PlayerStateSnapshotService.sendMutableStateSnapshot(player);
            return;
        }
        plugin.getSchedulerAdapter().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.getSchedulerAdapter().runEntityTask(plugin, player, () -> {
                if (player.isOnline()) {
                    PlayerStateSnapshotService.sendMutableStateSnapshot(player);
                }
            });
        }, 1L);
    }

    private void scheduleCommandStateSnapshot(Player player) {
        if (plugin == null || plugin.getSchedulerAdapter() == null) {
            PlayerStateSnapshotService.sendCommandStateSnapshot(player);
            return;
        }
        plugin.getSchedulerAdapter().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.getSchedulerAdapter().runEntityTask(plugin, player, () -> {
                if (player.isOnline()) {
                    PlayerStateSnapshotService.sendCommandStateSnapshot(player);
                }
            });
        }, 1L);
    }

    private void scheduleInventoryStateSnapshot(
            Player player,
            byte windowId,
            short slotId,
            byte button,
            short mode,
            short transactionId) {
        if (plugin == null || plugin.getSchedulerAdapter() == null) {
            PlayerStateSnapshotService.sendInventoryDetailSnapshot(
                    player, windowId, -1, slotId, button, mode, transactionId, true);
            PlayerStateSnapshotService.sendMutableStateSnapshot(player);
            return;
        }
        plugin.getSchedulerAdapter().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.getSchedulerAdapter().runEntityTask(plugin, player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                PlayerStateSnapshotService.sendInventoryDetailSnapshot(
                        player, windowId, -1, slotId, button, mode, transactionId, true);
                PlayerStateSnapshotService.sendMutableStateSnapshot(player);
            });
        }, 1L);
    }

    // ────────────────────── Block Change Ack ─────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockChangeAck(PlayerInteractEvent event) {
        // Block change acknowledgements are emitted after a player interacts
        // with a block in a way that changes its state (place / break).
        if (event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK &&
                event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        PacketPlayerBlockChangeAck packet = new PacketPlayerBlockChangeAck(
                timestamp,
                uid,
                name);
        PacketQueue.push(packet);
    }

    // ───────────────────── Confirm Transaction ───────────────────────

    // NOTE: Confirm transaction was removed. Modern Minecraft (1.17+) no
    // longer uses server→client transaction confirmation packets. The
    // previous synthesised confirm used rawSlot as the action number which
    // NEVER matched the transaction_id (always 0) in the ClickWindow packet,
    // so item_clicks were never cleaned up on the Rust side.
    //
    // The Rust side now auto-expires item_clicks every tick:
    // • on_player_close_window → clears item_clicks
    // • WindowState::tick() → ages-out stale clicks

    // ─────────────────── Steer Vehicle (Bukkit) ─────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleSteer(
            org.bukkit.event.vehicle.VehicleMoveEvent event) {
        // Bukkit does not have a dedicated steer-vehicle event, so we derive
        // steering from movement deltas. The raw packet version is handled by
        // PacketSteerVehicleListener via ProtocolLib; this is a best-effort
        // fallback for environments where ProtocolLib may not intercept it.
        // Intentionally left as a no-op to avoid duplicate data; the
        // ProtocolLib-based listener covers this packet fully.
    }

    // ────────────────── Vehicle Enter / Exit (Bug #4) ─────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntered();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        // Distinguish boat vs generic vehicle
        ServerBoundPlayerCommandActions action = (event.getVehicle() instanceof Boat)
                ? ServerBoundPlayerCommandActions.START_RIDING_BOAT
                : ServerBoundPlayerCommandActions.START_RIDING_VEHICLE;

        PacketQueue.push(new PacketServerBoundPlayerCommand(timestamp, uid, name, action));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getExited();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        ServerBoundPlayerCommandActions action = (event.getVehicle() instanceof Boat)
                ? ServerBoundPlayerCommandActions.STOP_RIDING_BOAT
                : ServerBoundPlayerCommandActions.STOP_RIDING_VEHICLE;

        PacketQueue.push(new PacketServerBoundPlayerCommand(timestamp, uid, name, action));
    }

    // ──────────────── Swing Hand Bukkit Fallback (Bug #5) ─────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerArmSwing(PlayerAnimationEvent event) {
        if (!isFallbackEnabled(RawCaptureCapability.SWING_HAND)) {
            return;
        }

        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        PacketPlayerSwingHand packet = new PacketPlayerSwingHand(
                timestamp, uid, name, false);
        PacketQueue.push(packet);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        emitPistonForces(event.getBlocks(), event.getDirection(), false);
        emitPistonBlockChanges(event.getBlocks(), event.getDirection(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        emitPistonForces(event.getBlocks(), event.getDirection(), true);
        emitPistonBlockChanges(event.getBlocks(), event.getDirection(), true);
    }
    // ─────────────────── Block Change Events (CompensatedWorld) ──────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        // Fluid flow (water/lava spreading)
        long timestamp = System.currentTimeMillis();
        org.bukkit.block.Block to = event.getToBlock();
        String blockType = to.getType().name();
        PacketQueue.push(new PacketBlockChangeEvent(
                timestamp, "world", "world",
                to.getX(), to.getY(), to.getZ(),
                blockType,
                (byte) 0x03));
    }

    // BlockRedstoneEvent is a Spigot-specific event for current/old current state
    // We don't need to track this for CompensatedWorld - redstone component blocks
    // (repeater/comparator) don't change actual block type, only signal state.
    // Keep this commented handler as a placeholder in case we need to track later.
    //
    // @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    // public void onBlockRedstone(BlockRedstoneEvent event) {
    //     // Redstone signal change: track for redstone-component blocks (repeater, comparator, etc.)
    //     org.bukkit.block.Block block = event.getBlock();
    //     long timestamp = System.currentTimeMillis();
    //     PacketQueue.push(new PacketBlockChangeEvent(
    //             timestamp, "world", "world",
    //             block.getX(), block.getY(), block.getZ(),
    //             block.getType().name(),
    //             (byte) 0x04));
    // }


    // ───────────────────── Block Face (Bukkit) ──────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteractBlockFace(PlayerInteractEvent event) {
        if (!isFallbackEnabled(RawCaptureCapability.BLOCK_FACE)) {
            return;
        }

        if (event.getBlockFace() == null) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK &&
                event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        byte face = mapBlockFace(event.getBlockFace());

        PacketPlayerBlockFace packet = new PacketPlayerBlockFace(
                timestamp,
                uid,
                name,
                face);
        PacketQueue.push(packet);
    }

    // ───────────────────────── Helpers ────────────────────────────────

    private void emitPistonForces(
            java.util.List<Block> movedBlocks,
            org.bukkit.block.BlockFace direction,
            boolean retracting) {
        if (movedBlocks == null || movedBlocks.isEmpty() || direction == null) {
            return;
        }

        Vector dir = direction.getDirection();
        for (Player player : movedBlocks.get(0).getWorld().getPlayers()) {
            Location playerLocation = player.getLocation();
            double halfWidth = EntityCompat.getPlayerWidth(player) / 2.0;
            double[] playerBox = new double[] {
                    playerLocation.getX() - halfWidth,
                    playerLocation.getY(),
                    playerLocation.getZ() - halfWidth,
                    playerLocation.getX() + halfWidth,
                    playerLocation.getY() + EntityCompat.getPlayerHeight(player),
                    playerLocation.getZ() + halfWidth
            };
            for (Block block : movedBlocks) {
                Vector offset = retracting ? new Vector(0, 0, 0) : dir;
                Location dest = block.getLocation().add(offset);
                double[] movedBox = new double[] {
                        dest.getX(),
                        dest.getY(),
                        dest.getZ(),
                        dest.getX() + 1.0,
                        dest.getY() + 1.0,
                        dest.getZ() + 1.0
                };
                if (!overlaps(playerBox, movedBox)) {
                    continue;
                }

                String blockName = block.getType().name();
                int flags = ExternalForceFlags.DIRECT_INTERSECT | ExternalForceFlags.ENVIRONMENT_BACKED;
                ExternalForceType type = ExternalForceType.PISTON;
                if (blockName.equals("SLIME_BLOCK")) {
                    flags |= ExternalForceFlags.HAS_SLIME;
                    type = ExternalForceType.SLIME_PISTON;
                } else if (blockName.equals("HONEY_BLOCK")) {
                    flags |= ExternalForceFlags.HAS_HONEY;
                }
                if (retracting) {
                    flags |= ExternalForceFlags.RETRACTING;
                }

                emitExternalForce(
                        player,
                        type,
                        block.getLocation().add(0.5, 0.5, 0.5),
                        dir,
                        player.getVelocity(),
                        Math.max(1.0, player.getVelocity().length()),
                        (short) (type == ExternalForceType.SLIME_PISTON ? 30 : 15),
                        flags);
                PlayerStateSnapshotService.sendPositionAndBlocksSnapshot(player);
                break;
            }
        }
    }
    /**
     * Emit PacketBlockChangeEvent for each block moved by a piston.
     * This updates the CompensatedWorld so the simulation knows the block
     * has moved from its old position to its new position.
     */
    private void emitPistonBlockChanges(
            java.util.List<Block> movedBlocks,
            org.bukkit.block.BlockFace direction,
            boolean retracting) {
        if (movedBlocks == null || movedBlocks.isEmpty() || direction == null) {
            return;
        }

        Vector dir = direction.getDirection();
        int dx = dir.getBlockX();
        int dy = dir.getBlockY();
        int dz = dir.getBlockZ();

        long timestamp = System.currentTimeMillis();

        for (Block block : movedBlocks) {
            int oldX = block.getX();
            int oldY = block.getY();
            int oldZ = block.getZ();

            int newX = oldX + dx;
            int newY = oldY + dy;
            int newZ = oldZ + dz;

            String blockName = block.getType().name();

            // Old position becomes AIR
            PacketQueue.push(new PacketBlockChangeEvent(
                    timestamp, "world", "world",
                    oldX, oldY, oldZ,
                    "AIR",
                    (byte) 0x01));

            // New position gets the block
            PacketQueue.push(new PacketBlockChangeEvent(
                    timestamp, "world", "world",
                    newX, newY, newZ,
                    blockName,
                    (byte) 0x01));
        }
    }


    private boolean overlaps(double[] first, double[] second) {
        return first[0] < second[3] && first[3] > second[0]
                && first[1] < second[4] && first[4] > second[1]
                && first[2] < second[5] && first[5] > second[2];
    }

    private ExternalForceType classifyDamageForce(EntityDamageByEntityEvent event, Entity attacker) {
        String typeName = attacker.getType().name();
        if (attacker instanceof Player) {
            return ExternalForceType.PLAYER_ATTACK;
        }
        if (typeName.contains("WIND_CHARGE")) {
            return ExternalForceType.WIND_CHARGE;
        }
        if (typeName.contains("FISHING") || typeName.contains("BOBBER")) {
            return ExternalForceType.FISHING_HOOK;
        }
        if (attacker instanceof Projectile || event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
            return ExternalForceType.PROJECTILE;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || typeName.contains("TNT")
                || typeName.contains("FIREBALL")
                || typeName.contains("CRYSTAL")) {
            return ExternalForceType.EXPLOSION;
        }
        return ExternalForceType.ENTITY_ATTACK;
    }

    private void emitExternalForce(
            Player player,
            ExternalForceType type,
            Location source,
            Vector direction,
            Vector velocity,
            double strength,
            short durationTicks,
            int flags) {
        Location sourceLoc = source == null ? player.getLocation() : source;
        Vector dir = direction == null ? new Vector(0, 0, 0) : direction.clone();
        if (dir.lengthSquared() < 1.0e-9) {
            dir = player.getLocation().toVector().subtract(sourceLoc.toVector());
        }
        if (dir.lengthSquared() >= 1.0e-9) {
            dir.normalize();
        }
        Vector vel = velocity == null ? new Vector(0, 0, 0) : velocity;
        PacketQueue.push(new PacketPlayerExternalForce(
                System.currentTimeMillis(),
                player.getUniqueId().toString(),
                player.getName(),
                type,
                sourceLoc.getX(),
                sourceLoc.getY(),
                sourceLoc.getZ(),
                dir.getX(),
                dir.getY(),
                dir.getZ(),
                vel.getX(),
                vel.getY(),
                vel.getZ(),
                strength,
                durationTicks,
                flags));
    }

    private DamageCause mapDamageCause(
            EntityDamageEvent.DamageCause bukkitCause) {
        switch (bukkitCause) {
            case CONTACT:
                return DamageCause.CONTACT;
            case ENTITY_ATTACK:
            case ENTITY_SWEEP_ATTACK:
                return DamageCause.ENTITY_ATTACK;
            case PROJECTILE:
                return DamageCause.PROJECTILE;
            case SUFFOCATION:
                return DamageCause.SUFFOCATION;
            case FALL:
                return DamageCause.FALL;
            case FIRE:
                return DamageCause.FIRE;
            case FIRE_TICK:
                return DamageCause.FIRE_TICK;
            case LAVA:
                return DamageCause.LAVA;
            case DROWNING:
                return DamageCause.DROWNING;
            case BLOCK_EXPLOSION:
                return DamageCause.BLOCK_EXPLOSION;
            case ENTITY_EXPLOSION:
                return DamageCause.ENTITY_EXPLOSION;
            case VOID:
                return DamageCause.VOID;
            case SUICIDE:
                return DamageCause.SUICIDE;
            case MAGIC:
            case POISON:
            case WITHER:
                return DamageCause.MAGIC;
            case STARVATION:
                return DamageCause.STARVATION;
            case FALLING_BLOCK:
                return DamageCause.FALLING_BLOCK;
            default:
                return DamageCause.CUSTOM;
        }
    }

    private String debugStack(org.vennv.utils.ItemStack stack) {
        return (stack.isEmpty() ? "empty" : stack.getId())
                + "x" + Byte.toUnsignedInt(stack.getCount());
    }

    /**
     * Maps a Bukkit BlockFace to the protocol's face byte value.
     * 0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST
     */
    private byte mapBlockFace(org.bukkit.block.BlockFace face) {
        switch (face) {
            case DOWN:
                return (byte) 0;
            case UP:
                return (byte) 1;
            case NORTH:
                return (byte) 2;
            case SOUTH:
                return (byte) 3;
            case WEST:
                return (byte) 4;
            case EAST:
                return (byte) 5;
            default:
                return (byte) 0;
        }
    }
}
