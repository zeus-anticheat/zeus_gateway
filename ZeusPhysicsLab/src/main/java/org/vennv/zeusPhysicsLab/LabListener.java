package org.vennv.zeusPhysicsLab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LabListener implements Listener {
    private final List<Station> stations;
    private final LabGenerator generator;
    private final Map<UUID, Set<String>> completedByPlayer = new HashMap<>();
    private final Map<UUID, Location> checkpointsByPlayer = new HashMap<>();

    public LabListener(List<Station> stations, LabGenerator generator) {
        this.stations = stations;
        this.generator = generator;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        for (Station station : stations) {
            if (station.id().equals("STEP_HEIGHT_VARIATION")) {
                for (StepHeightZone zone : generator.zonesFor(station)) {
                    if (zone.contains(event.getTo().getBlockX(), event.getTo().getBlockY(), event.getTo().getBlockZ())) {
                        updateScoreboardZone(player, station, zone);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.STONE_PRESSURE_PLATE) return;

        Player player = event.getPlayer();
        LabPlate plate = generator.plateAt(block.getLocation());
        if (plate == null) return;

        if (plate.finish()) {
            showFinish(player, plate.station());
        } else {
            showStart(player, plate.station(), block.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        Location checkpoint = checkpointsByPlayer.get(event.getPlayer().getUniqueId());
        if (checkpoint != null) {
            event.setRespawnLocation(checkpoint.clone());
        }
    }

    private void showStart(Player player, Station station, Location plateLocation) {
        // A station start is also a checkpoint. If the player dies, they resume the current test.
        Location checkpoint = plateLocation.clone().add(0.5, 1.0, 0.5);
        checkpoint.setYaw(player.getLocation().getYaw());
        checkpoint.setPitch(player.getLocation().getPitch());
        checkpointsByPlayer.put(player.getUniqueId(), checkpoint.clone());
        player.setBedSpawnLocation(checkpoint.clone(), true);

        // Start each test from a clean inventory, then grant only the tools required by this station.
        clearInventory(player);
        grantRequiredItems(player, station);

        if (station.id().equals("STEP_HEIGHT_VARIATION")) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("Step Height Test", NamedTextColor.GOLD),
                Component.text("Walk through each block zone and observe!", NamedTextColor.WHITE),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(500), java.time.Duration.ofSeconds(3), java.time.Duration.ofSeconds(1))
            ));
        } else {
            player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text(station.id(), NamedTextColor.GOLD),
                Component.text("Task: " + station.description(), NamedTextColor.WHITE)
            ));
        }
        player.sendActionBar(Component.text("Start: " + station.description() + " | Follow the lane to the green finish plate.", NamedTextColor.YELLOW));
        updateScoreboard(player, station, false);
    }

    private void showFinish(Player player, Station station) {
        completedByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(station.id());

        // Prevent tools from leaking into the next station and contaminating replay labels.
        clearInventory(player);

        player.showTitle(net.kyori.adventure.title.Title.title(
            Component.text("DONE " + station.id(), NamedTextColor.GREEN),
            Component.text("Continue forward to the next gold start plate", NamedTextColor.WHITE)
        ));
        player.sendActionBar(Component.text("Completed " + station.id() + ". Continue forward.", NamedTextColor.GREEN));
        updateScoreboard(player, station, true);
    }

    private void updateScoreboard(Player player, Station station, boolean done) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective(
            "zeus_lab",
            "dummy",
            Component.text("[ " + station.id() + " ]", NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int completed = completedByPlayer.getOrDefault(player.getUniqueId(), Set.of()).size();
        List<String> instructions = station.instructions();

        int score = 15;

        objective.getScore(ChatColor.GRAY + "Objective:").setScore(score--);
        for (String line : wrapText(station.description(), 30, ChatColor.WHITE.toString(), "")) {
            objective.getScore(line).setScore(score--);
        }

        objective.getScore(" ").setScore(score--);
        objective.getScore(ChatColor.GRAY + "Instructions:").setScore(score--);
        String inst1 = instructions.size() > 1 ? instructions.get(1) : "Follow the lane markers.";
        for (String line : wrapText(inst1, 30, ChatColor.WHITE.toString(), ChatColor.RESET.toString())) {
            objective.getScore(line).setScore(score--);
        }

        String inst2 = instructions.size() > 2 ? instructions.get(2) : "Finish on green plate.";
        for (String line : wrapText(inst2, 30, ChatColor.AQUA.toString(), ChatColor.RESET.toString() + ChatColor.RESET.toString())) {
            objective.getScore(line).setScore(score--);
        }

        objective.getScore("  ").setScore(score--);
        objective.getScore(ChatColor.YELLOW + "Effect: " + ChatColor.WHITE + activeEffectsFor(station)).setScore(score--);

        objective.getScore("   ").setScore(score--);
        objective.getScore(ChatColor.GREEN + "Completed: " + ChatColor.WHITE + completed + ChatColor.GRAY + " / " + ChatColor.WHITE + stations.size()).setScore(score--);
        objective.getScore((done ? ChatColor.GREEN : ChatColor.YELLOW) + "Status: " + ChatColor.WHITE + (done ? "DONE" : "RUNNING")).setScore(score--);

        player.setScoreboard(board);
    }

    private void updateScoreboardZone(Player player, Station station, StepHeightZone zone) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective(
            "zeus_lab_zone",
            "dummy",
            Component.text("[ STEP HEIGHT TEST ]", NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int completed = completedByPlayer.getOrDefault(player.getUniqueId(), Set.of()).size();
        objective.getScore(ChatColor.GRAY + "Current Block: " + ChatColor.YELLOW + zone.blockName()).setScore(10);
        objective.getScore(ChatColor.GRAY + "Collision Height: " + ChatColor.AQUA + zone.collisionHeight()).setScore(9);
        objective.getScore(" ").setScore(8);
        objective.getScore(ChatColor.GRAY + "Instructions:").setScore(7);
        objective.getScore(ChatColor.WHITE + "Walk through each zone").setScore(6);
        objective.getScore(ChatColor.WHITE + "steps up / down each block").setScore(5);
        objective.getScore("  ").setScore(4);
        objective.getScore(ChatColor.YELLOW + "Enchant/Effect active: " + ChatColor.WHITE + "None").setScore(3);
        objective.getScore("   ").setScore(2);
        objective.getScore(ChatColor.GREEN + "Completed: " + ChatColor.WHITE + completed + ChatColor.GRAY + " / " + ChatColor.WHITE + stations.size()).setScore(1);

        player.setScoreboard(board);
    }
    private String activeEffectsFor(Station station) {
        if (station.id().contains("SPEED_BURST")) return "Speed";
        if (station.id().contains("JUMP_BOOST")) return "Jump Boost";
        if (station.id().contains("SLOW_FALLING")) return "Slow Falling";
        if (station.id().contains("DOLPHINS_GRACE")) return "Dolphin's Grace";
        if (station.id().contains("DEPTH_STRIDER")) return "Depth Strider";
        if (station.id().contains("SOUL_SPEED")) return "Soul Speed";
        if (station.id().contains("SWIFT_SNEAK")) return "Swift Sneak";
        return "None";
    }

    private void clearInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void grantRequiredItems(Player player, Station station) {
        String id = station.id();
        switch (station.category()) {
            case COMBAT, CROSS_FEATURE -> grantCombatItems(player, id);
            case INTERACT -> {
                player.getInventory().addItem(new ItemStack(Material.DIAMOND_PICKAXE, 1));
                player.getInventory().addItem(new ItemStack(Material.DIAMOND_AXE, 1));
                player.getInventory().addItem(new ItemStack(Material.STONE, 64));
            }
            case TRANSACTION -> {
                player.getInventory().addItem(new ItemStack(Material.OAK_LOG, 64));
                player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 64));
                player.getInventory().addItem(new ItemStack(Material.STICK, 32));
            }
            case EXTERNAL_FORCE -> {
                player.getInventory().addItem(new ItemStack(Material.SHIELD, 1));
                if (id.contains("WIND_CHARGE")) player.getInventory().addItem(new ItemStack(Material.WIND_CHARGE, 16));
            }
            case VEHICLE -> {
                player.getInventory().addItem(new ItemStack(Material.CARROT_ON_A_STICK, 1));
                player.getInventory().addItem(new ItemStack(Material.WARPED_FUNGUS_ON_A_STICK, 1));
                if (id.contains("HORSE")) {
                    player.getInventory().addItem(new ItemStack(Material.SADDLE, 1));
                    player.getInventory().addItem(new ItemStack(Material.GOLDEN_CARROT, 16));
                    addIfPresent(player, "HORSE_SPAWN_EGG", 1);
                }
                if (id.contains("PIG_STRIDER")) {
                    player.getInventory().addItem(new ItemStack(Material.SADDLE, 2));
                }
            }
            default -> grantMovementItems(player, id);
        }
    }

    private void grantCombatItems(Player player, String id) {
        player.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD, 1));
        player.getInventory().addItem(new ItemStack(Material.DIAMOND_AXE, 1));
        player.getInventory().addItem(new ItemStack(Material.BOW, 1));
        player.getInventory().addItem(new ItemStack(Material.ARROW, 32));
        player.getInventory().addItem(new ItemStack(Material.SHIELD, 1));
        if (id.contains("FISHING")) player.getInventory().addItem(new ItemStack(Material.FISHING_ROD, 1));
        if (id.contains("TRIDENT")) player.getInventory().addItem(new ItemStack(Material.TRIDENT, 1));
        if (id.contains("SPEAR")) {
            grantSpears(player, id);
        }
        if (id.contains("WEAPON_FAMILIES")) {
            player.getInventory().addItem(new ItemStack(Material.TRIDENT, 1));
            player.getInventory().addItem(new ItemStack(Material.IRON_SPEAR, 1));
            player.getInventory().addItem(new ItemStack(Material.MACE, 1));
            player.getInventory().addItem(new ItemStack(Material.CROSSBOW, 1));
        }
        if (id.contains("EATING")) player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 16));
    }

    private void grantMovementItems(Player player, String id) {
        if (id.contains("ELYTRA")) {
            player.getInventory().setChestplate(new ItemStack(Material.ELYTRA, 1));
            player.getInventory().addItem(new ItemStack(Material.FIREWORK_ROCKET, 16));
        }
        if (id.contains("RIPTIDE")) player.getInventory().addItem(new ItemStack(Material.TRIDENT, 1));
        if (id.contains("WIND_CHARGE")) player.getInventory().addItem(new ItemStack(Material.WIND_CHARGE, 16));
        if (id.contains("MACE")) player.getInventory().addItem(new ItemStack(Material.MACE, 1));
        if (id.contains("ENDER_PEARL")) player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 16));
        if (id.contains("WEB") || id.contains("VINES") || id.contains("BERRY_BUSH")) player.getInventory().addItem(new ItemStack(Material.SHEARS, 1));
    }

    private void grantSpears(Player player, String id) {
        player.getInventory().addItem(new ItemStack(Material.WOODEN_SPEAR, 1));
        player.getInventory().addItem(new ItemStack(Material.STONE_SPEAR, 1));
        player.getInventory().addItem(new ItemStack(Material.COPPER_SPEAR, 1));
        player.getInventory().addItem(new ItemStack(Material.IRON_SPEAR, 1));
        player.getInventory().addItem(new ItemStack(Material.GOLDEN_SPEAR, 1));
        player.getInventory().addItem(new ItemStack(Material.DIAMOND_SPEAR, 1));
        player.getInventory().addItem(new ItemStack(Material.NETHERITE_SPEAR, 1));

        if (id.contains("CHARGE") || id.contains("MULTI_ENTITY") || id.contains("DISMOUNT")) {
            ItemStack lungeSpear = new ItemStack(Material.DIAMOND_SPEAR, 1);
            lungeSpear.addUnsafeEnchantment(Enchantment.LUNGE, 3);
            player.getInventory().addItem(lungeSpear);
        }
    }

    private boolean addIfPresent(Player player, String materialName, int amount) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) return false;
        player.getInventory().addItem(new ItemStack(material, amount));
        return true;
    }

    private String trimScore(String value) {
        return value.length() <= 32 ? value : value.substring(0, 29) + "...";
    }

    private List<String> wrapText(String text, int maxLineLength, String colorPrefix, String colorSuffix) {
        List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder(colorPrefix);

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 - colorPrefix.length() > maxLineLength) {
                if (currentLine.length() > colorPrefix.length()) {
                    lines.add(currentLine.toString() + colorSuffix);
                    currentLine = new StringBuilder(colorPrefix);
                }
            }
            if (currentLine.length() > colorPrefix.length()) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }
        if (currentLine.length() > colorPrefix.length()) {
            lines.add(currentLine.toString() + colorSuffix);
        }
        return lines;
    }
}
