package org.vennv.zeusGateway.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.junit.jupiter.api.Test;
import org.vennv.packets.PacketUpdateAttributes;

class AttributeCompatTest {
    @Test
    void snapshotAttributeKeepsRawBukkitModifier() {
        AttributeInstance instance = mock(AttributeInstance.class);
        AttributeModifier modifier = mock(AttributeModifier.class);
        UUID modifierId = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        when(instance.getBaseValue()).thenReturn(0.1);
        when(instance.getModifiers()).thenReturn(Collections.singletonList(modifier));
        when(modifier.getUniqueId()).thenReturn(modifierId);
        when(modifier.getKey()).thenReturn(new NamespacedKey("plugin", "item_speed"));
        when(modifier.getName()).thenReturn("item_speed");
        when(modifier.getAmount()).thenReturn(0.3);
        when(modifier.getOperation()).thenReturn(AttributeModifier.Operation.MULTIPLY_SCALAR_1);

        PacketUpdateAttributes.Property property =
                AttributeCompat.toProperty(instance, "minecraft:movement_speed");

        assertEquals("minecraft:movement_speed", property.getKey());
        assertEquals(0.1, property.getBaseValue());
        assertEquals(1, property.getModifiers().size());
        PacketUpdateAttributes.Modifier raw = property.getModifiers().get(0);
        assertEquals(modifierId.toString(), raw.getStableId());
        assertEquals("plugin:item_speed", raw.getName());
        assertEquals(0.3, raw.getAmount());
        assertEquals(PacketUpdateAttributes.Operation.MULTIPLY_TOTAL, raw.getOperation());
    }
}
