package org.vennv.zeusFabric.mixins;

import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.packets.PacketPlayerClickWindow;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusFabric.provider.PacketQueue;
import org.vennv.zeusFabric.task.PlayerStateSnapshotService;
import org.vennv.zeusFabric.utils.BlockUtil;
import org.vennv.zeusFabric.utils.ItemUtil;
import org.vennv.zeusFabric.utils.MinecraftCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow public ServerPlayerEntity player;
    private static final Map<String, Boolean> ZEUS_LAST_SNEAKING = new ConcurrentHashMap<>();

    @Inject(method = "onPlayerMove", at = @At("HEAD"))
    private void zeus$onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (this.player == null) return;
        
        String uid = player.getUuidAsString();
        String name = player.getName().getString();
        long timestamp = System.currentTimeMillis();

        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        float yaw = packet.getYaw(player.getYaw());
        float pitch = packet.getPitch(player.getPitch());
        
        Vec3d packetPos = new Vec3d(x, y, z);
        
        double eyeX = player.getX();
        double eyeY = player.getY() + player.getStandingEyeHeight();
        double eyeZ = player.getZ();
        float height = player.getHeight();
        boolean cancelled = false;

        // Network handlers execute on the server thread; keep all world reads here.
        boolean onGround = BlockUtil.isOnGround(this.player, packetPos);

        PacketPlayerPosition packetPP = new PacketPlayerPosition(
            timestamp, uid, name, cancelled,
            x, y, z, eyeX, eyeY, eyeZ, yaw, pitch, height, onGround
        );
        PacketQueue.push(packetPP);

    }

    @Inject(method = "onClientCommand", at = @At("HEAD"))
    private void zeus$onClientCommand(ClientCommandC2SPacket packet, CallbackInfo ci) {
        if (this.player == null) return;
        
        String uid = player.getUuidAsString();
        String name = player.getName().getString();
        long timestamp = System.currentTimeMillis();

        ServerBoundPlayerCommandActions action = null;
        switch (packet.getMode()) {
            case OPEN_INVENTORY -> action = ServerBoundPlayerCommandActions.OPEN_INVENTORY;
            case STOP_SLEEPING -> action = ServerBoundPlayerCommandActions.STOP_SLEEPING;
            case START_SPRINTING -> action = ServerBoundPlayerCommandActions.START_SPRINTING;
            case STOP_SPRINTING -> action = ServerBoundPlayerCommandActions.STOP_SPRINTING;
            case START_RIDING_JUMP -> action = ServerBoundPlayerCommandActions.START_RIDING_JUMP;
            case STOP_RIDING_JUMP -> action = ServerBoundPlayerCommandActions.STOP_RIDING_JUMP;
            case START_FALL_FLYING -> action = ServerBoundPlayerCommandActions.START_FALL_FLYING;
            default -> action = null;
        }

        if (action != null) {
            PacketQueue.push(new PacketServerBoundPlayerCommand(timestamp, uid, name, action));
        }
    }

    @Inject(method = "onPlayerInput", at = @At("HEAD"))
    private void zeus$onPlayerInput(PlayerInputC2SPacket packet, CallbackInfo ci) {
        if (this.player == null) return;

        String uid = player.getUuidAsString();
        String name = player.getName().getString();
        long timestamp = System.currentTimeMillis();
        boolean sneaking = packet.input().sneak();
        Boolean previous = ZEUS_LAST_SNEAKING.put(uid, sneaking);
        if (previous != null && previous == sneaking) return;

        PacketQueue.push(new PacketServerBoundPlayerCommand(
            timestamp,
            uid,
            name,
            sneaking
                ? ServerBoundPlayerCommandActions.START_SNEAKING
                : ServerBoundPlayerCommandActions.STOP_SNEAKING
        ));
    }

    @Inject(method = "onClickSlot", at = @At("HEAD"))
    private void zeus$onClickSlot(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (this.player == null) return;

        String uid = player.getUuidAsString();
        String name = player.getName().getString();
        long timestamp = System.currentTimeMillis();

        net.minecraft.item.ItemStack clickedStack = net.minecraft.item.ItemStack.EMPTY;
        short slot = MinecraftCompat.clickSlot(packet);
        byte button = MinecraftCompat.clickButton(packet);
        short actionType = MinecraftCompat.clickActionTypeIndex(packet);
        if (slot >= 0 && slot < player.currentScreenHandler.slots.size()) {
            clickedStack = player.currentScreenHandler.slots.get(slot).getStack();
        }

        PacketQueue.push(new PacketPlayerClickWindow(
            timestamp,
            uid,
            name,
            (byte) MinecraftCompat.clickSyncId(packet),
            slot,
            button,
            actionType,
            ItemUtil.protocolStack(clickedStack),
            (short) 0
        ));
    }

    @Inject(method = "onClickSlot", at = @At("TAIL"))
    private void zeus$onClickSlotPost(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (this.player == null) return;

        String uid = player.getUuidAsString();
        String name = player.getName().getString();
        long timestamp = System.currentTimeMillis();

        short slot = MinecraftCompat.clickSlot(packet);
        byte button = MinecraftCompat.clickButton(packet);
        short actionType = MinecraftCompat.clickActionTypeIndex(packet);
        List<Integer> changedSlotIds = new ArrayList<>(MinecraftCompat.clickModifiedSlotIds(packet));
        changedSlotIds.sort(Comparator.naturalOrder());

        List<PacketPlayerInventoryTransaction.ChangedSlot> changedSlots =
            new ArrayList<>(changedSlotIds.size());
        for (int changedSlot : changedSlotIds) {
            changedSlots.add(new PacketPlayerInventoryTransaction.ChangedSlot(
                (short) changedSlot,
                ItemUtil.protocolStack(stackAtSlot(changedSlot))
            ));
        }

        if (changedSlots.isEmpty() && slot >= 0) {
            changedSlots.add(new PacketPlayerInventoryTransaction.ChangedSlot(
                slot,
                ItemUtil.protocolStack(stackAtSlot(slot))
            ));
        }

        PacketQueue.push(new PacketPlayerInventoryTransaction(
            timestamp,
            uid,
            name,
            (byte) MinecraftCompat.clickSyncId(packet),
            MinecraftCompat.clickRevision(packet),
            slot,
            button,
            actionType,
            (short) 0,
            ItemUtil.protocolStack(player.currentScreenHandler.getCursorStack()),
            changedSlots
        ));

        PlayerStateSnapshotService.sendHeldItemSnapshot(player, false);
        PlayerStateSnapshotService.sendArmorSnapshot(player, false);
        PlayerStateSnapshotService.sendEnchantmentsSnapshot(player, false);
    }

    private net.minecraft.item.ItemStack stackAtSlot(int slot) {
        if (slot >= 0 && slot < player.currentScreenHandler.slots.size()) {
            return player.currentScreenHandler.slots.get(slot).getStack();
        }
        return net.minecraft.item.ItemStack.EMPTY;
    }
}
