package org.vennv.zeusGateway.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.mockito.ArgumentCaptor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.vennv.PacketId;
import org.vennv.packets.PacketPlayerClickWindow;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketServerBoundPlayerCommand;
import org.vennv.utils.ItemStack;
import org.vennv.utils.ServerBoundPlayerCommandActions;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.platform.SchedulerAdapter;

class PacketDebugTest {
    private static final UUID TARGET_ID = UUID.fromString("b4abb7c5-345a-42c1-8b9d-7a46b1b24432");

    @Test
    void formatterShowsStableClickItemAndCommandFields() {
        PacketPlayerClickWindow click = new PacketPlayerClickWindow(
                1L, TARGET_ID.toString(), "Venn", (byte) 4, (short) 12,
                (byte) 1, (short) 0,
                new ItemStack("minecraft:diamond_sword", 0, (byte) 1),
                (short) 0);
        PacketServerBoundPlayerCommand command = new PacketServerBoundPlayerCommand(
                1L, TARGET_ID.toString(), "Venn",
                ServerBoundPlayerCommandActions.START_SPRINTING);

        String clickText = PacketDebugFormatter.format(click);
        assertTrue(clickText.contains("ClickWindow target=Venn window=4 slot=12 button=1"));
        assertTrue(clickText.contains("mode=PICKUP tx=0 item=minecraft:diamond_sword meta=0 count=1"));
        assertTrue(PacketDebugFormatter.format(command).contains("action=START_SPRINTING"));

        String enriched = PacketDebugFormatter.format(new PacketDebugEnvelope(
                click, " rawState=12 rawChanged=[5=minecraft:stonex2]"));
        assertTrue(enriched.contains("rawState=12 rawChanged=[5=minecraft:stonex2]"));
    }

    @Test
    void inventoryTransactionEncodesDetailsAndIsFilterable() throws IOException {
        PacketPlayerInventoryTransaction transaction = new PacketPlayerInventoryTransaction(
                1L,
                TARGET_ID.toString(),
                "Venn",
                (byte) 4,
                123,
                (short) 9,
                (byte) 1,
                (short) 0,
                (short) 17,
                new ItemStack("", 0, (byte) 0),
                Arrays.asList(
                        new PacketPlayerInventoryTransaction.ChangedSlot(
                                (short) 9,
                                new ItemStack("minecraft:stone", 0, (byte) 63)),
                        new PacketPlayerInventoryTransaction.ChangedSlot(
                                (short) 37,
                                new ItemStack("minecraft:diamond", 0, (byte) 1))));

        String formatted = PacketDebugFormatter.format(transaction);
        assertTrue(formatted.contains("InventoryTransaction target=Venn window=4 state=123"));
        assertTrue(formatted.contains("changed=2 cursor=empty:0x0"));
        assertTrue(PacketDebugFilter.actions().matches(transaction));
        assertTrue(PacketDebugFilter.parse("inventory").matches(transaction));
        assertTrue(PacketDebugFilter.parse("inventorytx").matches(transaction));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transaction.encode(out);
        ByteBuffer buf = ByteBuffer.wrap(out.toByteArray());
        assertEquals(PacketId.PACKET_PLAYER_INVENTORY_TRANSACTION, buf.get());
        assertEquals(1L, buf.getLong());
        assertEquals(TARGET_ID.toString(), readString(buf));
        assertEquals("Venn", readString(buf));
        assertEquals(0, Byte.toUnsignedInt(buf.get())); // protocol_version: None
        assertEquals(4, Byte.toUnsignedInt(buf.get()));
        assertEquals(123, buf.getInt());
        assertEquals(9, buf.getShort());
        assertEquals(1, Byte.toUnsignedInt(buf.get()));
        assertEquals(0, buf.getShort());
        assertEquals(17, buf.getShort());
        assertEquals("", readString(buf));
        assertEquals(0, buf.getInt());
        assertEquals(0, Byte.toUnsignedInt(buf.get()));
        assertEquals(2,  buf.getInt());
        assertEquals(9, buf.getShort());
        assertEquals("minecraft:stone", readString(buf));
        assertEquals(0, buf.getInt());
        assertEquals(63, Byte.toUnsignedInt(buf.get()));
        assertEquals(37, buf.getShort());
        assertEquals("minecraft:diamond", readString(buf));
        assertEquals(0, buf.getInt());
        assertEquals(1, Byte.toUnsignedInt(buf.get()));
        assertFalse(buf.hasRemaining());
    }

    @Test
    void producerDetailsDoNotChangeEncodedUdpBytes() throws IOException {
        PacketPlayerClickWindow click = new PacketPlayerClickWindow(
                1L, TARGET_ID.toString(), "Venn", (byte) 4, (short) 12,
                (byte) 1, (short) 0,
                new ItemStack("minecraft:diamond_sword", 0, (byte) 1),
                (short) 0);
        ByteArrayOutputStream direct = new ByteArrayOutputStream();
        ByteArrayOutputStream wrapped = new ByteArrayOutputStream();

        click.encode(direct);
        new PacketDebugEnvelope(click, " rawChanged=[12=minecraft:stonex1]").encode(wrapped);

        assertArrayEquals(direct.toByteArray(), wrapped.toByteArray());
    }

