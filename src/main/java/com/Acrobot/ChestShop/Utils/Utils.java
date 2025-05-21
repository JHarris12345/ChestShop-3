package com.Acrobot.ChestShop.Utils;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

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
}
