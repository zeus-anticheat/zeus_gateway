package org.vennv.zeusGateway.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
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
}
