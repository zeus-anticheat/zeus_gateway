package org.vennv.zeusGateway.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.vennv.zeusGateway.ZeusGateway;

class ZeusDebugCommandTest {
    @Test
    void paperSuggestionRequestMaySupplyNoArguments() {
        ZeusGateway plugin = mock(ZeusGateway.class);
        Player sender = mock(Player.class);
        when(sender.hasPermission("zeusgateway.debug.self")).thenReturn(true);

        ZeusDebugCommand command = new ZeusDebugCommand(
                plugin, new PacketDebugService(plugin));

        assertEquals(Arrays.asList("on", "off", "status"), command.complete(sender, new String[0]));
    }
}
