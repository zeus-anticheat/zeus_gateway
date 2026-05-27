package org.vennv.zeusGateway.listener.event;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.vennv.EntityState;
import org.vennv.packets.PacketPlayerAttackEntity;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.compat.EntityCompat;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.task.PlayerStateSnapshotService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paper-exclusive event listener.
 * <p>
 * This class is only instantiated and registered when the server is running
 * Paper (or a Paper fork like Purpur). It uses Paper-specific events that do
 * not exist on Spigot or Folia-without-Paper.
 * <p>
 * All imports of Paper-only classes are done reflectively or guarded so that
 * the class can be loaded without issues even if Paper classes are absent
 * at compile time on a pure-Spigot build — however, since ZeusGateway already
 * has paper-api on the compile classpath we import directly for clarity.
 */
public class PaperEventListener implements Listener {
    private final ZeusGateway plugin;
    private final boolean rawAttackCapture;

    private static final Map<String, Location> LAST_POSITION = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LAST_SNEAKING = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LAST_SPRINTING = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LAST_IN_VEHICLE = new ConcurrentHashMap<>();
    private static final Map<String, Location> LAST_VEHICLE_POS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> LAST_SCREEN_HANDLER = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LAST_GLIDING = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LAST_USING_RIPTIDE = new ConcurrentHashMap<>();

    public PaperEventListener() {
        this(null, false);
    }

    public PaperEventListener(ZeusGateway plugin) {
        this(plugin, false);
    }

    public PaperEventListener(ZeusGateway plugin, boolean rawAttackCapture) {
        this.plugin = plugin;
        this.rawAttackCapture = rawAttackCapture;
    }

    // ─────────────────── PrePlayerAttackEntityEvent ───────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPrePlayerAttackEntity(
        io.papermc.paper.event.player.PrePlayerAttackEntityEvent event
    ) {
        if (rawAttackCapture) {
            return;
        }

        Player player = event.getPlayer();
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        long timestamp = System.currentTimeMillis();

        Entity entity = event.getAttacked();
        Location eLoc = entity.getLocation();
        double height = EntityCompat.getHeight(entity);
        double width = EntityCompat.getWidth(entity);
        double eyeY =
            eLoc.getY() +
            (entity instanceof org.bukkit.entity.LivingEntity
                ? ((org.bukkit.entity.LivingEntity) entity).getEyeHeight()
                : height * 0.85);

        PacketPlayerAttackEntity packet = new PacketPlayerAttackEntity(
            timestamp,
            uid,
            name,
            new EntityState(
                entity.getUniqueId().toString(), // This was the original argument
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
                entity.isOnGround()
            )
        );
        PacketQueue.push(packet);
    }

    // ──────────────── PlayerArmorChangeEvent (Paper) ─────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmorChange(
        com.destroystokyo.paper.event.player.PlayerArmorChangeEvent event
    ) {
        scheduleMutableStateSnapshot(event.getPlayer());
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
}
