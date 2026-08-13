package de.mcfriends.hearts;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
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
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class McFriendsHeartsPlugin extends JavaPlugin
        implements Listener, CommandExecutor, TabCompleter {

    private static final int START_LIVES = 3;

    // Herz-Glyphen aus deinem Ressourcenpaket
    // active_heart  = blau
    // inactive_heart = grau
    private static final char ACTIVE_HEART = '\uE01B';
    private static final char INACTIVE_HEART = '\uE01C';

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

    private int getLives(UUID uuid) {

        String path = "players." + uuid;

        if (!getConfig().contains(path)) {
            return START_LIVES;
        }

        return Math.max(
                0,
                Math.min(
                        3,
                        getConfig().getInt(path)
                )
        );
    }

    private void setLives(UUID uuid, int lives) {

        lives = Math.max(0, Math.min(3, lives));

        getConfig().set(
                "players." + uuid,
                lives
        );

        saveConfig();

        Player player = Bukkit.getPlayer(uuid);

        if (player != null && player.isOnline()) {

            if (lives > 0) {
                keepNormalHealth(player);
                showLives(player);
            }
        }
    }

    private void showLives(Player player) {

        int lives = getLives(
                player.getUniqueId()
        );

        StringBuilder display = new StringBuilder();

        // Immer genau 3 Herz-Plätze anzeigen
        for (int i = 0; i < 3; i++) {

            if (i < lives) {
                display.append(ACTIVE_HEART);
            } else {
                display.append(INACTIVE_HEART);
            }
        }

        // Anzeige über der Hotbar
        player.sendActionBar(
                Component.text(display.toString())
        );
    }

    private void keepNormalHealth(Player player) {

        var attribute = player.getAttribute(
                Attribute.MAX_HEALTH
        );

        if (attribute != null) {
            attribute.setBaseValue(20.0);
        }

        // Normale Minecraft-Gesundheit:
        // 20 HP = 10 rote Herzen
        if (player.getHealth() > 20.0) {
            player.setHealth(20.0);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        UUID uuid = player.getUniqueId();

        /*
         * Erster Join:
         * Spieler bekommt automatisch 3 blaue Leben.
         */
        if (!getConfig().contains("players." + uuid)) {

            setLives(uuid, START_LIVES);
        }

        int lives = getLives(uuid);

        /*
         * 0 Leben:
         * Spieler darf nicht auf den Server.
         */
        if (lives <= 0) {

            Bukkit.getScheduler().runTask(
                    this,
                    () -> player.kick(
                            Component.text(
                                    "Du hast keine Leben mehr!",
                                    NamedTextColor.RED
                            )
                    )
            );

            return;
        }

        /*
         * Normale Minecraft-Gesundheit bleibt erhalten.
         */
        keepNormalHealth(player);

        /*
         * Blaue/graue Herzen anzeigen.
         */
        showLives(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {

        Player player = event.getPlayer();

        Bukkit.getScheduler().runTask(
                this,
                () -> {

                    int lives = getLives(
                            player.getUniqueId()
                    );

                    if (lives <= 0) {

                        player.kick(
                                Component.text(
                                        "Du hast keine Leben mehr!",
                                        NamedTextColor.RED
                                )
                        );

                        return;
                    }

                    keepNormalHealth(player);

                    showLives(player);
                }
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Leben werden in config.yml gespeichert.
        // Beim Verlassen wird nichts zurückgesetzt.
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {

        Player victim = event.getEntity();

        /*
         * Nur wenn ein Spieler einen anderen Spieler tötet,
         * wird ein blaues Leben abgezogen.
         */
        Player killer = victim.getKiller();

        if (killer == null) {
            return;
        }

        int remaining = Math.max(
                0,
                getLives(victim.getUniqueId()) - 1
        );

        setLives(
                victim.getUniqueId(),
                remaining
        );

        /*
         * Letztes Leben verloren.
         */
        if (remaining <= 0) {

            Bukkit.getScheduler().runTask(
                    this,
                    () -> {

                        if (victim.isOnline()) {

                            victim.kick(
                                    Component.text(
                                            "Du hast keine Leben mehr!",
                                            NamedTextColor.RED
                                    )
                            );
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

        /*
         * /nation hearts set <Spieler> <1|2|3>
         */
        if (args.length == 4
                && args[0].equalsIgnoreCase("hearts")
                && args[1].equalsIgnoreCase("set")) {

            /*
             * Nur OPs / Spieler mit Permission dürfen setzen.
             */
            if (!sender.hasPermission(
                    "mcfriends.hearts.admin"
            )) {

                sender.sendMessage(
                        Component.text(
                                "Keine Berechtigung.",
                                NamedTextColor.RED
                        )
                );

                return true;
            }

            String playerName = args[2];

            OfflinePlayer target =
                    Bukkit.getOfflinePlayer(playerName);

            int amount;

            try {

                amount = Integer.parseInt(args[3]);

            } catch (NumberFormatException exception) {

                sender.sendMessage(
                        Component.text(
                                "Benutzung: /nation hearts set <Spieler> <1|2|3>",
                                NamedTextColor.RED
                        )
                );

                return true;
            }

            /*
             * Nur 1, 2 oder 3 sind erlaubt.
             */
            if (amount < 1 || amount > 3) {

                sender.sendMessage(
                        Component.text(
                                "Die Anzahl muss 1, 2 oder 3 sein.",
                                NamedTextColor.RED
                        )
                );

                return true;
            }

            /*
             * Leben setzen.
             * Das funktioniert auch bei Spielern,
             * die aktuell offline sind oder 0 Leben haben.
             */
            setLives(
                    target.getUniqueId(),
                    amount
            );

            sender.sendMessage(
                    Component.text(
                            "Leben von "
                                    + playerName
                                    + " auf "
                                    + amount
                                    + " gesetzt.",
                            NamedTextColor.GREEN
                    )
            );

            /*
             * Falls Spieler online ist:
             * Herzanzeige sofort aktualisieren.
             */
            Player online =
                    Bukkit.getPlayer(
                            target.getUniqueId()
                    );

            if (online != null && online.isOnline()) {

                keepNormalHealth(online);

                showLives(online);
            }

            return true;
        }

        sender.sendMessage(
                Component.text(
                        "Benutzung: /nation hearts set <Spieler> <1|2|3>",
                        NamedTextColor.YELLOW
                )
        );

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {

        List<String> result =
                new ArrayList<>();

        if (args.length == 1) {

            result.add("hearts");

        } else if (args.length == 2) {

            result.add("set");

        } else if (args.length == 3) {

            for (Player player :
                    Bukkit.getOnlinePlayers()) {

                result.add(
                        player.getName()
                );
            }

        } else if (args.length == 4) {

            result.add("1");
            result.add("2");
            result.add("3");
        }

        return result;
    }
}
