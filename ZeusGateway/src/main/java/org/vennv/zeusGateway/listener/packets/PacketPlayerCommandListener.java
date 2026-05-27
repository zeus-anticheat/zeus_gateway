package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.task.PlayerStateSnapshotService;

public class PacketPlayerCommandListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public PacketPlayerCommandListener(ZeusGateway plugin) {
        super(
            plugin,
            ListenerPriority.LOWEST,
            PacketType.Play.Client.ENTITY_ACTION
        );
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        long timestamp = System.currentTimeMillis();

        ServerBoundPlayerCommandActions action = null;

        try {
            StructureModifier<EnumWrappers.PlayerAction> actions = event.getPacket().getPlayerActions();
            if (actions.size() > 0) {
                EnumWrappers.PlayerAction playerAction = actions.read(0);
                action = mapPlayerAction(playerAction);
            }
        } catch (Exception e) {
            // Fallback: try reading the action as an integer enum ordinal
            try {
                StructureModifier<Integer> integers = event.getPacket().getIntegers();
                if (integers.size() > 1) {
                    int actionId = integers.read(1);
                    action = mapActionId(actionId);
                }
            } catch (Exception ignored) {
                // Unable to determine action on this version
            }
        }

        if (action == null) {
            return;
        }

        if (plugin.getSchedulerAdapter() == null) {
            return;
        }
        ServerBoundPlayerCommandActions capturedAction = action;
        plugin.getSchedulerAdapter().runEntityTask(
                plugin, player, () -> emitCommand(player, capturedAction, timestamp));
    }

    private void emitCommand(
            Player player, ServerBoundPlayerCommandActions action, long timestamp) {
        if (!player.isOnline()) {
            return;
        }
        // Server-authoritative validation for START_RIPTIDE:
        // Only forward if the player legitimately has a Trident with Riptide
        // enchantment AND is in a valid environment (water or exposed to rain).
        // This prevents cheaters from spamming the packet to permanently
        // bypass gravity/fly checks on the proxy side.
        if (action == ServerBoundPlayerCommandActions.START_RIPTIDE) {
            if (!isValidRiptide(player)) {
                return; // discard — do not forward to proxy
            }
        }

        String uid = player.getUniqueId().toString();
        String name = player.getName();
        PacketServerBoundPlayerCommand packet =
            new PacketServerBoundPlayerCommand(timestamp, uid, name, action);
        PacketQueue.push(packet);
        scheduleCommandStateSnapshot(player);
    }

    private void scheduleCommandStateSnapshot(Player player) {
        if (plugin.getSchedulerAdapter() == null) {
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

    /**
     * Validates that a START_RIPTIDE action is legitimate.
     *
     * <p>All three conditions must hold simultaneously:
     * <ol>
     *   <li>Player is holding a Trident in their main hand.</li>
     *   <li>That Trident has the Riptide enchantment (level ≥ 1).</li>
     *   <li>Player is in water OR is exposed to rain in the Overworld.</li>
     * </ol>
     *
     * <p>Every check is fully server-authoritative — the client cannot
     * influence the outcome.
     */
    private boolean isValidRiptide(Player player) {
        // 1. Must be holding a Trident
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.TRIDENT) {
            return false;
        }

        // 2. Trident must have Riptide enchantment
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasEnchant(Enchantment.RIPTIDE)) {
            return false;
        }

        // 3. Valid environment: in water OR exposed to rain
        //    For rain: only Overworld dimensions have weather. We also check
        //    that the block above the player is not occluded (i.e. the player
        //    is actually under the open sky, not sheltered under a roof).
        if (player.isInWater()) {
            return true;
        }

        World world = player.getWorld();
        if (world.hasStorm() && world.getEnvironment() == World.Environment.NORMAL) {
            // getHighestBlockYAt() returns the Y of the topmost non-air block.
            // If the player's block-Y is at or above that height, they are
            // exposed to sky and therefore to rain.
            int highestY = world.getHighestBlockYAt(player.getLocation());
            return player.getLocation().getBlockY() >= highestY;
        }

        return false;
    }

    private ServerBoundPlayerCommandActions mapPlayerAction(
        EnumWrappers.PlayerAction playerAction
    ) {
        if (playerAction == null) {
            return null;
        }

        switch (playerAction) {
            case START_SNEAKING:
                return ServerBoundPlayerCommandActions.START_SNEAKING;
            case STOP_SNEAKING:
                return ServerBoundPlayerCommandActions.STOP_SNEAKING;
            case START_SPRINTING:
                return ServerBoundPlayerCommandActions.START_SPRINTING;
            case STOP_SPRINTING:
                return ServerBoundPlayerCommandActions.STOP_SPRINTING;
            case START_RIDING_JUMP:
                return ServerBoundPlayerCommandActions.START_RIDING_JUMP;
            case STOP_RIDING_JUMP:
                return ServerBoundPlayerCommandActions.STOP_RIDING_JUMP;
            case OPEN_INVENTORY:
                return ServerBoundPlayerCommandActions.OPEN_INVENTORY;
            case START_FALL_FLYING:
                return ServerBoundPlayerCommandActions.START_FALL_FLYING;
            case STOP_SLEEPING:
                return ServerBoundPlayerCommandActions.STOP_SLEEPING;
            default:
                return null;
        }
    }

    private ServerBoundPlayerCommandActions mapActionId(int actionId) {
        // NMS EntityAction ordinals (Minecraft 1.21):
        // 0  = START_SNEAKING,     1  = STOP_SNEAKING,
        // 2  = STOP_SLEEPING,      3  = START_SPRINTING,
        // 4  = STOP_SPRINTING,     5  = START_RIDING_JUMP,
        // 6  = STOP_RIDING_JUMP,   7  = OPEN_INVENTORY,
        // 8  = START_FALL_FLYING,  9  = STOP_FALL_FLYING,
        // 10 = START_SWIMMING,     11 = STOP_SWIMMING,
        // 12 = START_RIPTIDE,      13 = STOP_RIPTIDE,
        // 14 = START_RIDING_BOAT,  15 = STOP_RIDING_BOAT,
        // 16 = START_RIDING_VEHICLE, 17 = STOP_RIDING_VEHICLE
        switch (actionId) {
            case 0:
                return ServerBoundPlayerCommandActions.START_SNEAKING;
            case 1:
                return ServerBoundPlayerCommandActions.STOP_SNEAKING;
            case 2:
                return ServerBoundPlayerCommandActions.STOP_SLEEPING;
            case 3:
                return ServerBoundPlayerCommandActions.START_SPRINTING;
            case 4:
                return ServerBoundPlayerCommandActions.STOP_SPRINTING;
            case 5:
                return ServerBoundPlayerCommandActions.START_RIDING_JUMP;
            case 6:
                return ServerBoundPlayerCommandActions.STOP_RIDING_JUMP;
            case 7:
                return ServerBoundPlayerCommandActions.OPEN_INVENTORY;
            case 8:
                return ServerBoundPlayerCommandActions.START_FALL_FLYING;
            case 9:
                return ServerBoundPlayerCommandActions.STOP_FALL_FLYING;
            case 10:
                return ServerBoundPlayerCommandActions.START_SWIMMING;
            case 11:
                return ServerBoundPlayerCommandActions.STOP_SWIMMING;
            case 12:
                return ServerBoundPlayerCommandActions.START_RIPTIDE;
            case 13:
                return ServerBoundPlayerCommandActions.STOP_RIPTIDE;
            case 14:
                return ServerBoundPlayerCommandActions.START_RIDING_BOAT;
            case 15:
                return ServerBoundPlayerCommandActions.STOP_RIDING_BOAT;
            case 16:
                return ServerBoundPlayerCommandActions.START_RIDING_VEHICLE;
            case 17:
                return ServerBoundPlayerCommandActions.STOP_RIDING_VEHICLE;
            default:
                return null;
        }
    }
}
