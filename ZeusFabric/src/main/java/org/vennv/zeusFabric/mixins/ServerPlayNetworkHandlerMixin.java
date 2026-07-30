package org.vennv.zeusFabric.mixins;

import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vennv.packets.PacketPlayerInput;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.packets.PacketPlayerClickWindow;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusFabric.ZeusFabricMod;
import org.vennv.zeusFabric.provider.MovementSemantics;
import org.vennv.zeusFabric.provider.PacketQueue;
import org.vennv.zeusFabric.task.PlayerStateSnapshotService;
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
    @Unique private final MovementSemantics.EmissionGate zeus$movementGate = new MovementSemantics.EmissionGate();
    @Unique private long zeus$movementTimestamp;

    @Inject(method = "onPlayerMove", at = @At("HEAD"))
    private void zeus$onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (!zeus$isServerThread() || this.player == null) return;

        boolean hasPosition = packet.changesPosition();
        boolean hasLook = packet.changesLook();
        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        float yaw = packet.getYaw(player.getYaw());
        float pitch = packet.getPitch(player.getPitch());
        if (!zeus$movementGate.begin(true, hasPosition, hasLook, x, y, z, yaw, pitch)) return;

        zeus$movementTimestamp = System.currentTimeMillis();
    }

    @Inject(
        method = "onPlayerMove",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;updatePositionAndAngles(DDDFF)V",
            ordinal = 1,
            shift = At.Shift.AFTER))
    private void zeus$acceptPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        zeus$movementGate.accept();
    }

    @Inject(method = "onPlayerMove", at = @At("RETURN"))
    private void zeus$onPlayerMoveProcessed(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (!zeus$isServerThread() || this.player == null) return;
        zeus$movementGate.complete().ifPresent(this::zeus$emitAcceptedMovement);
    }

    @Unique
    private void zeus$emitAcceptedMovement(MovementSemantics.PacketContext context) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        double eyeHeight = player.getEyeHeight(player.getPose());
        PacketPlayerPosition position = new PacketPlayerPosition(
            zeus$movementTimestamp, player.getUuidAsString(), player.getName().getString(), false,
            x, y, z,
            MovementSemantics.eyeX(x), MovementSemantics.eyeY(y, eyeHeight), MovementSemantics.eyeZ(z),
            yaw, pitch, player.getHeight(), player.isOnGround(),
            PacketPlayerPosition.SOURCE_RAW_CLIENT, context.sequence(), context.hasPosition(), context.hasLook()
        );
        if (context.hasPosition()) {
            PlayerStateSnapshotService.onMovement(player, x, y, z);
        }
        PacketQueue.push(position);
    }

    @Unique
    private static boolean zeus$isServerThread() {
        MinecraftServer server = ZeusFabricMod.getServer();
        return server != null && server.isOnThread();
    }

    @Inject(method = "onClientCommand", at = @At("HEAD"))
    private void zeus$onClientCommand(ClientCommandC2SPacket packet, CallbackInfo ci) {
        if (!zeus$isServerThread() || this.player == null) return;
        
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
            PacketQueue.push(new PacketServerBoundPlayerCommand(
                timestamp, uid, name, action,
                action == ServerBoundPlayerCommandActions.START_RIDING_JUMP
                    ? Math.max(0, Math.min(100, packet.getMountJumpHeight()))
                    : null));
        }
    }

    @Inject(method = "onPlayerInput", at = @At("HEAD"))
    private void zeus$onPlayerInput(PlayerInputC2SPacket packet, CallbackInfo ci) {
        if (!zeus$isServerThread() || this.player == null) return;

        String uid = player.getUuidAsString();
        String name = player.getName().getString();
        long timestamp = System.currentTimeMillis();
        var input = packet.input();
        int flags = 0;
        if (input.forward()) flags |= 0x01;
        if (input.backward()) flags |= 0x02;
        if (input.left()) flags |= 0x04;
        if (input.right()) flags |= 0x08;
        if (input.jump()) flags |= 0x10;
        if (input.sneak()) flags |= 0x20;
        if (input.sprint()) flags |= 0x40;
        PacketQueue.push(new PacketPlayerInput(timestamp, uid, name, MovementSemantics.rawPacketInputFlags(flags)));

        boolean sneaking = input.sneak();
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

    @Inject(method = "onUpdateSelectedSlot", at = @At("TAIL"))
    private void zeus$onUpdateSelectedSlot(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        if (!zeus$isServerThread() || this.player == null) return;
        PlayerStateSnapshotService.sendHeldItemSnapshot(player, false);
        PlayerStateSnapshotService.sendEnchantmentsSnapshot(player, false);
    }

    @Inject(method = "onClickSlot", at = @At("HEAD"))
    private void zeus$onClickSlot(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (!zeus$isServerThread() || this.player == null) return;

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
        if (!zeus$isServerThread() || this.player == null) return;

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
