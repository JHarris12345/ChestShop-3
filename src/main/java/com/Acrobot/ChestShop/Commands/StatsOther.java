package com.Acrobot.ChestShop.Commands;

import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Utils.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.List;

public class StatsOther implements TabExecutor {

    private static ChestShop plugin = ChestShop.getPlugin();
    private static DecimalFormat df = new DecimalFormat("#,###.##");


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {

        // /csstatsother [name] [hours] (page)
        if (args.length == 2 || args.length == 3) {
            int page = 1;
            String username = args[0];

            if (!Utils.isNumber(args[1], false, false)) {
                sender.sendMessage(Utils.colour("&a[Shop] &f'" + args[0] + "' is not a valid number of hours"));
                return true;
            }

            if (args.length == 3) {
                if (!Utils.isNumber(args[2], false, false)) {
                    sender.sendMessage(Utils.colour("&a[Shop] &f'" + args[0] + "' is not a valid page number"));
                    return true;
                }

                page = Integer.parseInt(args[2]);
            }

            int hours = Integer.parseInt(args[1]);
            sender.sendMessage(Utils.colour("&a[Shop] &fGenerating global chest shop stats for " + username + " for the last " + hours + " hour" + ((hours > 1) ? "s" : "") + ". This may take a while..."));

            int finalPage = page;
            new BukkitRunnable() {
                @Override
                public void run() {
                    List<String> stats = Stats.getStats(username, hours);

                    if (stats.isEmpty()) {
                        sender.sendMessage(Utils.colour("&a[Shop] &fThere are no stats to show over the last " + hours + " hour" + ((hours > 1) ? "s" : "")));
                        return;
                    }

                    int entriesPerPage = 15;
                    int pagesNeeded = (int) Math.ceil((double) stats.size() / entriesPerPage);

                    sender.sendMessage(" ");
                    sender.sendMessage(Utils.colour("&a&l&m=&a&l[ &a&l" + username + " Chest Shop Stats - Last " + hours + " hour(s) &a&l]&m="));

                    // Start index = (page number * itemsPerPage) - itemsPerPage
                    // End index = start index + (itemsPerPage - 1)

                    int startIndex = (finalPage * entriesPerPage) - entriesPerPage;
                    int endIndex = startIndex + (entriesPerPage - 1);

                    if (endIndex >= stats.size()) endIndex = stats.size() - 1;

                    for (int i=startIndex; i<=endIndex; i++) {
                        String line = stats.get(i);
                        sender.sendMessage(line);
                    }

                    boolean sentPageButtons = Utils.sendPageButtons(sender, pagesNeeded, finalPage,
                            "/csstatsother " + username + " " + hours + " " + (finalPage + 1),
                            "/csstatsother " + username + " " + hours + " " + (finalPage - 1),
                            true);

                    sender.sendMessage(" ");

                }
            }.runTaskAsynchronously(plugin);
            return true;
        }

        sender.sendMessage(Utils.colour("&a[Shop] &fDid you mean &a/csstats [hours]&f?"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {

        if (strings.length == 1) return null;

        return List.of();
    }
}
