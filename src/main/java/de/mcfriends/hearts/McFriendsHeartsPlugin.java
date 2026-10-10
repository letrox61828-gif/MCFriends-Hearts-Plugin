
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID uuid = victim.getUniqueId();

        // FriendsCombat hat dieses Leben bereits abgezogen.
        if (combatLifeHandled.remove(uuid)) {
            kickIfOutOfLives(victim);
            return;
        }

        // Außerhalb des Combat-Systems bleibt das bisherige Verhalten:
        // Nur der Tod durch einen Spieler zieht ein Leben ab.
        if (victim.getKiller() == null) {
            return;
        }

        int remaining = Math.max(0, getLives(uuid) - 1);
        setLives(uuid, remaining);

        if (remaining <= 0) {
            stopHeartDisplay(victim);
            Bukkit.getScheduler().runTask(this, () -> {
                if (victim.isOnline()) {
                    victim.kick(Component.text(
                            "Du hast keine Leben mehr!",
                            NamedTextColor.RED
                    ));
                }
            });
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
