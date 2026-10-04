package pl.s16.spawn;

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
import java.util.Map;
import java.util.UUID;

public final class Main extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<UUID, BukkitTask> activeTeleports = new HashMap<>();

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
        activeTeleports.clear();
    }

    private String getMsg(String path) {
        String raw = getConfig().getString("messages." + path, "");
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (command.getName().equalsIgnoreCase("setspawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getMsg("only-players"));
                return true;
            }

            Player player = (Player) sender;
            Location loc = player.getLocation();

            getConfig().set("spawn.world", loc.getWorld().getName());
            getConfig().set("spawn.x", loc.getX());
            getConfig().set("spawn.y", loc.getY());
            getConfig().set("spawn.z", loc.getZ());
            getConfig().set("spawn.yaw", loc.getYaw());
            getConfig().set("spawn.pitch", loc.getPitch());
            saveConfig();

            player.sendMessage(getMsg("spawn-set-success"));
            return true;
        }

        if (command.getName().equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getMsg("only-players"));
                return true;
            }

            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            if (!getConfig().contains("spawn.world") || getConfig().getString("spawn.world").isEmpty()) {
                player.sendMessage(getMsg("spawn-not-set"));
                return true;
            }

            if (activeTeleports.containsKey(uuid)) {
                player.sendMessage(getMsg("teleport-already"));
                return true;
            }

            int delaySeconds = getConfig().getInt("teleport-delay", 10);
            int delayTicks = delaySeconds * 20;

            String startMsg = getMsg("teleport-start").replace("{time}", String.valueOf(delaySeconds));
            player.sendMessage(startMsg);

            BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline()) return;

                String worldName = getConfig().getString("spawn.world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    player.sendMessage(ChatColor.RED + "Błąd: Świat spawnu nie istnieje!");
                    return;
                }

                Location spawnLoc = new Location(
                        world,
                        getConfig().getDouble("spawn.x"),
                        getConfig().getDouble("spawn.y"),
                        getConfig().getDouble("spawn.z"),
                        (float) getConfig().getDouble("spawn.yaw"),
                        (float) getConfig().getDouble("spawn.pitch")
                );

                player.teleport(spawnLoc);
                player.sendMessage(getMsg("teleport-success"));
                activeTeleports.remove(uuid);
            }, delayTicks);

            activeTeleports.put(uuid, task);
            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!activeTeleports.containsKey(uuid)) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to != null && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())) {
            activeTeleports.get(uuid).cancel();
            activeTeleports.remove(uuid);

            player.sendMessage(getMsg("teleport-cancelled"));
        }
    }
}
