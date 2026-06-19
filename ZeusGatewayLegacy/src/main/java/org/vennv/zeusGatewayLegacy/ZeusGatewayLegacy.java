package org.vennv.zeusGatewayLegacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.vennv.EntityState;
import org.vennv.PacketEncode;
import org.vennv.packets.PacketPlayerAttackEntity;
import org.vennv.packets.PacketPlayerExternalForce;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.packets.PacketPlayerJoin;
import org.vennv.packets.PacketPlayerLeave;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketPlayerVelocity;
import org.vennv.utils.ExternalForceFlags;
import org.vennv.utils.ExternalForceType;

public final class ZeusGatewayLegacy extends JavaPlugin implements Listener {
    private LegacyProxyClient proxyClient;
    private LegacyBatchSender batchSender;
    private Thread batchThread;
    private BukkitTask positionTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            proxyClient = new LegacyProxyClient(
                    getConfig().getString("proxy-ac.host", "127.0.0.1"),
                    getConfig().getInt("proxy-ac.port", 9999));
            batchSender = new LegacyBatchSender(proxyClient, getConfig().getInt("packets.batch-size", 100));
            batchThread = new Thread(batchSender, "ZeusGatewayLegacy-BatchSender");
            batchThread.setDaemon(true);
            batchThread.start();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[ZeusGatewayLegacy] Failed to initialize UDP proxy client.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);
        long period = Math.max(1L, getConfig().getLong("packets.position-snapshot-ticks", 1L));
        positionTask = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                publishOnlinePlayerSnapshots();
            }
        }, period, period);
        getLogger().info("[ZeusGatewayLegacy] Plugin enabled; Java 8 legacy artifact foundation active.");
    }

    @Override
    public void onDisable() {
        if (positionTask != null) {
            positionTask.cancel();
        }
        if (batchSender != null) {
            batchSender.shutdown();
        }
        if (proxyClient != null) {
            proxyClient.close();
        }
        getLogger().info("[ZeusGatewayLegacy] Plugin disabled.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        push(new PacketPlayerJoin(now(), uid(player), player.getName()));
        publishPlayerSnapshot(player, now(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        push(new PacketPlayerLeave(now(), uid(player), player.getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        publishPlayerSnapshot(player, now(), event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        Vector velocity = event.getVelocity();
        push(new PacketPlayerVelocity(now(), uid(player), player.getName(),
                velocity.getX(), velocity.getY(), velocity.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        long timestamp = now();
        Entity damager = event.getDamager();
        Entity damaged = event.getEntity();
        if (damager instanceof Player) {
            Player attacker = (Player) damager;
            push(new PacketPlayerAttackEntity(timestamp, uid(attacker), attacker.getName(), entityState(damaged)));
        }
        if (damaged instanceof Player) {
            Player victim = (Player) damaged;
            ExternalForceType forceType = damager instanceof Player
                    ? ExternalForceType.PLAYER_ATTACK
                    : ExternalForceType.ENTITY_ATTACK;
            push(externalForce(timestamp, victim, damager.getLocation(), victim.getVelocity(), forceType,
                    ExternalForceFlags.DAMAGE_BACKED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        Player player = (Player) event.getEntity();
        push(externalForce(now(), player, player.getLocation(), player.getVelocity(),
                ExternalForceType.EXPLOSION, ExternalForceFlags.ENVIRONMENT_BACKED));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        short clickedSlot = (short) event.getRawSlot();
        byte button = (byte) event.getClick().ordinal();
        short mode = (short) event.getAction().ordinal();
        byte windowId = (byte) windowId(getViewReflect(event));
        org.vennv.utils.ItemStack cursor = itemStack(event.getCursor());
        List<PacketPlayerInventoryTransaction.ChangedSlot> changedSlots =
                Collections.singletonList(new PacketPlayerInventoryTransaction.ChangedSlot(
                        clickedSlot,
                        itemStack(event.getCurrentItem())));

        push(new PacketPlayerInventoryTransaction(
                now(),
                uid(player),
                player.getName(),
                windowId,
                0,
                clickedSlot,
                button,
                mode,
                (short) 0,
                cursor,
                changedSlots));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) {
        Location source = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        BlockFace direction = event.getDirection();
        Vector velocity = new Vector(direction.getModX(), direction.getModY(), direction.getModZ());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!sameWorld(player.getWorld(), source.getWorld())) {
                continue;
            }
            if (player.getLocation().distanceSquared(source) > 16.0) {
                continue;
            }
            push(externalForce(now(), player, source, velocity,
                    ExternalForceType.PISTON, ExternalForceFlags.ENVIRONMENT_BACKED));
        }
    }

    private void publishOnlinePlayerSnapshots() {
        long timestamp = now();
        for (Player player : Bukkit.getOnlinePlayers()) {
            publishPlayerSnapshot(player, timestamp, false);
        }
    }

    private void publishPlayerSnapshot(Player player, long timestamp, boolean cancelled) {
        Location location = player.getLocation();
        Location eye = player.getEyeLocation();
        push(new PacketPlayerPosition(
                timestamp,
                uid(player),
                player.getName(),
                cancelled,
                location.getX(),
                location.getY(),
                location.getZ(),
                eye.getX(),
                eye.getY(),
                eye.getZ(),
                location.getYaw(),
                location.getPitch(),
                player.isSneaking() ? 1.5f : 1.8f,
                player.isOnGround()));
    }

    private PacketPlayerExternalForce externalForce(
            long timestamp,
            Player player,
            Location source,
            Vector velocity,
            ExternalForceType forceType,
            int flags) {
        Location playerLocation = player.getLocation();
        Vector direction = playerLocation.toVector().subtract(source.toVector());
        if (direction.lengthSquared() > 0.0001) {
            direction.normalize();
        }
        double strength = velocity == null ? 0.0 : velocity.length();
        return new PacketPlayerExternalForce(
                timestamp,
                uid(player),
                player.getName(),
                forceType,
                source.getX(),
                source.getY(),
                source.getZ(),
                direction.getX(),
                direction.getY(),
                direction.getZ(),
                velocity == null ? 0.0 : velocity.getX(),
                velocity == null ? 0.0 : velocity.getY(),
                velocity == null ? 0.0 : velocity.getZ(),
                strength,
                (short) 1,
                flags);
    }

    private EntityState entityState(Entity entity) {
        Location location = entity.getLocation();
        Location eye = entity instanceof Player ? ((Player) entity).getEyeLocation() : location.clone().add(0.0, 1.62, 0.0);
        return new EntityState(
                entity.getUniqueId().toString(),
                location.getX(),
                location.getY(),
                location.getZ(),
                eye.getX(),
                eye.getY(),
                eye.getZ(),
                location.getYaw(),
                location.getPitch(),
                entity instanceof Player && ((Player) entity).isSneaking() ? 1.5f : 1.8f,
                0.6f,
                entity instanceof Player && ((Player) entity).isOnGround());
    }


    private org.vennv.utils.ItemStack itemStack(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return new org.vennv.utils.ItemStack(org.vennv.utils.ItemStack.EMPTY_ID, 0, (byte) 0);
        }
        short durability = 0;
        try {
            durability = stack.getDurability();
        } catch (NoSuchMethodError ignored) {
            durability = 0;
        }
        return new org.vennv.utils.ItemStack(materialKey(stack.getType()), durability, (byte) stack.getAmount());
    }

    private String materialKey(Material material) {
        if (material == null) {
            return "";
        }
        return "minecraft:" + material.name().toLowerCase(Locale.ROOT);
    }

    private int windowId(Object view) {
        if (view == null) {
            return 0;
        }
        try {
            java.lang.reflect.Method m = view.getClass().getMethod("getType");
            Object type = m.invoke(view);
            if (type instanceof Enum) {
                return ((Enum<?>) type).ordinal();
            }
        } catch (ReflectiveOperationException ignored) {}
        return 0;
    }

    private static Object getViewReflect(org.bukkit.event.inventory.InventoryEvent event) {
        if (event == null) {
            return null;
        }
        try {
            java.lang.reflect.Method m = event.getClass().getMethod("getView");
            return m.invoke(event);
        } catch (ReflectiveOperationException e) {
            return null;
        }
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

    private void push(PacketEncode packet) {
        LegacyPacketQueue.push(packet);
    }
}
