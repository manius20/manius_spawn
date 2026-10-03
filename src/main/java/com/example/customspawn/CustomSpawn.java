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

    // Przechowuje aktywne zadania teleportacji oraz liczniki czasu dla graczy
    private final HashMap<UUID, BukkitTask> teleportTasks = new HashMap<>();
    private final HashMap<UUID, Location> startLocations = new HashMap<>();

    @Override
    public void onEnable() {
        // Rejestracja komendy /spawn oraz nasłuchiwacza ruchu
        getCommand("spawn").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Plugin SpawnPlugin (10 sekund + anty-ruch) został pomyślnie włączony!");
    }

    @Override
    public void onDisable() {
        teleportTasks.clear();
        startLocations.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tę komendę może wykonać tylko gracz!");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        // Jeśli gracz już jest w trakcie teleportacji
        if (teleportTasks.containsKey(uuid)) {
            player.sendMessage(ChatColor.RED + "Masz już aktywną próbę teleportacji! Nie ruszaj się.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Teleportacja nastąpi za " + ChatColor.GOLD + "10 sekund" + ChatColor.YELLOW + ". Nie ruszaj się!");

        // Zapisujemy pozycję początkową gracza
        startLocations.put(uuid, player.getLocation().clone());

        // Uruchamiamy zadanie cykliczne (co 1 sekundę = 20 ticków), które odlicza czas od 10 do 0
        final int[] secondsLeft = {10};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (secondsLeft[0] > 0) {
                // Wyświetlamy informację co sekundę (możesz zmienić na pasek akcji lub zostawić na czacie)
                player.sendMessage(ChatColor.GRAY + "Teleportacja za: " + ChatColor.YELLOW + secondsLeft[0] + "s...");
                secondsLeft[0]--;
            } else {
                // Minęło 10 sekund - wykonujemy teleportację!
                
                // === TUTAJ USTAW WSPÓŁRZĘDNE SWOJEGO SPAWNU ===
                World world = Bukkit.getWorld("world"); // nazwa świata (zazwyczaj "world")
                double x = 0.5;   // koordynat X
                double y = 100.0; // koordynat Y (wysokość)
                double z = 0.5;   // koordynat Z
                float yaw = 0.0f;   // obrót poziomy (patrzenie w lewo/prawo)
                float pitch = 0.0f; // obrót pionowy (góra/dół)

                if (world != null) {
                    Location spawnLocation = new Location(world, x, y, z, yaw, pitch);
                    player.teleport(spawnLocation);
                    player.sendMessage(ChatColor.GREEN + "Zostałeś pomyślnie przeteleportowany na spawn!");
                } else {
                    player.sendMessage(ChatColor.RED + "Błąd: Nie znaleziono świata docelowego!");
                }

                // Czyszczymy dane gracza po zakończeniu
                cancelTeleport(uuid);
            }
        }, 0L, 20L); // Start natychmiast (0L), powtarzaj co sekundę (20L)

        teleportTasks.put(uuid, task);
        return true;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Sprawdzamy, czy gracz ma włączoną teleportację
        if (teleportTasks.containsKey(uuid)) {
            Location startLoc = startLocations.get(uuid);
            Location currentLoc = event.getTo();

            if (startLoc != null && currentLoc != null) {
                // Sprawdzamy, czy gracz zmienił blok (X, Y lub Z). Obracanie głowy (Yaw/Pitch) jest ignorowane.
                if (startLoc.getBlockX() != currentLoc.getBlockX() ||
                    startLoc.getBlockY() != currentLoc.getBlockY() ||
                    startLoc.getBlockZ() != currentLoc.getBlockZ()) {

                    // Przerywamy teleportację
                    cancelTeleport(uuid);
                    player.sendMessage(ChatColor.RED + "Ruszyłeś się! Teleportacja została przerwana.");
                }
            }
        }
    }

    // Pomocnicza metoda do bezpiecznego czyszczenia zadań
    private void cancelTeleport(UUID uuid) {
        if (teleportTasks.containsKey(uuid)) {
            teleportTasks.get(uuid).cancel();
            teleportTasks.remove(uuid);
        }
        startLocations.remove(uuid);
    }
}
