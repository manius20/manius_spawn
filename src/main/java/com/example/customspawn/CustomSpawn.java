package pl.s16.spawn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
    private final Map<UUID, Location> teleportStartLocations = new HashMap<>();

    @Override
    public void onEnable() {
        // Zapisz domyślny config jeśli nie istnieje
        saveDefaultConfig();

        // Rejestracja komend i zdarzeń
        getCommand("spawn").setExecutor(this);
        getCommand("setspawn").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("S16Spawn został włączony pomyślnie!");
    }

    @Override
    public void onDisable() {
        activeTeleports.clear();
        teleportStartLocations.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Obsługa /setspawn
        if (command.getName().equalsIgnoreCase("setspawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Ta komenda jest dostępna tylko dla graczy.");
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

            player.sendMessage(ChatColor.GREEN + "✔ Pomyślnie ustawiono spawn S16 SMP!");
            return true;
        }

        // Obsługa /spawn
        if (command.getName().equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Ta komenda jest dostępna tylko dla graczy.");
                return true;
            }

            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            // Sprawdzanie czy spawn został ustawiony w configu
            if (!getConfig().contains("spawn.world")) {
                player.sendMessage(ChatColor.RED + "✖ Spawn serwera nie został jeszcze ustawiony przez administrację!");
                return true;
            }

            // Jeśli gracz już ma aktywną teleportację
            if (activeTeleports.containsKey(uuid)) {
                player.sendMessage(ChatColor.YELLOW + "⚠ Już trwa Twoja teleportacja na spawn! Nie ruszaj się.");
                return true;
            }

            player.sendMessage(ChatColor.AQUAMARK + "⌛ Teleportacja na spawn za 10 sekund... " + ChatColor.RED + "Nie ruszaj się!");
            
            // Zapisz pozycję startową gracza do weryfikacji ruchu
            teleportStartLocations.put(uuid, player.getLocation().clone());

            // Zadanie odliczające 10 sekund (200 ticków)
            BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline()) return;

                String worldName = getConfig().getString("spawn.world");
                Location spawnLoc = new Location(
                        Bukkit.getWorld(worldName),
                        getConfig().getDouble("spawn.x"),
                        getConfig().getDouble("spawn.y"),
                        getConfig().getDouble("spawn.z"),
                        (float) getConfig().getDouble("spawn.yaw"),
                        (float) getConfig().getDouble("spawn.pitch")
                );

                player.teleport(spawnLoc);
                player.sendMessage(ChatColor.GREEN + "✔ Zostałeś pomyślnie przeteleportowany na spawn!");

                // Usunięcie z map aktywnych
                activeTeleports.remove(uuid);
                teleportStartLocations.remove(uuid);
            }, 200L); // 200 ticków = 10 sekund

            activeTeleports.put(uuid, task);
            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Jeśli gracz nie jest w trakcie teleportacji, pomijamy
        if (!activeTeleports.containsKey(uuid)) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        // Sprawdzamy czy gracz faktycznie zmienił pozycję blokową (ignorujemy samo obracanie głowy/myszką)
        if (to != null && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())) {
            // Anuluj zadanie
            activeTeleports.get(uuid).cancel();
            activeTeleports.remove(uuid);
            teleportStartLocations.remove(uuid);

            player.sendMessage(ChatColor.RED + "✖ Ruszyłeś się! Teleportacja na spawn została anulowana.");
        }
    }
}
