package org.vennv.zeusGatewayLegacy;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.vennv.EntityState;
import org.vennv.PacketEncode;
import org.vennv.packets.PacketPlayerAttackEntity;
import org.vennv.packets.PacketPlayerDeath;
import org.vennv.packets.PacketPlayerExternalForce;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.packets.PacketPlayerJoin;
import org.vennv.packets.PacketPlayerLeave;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketPlayerRespawn;
import org.vennv.packets.PacketPlayerTeleport;
import org.vennv.packets.PacketPlayerVelocity;
import org.vennv.utils.ExternalForceFlags;
import org.vennv.utils.ExternalForceType;

public final class LegacyGatewaySession implements AutoCloseable, Listener {
    private final JavaPlugin plugin;
    private LegacyProxyClient proxyClient;
    private LegacyBatchSender batchSender;
    private Thread batchThread;
    private LegacyPacketEventsSession packetEventsSession;
    private LegacyCollisionWindowProducer collisionProducer;
    private BukkitTask resyncTask;
    private final Set<UUID> crossWorldHandled =
            Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private boolean closed;

    private LegacyGatewaySession(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static LegacyGatewaySession start(JavaPlugin plugin) {
        LegacyGatewaySession session = new LegacyGatewaySession(plugin);
        try {
            session.open();
            return session;
        } catch (RuntimeException | LinkageError error) {
            session.close();
            throw error;
        }
    }

    private void open() {
        plugin.saveDefaultConfig();
        try {
            proxyClient = new LegacyProxyClient(
                    plugin.getConfig().getString("proxy-ac.host", "127.0.0.1"),
                    plugin.getConfig().getInt("proxy-ac.port", 9999));
            batchSender = new LegacyBatchSender(
                    proxyClient, plugin.getConfig().getInt("packets.batch-size", 100));
            batchThread = new Thread(batchSender, "ZeusGatewayLegacy-BatchSender");
            batchThread.setDaemon(true);
            batchThread.start();
            LegacyPhysicsCaptureManager.start(plugin);
            collisionProducer = LegacyCollisionWindowProducer.start(
                    plugin, new LegacyCollisionWindowProducer.RecoveryHandler() {
                        @Override
                        public void onFullQueued(UUID playerId) {
                            LegacyPacketEventsSession current = packetEventsSession;
                            if (current != null) current.collisionRecovered(playerId);
                        }
                    });
            packetEventsSession = LegacyPacketEventsSession.register(this);
            LegacyPacketQueue.setOverflowHandler(new LegacyPacketQueue.OverflowHandler() {
                @Override
                public void onOverflow(final UUID owner, final boolean global) {
                    Runnable recovery = new Runnable() {
                        @Override
                        public void run() {
                            handleOverflow(owner, global);
                        }
                    };
                    if (Bukkit.isPrimaryThread()) recovery.run();
                    else if (!closed) plugin.getServer().getScheduler().runTask(plugin, recovery);
                }
            });
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            for (Player player : Bukkit.getOnlinePlayers()) resync(player, true);
            resyncTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override
                public void run() {
                    for (Player player : Bukkit.getOnlinePlayers()) resync(player, false);
                }
            }, 100L, 200L);
            plugin.getLogger().info("[ZeusGateway] Legacy PacketEvents forwarding active.");
        } catch (Exception error) {
            throw new IllegalStateException("Legacy Gateway initialization failed", error);
        }
    }

    JavaPlugin plugin() {
        return plugin;
    }

    private void handleOverflow(UUID owner, boolean global) {
        LegacyPacketEventsSession current = packetEventsSession;
        if (current != null) current.outputOverflow(owner, global);
        LegacyCollisionWindowProducer producer = collisionProducer;
        if (producer == null) return;
        if (global) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                producer.invalidate(player.getUniqueId());
                producer.requestFull(player.getUniqueId());
            }
        } else if (owner != null) {
            producer.invalidate(owner);
            producer.requestFull(owner);
        }
    }

    void captureCollisionMovement(Player player, double x, double y, double z) {
        LegacyCollisionWindowProducer producer = collisionProducer;
        if (producer != null) producer.onMovement(player, x, y, z);
    }

    void collisionBlockChanged(UUID playerId) {
        LegacyCollisionWindowProducer producer = collisionProducer;
        if (producer != null) producer.requestFull(playerId);
    }

    void emitRawAttack(UUID attackerId, int targetEntityId, long timestamp) {
        Player attacker = Bukkit.getPlayer(attackerId);
        if (attacker == null || !attacker.isOnline()) return;
        Entity entity = SpigotConversionUtil.getEntityById(attacker.getWorld(), targetEntityId);
        if (entity != null) {
            push(attackerId, new PacketPlayerAttackEntity(
                    timestamp, uid(attacker), attacker.getName(), entityState(entity)));
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (resyncTask != null) {
            resyncTask.cancel();
            resyncTask = null;
        }
        if (packetEventsSession != null) {
            packetEventsSession.close();
            packetEventsSession = null;
        }
        if (collisionProducer != null) {
            collisionProducer.close();
            collisionProducer = null;
        }
        LegacyPhysicsCaptureManager.stop();
        if (batchSender != null) batchSender.shutdown();
        if (batchThread != null) {
            batchThread.interrupt();
            try {
                batchThread.join(1000L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            batchThread = null;
        }
        if (proxyClient != null) proxyClient.close();
        LegacyPacketQueue.setOverflowHandler(null);
        LegacyPacketQueue.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        resync(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        resetLifecycle(player, false);
        pushControl(player.getUniqueId(), deathPacket(now(), uid(player), player.getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        resetLifecycle(player, true);
        pushControl(player.getUniqueId(), respawnPacket(now(), uid(player), player.getName()));
        LegacyCollisionWindowProducer producer = collisionProducer;
        if (producer != null) producer.forceFull(player, event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        Player player = event.getPlayer();
        boolean crossWorld = !sameWorld(event.getFrom().getWorld(), to.getWorld());
        if (crossWorld) crossWorldHandled.add(player.getUniqueId());
        resetLifecycle(player, crossWorld);
        pushControl(player.getUniqueId(), teleportPacket(
                now(), uid(player), player.getName(), to.getX(), to.getY(), to.getZ()));
        LegacyCollisionWindowProducer producer = collisionProducer;
        if (producer != null) producer.forceFull(player, to);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (crossWorldHandled.remove(player.getUniqueId())) return;
        Location to = player.getLocation();
        resetLifecycle(player, true);
        pushControl(player.getUniqueId(), teleportPacket(
                now(), uid(player), player.getName(), to.getX(), to.getY(), to.getZ()));
        LegacyCollisionWindowProducer producer = collisionProducer;
        if (producer != null) producer.forceFull(player, to);
    }

    private void resetLifecycle(Player player, boolean requireFullChunk) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        LegacyPacketEventsSession current = packetEventsSession;
        if (current != null) current.resetLifecycle(uuid, requireFullChunk);
        else LegacyPhysicsCaptureManager.reset(uuid);
        LegacyCollisionWindowProducer producer = collisionProducer;
        if (producer != null) producer.invalidate(uuid);
    }

    private void resync(Player player, boolean awaitWorld) {
        if (closed || player == null || !player.isOnline()) return;
        UUID owner = player.getUniqueId();
        long timestamp = now();
        PacketEncode join = joinPacket(
                timestamp, uid(player), player.getName(), LegacyPhysicsCaptureManager.clientProtocol(player));
        PacketEncode config = LegacyPhysicsCaptureManager.serverConfig(player, timestamp);
        if (awaitWorld) {
            pushControl(owner, join);
            pushControl(owner, config);
        } else {
            pushCoalescing(owner, "resync-join", join);
            pushCoalescing(owner, "resync-config", config);
        }
        publishPlayerSnapshot(player, timestamp, false, !awaitWorld);
        LegacyCollisionWindowProducer producer = collisionProducer;
        if (producer != null) {
            if (awaitWorld) producer.lifecycle(player, player.getLocation());
            else producer.forceFull(player);
        }
        if (awaitWorld && packetEventsSession != null) {
            packetEventsSession.awaitWorldResync(owner);
        }
    }

    void scheduleInventoryTransaction(
            final UUID playerId, final byte windowId, final short clickedSlot,
            final byte button, final short mode, final short transactionId) {
        final Player player = Bukkit.getPlayer(playerId);
        if (closed || player == null || !player.isOnline()) return;
        final InventoryView clickedView = player.getOpenInventory();
        final Inventory top = clickedView.getTopInventory();
        final Inventory bottom = clickedView.getBottomInventory();
        final List<org.vennv.utils.ItemStack> before = snapshot(clickedView);
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    final Player current = Bukkit.getPlayer(playerId);
                    if (closed || current == null || !current.isOnline()) return;
                    InventoryView currentView = current.getOpenInventory();
                    boolean sameView = currentView != null
                            && currentView.getTopInventory() == top
                            && currentView.getBottomInventory() == bottom
                            && currentView.countSlots() == before.size();
                    final List<PacketPlayerInventoryTransaction.ChangedSlot> changedSlots = sameView
                            ? changedSlots(before, snapshot(currentView))
                            : Collections.<PacketPlayerInventoryTransaction.ChangedSlot>emptyList();
                    org.vennv.utils.ItemStack cursorAfter = item(current.getItemOnCursor());
                    org.vennv.utils.ItemStack cursor = sameView ? cursorAfter : emptyItem();
                    if (packetEventsSession == null) return;
                    final PacketPlayerInventoryTransaction transaction = inventoryTransaction(
                            System.currentTimeMillis(), uid(current), current.getName(), windowId,
                            clickedSlot, button, mode, transactionId, cursor, changedSlots);
                    packetEventsSession.dispatchOrdered(playerId, new Runnable() {
                        @Override
                        public void run() {
                            push(playerId, transaction);
                        }
                    });
                } catch (RuntimeException | LinkageError error) {
                    plugin.getLogger().warning(
                            "[ZeusGatewayLegacy] Inventory transaction task failed: "
                                    + error.getMessage());
                    if (packetEventsSession == null) return;
                    final Player current = Bukkit.getPlayer(playerId);
                    if (current == null) return;
                    final PacketPlayerInventoryTransaction fallback = inventoryTransaction(
                            System.currentTimeMillis(), uid(current), current.getName(),
                            windowId, clickedSlot, button, mode, transactionId,
                            emptyItem(),
                            Collections.<PacketPlayerInventoryTransaction.ChangedSlot>emptyList());
                    packetEventsSession.dispatchOrdered(playerId, new Runnable() {
                        @Override
                        public void run() {
                            push(playerId, fallback);
                        }
                    });
                }
            }
        }, 1L);
    }

    static PacketPlayerJoin joinPacket(long timestamp, String uid, String name, int protocol) {
        return new PacketPlayerJoin(timestamp, uid, name, protocol);
    }

    static PacketPlayerDeath deathPacket(long timestamp, String uid, String name) {
        return new PacketPlayerDeath(timestamp, uid, name);
    }

    static PacketPlayerRespawn respawnPacket(long timestamp, String uid, String name) {
        return new PacketPlayerRespawn(timestamp, uid, name);
    }

    static PacketPlayerTeleport teleportPacket(
            long timestamp, String uid, String name, double x, double y, double z) {
        return new PacketPlayerTeleport(timestamp, uid, name, x, y, z);
    }

    static PacketPlayerInventoryTransaction inventoryTransaction(
            long timestamp, String uid, String name, byte windowId, short clickedSlot,
            byte button, short mode, short transactionId, org.vennv.utils.ItemStack cursor,
            List<PacketPlayerInventoryTransaction.ChangedSlot> changedSlots) {
        return new PacketPlayerInventoryTransaction(
                timestamp, uid, name, windowId, -1, clickedSlot, button, mode,
                transactionId, cursor, changedSlots);
    }

    static List<PacketPlayerInventoryTransaction.ChangedSlot> changedSlots(
            List<org.vennv.utils.ItemStack> before, List<org.vennv.utils.ItemStack> after) {
        if (before == null || after == null || before.size() != after.size()) {
            return Collections.emptyList();
        }
        List<PacketPlayerInventoryTransaction.ChangedSlot> changed =
                new ArrayList<PacketPlayerInventoryTransaction.ChangedSlot>();
        for (int slot = 0; slot < before.size(); slot++) {
            if (!sameItem(before.get(slot), after.get(slot))) {
                changed.add(new PacketPlayerInventoryTransaction.ChangedSlot(
                        (short) slot, after.get(slot)));
            }
        }
        return changed;
    }

    static boolean sameItem(org.vennv.utils.ItemStack left, org.vennv.utils.ItemStack right) {
        return left != null && right != null
                && left.getId().equals(right.getId())
                && left.getMeta() == right.getMeta()
                && left.getCount() == right.getCount();
    }

    private static List<org.vennv.utils.ItemStack> snapshot(InventoryView view) {
        List<org.vennv.utils.ItemStack> items = new ArrayList<org.vennv.utils.ItemStack>();
        if (view == null) return items;
        int count = view.countSlots();
        for (int slot = 0; slot < count; slot++) {
            try {
                items.add(item(view.getItem(slot)));
            } catch (IndexOutOfBoundsException e) {
                break;
            }
        }
        return items;
    }

    private static org.vennv.utils.ItemStack emptyItem() {
        return new org.vennv.utils.ItemStack(org.vennv.utils.ItemStack.EMPTY_ID, 0, (byte) 0);
    }

    static org.vennv.utils.ItemStack item(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType() == null || stack.getType().name().equals("AIR")
                || stack.getAmount() <= 0) {
            return emptyItem();
        }
        try {
            com.github.retrooper.packetevents.protocol.item.ItemStack converted =
                    SpigotConversionUtil.fromBukkitItemStack(stack);
            if (converted != null && !converted.isEmpty() && converted.getType() != null) {
                return new org.vennv.utils.ItemStack(
                        converted.getType().getName().toString(), converted.getLegacyData(),
                        (byte) converted.getAmount());
            }
        } catch (RuntimeException | LinkageError ignored) {}
        return new org.vennv.utils.ItemStack(
                "minecraft:" + stack.getType().name().toLowerCase(java.util.Locale.ROOT),
                stack.getDurability(), (byte) stack.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        crossWorldHandled.remove(player.getUniqueId());
        LegacyCollisionWindowProducer producer = collisionProducer;
        if (producer != null) producer.remove(player.getUniqueId());
        if (packetEventsSession != null) {
            packetEventsSession.leave(player, now());
        } else {
            pushControl(player.getUniqueId(), new PacketPlayerLeave(now(), uid(player), player.getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        Vector velocity = event.getVelocity();
        push(player.getUniqueId(), new PacketPlayerVelocity(now(), uid(player), player.getName(),
                velocity.getX(), velocity.getY(), velocity.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        Entity damager = event.getDamager();
        push(victim.getUniqueId(), externalForce(now(), victim, damager.getLocation(), victim.getVelocity(),
                forceType(event, damager), ExternalForceFlags.DAMAGE_BACKED));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || event instanceof EntityDamageByEntityEvent) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;
        Player player = (Player) event.getEntity();
        push(player.getUniqueId(), externalForce(now(), player, player.getLocation(), player.getVelocity(),
                ExternalForceType.EXPLOSION, ExternalForceFlags.ENVIRONMENT_BACKED));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        changed(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        changed(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        changed(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        changed(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        changed(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        changed(event.getBlock());
        changed(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        changed(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        changed(event.getBlock());
        changed(event.getRetractLocation().getBlock());
        for (org.bukkit.block.Block block : event.getBlocks()) changed(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        for (org.bukkit.block.Block block : event.blockList()) changed(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        for (org.bukkit.block.BlockState state : event.getBlocks()) changed(state.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) {
        changed(event.getBlock());
        for (org.bukkit.block.Block block : event.getBlocks()) {
            changed(block);
            changed(block.getRelative(event.getDirection()));
        }
        Location source = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        BlockFace direction = event.getDirection();
        Vector velocity = new Vector(direction.getModX(), direction.getModY(), direction.getModZ());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (sameWorld(player.getWorld(), source.getWorld()) && pistonIntersects(event, player)) {
                push(player.getUniqueId(), externalForce(now(), player, source, velocity, ExternalForceType.PISTON,
                        ExternalForceFlags.ENVIRONMENT_BACKED | ExternalForceFlags.DIRECT_INTERSECT));
            }
        }
    }

    private void publishPlayerSnapshot(
            Player player, long timestamp, boolean cancelled, boolean coalesce) {
        Location location = player.getLocation();
        Location eye = player.getEyeLocation();
        PacketEncode packet = new PacketPlayerPosition(
                timestamp, uid(player), player.getName(), cancelled,
                location.getX(), location.getY(), location.getZ(),
                eye.getX(), eye.getY(), eye.getZ(), location.getYaw(), location.getPitch(),
                player.isSneaking() ? 1.5f : 1.8f, player.isOnGround(),
                PacketPlayerPosition.SOURCE_RESYNC);
        if (coalesce) pushCoalescing(player.getUniqueId(), "resync-position", packet);
        else push(player.getUniqueId(), packet);
    }

    private PacketPlayerExternalForce externalForce(
            long timestamp, Player player, Location source, Vector velocity,
            ExternalForceType forceType, int flags) {
        Vector direction = player.getLocation().toVector().subtract(source.toVector());
        if (direction.lengthSquared() > 0.0001) direction.normalize();
        double strength = velocity == null ? 0.0 : velocity.length();
        return new PacketPlayerExternalForce(
                timestamp, uid(player), player.getName(), forceType,
                source.getX(), source.getY(), source.getZ(),
                direction.getX(), direction.getY(), direction.getZ(),
                velocity == null ? 0.0 : velocity.getX(),
                velocity == null ? 0.0 : velocity.getY(),
                velocity == null ? 0.0 : velocity.getZ(), strength, (short) 1, flags);
    }

    private ExternalForceType forceType(EntityDamageByEntityEvent event, Entity damager) {
        if (damager instanceof Player) return ExternalForceType.PLAYER_ATTACK;
        if (damager instanceof Projectile || event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
            return damager.getType().name().contains("FISHING")
                    ? ExternalForceType.FISHING_HOOK : ExternalForceType.PROJECTILE;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || damager.getType().name().contains("TNT")
                || damager.getType().name().contains("FIREBALL")) return ExternalForceType.EXPLOSION;
        return ExternalForceType.ENTITY_ATTACK;
    }

    private void changed(org.bukkit.block.Block block) {
        if (block == null) return;
        LegacyCollisionWindowProducer producer = collisionProducer;
        if (producer != null) producer.blockChanged(
                block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    private boolean pistonIntersects(BlockPistonExtendEvent event, Player player) {
        Location location = player.getLocation();
        double height = player.isSneaking() ? 1.5 : 1.8;
        int dx = event.getDirection().getModX();
        int dy = event.getDirection().getModY();
        int dz = event.getDirection().getModZ();
        for (org.bukkit.block.Block block : event.getBlocks()) {
            double x = block.getX() + dx;
            double y = block.getY() + dy;
            double z = block.getZ() + dz;
            if (location.getX() - 0.3 < x + 1.0 && location.getX() + 0.3 > x
                    && location.getY() < y + 1.0 && location.getY() + height > y
                    && location.getZ() - 0.3 < z + 1.0 && location.getZ() + 0.3 > z) return true;
        }
        return false;
    }

    private EntityState entityState(Entity entity) {
        Location location = entity.getLocation();
        double eyeHeight = entity instanceof LivingEntity ? ((LivingEntity) entity).getEyeHeight() : 0.85;
        double height = entity instanceof Player && ((Player) entity).isSneaking()
                ? 1.5 : Math.max(eyeHeight + 0.18, 1.0);
        double width = entity.getType().name().contains("SPIDER") ? 1.4 : 0.6;
        return new EntityState(
                entity.getUniqueId().toString(), location.getX(), location.getY(), location.getZ(),
                location.getX(), location.getY() + eyeHeight, location.getZ(),
                location.getYaw(), location.getPitch(), (float) height, (float) width,
                entity instanceof Player && ((Player) entity).isOnGround());
    }

    private boolean sameWorld(World left, World right) {
        return left != null && right != null && left.getUID().equals(right.getUID());
    }

    private String uid(Player player) {
        UUID uniqueId = player.getUniqueId();
        return uniqueId == null ? player.getName() : uniqueId.toString();
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private boolean push(UUID owner, PacketEncode packet) {
        return LegacyPacketQueue.push(owner, packet);
    }

    private boolean pushControl(UUID owner, PacketEncode packet) {
        return LegacyPacketQueue.pushControl(owner, packet);
    }

    private boolean pushCoalescing(UUID owner, String key, PacketEncode packet) {
        return LegacyPacketQueue.pushCoalescing(owner, key, packet);
    }
}
