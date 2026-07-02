package org.vennv.zeusGateway.listener.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.entity.Player;
import org.vennv.packets.PacketPlayerInput;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.provider.PacketQueue;

/**
 * Intercepts Minecraft's {@code STEER_VEHICLE} / {@code PLAYER_INPUT} packets
 * and forwards them as Zeus-compatible input flags.
 *
 * Multi-version layout:
 * <ul>
 *   <li><b>1.21.2+ (new world input)</b>: packet exposes {@code booleans[8]}
 *       (forward, backward, left, right, jump, shift, sprint, reserved).</li>
 *   <li><b>Pre-1.21.2 (legacy steer)</b>: packet exposes {@code floats[2]}
 *       (sideway, forward) and {@code booleans[2]} (jump, unmount). We
 *       derive key flags from sign of sideway/forward plus the jump flag.</li>
 * </ul>
 *
 * Each flag is encoded as a single byte bit-mask (matching
 * {@link PacketPlayerInput}).
 */
public class PacketPlayerInputListener extends PacketAdapter {

    private final ZeusGateway plugin;

    public PacketPlayerInputListener(ZeusGateway plugin, PacketType type) {
        super(plugin, ListenerPriority.LOWEST, type);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        String uid    = player.getUniqueId().toString();
        String name   = player.getName();
        long   ts     = System.currentTimeMillis();

        int flags = 0;
        boolean decoded = false;

        try {
            StructureModifier<Boolean> booleans = event.getPacket().getBooleans();

            // ---- 1.21.2+ path: booleans[8] (forward/backward/left/right/jump/shift/sprint) ----
            if (booleans.size() >= 7) {
                decoded = true;
                if (booleans.read(0)) flags |= 0x01; // forward
                if (booleans.read(1)) flags |= 0x02; // backward
                if (booleans.read(2)) flags |= 0x04; // left
                if (booleans.read(3)) flags |= 0x08; // right
                if (booleans.read(4)) flags |= 0x10; // jump
                if (booleans.read(5)) flags |= 0x20; // shift
                if (booleans.read(6)) flags |= 0x40; // sprint
            }
            // ---- Pre-1.21.2 fallback: booleans[2] + floats[2] ----
            else if (booleans.size() >= 2) {
                decoded = true;
                StructureModifier<Float> floats = event.getPacket().getFloat();
                float sideway = (floats.size() >= 1) ? floats.read(0) : 0f;
                float forward = (floats.size() >= 2) ? floats.read(1) : 0f;

                // Derive directional booleans from signed float values
                if (forward > 0.01f)  flags |= 0x01; // forward
                if (forward < -0.01f) flags |= 0x02; // backward
                if (sideway > 0.01f)  flags |= 0x04; // left
                if (sideway < -0.01f) flags |= 0x08; // right

                // booleans.read(0) == jump flag in legacy STEER_VEHICLE
                if (booleans.read(0)) flags |= 0x10; // jump
                // booleans.read(1) == unmount — not an input flag, skip
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[PacketPlayerInput] Could not decode input flags: " + e.getMessage());
            return;
        }

        if (!decoded) {
            plugin.getLogger().fine("[PacketPlayerInput] Unknown packet layout; keeping previous input state");
            return;
        }

        PacketPlayerInput packet = new PacketPlayerInput(ts, uid, name, (byte) (flags & 0xFF));
        PacketQueue.push(packet);
    }
}
