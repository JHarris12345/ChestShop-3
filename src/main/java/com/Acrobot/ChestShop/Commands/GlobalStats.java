package com.Acrobot.ChestShop.Commands;

import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Logging.TransactionLogEntry;
import com.Acrobot.ChestShop.Utils.ChestShopStats;
import com.Acrobot.ChestShop.Utils.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class GlobalStats implements TabExecutor {

    private ChestShop plugin = ChestShop.getPlugin();
    private DecimalFormat df = new DecimalFormat("#,###.##");


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {

        // /csglobalstats [hours]
        if (args.length == 1) {
            if (!Utils.isNumber(args[0], false, false)) {
                sender.sendMessage(Utils.colour("&a[Shop] &f'" + args[0] + "' is not a valid number of hours"));
                return true;
            }

            int hours = Integer.parseInt(args[0]);

            sender.sendMessage(Utils.colour("&a[Shop] &fGenerating global chest shop stats for the last " + hours + " hour(s). This may take a while..."));

            new BukkitRunnable() {
                @Override
                public void run() {
                    ChestShopStats stats = getStats(hours);

                    sender.sendMessage(" ");
                    sender.sendMessage(Utils.colour("&a&l&m===&a&l[ &a&lChest Shop Stats - Last " + hours + " hour(s) &a&l]&m==="));
                    sender.sendMessage(Utils.colour("&aTotal sold volume: &f$" + df.format(stats.getTotalVolume())));
                    sender.sendMessage(Utils.colour("&aTotal tax volume: &f$" + df.format(stats.getTotalVolume() - stats.getTotalAfterTax())));
                    sender.sendMessage(Utils.colour("&aTotal GC volume: &f" + df.format(stats.getGcVolume()) + " GC"));
                    sender.sendMessage(" ");

                }
            }.runTaskAsynchronously(plugin);
            return true;
        }

        sender.sendMessage(Utils.colour("&a[Shop] &fDid you mean &a/csglobalstats [hours]&f?"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        return List.of();
    }

    private ChestShopStats getStats(long hours) {
        double total = 0;
        double tax = 0; // This is the total volume after tax is removed. NOT the total tax paid. Total tax paid would be total - tax
        double gcVolume = 0; // GC trades are never taxed and tracked separately so currencies don't combine

        File[] files = new File(plugin.getDataFolder(), "logs").listFiles();

        for (File file : files) {
            if (file.isDirectory()) continue;

            try {
                BufferedReader br = new BufferedReader(new FileReader(file));
                String l;

                List<String> lines = new ArrayList<>();
                try {
                    while ((l = br.readLine()) != null) {
                        lines.add(l);
                    }
                } finally {
                    br.close();
                }

                // If none of the lines in this log fall within the selected time frame, skip the log
                // (we get this info by just looking at the last line of the log)
                String lastLine = lines.getLast();

                String lastLineTimeString = lastLine.substring(0, 22);
                long lastLineTime = Utils.getLongTimeFromLogTime(lastLineTimeString);

                if (System.currentTimeMillis() - lastLineTime > (hours * 60 * 60000)) continue;

                for (String line : lines) {
                    try {
                        // Handles both the current log format (which includes each player's UUID) and older ones
                        TransactionLogEntry entry = TransactionLogEntry.parse(line);
                        if (entry == null) continue;

                        // Only the time frame stated
                        if (System.currentTimeMillis() - entry.getTime() > (hours * 60 * 60000)) continue;

                        // GC trades are never taxed (so they have no "after tax)" suffix) - count them separately
                        if (entry.isGc()) {
                            gcVolume += entry.getPrice();
                            continue;
                        }

                        // TODO: If we ever remove this check and include lines that DON'T have tax, then we need to re-do the system so that the totalAfterTax
                        // becomes totalTax and the tax is calculated for each individual line, else it will be off (see how auction house stats command does it)
                        if (entry.getAfterTax() == null) continue;

                        total += entry.getPrice();
                        tax += entry.getAfterTax();

                    } catch (Exception ex) {
                        plugin.getLogger().info("Error whilst calculating line");
                        plugin.getLogger().info(line);

                        ex.printStackTrace();
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().info("Error calculating chest shop stats");
                e.printStackTrace();
            }
        }

        return new ChestShopStats(total, tax, gcVolume);
    }
}
