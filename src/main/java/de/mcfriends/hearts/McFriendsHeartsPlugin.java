package de.mcfriends.hearts;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class McFriendsHeartsPlugin extends JavaPlugin
        implements Listener, CommandExecutor, TabCompleter {

    private static final int START_LIVES = 3;

    // Exact glyphs used by the supplied resource pack.
    // active_heart   = blue heart
    // inactive_heart = grey heart
    private static final char ACTIVE_HEART = '\uE01B';
    private static final char INACTIVE_HEART = '\uE01C';

    // Refresh the ActionBar continuously so the hearts stay visible.
    // 10 ticks = 0.5 seconds.
    private static final long DISPLAY_REFRESH_TICKS = 10L;

    private final Map<UUID, BukkitTask> displayTasks = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand command = getCommand("nation");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        getLogger().info("McFriends-Hearts-Plugin aktiviert.");
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : displayTasks.values()) {
            task.cancel();
        }
        displayTasks.clear();
    }

    private boolean hasStoredLives(UUID uuid) {
        return getConfig().contains("players." + uuid);
    }

    private int getLives(UUID uuid) {
        if (!hasStoredLives(uuid)) {
            return START_LIVES;
        }

        return Math.max(
                0,
                Math.min(
                        3,
                        getConfig().getInt("players." + uuid, START_LIVES)
                )
        );
    }

    private void setLives(UUID uuid, int lives) {
        lives = Math.max(0, Math.min(3, lives));

        getConfig().set("players." + uuid, lives);
        saveConfig();

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline() && lives > 0) {
            startHeartDisplay(player);
        }
    }

    private String buildHeartDisplay(Player player) {
        int lives = getLives(player.getUniqueId());

        StringBuilder display = new StringBuilder(3);

        for (int i = 0; i < 3; i++) {
            display.append(i < lives ? ACTIVE_HEART : INACTIVE_HEART);
        }

        return display.toString();
    }

    private void showLives(Player player) {
        if (!player.isOnline()) {
            return;
        }

        int lives = getLives(player.getUniqueId());

        if (lives <= 0) {
            return;
        }

        player.sendActionBar(Component.text(buildHeartDisplay(player)));
    }

    private void startHeartDisplay(Player player) {
        UUID uuid = player.getUniqueId();

        stopHeartDisplay(player);

        // Show immediately.
        showLives(player);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {
                    if (!player.isOnline()) {
                        stopHeartDisplay(player);
                        return;
                    }

                    if (getLives(uuid) <= 0) {
                        stopHeartDisplay(player);
                        return;
                    }

                    // Keep the three blue/grey hearts permanently visible.
                    showLives(player);
                },
                DISPLAY_REFRESH_TICKS,
                DISPLAY_REFRESH_TICKS
        );

        displayTasks.put(uuid, task);
    }

    private void stopHeartDisplay(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask oldTask = displayTasks.remove(uuid);

        if (oldTask != null) {
            oldTask.cancel();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // First-ever join gets 3 lives.
        if (!hasStoredLives(uuid)) {
            setLives(uuid, START_LIVES);
        }

        int lives = getLives(uuid);

        // Zero lives = blocked from joining.
        if (lives <= 0) {
            stopHeartDisplay(player);

            Bukkit.getScheduler().runTask(
                    this,
                    () -> {
                        if (player.isOnline()) {
                            player.kick(Component.text(
                                    "Du hast keine Leben mehr!",
                                    NamedTextColor.RED
                            ));
                        }
                    }
            );
            return;
        }

        // Do NOT modify MAX_HEALTH. Normal Minecraft health stays exactly normal.
        startHeartDisplay(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTask(
                this,
                () -> {
                    int lives = getLives(player.getUniqueId());

                    if (lives <= 0) {
                        stopHeartDisplay(player);

                        if (player.isOnline()) {
                            player.kick(Component.text(
                                    "Du hast keine Leben mehr!",
                                    NamedTextColor.RED
                            ));
                        }
                        return;
                    }

                    startHeartDisplay(player);
                }
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopHeartDisplay(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // Only a real PvP kill removes a blue life.
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }

        int remaining = Math.max(
                0,
                getLives(victim.getUniqueId()) - 1
        );

        setLives(victim.getUniqueId(), remaining);

        if (remaining <= 0) {
            stopHeartDisplay(victim);

            Bukkit.getScheduler().runTask(
                    this,
                    () -> {
                        if (victim.isOnline()) {
                            victim.kick(Component.text(
                                    "Du hast keine Leben mehr!",
                                    NamedTextColor.RED
                            ));
                        }
                    }
            );
        }
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("nation")) {
            return false;
        }

        // /nation hearts set <Spieler> <1|2|3>
        if (args.length == 4
                && args[0].equalsIgnoreCase("hearts")
                && args[1].equalsIgnoreCase("set")) {

            if (!sender.hasPermission("mcfriends.hearts.admin")) {
                sender.sendMessage(Component.text(
                        "Keine Berechtigung.",
                        NamedTextColor.RED
                ));
                return true;
            }

            String playerName = args[2];
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

            final int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text(
                        "Benutzung: /nation hearts set <Spieler> <1|2|3>",
                        NamedTextColor.RED
                ));
                return true;
            }

            if (amount < 1 || amount > 3) {
                sender.sendMessage(Component.text(
                        "Die Anzahl muss 1, 2 oder 3 sein.",
                        NamedTextColor.RED
                ));
                return true;
            }

            // This also revives a player who previously had 0 lives.
            setLives(target.getUniqueId(), amount);

            Player online = Bukkit.getPlayer(target.getUniqueId());

            if (online != null && online.isOnline()) {
                startHeartDisplay(online);
            }

            sender.sendMessage(Component.text(
                    "Leben von " + playerName + " auf " + amount + " gesetzt.",
                    NamedTextColor.GREEN
            ));

            return true;
        }

        sender.sendMessage(Component.text(
                "Benutzung: /nation hearts set <Spieler> <1|2|3>",
                NamedTextColor.YELLOW
        ));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            result.add("hearts");
        } else if (args.length == 2) {
            result.add("set");
        } else if (args.length == 3) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                result.add(player.getName());
            }
        } else if (args.length == 4) {
            result.add("1");
            result.add("2");
            result.add("3");
        }

        return result;
    }
}
