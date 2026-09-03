package org.vennv.zeusFabric.utils;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

final class MinecraftVersionAccess {
    private MinecraftVersionAccess() {}

    public static World entityWorld(Entity entity) {
        return entity.getWorld();
    }

    public static Vec3d velocity(EntityVelocityUpdateS2CPacket packet) {
        return new Vec3d(packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ());
    }

    public static ItemStack selectedStack(ServerPlayerEntity player) {
        return player.getInventory().getSelectedStack();
    }

    public static int gameModeIndex(ServerPlayerEntity player) {
        return player.interactionManager.getGameMode().getIndex();
    }

    public static int clickSyncId(ClickSlotC2SPacket packet) {
        return packet.syncId();
    }

    public static int clickRevision(ClickSlotC2SPacket packet) {
        return packet.revision();
    }

    public static short clickSlot(ClickSlotC2SPacket packet) {
        return packet.slot();
    }

    public static byte clickButton(ClickSlotC2SPacket packet) {
        return packet.button();
    }

    public static short clickActionTypeIndex(ClickSlotC2SPacket packet) {
        return (short) packet.actionType().ordinal();
    }

    public static List<Integer> clickModifiedSlotIds(ClickSlotC2SPacket packet) {
        return new ArrayList<>(packet.modifiedStacks().keySet());
    }

    public static boolean isHorseSaddled(Entity vehicle) {
        return vehicle instanceof net.minecraft.entity.passive.AbstractHorseEntity horse && horse.hasStackEquipped(net.minecraft.entity.EquipmentSlot.SADDLE);
    }
}
