package com.Acrobot.ChestShop.Utils;

import com.Acrobot.ChestShop.ChestShop;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static HashMap<String, Long> dateToTimeCache = new HashMap<>(); // Prevents needing to do a try block with date parsing for EVERY log of the same date

    public static boolean isNumber(String string, Boolean canBeNegative, Boolean canBeZero) {
        try {
            Long.valueOf(string);

            if (!canBeNegative) {
                if (Long.parseLong(string) < 0) return false;
            }

            if (!canBeZero) {
                if (Long.parseLong(string) == 0) return false;
            }

            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String colour(String string) {
        Pattern pattern = Pattern.compile("&?#[A-Fa-f0-9]{6}");
        Matcher matcher = pattern.matcher(string);
        String output = ChatColor.translateAlternateColorCodes('&', string);

        while (matcher.find()) {
            String color = string.substring(matcher.start(), matcher.end());
            output = output.replace(color, "" + net.md_5.bungee.api.ChatColor.of(color.replace("&", "")));
        }

        return output;
    }

    public static long getLongTimeFromLogTime(String logTime) {
        long time = dateToTimeCache.getOrDefault(logTime, 0L);

        if (time == 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("[dd-MMM-yyyy HH:mm:ss]");

            try {
                time = sdf.parse(logTime).getTime();
                dateToTimeCache.put(logTime, time);

            } catch (ParseException e) {
                ChestShop.getPlugin().getLogger().info("Error parsing date for log time: ");
                ChestShop.getPlugin().getLogger().info(logTime);

                e.printStackTrace();
                return 0;
            }
        }

        return time;
    }

    // Set commandWithSlash to null for just hover text
    public static BaseComponent createClickableText(String text, String hoverText, String commandWithSlash) {
        TextComponent textComponent = new TextComponent();

        textComponent.setText(Utils.colour(text));
        textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(Utils.colour(hoverText))));
        if (commandWithSlash != null) textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandWithSlash));

        return textComponent;
    }


    // Returns true if page buttons were sent and false if not
    public static boolean sendPageButtons(CommandSender sender, int pagesNeeded, int pageNumber, String nextPageCommandWithSlash, String backPageCommandWithSlash, boolean addSpace) {
        boolean sentButtons = false;
        if (pagesNeeded > 1 && addSpace) sender.sendMessage("");

        boolean java = (sender instanceof Player) && !isPlayerBedrock(((Player) sender).getUniqueId());

        if (pagesNeeded > 1 && pageNumber == 1) {
            if (java) {
                sender.sendMessage(Utils.createClickableText("&7Next Page [»]", "Click me", nextPageCommandWithSlash));
            }
            sentButtons = true;
        }

        if (pageNumber > 1 && pageNumber < pagesNeeded) {
            if (java) {
                sender.sendMessage(Utils.createClickableText("&7[«] Previous Page", "Click me", backPageCommandWithSlash),
                        new TextComponent("         "),
                        Utils.createClickableText("&7Next Page [»]", "Click me", nextPageCommandWithSlash));
            }
            sentButtons = true;
        }

        if (pageNumber == pagesNeeded && pagesNeeded != 1) {
            if (java) {
                sender.sendMessage(Utils.createClickableText("&7[«] Previous Page", "Click me", backPageCommandWithSlash));
            }
            sentButtons = true;
        }

        // For bedrock players we send the page command but replace the page number with (page)
        if (sentButtons && !java) {
            StringBuilder commandNoPageNumber = new StringBuilder();
            String[] words = nextPageCommandWithSlash.split(" ");

            for (int i=0; i<words.length; i++) {
                if (i != words.length-1) {
                    commandNoPageNumber.append(words[i]).append(" ");
                } else {
                    commandNoPageNumber.append("(page)");
                }
            }

            sender.sendMessage(Utils.colour("&7" + commandNoPageNumber));
        }

        return sentButtons;
    }

    public static boolean isPlayerBedrock(UUID uuid) {
        return uuid.toString().startsWith("00000000-0000-0000");
    }

    public static String createPasteLink(List<String> contentList) {
        String content = String.join("\n", contentList);

        try {
            // The pastes.dev API URL for creating a new paste
            URL url = new URL("https://api.pastes.dev/post");

            // Open a connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            // Send the request payload
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = content.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read the response
            try (Scanner scanner = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8)) {
                String response = scanner.useDelimiter("\\A").next(); // This is the response from the server, which will contain the paste URL key
                JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

                return "https://api.pastes.dev/" + jsonResponse.get("key").getAsString();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
