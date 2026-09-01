package com.example.jailplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class JailPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, JailEntry> jailedPlayers = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;
    private Location jailSpawn;
    private Location releaseSpawn;
    private Location torchMarker;
    private Location releaseButton;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.dataFile = new File(getDataFolder(), "jails.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create jail data file: " + e.getMessage());
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        normalizeSavedSpawnConfig();
        loadConfigLocations();
        loadJailedPlayers();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("jail").setExecutor(this);
        getCommand("unjail").setExecutor(this);
        getCommand("setjailspawn").setExecutor(this);
        getCommand("setreleasespawn").setExecutor(this);
        getCommand("setdoorcontrol").setExecutor(this);
        getCommand("setreleasebutton").setExecutor(this);
        getCommand("jailstatus").setExecutor(this);
        getCommand("jailtest").setExecutor(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                tickJails();
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (dataConfig != null) {
            saveJailedPlayers();
        }
    }

    private void normalizeSavedSpawnConfig() {
        FileConfiguration config = getConfig();
        if (isPlaceholderSpawn(config, "jail-spawn")) {
            config.set("jail-spawn", null);
        }
        if (isPlaceholderSpawn(config, "release-spawn")) {
            config.set("release-spawn", null);
        }
        if (isPlaceholderSpawn(config, "torch-marker")) {
            config.set("torch-marker", null);
        }
        if (isPlaceholderSpawn(config, "release-button")) {
            config.set("release-button", null);
        }
        saveConfig();
    }

    private boolean isPlaceholderSpawn(FileConfiguration config, String path) {
        if (!config.contains(path + ".world")) {
            return false;
        }
        String worldName = config.getString(path + ".world");
        double x = config.getDouble(path + ".x", Double.NaN);
        double y = config.getDouble(path + ".y", Double.NaN);
        double z = config.getDouble(path + ".z", Double.NaN);
        if (worldName == null || worldName.equalsIgnoreCase("world") || worldName.isBlank()) {
            return x == 0.0D && y == 0.0D && z == 0.0D;
        }
        return false;
    }

    private Location loadConfiguredLocation(String path) {
        FileConfiguration config = getConfig();
        if (!config.contains(path + ".world") || isPlaceholderSpawn(config, path)) {
            return null;
        }

        String worldName = config.getString(path + ".world");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(
                world,
                config.getDouble(path + ".x"),
                config.getDouble(path + ".y"),
                config.getDouble(path + ".z"),
                (float) config.getDouble(path + ".yaw", 0.0D),
                (float) config.getDouble(path + ".pitch", 0.0D)
        );
    }

    private void loadConfigLocations() {
        jailSpawn = loadConfiguredLocation("jail-spawn");
        releaseSpawn = loadConfiguredLocation("release-spawn");
        torchMarker = loadConfiguredLocation("torch-marker");
        releaseButton = loadConfiguredLocation("release-button");
    }

    private boolean isJailTeleportEnabled() {
        return getConfig().getBoolean("jail-teleport.enabled", true);
    }

    private double getJailTeleportDistance() {
        return getConfig().getDouble("jail-teleport.max-distance", 200.0D);
    }

    private boolean shouldTeleportPlayerBack(Player player) {
        if (!isJailTeleportEnabled()) {
            return false;
        }
        if (jailSpawn == null || player.getWorld() != jailSpawn.getWorld()) {
            return false;
        }

        double maxDistance = getJailTeleportDistance();
        if (maxDistance <= 0.0D) {
            return false;
        }

        return player.getLocation().distanceSquared(jailSpawn) > (maxDistance * maxDistance);
    }

    private boolean hasRequiredSpawnsForJail() {
        return jailSpawn != null && releaseSpawn != null;
    }

    private boolean hasRequiredSpawnsForRelease() {
        return releaseSpawn != null;
    }

    private void loadJailedPlayers() {
        ConfigurationSection section = dataConfig.getConfigurationSection("players");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long jailUntil = section.getLong(key + ".jailUntil");
                String reason = section.getString(key + ".reason", "Jail");
                boolean permanentLoop = section.getBoolean(key + ".permanentLoop", false);
                int offenseCount = section.getInt(key + ".offenseCount", 1);
                jailedPlayers.put(uuid, new JailEntry(uuid, jailUntil, reason, permanentLoop, offenseCount));
            } catch (IllegalArgumentException ignored) {
                // ignore malformed entries
            }
        }
    }

    private void saveJailedPlayers() {
        if (dataConfig == null || dataFile == null) {
            return;
        }

        dataConfig.set("players", null);
        for (Map.Entry<UUID, JailEntry> entry : jailedPlayers.entrySet()) {
            JailEntry jailEntry = entry.getValue();
            String key = entry.getKey().toString();
            dataConfig.set("players." + key + ".jailUntil", jailEntry.getJailUntil());
            dataConfig.set("players." + key + ".reason", jailEntry.getReason());
            dataConfig.set("players." + key + ".permanentLoop", jailEntry.isPermanentLoop());
            dataConfig.set("players." + key + ".offenseCount", jailEntry.getOffenseCount());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save jail data: " + e.getMessage());
        }
    }

    private void tickJails() {
        long now = System.currentTimeMillis();
        handleButtonTorchLogic();

        for (Map.Entry<UUID, JailEntry> entry : new HashMap<>(jailedPlayers).entrySet()) {
            JailEntry jailEntry = entry.getValue();
            if (jailEntry.isPermanentLoop()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && shouldTeleportPlayerBack(player)) {
                    teleportToJail(player);
                }
                continue;
            }
            if (now >= jailEntry.getJailUntil()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    releasePlayer(player, "Your jail sentence has ended.");
                }
                jailedPlayers.remove(entry.getKey());
            } else {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && shouldTeleportPlayerBack(player)) {
                    teleportToJail(player);
                }
            }
        }
    }

    private static final long TORCH_LIFETIME_MS = 10_000L;
    private long torchExpiresAt = -1L;

    private void handleButtonTorchLogic() {
        if (releaseButton == null || torchMarker == null) {
            return;
        }

        Block buttonBlock = releaseButton.getBlock();
        if (buttonBlock.getType() != Material.OAK_BUTTON && buttonBlock.getType() != Material.STONE_BUTTON) {
            getLogger().warning("Release button block is not a button. Type: " + buttonBlock.getType());
            return;
        }

        if (torchExpiresAt > 0 && System.currentTimeMillis() >= torchExpiresAt) {
            if (torchMarker.getBlock().getType() == Material.REDSTONE_TORCH) {
                torchMarker.getBlock().setType(Material.AIR);
                getLogger().info("Cleared redstone torch at marker: " + torchMarker);
            }
            torchExpiresAt = -1L;
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || releaseButton == null || torchMarker == null) {
            return;
        }

        if (!clickedBlock.getLocation().equals(releaseButton)) {
            return;
        }

        if (clickedBlock.getType() != Material.OAK_BUTTON && clickedBlock.getType() != Material.STONE_BUTTON) {
            return;
        }

        getLogger().info("Release button pressed: clicked=" + clickedBlock.getLocation() + ", marker=" + torchMarker);
        torchMarker.getBlock().setType(Material.REDSTONE_TORCH);
        torchExpiresAt = System.currentTimeMillis() + TORCH_LIFETIME_MS;
        getLogger().info("Placed redstone torch at marker: " + torchMarker);
    }

    private void teleportToJail(Player player) {
        if (jailSpawn == null) {
            player.sendMessage(ChatColor.RED + "No jail spawn is configured yet.");
            return;
        }
        player.teleport(jailSpawn);
        player.sendMessage(ChatColor.RED + "You are jailed. Follow the rules and wait for release.");
    }

    private void releasePlayer(Player player, String message) {
        if (!hasRequiredSpawnsForRelease()) {
            player.sendMessage(ChatColor.RED + "Cannot release player because no valid release spawn is configured.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + message);
        jailedPlayers.remove(player.getUniqueId());
        player.teleport(releaseSpawn);
        saveJailedPlayers();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        JailEntry entry = jailedPlayers.get(player.getUniqueId());
        if (entry == null) {
            return;
        }
        if (entry.isPermanentLoop()) {
            player.sendMessage(ChatColor.RED + "You have reached 3 offenses. Please contact staff.");
            teleportToJail(player);
            return;
        }
        if (System.currentTimeMillis() < entry.getJailUntil()) {
            teleportToJail(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        saveJailedPlayers();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        JailEntry entry = jailedPlayers.get(player.getUniqueId());
        if (entry == null) {
            return;
        }

        if (shouldTeleportPlayerBack(player)) {
            teleportToJail(player);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "jail" -> {
                if (!hasRequiredSpawnsForJail()) {
                    sender.sendMessage(ChatColor.RED + "Jail cannot start until both the jail spawn and release spawn are configured.");
                    return true;
                }
                if (args.length < 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /jail <player> [duration]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                    return true;
                }
                long durationMs = parseDuration(args.length > 1 ? args[1] : "24h");
                jailPlayer(target, durationMs, "Staff-issued jail");
                sender.sendMessage(ChatColor.GREEN + "Jailed " + target.getName() + " for " + formatDuration(durationMs) + ".");
                return true;
            }
            case "unjail" -> {
                if (args.length < 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /unjail <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                    return true;
                }
                jailedPlayers.remove(target.getUniqueId());
                if (releaseSpawn != null) {
                    target.teleport(releaseSpawn);
                }
                sender.sendMessage(ChatColor.GREEN + "Released " + target.getName() + ".");
                return true;
            }
            case "setjailspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
                    return true;
                }
                jailSpawn = player.getLocation();
                getConfig().set("jail-spawn.world", jailSpawn.getWorld().getName());
                getConfig().set("jail-spawn.x", jailSpawn.getX());
                getConfig().set("jail-spawn.y", jailSpawn.getY());
                getConfig().set("jail-spawn.z", jailSpawn.getZ());
                getConfig().set("jail-spawn.yaw", jailSpawn.getYaw());
                getConfig().set("jail-spawn.pitch", jailSpawn.getPitch());
                saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Jail spawn set.");
                return true;
            }
            case "setreleasespawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
                    return true;
                }
                releaseSpawn = player.getLocation();
                getConfig().set("release-spawn.world", releaseSpawn.getWorld().getName());
                getConfig().set("release-spawn.x", releaseSpawn.getX());
                getConfig().set("release-spawn.y", releaseSpawn.getY());
                getConfig().set("release-spawn.z", releaseSpawn.getZ());
                getConfig().set("release-spawn.yaw", releaseSpawn.getYaw());
                getConfig().set("release-spawn.pitch", releaseSpawn.getPitch());
                saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Release spawn set.");
                return true;
            }
            case "setdoorcontrol" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
                    return true;
                }
                Block targetBlock = player.getTargetBlockExact(6);
                if (targetBlock == null) {
                    sender.sendMessage(ChatColor.RED + "Look at a valid block to set the control marker.");
                    return true;
                }

                Block doorBlock = targetBlock;
                if (doorBlock.getType() != Material.IRON_DOOR && doorBlock.getType() != Material.IRON_TRAPDOOR) {
                    Block upper = doorBlock.getRelative(BlockFace.UP);
                    if (upper.getType() == Material.IRON_DOOR || upper.getType() == Material.IRON_TRAPDOOR) {
                        doorBlock = upper;
                    }
                }

                torchMarker = doorBlock.getLocation().clone().add(0, -3, 0);
                getConfig().set("torch-marker.world", torchMarker.getWorld().getName());
                getConfig().set("torch-marker.x", torchMarker.getX());
                getConfig().set("torch-marker.y", torchMarker.getY());
                getConfig().set("torch-marker.z", torchMarker.getZ());
                saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Torch marker set exactly 2 blocks below the door block.");
                return true;
            }
            case "setreleasebutton" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
                    return true;
                }

                Block targetBlock = player.getTargetBlockExact(6);
                Block buttonBlock = null;

                if (targetBlock != null && (targetBlock.getType() == Material.OAK_BUTTON || targetBlock.getType() == Material.STONE_BUTTON)) {
                    buttonBlock = targetBlock;
                } else {
                    int range = 6;
                    for (int dx = -range; dx <= range; dx++) {
                        for (int dy = -range; dy <= range; dy++) {
                            for (int dz = -range; dz <= range; dz++) {
                                Block nearby = player.getLocation().add(dx, dy, dz).getBlock();
                                if (nearby.getType() == Material.OAK_BUTTON || nearby.getType() == Material.STONE_BUTTON) {
                                    buttonBlock = nearby;
                                    break;
                                }
                            }
                            if (buttonBlock != null) {
                                break;
                            }
                        }
                        if (buttonBlock != null) {
                            break;
                        }
                    }
                }

                if (buttonBlock == null) {
                    if (targetBlock == null) {
                        sender.sendMessage(ChatColor.RED + "Look at a valid block or stand near an existing button to set the release trigger.");
                    } else {
                        releaseButton = targetBlock.getLocation().clone();
                        targetBlock.setType(Material.OAK_BUTTON);
                        buttonBlock = targetBlock;
                    }
                }

                if (buttonBlock != null) {
                    releaseButton = buttonBlock.getLocation().clone();
                    getConfig().set("release-button.world", releaseButton.getWorld().getName());
                    getConfig().set("release-button.x", releaseButton.getX());
                    getConfig().set("release-button.y", releaseButton.getY());
                    getConfig().set("release-button.z", releaseButton.getZ());
                    saveConfig();
                    sender.sendMessage(ChatColor.GREEN + "Release button set to the nearest valid button at " + releaseButton.getBlockX() + ", " + releaseButton.getBlockY() + ", " + releaseButton.getBlockZ() + ".");
                }
                return true;
            }
            case "jailstatus" -> {
                if (args.length < 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /jailstatus <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                    return true;
                }
                JailEntry entry = jailedPlayers.get(target.getUniqueId());
                if (entry == null) {
                    sender.sendMessage(ChatColor.GREEN + target.getName() + " is not jailed.");
                    return true;
                }
                sender.sendMessage(ChatColor.YELLOW + target.getName() + " is jailed until " + entry.getJailUntil() + ".");
                return true;
            }
            case "jailtest" -> {
                if (!hasRequiredSpawnsForJail()) {
                    sender.sendMessage(ChatColor.RED + "Test jail cannot run until both the jail spawn and release spawn are configured.");
                    return true;
                }
                if (args.length < 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /jailtest <player> [seconds]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                    return true;
                }
                long seconds = args.length > 1 ? Long.parseLong(args[1]) : 30L;
                jailPlayer(target, seconds * 1000L, "Admin test jail");
                sender.sendMessage(ChatColor.GREEN + "Placed " + target.getName() + " in a test jail for " + seconds + "s.");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void jailPlayer(Player player, long durationMs, String reason) {
        if (!hasRequiredSpawnsForJail()) {
            player.sendMessage(ChatColor.RED + "Jail failed: configure a jail spawn and a release spawn first.");
            return;
        }

        long jailUntil = System.currentTimeMillis() + durationMs;
        JailEntry entry = new JailEntry(player.getUniqueId(), jailUntil, reason, false, 1);
        jailedPlayers.put(player.getUniqueId(), entry);
        saveJailedPlayers();
        teleportToJail(player);
    }

    private long parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return 24L * 60L * 60L * 1000L;
        }
        String lower = value.toLowerCase();
        long multiplier = 1L;
        if (lower.endsWith("h")) {
            multiplier = 60L * 60L * 1000L;
        } else if (lower.endsWith("m")) {
            multiplier = 60L * 1000L;
        } else if (lower.endsWith("s")) {
            multiplier = 1000L;
        } else if (lower.endsWith("d")) {
            multiplier = 24L * 60L * 60L * 1000L;
        }
        String number = lower.replaceAll("[a-zA-Z]", "");
        try {
            long amount = Long.parseLong(number);
            return amount * multiplier;
        } catch (NumberFormatException e) {
            return 24L * 60L * 60L * 1000L;
        }
    }

    private String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }
}
