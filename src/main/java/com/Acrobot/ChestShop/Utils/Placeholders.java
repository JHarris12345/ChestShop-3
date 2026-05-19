package com.Acrobot.ChestShop.Utils;

import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Database.Account;
import com.Acrobot.ChestShop.UUIDs.NameManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class Placeholders extends PlaceholderExpansion {

    ChestShop plugin;

    public Placeholders(ChestShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "chestshop";
    }

    @Override
    public @NotNull String getAuthor() {
        return "JHarris";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }


    @Override
    public String onRequest(OfflinePlayer player, String identifier) {

        // %chestshop_seeing_messages%
        if (identifier.equalsIgnoreCase("seeing_messages")) {
            Account account = NameManager.getOrCreateAccount(player);
            return (!account.isIgnoringMessages() ? "true" : "false");
        }

        return null;
    }
}
