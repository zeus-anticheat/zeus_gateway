package org.vennv.zeusPhysicsLab;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ZeusPhysicsLab extends JavaPlugin implements CommandExecutor, TabCompleter {
    private final List<Station> stations = StationCatalog.all();
    private LabGenerator generator;

    @Override
    public void onEnable() {
        this.generator = new LabGenerator(this, stations);
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new LabListener(stations, generator), this);
        getConfig().addDefault("origin.explicit", false);
        getConfig().addDefault("origin.x", 0);
        getConfig().addDefault("origin.y", 80);
        getConfig().addDefault("origin.z", 0);
        getConfig().addDefault("world", "");
        getConfig().options().copyDefaults(true);
        saveConfig();
        if (getCommand("zeuslab") != null) {
            getCommand("zeuslab").setExecutor(this);
            getCommand("zeuslab").setTabCompleter(this);
        }
        getLogger().info("ZeusPhysicsLab loaded with " + stations.size() + " stations.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        World world = resolveWorld(sender);
        int originX = getConfig().getInt("origin.x", 0);
        int originY = getConfig().getInt("origin.y", 80);
        int originZ = getConfig().getInt("origin.z", 0);
        if (sender instanceof Player player && !hasConfiguredOrigin()) {
            Location loc = player.getLocation();
            originX = loc.getBlockX();
            originY = loc.getBlockY() - 1; // Align floor with the block under the player
            originZ = loc.getBlockZ();
        }

        switch (action) {
            case "generate" -> {
                generator.generate(sender, world, originX, originY, originZ);
                return true;
            }
            case "reset" -> {
                generator.reset(sender, world, originX, originY, originZ);
                return true;
            }
            case "list" -> {
                listStations(sender, args.length > 1 ? args[1] : null);
                return true;
            }
            case "tp" -> {
                teleportToStation(sender, world, originX, originY, originZ, args);
                return true;
            }
            case "manifest" -> {
                writeManifest(world.getName(), originX, originY, originZ);
                sender.sendMessage("Manifest written to " + getDataFolder().toPath().resolve("zeus_physics_lab_manifest.json"));
                return true;
            }
            case "verify" -> {
                LabGenerator.VerificationResult result = generator.verify(world, originX, originY, originZ);
                sender.sendMessage("Stations expected: " + result.expectedStations());
                sender.sendMessage("Station signs found: " + result.signsFound());
                sender.sendMessage("Start command blocks found: " + result.commandBlocksFound());
                sender.sendMessage(result.signsFound() == result.expectedStations() && result.commandBlocksFound() == result.expectedStations()
                    ? "Verification passed."
                    : "Verification incomplete; run /zeuslab generate or inspect missing plots.");
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private boolean hasConfiguredOrigin() {
        return getConfig().getBoolean("origin.explicit", false);
    }
    public void writeManifest(String worldName, int originX, int originY, int originZ) {
        getDataFolder().mkdirs();
        Path manifest = getDataFolder().toPath().resolve("zeus_physics_lab_manifest.json");
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"world\": \"").append(worldName).append("\",\n");
        builder.append("  \"origin\": {\"x\": ").append(originX).append(", \"y\": ").append(originY).append(", \"z\": ").append(originZ).append("},\n");
        builder.append("  \"route_execution\": \"manual_real_player\",\n");
        builder.append("  \"station_count\": ").append(stations.size()).append(",\n");
        builder.append("  \"stations\": [\n");
        for (int i = 0; i < stations.size(); i++) {
            builder.append("    ").append(stations.get(i).manifestJson());
            if (i + 1 < stations.size()) builder.append(',');
            builder.append('\n');
        }
        builder.append("  ]\n");
        builder.append("}\n");
        try {
            Files.writeString(manifest, builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().warning("Failed to write manifest: " + e.getMessage());
        }
    }

    private World resolveWorld(CommandSender sender) {
        String configured = getConfig().getString("world", "");
        if (configured != null && !configured.isBlank() && Bukkit.getWorld(configured) != null) {
            return Bukkit.getWorld(configured);
        }
        if (sender instanceof Player player) {
            return player.getWorld();
        }
        return Bukkit.getWorlds().get(0);
    }

    private void listStations(CommandSender sender, @Nullable String filter) {
        String normalized = filter == null ? "" : filter.toUpperCase(Locale.ROOT);
        int shown = 0;
        for (Station station : stations) {
            if (!normalized.isBlank()
                && !station.id().contains(normalized)
                && !station.category().name().contains(normalized)) {
                continue;
            }
            sender.sendMessage(station.number() + ". " + station.id() + " [" + station.category() + "] " + station.description());
            shown++;
            if (shown >= 20) {
                sender.sendMessage("Showing 20 stations. Add a category or id filter for more.");
                break;
            }
        }
        if (shown == 0) sender.sendMessage("No stations matched filter: " + filter);
    }

    private void teleportToStation(CommandSender sender, World world, int originX, int originY, int originZ, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /zeuslab tp.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /zeuslab tp <station_id_or_number>");
            return;
        }
        Station station = findStation(args[1]);
        if (station == null) {
            sender.sendMessage("Unknown station: " + args[1]);
            return;
        }
        Location location = generator.stationLocation(world, originX, originY, originZ, station);
        player.teleport(location);
        player.sendMessage("Teleported to " + station.id() + ": " + station.description());
    }

    private Station findStation(String key) {
        try {
            int number = Integer.parseInt(key);
            return stations.stream().filter(station -> station.number() == number).findFirst().orElse(null);
        } catch (NumberFormatException ignored) {
            String normalized = key.toUpperCase(Locale.ROOT);
            return stations.stream().filter(station -> station.id().equalsIgnoreCase(normalized)).findFirst().orElse(null);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("Zeus Physics Lab commands:");
        sender.sendMessage("/zeuslab generate - generate all station plots and command blocks");
        sender.sendMessage("/zeuslab reset - clear generated station plots");
        sender.sendMessage("/zeuslab list [category|id] - list station catalog");
        sender.sendMessage("/zeuslab tp <station_id|number> - teleport to a station");
        sender.sendMessage("/zeuslab verify - count expected station signs and command blocks");
        sender.sendMessage("/zeuslab manifest - export station manifest JSON");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("generate", "reset", "list", "tp", "verify", "manifest", "help");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            return stations.stream().map(Station::id).filter(id -> id.startsWith(args[1].toUpperCase(Locale.ROOT))).limit(20).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            List<String> values = new ArrayList<>();
            for (StationCategory category : StationCategory.values()) values.add(category.name());
            return values;
        }
        return List.of();
    }
}
