package com.Acrobot.ChestShop.Commands;

import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Utils.ChestShopStats;
import com.Acrobot.ChestShop.Utils.Utils;
import com.j256.ormlite.stmt.query.In;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Stats implements TabExecutor {

    private static ChestShop plugin = ChestShop.getPlugin();
    private static DecimalFormat df = new DecimalFormat("#,###.##");

    public static HashMap<String, String> downloadCache = new HashMap<>(); // A map of recent player names that have requested stats and their download link (so it doesn't need to be generated each time when switching pages)


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] args) {

        // /csstats [hours] (page)
        if (args.length == 1 || args.length == 2) {
            int page = 1;
            String username;

            if (!(sender instanceof Player player)) {
                sender.sendMessage(Utils.colour("&a[Shop] &fYou must be a player to use this command"));
                return true;
            }

            username = player.getName();

            if (!Utils.isNumber(args[0], false, false)) {
                sender.sendMessage(Utils.colour("&a[Shop] &f'" + args[0] + "' is not a valid number of hours"));
                return true;
            }

            if (args.length == 2) {
                if (!Utils.isNumber(args[1], false, false)) {
                    sender.sendMessage(Utils.colour("&a[Shop] &f'" + args[0] + "' is not a valid page number"));
                    return true;
                }

                page = Integer.parseInt(args[1]);
            }

            // If they are scrolling through pages, don't clear the download link cache. If they re-ran the command again, clear it
            if (args.length == 1) {
                downloadCache.remove(username);
            }

            int hours = Integer.parseInt(args[0]);
            sender.sendMessage(Utils.colour("&a[Shop] &fGenerating global chest shop stats for the last " + hours + " hour" + ((hours > 1) ? "s" : "") + ". This may take a while..."));

            int finalPage = page;
            new BukkitRunnable() {
                @Override
                public void run() {
                    List<String> stats = getStats(username, hours);
                    String downloadLink = downloadCache.getOrDefault(username, null);

                    if (stats.isEmpty()) {
                        sender.sendMessage(Utils.colour("&a[Shop] &fThere are no stats to show over the last " + hours + " hour" + ((hours > 1) ? "s" : "")));
                        return;
                    }

                    if (downloadLink == null) {
                        List<String> downloadableStats = new ArrayList<>(); // The downloadable version can't have colours translated else it sends weird formatting
                        stats.forEach(stat -> downloadableStats.add(ChatColor.stripColor(stat)));

                        downloadLink = Utils.createPasteLink(downloadableStats);
                        downloadCache.put(username, downloadLink);
                    }

                    int entriesPerPage = 15;
                    int pagesNeeded = (int) Math.ceil((double) stats.size() / entriesPerPage);

                    sender.sendMessage(" ");
                    sender.sendMessage(Utils.colour("&a&l&m===&a&l[ &a&lChest Shop Stats - Last " + hours + " hour" + ((hours > 1) ? "s" : "") + " &a&l]&m==="));

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
                            "/csstats " + hours + " " + (finalPage + 1),
                            "/csstats " + hours + " " + (finalPage - 1),
                            true);

                    if (!Utils.isPlayerBedrock(player.getUniqueId())) {
                        if (!sentPageButtons) sender.sendMessage("");

                        TextComponent downloadButton = Component.text(Utils.colour("&e&l[&eDownload Stats&l]"));
                        downloadButton = downloadButton.hoverEvent(HoverEvent.showText(Component.text("Click to download stats")));
                        downloadButton = downloadButton.clickEvent(ClickEvent.openUrl(downloadLink));

                        sender.sendMessage(downloadButton);
                    }

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
        return List.of();
    }

    public static List<String> getStats(String username, long hours) {
        List<String> result = new ArrayList<>();
        LinkedHashMap<String, Double> chestshops = new LinkedHashMap<>(); // A map of the coords of every chest shop that had action and how much it earned the player (includes negative values for ones that lost the player money)

        File[] files = new File(plugin.getDataFolder(), "logs").listFiles();
        String lowercaseName = username.toLowerCase();

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
                        String lowercaseLine = line.toLowerCase();
                        if (!lowercaseLine.contains(" " + lowercaseName + " ")) continue;

                        String[] split = line.split(" ");
                        String action = split[3];

                        if (!action.equals("bought") && !action.equals("sold")) continue;
                        boolean earnedMoney = (action.equals("bought"));

                        String timeString = line.substring(0, 22);
                        long time = Utils.getLongTimeFromLogTime(timeString);

                        // Only the time frame stated
                        if (System.currentTimeMillis() - time > (hours * 60 * 60000)) continue;

                        // Make sure the username is the one who owns the chest shop
                        String receiver;
                        if (earnedMoney) {
                            receiver = line.split(" from ")[1].split(" ")[0];
                        } else {
                            receiver = line.split(" to ")[1].split(" ")[0];
                        }

                        if (!lowercaseName.equals(receiver.toLowerCase())) continue;

                        String location = line.split(" at \\[")[1];
                        location = "[" + location; // Add back the leading [ we removed for the split above (we included it in the split to make it a more accurate split)
                        if (location.contains(" (")) location = location.split(" \\(")[0]; // Remove the tax section if it is there

                        double price = Double.parseDouble(line.split(" for ")[1].split(" ")[0]);

                        chestshops.put(location, chestshops.getOrDefault(location, 0d) + ((earnedMoney) ? price : (0 - price)));

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

        chestshops = chestshops.entrySet().stream()
                .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()))  // Sort by value descending
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new  // To maintain insertion order after sorting
                ));

        for (Map.Entry<String, Double> entry : chestshops.entrySet()) {
            String location = entry.getKey();
            double value = entry.getValue();

            // We need to manually remove the - sign else it gets put after the $ (like $-25) so we just do that formatting ourselves
            result.add(Utils.colour("&f" + location + " " + ((value < 0) ? "&c-" : "&a") + "$" + ((value < 0) ? df.format((0 - value)) : df.format(value))));
        }

        return result;
    }
}
