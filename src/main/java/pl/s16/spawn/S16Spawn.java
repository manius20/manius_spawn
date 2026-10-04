package pl.s16.spawn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class S16Spawn extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<UUID, BukkitTask> teleportTasks = new HashMap<>();
    private final Map<UUID, Location> initialLocations = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("spawn").setExecutor(this);
        getCommand("setspawn").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("S16Spawn został włączony pomyślnie!");
    }

    @Override
    public void onDisable() {
        teleportTasks.values().forEach(BukkitTask::cancel);
        teleportTasks.clear();
        initialLocations.clear();
    }

    public Location getSpawnLocation() {
        FileConfiguration config = getConfig();
        if (!config.contains("spawn.world")) return null;
        World world = Bukkit.getWorld(config.getString("spawn.world"));
        if (world == null) return null;
        double x = config.getDouble("spawn.x");
        double y = config.getDouble("spawn.y");
        double z = config.getDouble("spawn.z");
        float yaw = (float) config.getDouble("spawn.yaw");
        float pitch = (float) config.getDouble("spawn.pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    public void setSpawnLocation(Location loc) {
        FileConfiguration config = getConfig();
        config.set("spawn.world", loc.getWorld().getName());
        config.set("spawn.x", loc.getX());
        config.set("spawn.y", loc.getY());
        config.set("spawn.z", loc.getZ());
        config.set("spawn.yaw", loc.getYaw());
        config.set("spawn.pitch", loc.getPitch());
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = getConfig();

        if (command.getName().equalsIgnoreCase("setspawn")) {
            if (!sender.hasPermission("s16spawn.setspawn")) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permission")));
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(config.getString("messages.only-players"));
                return true;
            }
            Player player = (Player) sender;
            setSpawnLocation(player.getLocation());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.spawn-set")));
            return true;
        }

        if (command.getName().equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(config.getString("messages.only-players"));
                return true;
            }
            Player player = (Player) sender;
            Location spawn = getSpawnLocation();

            if (spawn == null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.spawn-not-set")));
                return true;
            }

            if (teleportTasks.containsKey(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Już trwa Twoja teleportacja!");
                return true;
            }

            int delay = config.getInt("teleport-delay", 10);
            String startMsg = config.getString("messages.teleport-start")
                    .replace("%time%", String.valueOf(delay));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', startMsg));

            initialLocations.put(player.getUniqueId(), player.getLocation().clone());

            BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
                teleportTasks.remove(player.getUniqueId());
                initialLocations.remove(player.getUniqueId());
                player.teleport(spawn);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.teleport-success")));
            }, delay * 20L);

            teleportTasks.put(player.getUniqueId(), task);
            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!teleportTasks.containsKey(uuid)) return;

        Location initial = initialLocations.get(uuid);
        Location current = event.getTo();

        if (initial == null || current == null) return;

        // Ignoruje obrót głowy (yaw/pitch), sprawdza tylko fizyczne przemieszczenie (X, Y, Z)
        if (initial.getBlockX() != current.getBlockX() ||
                initial.getBlockY() != current.getBlockY() ||
                initial.getBlockZ() != current.getBlockZ()) {

            teleportTasks.get(uuid).cancel();
            teleportTasks.remove(uuid);
            initialLocations.remove(uuid);

            String cancelMsg = getConfig().getString("messages.teleport-cancelled");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', cancelMsg));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Sprawdza, czy gracz wchodzi na serwer po raz pierwszy (pierwszy login)
        if (!player.hasPlayedBefore()) {
            Location spawn = getSpawnLocation();
            if (spawn != null) {
                player.teleport(spawn);
            }
        }
    }
}
