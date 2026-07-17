package org.vennv.zeusFabric.mixins;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vennv.packets.CaptureFrameV3;
import org.vennv.packets.PacketPlayerInput;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.packets.PacketPlayerClickWindow;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusFabric.ZeusFabricMod;
import org.vennv.zeusFabric.listener.ZeusEventListeners;
import org.vennv.zeusFabric.provider.CaptureIdentity;
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
import java.util.stream.Collectors;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow public ServerPlayerEntity player;
    private static final Map<String, Boolean> ZEUS_LAST_SNEAKING = new ConcurrentHashMap<>();
    @Unique private final MovementSemantics.EmissionGate zeus$movementGate = new MovementSemantics.EmissionGate();
    @Unique private long zeus$movementTimestamp;
    @Unique private double zeus$beforeX;
    @Unique private double zeus$beforeY;
    @Unique private double zeus$beforeZ;
    @Unique private Vec3d zeus$velocityBefore = Vec3d.ZERO;
    @Unique private boolean zeus$previousOnGround;
    @Unique private boolean zeus$hasRawInput;
    @Unique private int zeus$rawInputFlags;
    @Unique private MovementSemantics.PacketContext zeus$movementContext;

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
        zeus$beforeX = player.getX();
        zeus$beforeY = player.getY();
        zeus$beforeZ = player.getZ();
        zeus$velocityBefore = player.getVelocity();
        zeus$previousOnGround = player.isOnGround();
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
        zeus$movementGate.complete().ifPresent(context -> zeus$emitAcceptedMovement(packet, context));
    }

    @Unique
    private void zeus$emitAcceptedMovement(PlayerMoveC2SPacket packet, MovementSemantics.PacketContext context) {
        zeus$movementContext = context;
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
            yaw, pitch, player.getHeight(), packet.isOnGround(),
            PacketPlayerPosition.SOURCE_RAW_CLIENT, context.sequence(), context.hasPosition(), context.hasLook()
        );
        if (ZeusEventListeners.isCaptureActive()) {
            PacketQueue.pushAll(List.of(position, zeus$captureFrame(packet)));
        } else {
            PacketQueue.push(position);
        }
        if (context.hasPosition()) {
            PlayerStateSnapshotService.onMovement(player, x, y, z);
        }
    }

    @Unique
    private CaptureFrameV3 zeus$captureFrame(PlayerMoveC2SPacket packet) {
        MovementSemantics.PacketContext context = zeus$movementContext;
        if (context == null) {
            throw new IllegalStateException("missing movement context");
        }
        boolean hasPosition = context.hasPosition();
        boolean hasLook = context.hasLook();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        Vec3d velocityAfter = player.getVelocity();
        BlockState support = MinecraftCompat.entityWorld(player).getBlockState(player.getVelocityAffectingPos());
        BlockState body = MinecraftCompat.entityWorld(player).getBlockState(player.getBlockPos());
        double baseSpeed = player.getAttributeBaseValue(EntityAttributes.MOVEMENT_SPEED);
        int previousStateFlags = zeus$previousOnGround ? 0x01 : 0;
        int stateFlags = zeus$stateFlags(packet.isOnGround());
        long presence = CaptureFrameV3.PRESENCE_IDENTITY
            | CaptureFrameV3.PRESENCE_VELOCITY
            | CaptureFrameV3.PRESENCE_BOUNDING_BOX
            | CaptureFrameV3.PRESENCE_BLOCK_CONTEXT
            | CaptureFrameV3.PRESENCE_ATTRIBUTES
            | CaptureFrameV3.PRESENCE_EFFECTS
            | CaptureFrameV3.PRESENCE_PING;
        if (hasPosition) presence |= CaptureFrameV3.PRESENCE_POSITION;
        if (hasLook) presence |= CaptureFrameV3.PRESENCE_LOOK;
        if (zeus$hasRawInput) presence |= CaptureFrameV3.PRESENCE_DIRECTIONAL_INPUT;
        Entity vehicle = player.getVehicle();
        if (vehicle != null) presence |= CaptureFrameV3.PRESENCE_VEHICLE;

        CaptureFrameV3.Builder frame = CaptureFrameV3.builder();
        frame.timestamp = zeus$movementTimestamp;
        frame.presenceMask = presence;
        frame.validityMask = presence;
        frame.adapterId = "fabric";
        frame.adapterVersion = CaptureFrameV3.configuredAdapterVersion();
        frame.adapterCapabilityBitmap = CaptureFrameV3.configuredCapabilityBitmap("fabric")
            | CaptureFrameV3.CAPABILITY_NO_POSITION_MOVEMENT
            | CaptureFrameV3.CAPABILITY_DIRECTIONAL_INPUT;
        frame.behaviorBundleHash = CaptureFrameV3.configuredBehaviorBundleHash(ZeusFabricMod.getServer().getVersion());
        frame.captureSessionId = System.getProperty("zeus.capture.session", "");
        frame.captureSubjectId = CaptureIdentity.captureSubjectId(player.getUuidAsString());
        frame.playerHash = CaptureIdentity.playerHash(player.getUuidAsString());
        frame.serverProtocol = MinecraftCompat.serverProtocol(ZeusFabricMod.getServer().getVersion());
        frame.clientProtocol = 0;
        frame.serverVersion = ZeusFabricMod.getServer().getVersion();
        frame.clientVersion = "";
        frame.serverBrand = "fabric";
        frame.platform = "fabric";
        frame.modPluginFingerprint = System.getProperty("zeus.physics.fingerprint", "vanilla");
        frame.effectiveBehaviorFingerprint = System.getenv().getOrDefault("ZEUS_TRANSLATION_BEHAVIOR_FINGERPRINT", "");
        frame.serverTick = player.age;
        frame.movementSequence = context.sequence();
        frame.inclusionFlags = context.inclusionFlags();
        frame.pingMs = player.networkHandler.getLatency();
        frame.posBeforeX = zeus$beforeX;
        frame.posBeforeY = zeus$beforeY;
        frame.posBeforeZ = zeus$beforeZ;
        frame.posDeltaX = hasPosition ? (float) (x - zeus$beforeX) : 0.0f;
        frame.posDeltaY = hasPosition ? (float) (y - zeus$beforeY) : 0.0f;
        frame.posDeltaZ = hasPosition ? (float) (z - zeus$beforeZ) : 0.0f;
        frame.velocityBeforeX = (float) zeus$velocityBefore.x;
        frame.velocityBeforeY = (float) zeus$velocityBefore.y;
        frame.velocityBeforeZ = (float) zeus$velocityBefore.z;
        frame.velocityAfterX = (float) velocityAfter.x;
        frame.velocityAfterY = (float) velocityAfter.y;
        frame.velocityAfterZ = (float) velocityAfter.z;
        frame.yaw = yaw;
        frame.pitch = pitch;
        frame.lookX = zeus$lookX(yaw, pitch);
        frame.lookY = zeus$lookY(pitch);
        frame.lookZ = zeus$lookZ(yaw, pitch);
        frame.pose = player.getPose().toString().toLowerCase(java.util.Locale.ROOT);
        frame.boundingWidth = player.getWidth();
        frame.boundingHeight = player.getHeight();
        frame.eyeHeight = player.getEyeHeight(player.getPose());
        frame.inputFlags = zeus$rawInputFlags;
        frame.forwardInput = zeus$axis(zeus$rawInputFlags, 0x01, 0x02);
        frame.sidewaysInput = zeus$axis(zeus$rawInputFlags, 0x04, 0x08);
        frame.previousStateFlags = previousStateFlags;
        frame.stateFlags = stateFlags;
        frame.supportBlockId = Registries.BLOCK.getId(support.getBlock()).toString();
        frame.bodyBlockId = Registries.BLOCK.getId(body.getBlock()).toString();
        frame.friction = support.getBlock().getSlipperiness();
        frame.speedFactor = support.getBlock().getVelocityMultiplier();
        frame.jumpFactor = support.getBlock().getJumpVelocityMultiplier();
        frame.surfaceKind = zeus$surfaceKind(frame.supportBlockId);
        frame.dimension = MinecraftCompat.entityWorld(player).getRegistryKey().getValue().toString();
        if (Double.isFinite(baseSpeed) && baseSpeed > 0.0) {
            frame.attributes.add(new CaptureFrameV3.NamedFloat("minecraft:generic.movement_speed", (float) baseSpeed));
        }
        player.getStatusEffects().stream()
            .sorted(Comparator.comparing(effect -> Registries.STATUS_EFFECT.getId(effect.getEffectType().value()).toString()))
            .forEach(effect -> frame.effects.add(new CaptureFrameV3.NamedLevel(
                Registries.STATUS_EFFECT.getId(effect.getEffectType().value()).toString(),
                (byte) Math.min(255, effect.getAmplifier() + 1))));
        if (vehicle != null) {
            frame.vehicleType = Registries.ENTITY_TYPE.getId(vehicle.getType()).toString();
            frame.vehicleId = vehicle.getId();
        }
        return frame.build();
    }

    @Unique
    private int zeus$stateFlags(boolean onGround) {
        int flags = onGround ? 0x01 : 0;
        if (player.isSprinting()) flags |= 0x02;
        if (player.isSwimming()) flags |= 0x04;
        if (player.isSneaking()) flags |= 0x08;
        if (player.isClimbing()) flags |= 0x10;
        if (player.isGliding()) flags |= 0x20;
        return flags;
    }

    @Unique
    private static float zeus$axis(int flags, int positive, int negative) {
        return (flags & positive) != 0 ? ((flags & negative) != 0 ? 0.0f : 1.0f) : ((flags & negative) != 0 ? -1.0f : 0.0f);
    }

    @Unique
    private static float zeus$lookX(float yaw, float pitch) {
        return (float) (-Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
    }

    @Unique
    private static float zeus$lookY(float pitch) {
        return (float) -Math.sin(Math.toRadians(pitch));
    }

    @Unique
    private static float zeus$lookZ(float yaw, float pitch) {
        return (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
    }

    @Unique
    private static String zeus$surfaceKind(String id) {
        if (id.contains("ice")) return "ice";
        if (id.contains("slime")) return "slime";
        if (id.contains("honey")) return "honey";
        if (id.contains("soul_sand") || id.contains("soul_soil")) return "soul_sand";
        if (id.contains("web")) return "cobweb";
        return "other";
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
            PacketQueue.push(new PacketServerBoundPlayerCommand(timestamp, uid, name, action));
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
        zeus$rawInputFlags = flags;
        zeus$hasRawInput = true;
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
