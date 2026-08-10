package org.vennv.zeusGateway.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.vennv.packets.PacketMovementStateSnapshot;
import org.vennv.utils.Enchantment;
import org.vennv.utils.Item;
import org.vennv.utils.ItemStack;

class PlayerStateSnapshotServiceTest {
    @Test
    void riptideAirStackWithRiptideBecomesTridentOnlyForActivation() {
        Item air = new Item("", "", new ItemStack("", 0, (byte) 0));

        Item activation = PlayerStateSnapshotService.riptideActivationItem(
                air, Arrays.asList(new Enchantment("riptide", (byte) 3)));

        assertEquals("minecraft:trident", activation.getName());
        assertEquals("minecraft:trident", activation.getItemStack().getId());
        assertEquals(1, activation.getItemStack().getCount());
        assertTrue(PlayerStateSnapshotService.riptideActivationItem(
                air, Collections.singletonList(new Enchantment("loyalty", (byte) 3)))
                .getItemStack().isEmpty());
    }

    @Test
    void capturesFullReplacementStateWithoutClaimingMissingAttributes() {
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.getAllowFlight()).thenReturn(true);
        when(player.isFlying()).thenReturn(true);
        when(player.getFlySpeed()).thenReturn(0.08f);
        when(player.isSprinting()).thenReturn(true);
        when(player.isSneaking()).thenReturn(true);
        when(player.isGliding()).thenReturn(true);
        when(player.isHandRaised()).thenReturn(true);
        when(player.isBlocking()).thenReturn(true);
        when(player.getActivePotionEffects()).thenReturn(Collections.emptyList());

        PacketMovementStateSnapshot.Snapshot snapshot =
                PlayerStateSnapshotService.movementStateSnapshot(player);

        assertEquals(1, snapshot.getGamemode());
        assertFalse(snapshot.getAttributes().isComplete());
        assertTrue(snapshot.getAbilities().canFly());
        assertTrue(snapshot.getAbilities().isFlying());
        assertEquals(0.08f, snapshot.getAbilities().getFlySpeed());
        assertTrue(snapshot.isSprinting());
        assertTrue(snapshot.isSneaking());
        assertTrue(snapshot.isFallFlying());
        assertTrue(snapshot.getUseItem().isUsing());
        assertTrue(snapshot.getUseItem().isBlocking());
        assertTrue(snapshot.getEffects().isEmpty());
        assertNull(snapshot.getVehicle());
    }
}
