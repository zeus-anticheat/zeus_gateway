package org.vennv.zeusGateway.debug;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.vennv.PacketEncode;

public final class PacketDebugFilter {
    private static final Set<String> ACTION_PACKETS = setOf(
            "helditem",
            "armorsequipment",
            "clickwindow",
            "inventorytransaction",
            "blockface",
            "useitem",
            "releaseuseitem",
            "swinghand",
            "entityinteraction",
            "attackentity",
            "externalforce",
            "vehiclemove",
            "playercommand",
            "shulkerboxaction");
    private static final Set<String> MOVEMENT_PACKETS = setOf(
            "position", "teleport", "vehiclemove", "velocity", "externalforce");
    private static final Set<String> INVENTORY_PACKETS = setOf(
            "helditem", "armorsequipment", "openwindow", "clickwindow", "inventorytransaction",
            "closewindow", "confirmtransaction", "enchantments");

    private static final Set<String> PACKET_NAMES = setOf(
            "join", "leave", "position", "keepalive", "changemode", "swinghand",
            "attackentity", "teleport", "effect",
            "gotdamage", "blockface", "blockraytrace", "blockchangeack",
            "attackedbyentity", "entityinteraction", "surroundingblocks", "helditem",
            "armorsequipment", "confirmtransaction", "openwindow", "clickwindow",
            "inventorytransaction", "closewindow", "useitem", "releaseuseitem", "steervehicle", "vehiclemove",
            "playercommand", "death", "customfeature", "attackedbyplayer", "velocity",
            "externalforce", "enchantments", "respawn", "serverconfig", "shulkerboxaction");

    private static final Map<String, String> ALIASES = createAliases();

    private enum Mode {
        ACTIONS,
        MOVEMENT,
        INVENTORY,
        ALL,
        SINGLE
    }

    private final Mode mode;
    private final String packetName;

    private PacketDebugFilter(Mode mode, String packetName) {
        this.mode = mode;
        this.packetName = packetName;
    }

    public static PacketDebugFilter actions() {
        return new PacketDebugFilter(Mode.ACTIONS, null);
    }

    public static PacketDebugFilter parse(String input) {
        if (input == null || input.trim().isEmpty() || "actions".equalsIgnoreCase(input)) {
            return actions();
        }
        if ("all".equalsIgnoreCase(input)) {
            return new PacketDebugFilter(Mode.ALL, null);
        }
        if ("movement".equalsIgnoreCase(input)) {
            return new PacketDebugFilter(Mode.MOVEMENT, null);
        }
        if ("inventory".equalsIgnoreCase(input) || "transactions".equalsIgnoreCase(input)) {
            return new PacketDebugFilter(Mode.INVENTORY, null);
        }

        String normalized = normalize(input);
        String alias = ALIASES.get(normalized);
        normalized = alias == null ? normalized : alias;
        if (!PACKET_NAMES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown packet filter: " + input);
        }
        return new PacketDebugFilter(Mode.SINGLE, normalized);
    }

    public boolean matches(PacketEncode packet) {
        String name = normalize(PacketDebugFormatter.packetName(packet));
        switch (mode) {
            case ALL:
                return true;
            case ACTIONS:
                return ACTION_PACKETS.contains(name);
            case MOVEMENT:
                return MOVEMENT_PACKETS.contains(name);
            case INVENTORY:
                return INVENTORY_PACKETS.contains(name);
            case SINGLE:
                return packetName.equals(name);
            default:
                return false;
        }
    }

    public String describe() {
        switch (mode) {
            case ACTIONS:
                return "actions";
            case MOVEMENT:
                return "movement";
            case INVENTORY:
                return "inventory";
            case ALL:
                return "all";
            case SINGLE:
                return packetName;
            default:
                return "actions";
        }
    }

    public static List<String> suggestions() {
        return Arrays.asList("actions", "movement", "inventory", "all",
                "helditem", "armor", "clickwindow",
                "placeblock", "diggingblock", "blockface", "useitem",
                "releaseuseitem", "swinghand", "entityinteraction",
                "attackentity", "externalforce", "vehiclemove", "playercommand", "shulkerboxaction",
                "position", "teleport", "velocity", "blockraytrace", "steervehicle");
    }

    private static String normalize(String input) {
        String name = input == null ? "" : input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (name.startsWith("packetplayer")) {
            name = name.substring("packetplayer".length());
        } else if (name.startsWith("packetserverbound")) {
            name = name.substring("packetserverbound".length());
        } else if (name.startsWith("packet")) {
            name = name.substring("packet".length());
        }
        return name;
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }

    private static Map<String, String> createAliases() {
        Map<String, String> aliases = new HashMap<String, String>();
        aliases.put("armor", "armorsequipment");
        aliases.put("armors", "armorsequipment");
        aliases.put("command", "playercommand");
        aliases.put("inventorytx", "inventorytransaction");
        aliases.put("interactentity", "entityinteraction");
        aliases.put("releaseitem", "releaseuseitem");
        aliases.put("shulker", "shulkerboxaction");
        return Collections.unmodifiableMap(aliases);
    }
}
