package com.Acrobot.ChestShop.Commands;

import com.Acrobot.ChestShop.ChestShop;
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
import java.util.stream.Collectors;

public class Stats implements TabExecutor {

    private ChestShop plugin = ChestShop.getPlugin();
    private DecimalFormat df = new DecimalFormat("#,###.##");

    private HashMap<String, Long> dateToTimeCache = new HashMap<>(); // Prevents needing to do a try block with date parsing for EVERY log of the same date

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {

        // /csstats [hours]
        if (args.length == 1) {
            if (!Utils.isNumber(args[0], false, false)) {
                sender.sendMessage(Utils.colour("&c'" + args[0] + "' is not a valid number of hours"));
                return true;
            }

            int hours = Integer.parseInt(args[0]);

            sender.sendMessage(Utils.colour("&7Generating chest shop stats for the last " + hours + " hour(s). This may take a while..."));

            new BukkitRunnable() {
                @Override
                public void run() {
                    ChestShopStats stats = getStats(hours);

                    sender.sendMessage(" ");
                    sender.sendMessage(Utils.colour("&3&l&m====&3&l[ Chest Shop Stats - Last " + hours + " hour(s) ]&3&l&m===="));
                    sender.sendMessage(Utils.colour("&bTotal sold volume: &f$" + df.format(stats.getTotalVolume())));
                    sender.sendMessage(Utils.colour("&bTotal tax volume: &f$" + df.format(stats.getTotalVolume() - stats.getTotalAfterTax())));
                    sender.sendMessage(Utils.colour("&3&l&m==========================================="));
                    sender.sendMessage(" ");

                }
            }.runTaskAsynchronously(plugin);
            return true;
        }

        sender.sendMessage(Utils.colour("&7Did you mean &b/csstats [hours]&7?"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        return List.of();
    }

    private ChestShopStats getStats(long hours) {
        double total = 0;
        double tax = 0; // This is the total volume after tax is removed. NOT the total tax paid. Total tax paid would be total - tax

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
                long lastLineTime = getLongTimeFromLogTime(lastLineTimeString);

                if (System.currentTimeMillis() - lastLineTime > (hours * 60 * 60000)) continue;

                for (String line : lines) {
                    try {
                        if (!line.contains("after tax)")) continue;

                        String timeString = line.substring(0, 22);
                        long time = getLongTimeFromLogTime(timeString);

                        // Only the time frame stated
                        if (System.currentTimeMillis() - time > (hours * 60 * 60000)) continue;

                        String[] split = line.split(" ");

                        total += Double.parseDouble(line.split(" for ")[1].split(" ")[0].replace(",", ""));
                        tax += Double.parseDouble(line.split("\\[world] ")[1].split(" ")[3].replace("(", "").replace(",", ""));

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

        return new ChestShopStats(total, tax);
    }

    private long getLongTimeFromLogTime(String logTime) {
        long time = dateToTimeCache.getOrDefault(logTime, 0L);

        if (time == 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("[dd-MMM-yyyy HH:mm:ss]");

            try {
                time = sdf.parse(logTime).getTime();
                dateToTimeCache.put(logTime, time);

            } catch (ParseException e) {
                plugin.getLogger().info("Error parsing date for log time: ");
                plugin.getLogger().info(logTime);

                e.printStackTrace();
                return 0;
            }
        }

        return time;
    }
}
