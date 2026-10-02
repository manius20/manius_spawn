package com.example.customspawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CustomSpawn extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("setspawn") != null) getCommand("setspawn").setExecutor(this);
        if (getCommand("spawn") != null) getCommand("spawn").setExecutor(this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPlayedBefore()) {
            Location spawnLoc = getSpawnLocation();
            if (spawnLoc != null) {
                player.teleport(spawnLoc);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("messages.only-players"));
            return true;
        }

        if (command.getName().equalsIgnoreCase("setspawn")) {
            if (!player.hasPermission("customspawn.setspawn")) {
                player.sendMessage(getMessage("messages.no-permission"));
                return true;
            }
            saveSpawnLocation(player.getLocation());
            player.sendMessage(getMessage("messages.spawn-set"));
            return true;
        }

        if (command.getName().equalsIgnoreCase("spawn")) {
            Location spawnLoc = getSpawnLocation();
            if (spawnLoc == null) {
                player.sendMessage(getMessage("messages.no-spawn-set"));
                return true;
            }

            int cooldownSec = getConfig().getInt("cooldown-time", 25);
            long currentTime = System.currentTimeMillis();

            if (cooldowns.containsKey(player.getUniqueId())) {
                long lastUse = cooldowns.get(player.getUniqueId());
                long timeLeft = (lastUse + (cooldownSec * 1000L) - currentTime) / 1000;
                if (timeLeft > 0) {
                    String msg = getConfig().getString("messages.cooldown-active", "&cOdczekaj {time}s!")
                            .replace("{time}", String.valueOf(timeLeft));
                    player.sendMessage(parseColor(msg));
                    return true;
                }
            }

            startTeleportCountdown(player, spawnLoc);
            return true;
        }

        return false;
    }

    private void startTeleportCountdown(Player player, Location targetLoc) {
        int delay = getConfig().getInt("teleport-delay", 5);

        new BukkitRunnable() {
            int secondsLeft = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                if (secondsLeft > 0) {
                    String subMsg = getConfig().getString("title.subtitle", "&eSekund do teleportacji");
                    Title title = Title.title(
                            parseColor("&6" + secondsLeft),
                            parseColor(subMsg),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)
                    );
                    player.showTitle(title);
                    secondsLeft--;
                } else {
                    player.teleport(targetLoc);
                    player.sendMessage(getMessage("messages.teleport-success"));
                    cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private Component getMessage(String path) {
        String msg = getConfig().getString(path, "");
        return parseColor(msg);
    }

    private Component parseColor(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private void saveSpawnLocation(Location loc) {
        FileConfiguration config = getConfig();
        config.set("spawn.world", loc.getWorld().getName());
        config.set("spawn.x", loc.getX());
        config.set("spawn.y", loc.getY());
        config.set("spawn.z", loc.getZ());
        config.set("spawn.yaw", loc.getYaw());
        config.set("spawn.pitch", loc.getPitch());
        saveConfig();
    }

    private Location getSpawnLocation() {
        FileConfiguration config = getConfig();
        if (!config.contains("spawn.world")) return null;

        String worldName = config.getString("spawn.world");
        if (worldName == null || Bukkit.getWorld(worldName) == null) return null;

        return new Location(
                Bukkit.getWorld(worldName),
                config.getDouble("spawn.x"),
                config.getDouble("spawn.y"),
                config.getDouble("spawn.z"),
                (float) config.getDouble("spawn.yaw"),
                (float) config.getDouble("spawn.pitch")
        );
    }
}
