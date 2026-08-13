package de.mcfriends.hearts;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class McFriendsHeartsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private static final int START_LIVES = 3;
    private static final char ACTIVE_HEART = '\uE01B';
    private static final char INACTIVE_HEART = '\uE01C';

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        Command command = getCommand("nation");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        getLogger().info("McFriends-Hearts-Plugin aktiviert.");
    }

    private boolean hasSavedLives(UUID uuid) {
        return getConfig().contains("players." + uuid);
    }

    private int getLives(UUID uuid) {
        int value = getConfig().getInt("players." + uuid, START_LIVES);
        return Math.max(0, Math.min(3, value));
    }

    private void setLives(UUID uuid, int amount) {
        amount = Math.max(0, Math.min(3, amount));
        getConfig().set("players." + uuid, amount);
        saveConfig();
    }

    private void showLives(Player player) {
        int lives = getLives(player.getUniqueId());
        StringBuilder hearts = new StringBuilder(3);
        for (int i = 0; i < 3; i++) {
            hearts.append(i < lives ? ACTIVE_HEART : INACTIVE_HEART);
        }
        player.sendActionBar(Component.text(hearts.toString()));
    }

    private void restoreNormalHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(20.0);
        }
        if (player.getHealth() > 20.0) {
            player.setHealth(20.0);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!hasSavedLives(uuid)) {
            setLives(uuid, START_LIVES);
        }

        int lives = getLives(uuid);
        if (lives <= 0) {
            Bukkit.getScheduler().runTask(this, () ->
                    player.kick(Component.text("Du hast keine Leben mehr!", NamedTextColor.RED)));
            return;
        }

        restoreNormalHealth(player);
        showLives(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            if (getLives(player.getUniqueId()) <= 0) {
                player.kick(Component.text("Du hast keine Leben mehr!", NamedTextColor.RED));
                return;
            }
            restoreNormalHealth(player);
            showLives(player);
        });
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim.getKiller() == null) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (victim.isOnline() && getLives(victim.getUniqueId()) > 0) {
                    showLives(victim);
                }
            });
            return;
        }

        int remaining = Math.max(0, getLives(victim.getUniqueId()) - 1);
        setLives(victim.getUniqueId(), remaining);

        if (remaining <= 0) {
            Bukkit.getScheduler().runTask(this, () -> {
                if (victim.isOnline()) {
                    victim.kick(Component.text("Du hast keine Leben mehr!", NamedTextColor.RED));
                }
            });
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("nation")) {
            return false;
        }

        if (args.length == 4
                && args[0].equalsIgnoreCase("hearts")
                && args[1].equalsIgnoreCase("set")) {

            if (!sender.hasPermission("mcfriends.hearts.admin")) {
                sender.sendMessage(Component.text("Keine Berechtigung.", NamedTextColor.RED));
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text(
                        "Benutzung: /nation hearts set <Spieler> <1|2|3>",
                        NamedTextColor.RED));
                return true;
            }

            if (amount < 1 || amount > 3) {
                sender.sendMessage(Component.text(
                        "Die Anzahl muss 1, 2 oder 3 sein.", NamedTextColor.RED));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            setLives(target.getUniqueId(), amount);

            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null && online.isOnline()) {
                restoreNormalHealth(online);
                showLives(online);
            }

            sender.sendMessage(Component.text(
                    "Leben von " + args[2] + " auf " + amount + " gesetzt.",
                    NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text(
                "Benutzung: /nation hearts set <Spieler> <1|2|3>",
                NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
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
