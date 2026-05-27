package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.vennv.packets.PacketPlayerHeldItem;
import org.vennv.utils.Item;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.task.PlayerStateSnapshotService;
import org.vennv.zeusGateway.utils.ItemUtil;

public class PacketHeldItemListener extends PacketAdapter {
    private final ZeusGateway plugin;

    public PacketHeldItemListener(ZeusGateway plugin) {
        super(plugin, ListenerPriority.LOWEST,
                PacketType.Play.Client.HELD_ITEM_SLOT);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        long timestamp = System.currentTimeMillis();

        int slotIndex = 0;
        try {
            slotIndex = event.getPacket().getIntegers().read(0);
        } catch (Exception ignored) {
        }

        if (plugin.getSchedulerAdapter() == null) {
            return;
        }
        int selectedSlot = slotIndex;
        plugin.getSchedulerAdapter().runEntityTask(
                plugin, player, () -> emitHeldItem(player, selectedSlot, timestamp));
    }

    private void emitHeldItem(Player player, int slotIndex, long timestamp) {
        if (!player.isOnline()) {
            return;
        }
        String uid = player.getUniqueId().toString();
        String name = player.getName();
        ItemStack bukkitItem = player.getInventory().getItem(slotIndex);
        Item item = ItemUtil.protocolItem(bukkitItem);

        PacketPlayerHeldItem packet = new PacketPlayerHeldItem(
                timestamp,
                uid,
                name,
                item);
        PacketQueue.push(packet);
        scheduleMutableStateSnapshot(player);
    }

    private void scheduleMutableStateSnapshot(Player player) {
        if (plugin.getSchedulerAdapter() == null) {
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
