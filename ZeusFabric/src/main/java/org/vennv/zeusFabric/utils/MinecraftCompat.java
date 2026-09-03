package org.vennv.zeusFabric.utils;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

public final class MinecraftCompat {
    private MinecraftCompat() {}

    public static World entityWorld(Entity entity) {
        return MinecraftVersionAccess.entityWorld(entity);
    }

    public static Vec3d velocity(EntityVelocityUpdateS2CPacket packet) {
        return MinecraftVersionAccess.velocity(packet);
    }

    public static ItemStack selectedStack(ServerPlayerEntity player) {
        return MinecraftVersionAccess.selectedStack(player);
    }

    public static int gameModeIndex(ServerPlayerEntity player) {
        return MinecraftVersionAccess.gameModeIndex(player);
    }

    public static int serverProtocol(String version) {
        if (version == null) return 0xffff;
        if (version.contains("1.21.11")) return 774;
        if (version.contains("1.21.10") || version.contains("1.21.9")) return 773;
        if (version.contains("1.21.8") || version.contains("1.21.7")) return 772;
        if (version.contains("1.21.6")) return 771;
        if (version.contains("1.21.5")) return 770;
        if (version.contains("1.21.4")) return 769;
        if (version.contains("1.21.2") || version.contains("1.21.3")) return 768;
        if (version.contains("1.21")) return 767;
        return 0xffff;
    }

    public static int clickSyncId(ClickSlotC2SPacket packet) {
        return MinecraftVersionAccess.clickSyncId(packet);
    }

    public static int clickRevision(ClickSlotC2SPacket packet) {
        return MinecraftVersionAccess.clickRevision(packet);
    }

    public static short clickSlot(ClickSlotC2SPacket packet) {
        return MinecraftVersionAccess.clickSlot(packet);
    }

    public static byte clickButton(ClickSlotC2SPacket packet) {
        return MinecraftVersionAccess.clickButton(packet);
    }

    public static short clickActionTypeIndex(ClickSlotC2SPacket packet) {
        return MinecraftVersionAccess.clickActionTypeIndex(packet);
    }

    public static List<Integer> clickModifiedSlotIds(ClickSlotC2SPacket packet) {
        return MinecraftVersionAccess.clickModifiedSlotIds(packet);
    }

    public static boolean isHorseSaddled(Entity vehicle) {
        return MinecraftVersionAccess.isHorseSaddled(vehicle);
    }
}
