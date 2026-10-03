package pl.manius.spawnplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.UUID;

public final class SpawnPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private final HashMap<UUID, BukkitTask> teleportTasks = new HashMap<>();
    private final HashMap<UUID, Location> startLocations = new HashMap<>();

    @Override
    public void onEnable() {
        if (getCommand("spawn") != null) {
            getCommand("spawn").setExecutor(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Plugin SpawnPlugin (10 sekund + anty-ruch) zostal wlaczony!");
    }

    @Override
    public void onDisable() {
        teleportTasks.clear();
        startLocations.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Te komende moze wykonac tylko gracz!");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (teleportTasks.containsKey(uuid)) {
            player.sendMessage(ChatColor.RED + "Masz juz aktywna probe teleportacji! Nie ruszaj sie.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Teleportacja nastapi za " + ChatColor.GOLD + "10 sekund" + ChatColor.YELLOW + ". Nie ruszaj sie!");

        startLocations.put(uuid, player.getLocation().clone());

        final int[] secondsLeft = {10};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (secondsLeft[0] > 0) {
                player.sendMessage(ChatColor.GRAY + "Teleportacja za: " + ChatColor.YELLOW + secondsLeft[0] + "s...");
                secondsLeft[0]--;
            } else {
                World world = Bukkit.getWorld("world");
                double x = 0.5;
                double y = 100.0;
                double z = 0.5;
                float yaw = 0.0f;
                float pitch = 0.0f;

                if (world != null) {
                    Location spawnLocation = new Location(world, x, y, z, yaw, pitch);
                    player.teleport(spawnLocation);
                    player.sendMessage(ChatColor.GREEN + "Zostales pomyslnie przeteleportowany na spawn!");
                } else {
                    player.sendMessage(ChatColor.RED + "Blad: Nie znaleziono swiata docelowego!");
                }

                cancelTeleport(uuid);
            }
        }, 0L, 20L);

        teleportTasks.put(uuid, task);
        return true;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (teleportTasks.containsKey(uuid)) {
            Location startLoc = startLocations.get(uuid);
            Location currentLoc = event.getTo();

            if (startLoc != null && currentLoc != null) {
                if (startLoc.getBlockX() != currentLoc.getBlockX() ||
                    startLoc.getBlockY() != currentLoc.getBlockY() ||
                    startLoc.getBlockZ() != currentLoc.getBlockZ()) {

                    cancelTeleport(uuid);
                    player.sendMessage(ChatColor.RED + "Ruszyles sie! Teleportacja zostala przerwana.");
                }
            }
        }
    }

    private void cancelTeleport(UUID uuid) {
        if (teleportTasks.containsKey(uuid)) {
            teleportTasks.get(uuid).cancel();
            teleportTasks.remove(uuid);
        }
        startLocations.remove(uuid);
    }
}
