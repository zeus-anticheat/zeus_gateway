package org.vennv.zeusGateway.listener.packets;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.vennv.packets.PacketPlayerHeldItem;
import org.vennv.utils.Item;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.task.PlayerStateSnapshotService;
import org.vennv.zeusGateway.utils.ItemUtil;

public class PacketHeldItemListener extends PacketListenerAbstract {
    private final ZeusGateway plugin;
    private final OrderedPlayerPacketDispatcher dispatcher;

    public PacketHeldItemListener(ZeusGateway plugin, OrderedPlayerPacketDispatcher dispatcher) {
        super(PacketListenerPriority.LOWEST);
        this.plugin = plugin;
        this.dispatcher = dispatcher;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.HELD_ITEM_CHANGE) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        int slotIndex = new WrapperPlayClientHeldItemChange(event).getSlot();
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        dispatcher.submit(player, () -> emitHeldItem(player, slotIndex, timestamp));
    }

    private void emitHeldItem(Player player, int slotIndex, long timestamp) {
        if (!player.isOnline() || slotIndex < 0 || slotIndex >= player.getInventory().getSize()) {
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
        plugin.getSchedulerAdapter().runEntityTaskLater(plugin, player, () -> {
            if (player.isOnline()) {
                PlayerStateSnapshotService.sendMutableStateSnapshot(player);
            }
        }, 1L);
    }
}
