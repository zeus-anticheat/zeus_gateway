package org.vennv.zeusGateway.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.vennv.zeusGateway.ZeusGateway;

public final class ZeusDebugCommand implements CommandExecutor, TabCompleter {
    private static final String SELF_PERMISSION = "zeusgateway.debug.self";
    private static final String OTHERS_PERMISSION = "zeusgateway.debug.others";

    private final ZeusGateway plugin;
    private final PacketDebugService service;

    public ZeusDebugCommand(ZeusGateway plugin, PacketDebugService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, args);
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is available to in-game players only.");
            return true;
        }
        Player viewer = (Player) sender;
        if (!viewer.hasPermission(SELF_PERMISSION)) {
            viewer.sendMessage(ChatColor.RED + "You do not have permission to view Zeus packets.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(viewer);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if ("on".equals(action)) {
            return enable(viewer, args);
        }
        if ("off".equals(action)) {
            return disable(viewer);
        }
        if ("status".equals(action)) {
            return status(viewer);
        }
        sendUsage(viewer);
        return true;
    }

    private boolean enable(Player viewer, String[] args) {
        Player target = viewer;
        PacketDebugFilter filter = PacketDebugFilter.actions();

        if (args.length >= 2) {
            Player selected = plugin.getServer().getPlayerExact(args[1]);
            if (selected != null) {
                target = selected;
                if (args.length >= 3) {
                    filter = parseFilter(viewer, args[2]);
                    if (filter == null) {
                        return true;
                    }
                }
            } else if (args.length == 2) {
                filter = parseFilter(viewer, args[1]);
                if (filter == null) {
                    viewer.sendMessage(ChatColor.RED
                            + "Use an online player name or a valid packet filter.");
                    return true;
                }
            } else {
                viewer.sendMessage(ChatColor.RED + "Target player is not online: " + args[1]);
                return true;
            }
        }

        if (!target.getUniqueId().equals(viewer.getUniqueId())
                && !viewer.hasPermission(OTHERS_PERMISSION)) {
            viewer.sendMessage(ChatColor.RED
                    + "You do not have permission to view another player's packets.");
            return true;
        }

        service.subscribe(viewer, target, filter);
        viewer.sendMessage(ChatColor.GRAY + "[Zeus TX] Debug enabled: target="
                + target.getName() + " filter=" + filter.describe()
                + " display=chat (position/vehicle HUD)");
        if ("inventory".equals(filter.describe()) || "all".equals(filter.describe())
                || "clickwindow".equals(filter.describe())) {
            viewer.sendMessage(ChatColor.GRAY
                    + "[Zeus TX] rawState/rawChanged/bukkitCursor are producer diagnostics; "
                    + "the UDP ClickWindow payload remains item/tx fields.");
        }
        return true;
    }

    private PacketDebugFilter parseFilter(Player viewer, String argument) {
        try {
            return PacketDebugFilter.parse(argument);
        } catch (IllegalArgumentException e) {
            viewer.sendMessage(ChatColor.RED + e.getMessage());
            return null;
        }
    }

    private boolean disable(Player viewer) {
        boolean removed = service.unsubscribe(viewer.getUniqueId());
        viewer.sendMessage(ChatColor.GRAY + "[Zeus TX] Debug "
                + (removed ? "disabled." : "was not enabled."));
        return true;
    }

    private boolean status(Player viewer) {
        PacketDebugService.Status status = service.status(viewer.getUniqueId());
        if (status == null) {
            viewer.sendMessage(ChatColor.GRAY + "[Zeus TX] Debug is off.");
        } else {
            viewer.sendMessage(ChatColor.GRAY + "[Zeus TX] Debug is on: target="
                    + status.targetName() + " filter=" + status.filter().describe());
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GRAY + "Usage: /zeusdebug on [player] [filter]");
        player.sendMessage(ChatColor.GRAY + "       /zeusdebug off | /zeusdebug status");
        player.sendMessage(ChatColor.GRAY
                + "Filters: actions (default), movement, inventory, all, or packet name");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        return complete(sender, args);
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        Player player = (Player) sender;
        if (!player.hasPermission(SELF_PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length == 0) {
            return Arrays.asList("on", "off", "status");
        }
        if (args.length == 1) {
            return matches(args[0], Arrays.asList("on", "off", "status"));
        }
        if (!"on".equalsIgnoreCase(args[0])) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            List<String> choices = new ArrayList<>(PacketDebugFilter.suggestions());
            if (player.hasPermission(OTHERS_PERMISSION)) {
                plugin.getServer().getOnlinePlayers().forEach(p -> choices.add(p.getName()));
            }
            return matches(args[1], choices);
        }
        if (args.length == 3) {
            return matches(args[2], PacketDebugFilter.suggestions());
        }
        return Collections.emptyList();
    }

    private List<String> matches(String prefix, List<String> choices) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return choices.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .collect(Collectors.toList());
    }
}