    @Test
    void actionsAndSpecificFiltersExcludePositionUnlessRequested() {
        PacketPlayerPosition position = new PacketPlayerPosition(
                1L, TARGET_ID.toString(), "Venn", false, 1, 2, 3, 1, 2, 3,
                0, 0, 1.8f, true);
        PacketPlayerClickWindow click = new PacketPlayerClickWindow(
                1L, TARGET_ID.toString(), "Venn", (byte) 1, (short) 1,
                (byte) 0, (short) 0, new ItemStack("", 0, (byte) 0), (short) 0);

        assertTrue(PacketDebugFilter.actions().matches(click));
        assertFalse(PacketDebugFilter.actions().matches(position));
        assertTrue(PacketDebugFilter.parse("all").matches(position));
        assertTrue(PacketDebugFilter.parse("position").matches(position));
        assertFalse(PacketDebugFilter.parse("position").matches(click));
        assertTrue(PacketDebugFilter.parse("movement").matches(position));
        assertTrue(PacketDebugFilter.parse("inventory").matches(new PacketDebugEnvelope(click, "")));
        assertTrue(PacketDebugFormatter.format(position).contains("xyz=1.00/2.00/3.00"));
    }

    @Test
    void actionPacketsRemainInChatAndIncludeLatestPositionContext() {
        ZeusGateway plugin = mock(ZeusGateway.class);
        SchedulerAdapter scheduler = mock(SchedulerAdapter.class);
        Player viewer = mock(Player.class);
        Player.Spigot popup = mock(Player.Spigot.class);
        Player target = mock(Player.class);
        when(plugin.getSchedulerAdapter()).thenReturn(scheduler);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.spigot()).thenReturn(popup);
        when(target.getUniqueId()).thenReturn(TARGET_ID);
        when(target.getName()).thenReturn("Venn");
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(scheduler).runEntityTask(any(), any(), any());

        PacketDebugService service = new PacketDebugService(plugin);
        service.subscribe(viewer, target, PacketDebugFilter.actions());
        service.onPacketTransmitted(new PacketPlayerPosition(
                1L, TARGET_ID.toString(), "Venn", false, 1, 2, 3, 1, 2, 3,
                0, 0, 1.8f, true));
        service.onPacketTransmitted(new PacketServerBoundPlayerCommand(
                1L, TARGET_ID.toString(), "Venn",
                ServerBoundPlayerCommandActions.START_SNEAKING));
        service.onPacketTransmitted(new PacketServerBoundPlayerCommand(
                1L, UUID.randomUUID().toString(), "Elsewhere",
                ServerBoundPlayerCommandActions.START_SNEAKING));

        verify(viewer).sendMessage(contains("target=Venn"));
        verify(viewer).sendMessage(contains("lastPos=1.00/2.00/3.00"));
        verify(popup, never()).sendMessage(eq(ChatMessageType.ACTION_BAR), any(BaseComponent[].class));
        verify(viewer, never()).sendMessage(contains("target=Elsewhere"));
    }

    @Test
    void movementPacketsUseACompactHudPopup() {
        ZeusGateway plugin = mock(ZeusGateway.class);
        SchedulerAdapter scheduler = mock(SchedulerAdapter.class);
        Player viewer = mock(Player.class);
        Player.Spigot popup = mock(Player.Spigot.class);
        Player target = mock(Player.class);
        when(plugin.getSchedulerAdapter()).thenReturn(scheduler);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.spigot()).thenReturn(popup);
        when(target.getUniqueId()).thenReturn(TARGET_ID);
        when(target.getName()).thenReturn("Venn");
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(scheduler).runEntityTask(any(), any(), any());

        PacketDebugService service = new PacketDebugService(plugin);
        service.subscribe(viewer, target, PacketDebugFilter.parse("movement"));
        service.onPacketTransmitted(new PacketPlayerPosition(
                1L, TARGET_ID.toString(), "Venn", false, 1, 2, 3, 1, 2, 3,
                90, 5, 1.8f, true));

        ArgumentCaptor<BaseComponent[]> content = ArgumentCaptor.forClass(BaseComponent[].class);
        verify(popup).sendMessage(eq(ChatMessageType.ACTION_BAR), content.capture());
        String hud = BaseComponent.toPlainText(content.getValue());
        assertTrue(hud.contains("[Z POS] Venn 1.00 / 2.00 / 3.00"));
        assertTrue(hud.contains("yaw 90.0 pitch 5.0"));
        verify(viewer, never()).sendMessage(any(String.class));
    }

    private static String readString(ByteBuffer buf) {
        byte[] bytes = new byte[Short.toUnsignedInt(buf.getShort())];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
