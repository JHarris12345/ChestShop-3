package com.Acrobot.ChestShop.Economy;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

/**
 * Bridges to the InsanityCore "giftcards" (GC) currency.
 *
 * The balance is read through the PlaceholderAPI placeholder
 * {@code %icore_gc_balance%} and modified through the console
 * {@code giftcards give/take} commands - the same approach the auction house
 * uses - so ChestShop does not need a compile time dependency on InsanityCore.
 */
public class GiftcardsHelper {

    /** Tag recorded as the "sender" in the giftcards log. */
    private static final String LOG_TAG = "CHESTSHOP";

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.US);

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null
                && Bukkit.getPluginManager().getPlugin("InsanityCore") != null;
    }

    public static double getBalance(OfflinePlayer player) {
        if (!isAvailable() || player == null) {
            return 0;
        }

        String raw = PlaceholderAPI.setPlaceholders(player, "%icore_gc_balance%");
        if (raw == null || raw.isEmpty()) {
            return 0;
        }

        try {
            return NUMBER_FORMAT.parse(raw).doubleValue();
        } catch (ParseException ex) {
            try {
                return Double.parseDouble(raw.replace(",", ""));
            } catch (NumberFormatException ex2) {
                return 0;
            }
        }
    }

    public static void give(String name, double amount) {
        dispatch("give", name, amount);
    }

    public static void take(String name, double amount) {
        dispatch("take", name, amount);
    }

    private static void dispatch(String action, String name, double amount) {
        if (amount <= 0 || name == null || name.isEmpty()) {
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "giftcards " + action + " " + name + " " + amount + " " + LOG_TAG);
    }
}
