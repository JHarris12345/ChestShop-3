package com.Acrobot.ChestShop;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * @author Acrobot
 */
public enum Permission {
    SHOP_CREATION_BUY("ChestShop.shop.create.buy"),
    SHOP_CREATION_BUY_ID("ChestShop.shop.create.buy."),

    SHOP_CREATION_SELL("ChestShop.shop.create.sell"),
    SHOP_CREATION_SELL_ID("ChestShop.shop.create.sell."),

    SHOP_CREATION("ChestShop.shop.create"),
    SHOP_CREATION_ID("ChestShop.shop.create."),

    BUY("ChestShop.shop.buy"),
    BUY_ID("ChestShop.shop.buy."),

    SELL_ID("ChestShop.shop.sell."),
    SELL("ChestShop.shop.sell"),

    ADMIN("ChestShop.admin"),
    ADMIN_SHOP("ChestShop.adminshop"),
    MOD("ChestShop.mod"),
    OTHER_NAME("ChestShop.name"),
    OTHER_NAME_CREATE("ChestShop.othername.create"),
    OTHER_NAME_DESTROY("ChestShop.othername.destroy"),
    OTHER_NAME_ACCESS("ChestShop.othername.access"),
    GROUP("ChestShop.group."),

    NOFEE("ChestShop.nofee"),
    DISCOUNT("ChestShop.discount."),
    NO_BUY_TAX("ChestShop.notax.buy"),
    NO_SELL_TAX("ChestShop.notax.sell"),

    NOTIFY_TOGGLE("ChestShop.toggle"),
    ACCESS_TOGGLE("ChestShop.accesstoggle"),
    STATS("ChestShop.stats"),
    STATS_OTHER("ChestShop.stats.other"),
    GLOBAL_STATS("ChestShop.globalstats"),
    ITEMINFO("ChestShop.iteminfo"),
    SHOPINFO("ChestShop.shopinfo"),

    NOLIMIT_MIN_BUY("ChestShop.nolimit.buy.min"),
    NOLIMIT_MIN_BUY_ID("ChestShop.nolimit.buy.min."),

    NOLIMIT_MAX_BUY("ChestShop.nolimit.buy.max"),
    NOLIMIT_MAX_BUY_ID("ChestShop.nolimit.buy.max."),

    NOLIMIT_MIN_SELL("ChestShop.nolimit.sell.min"),
    NOLIMIT_MIN_SELL_ID("ChestShop.nolimit.sell.min."),

    NOLIMIT_MAX_SELL("ChestShop.nolimit.sell.max"),
    NOLIMIT_MAX_SELL_ID("ChestShop.nolimit.sell.max.");

    private final String permission;

    Permission(String permission) {
        this.permission = permission;
    }

    public static boolean has(CommandSender sender, Permission permission) {
        return has(sender, permission.permission);
    }

    public static boolean has(CommandSender sender, String node) {
        return sender.hasPermission(node) || sender.hasPermission(node.toLowerCase(Locale.ROOT));
    }

    public static boolean otherName(Player player, String name) {
        return otherName(player, OTHER_NAME, name);
    }

    public static boolean otherName(Player player, Permission base, String name) {
        boolean hasBase = base != OTHER_NAME && otherName(player, OTHER_NAME, name);
        if (hasBase || has(player, base + ".*")) {
            return !hasPermissionSetFalse(player, base+ "." + name) && !hasPermissionSetFalse(player, base + "." + name.toLowerCase(Locale.ROOT));
        }

        return has(player, base + "." + name) || has(player, base + "." + name.toLowerCase(Locale.ROOT));
    }

    public static boolean hasPermissionSetFalse(CommandSender sender, String permission) {
        return (sender.isPermissionSet(permission) && !sender.hasPermission(permission))
                || (sender.isPermissionSet(permission.toLowerCase(Locale.ROOT)) && !sender.hasPermission(permission.toLowerCase(Locale.ROOT)));
    }

    public static org.bukkit.permissions.Permission getPermission(Permission permission) {
        org.bukkit.permissions.Permission bukkitPerm = Bukkit.getServer().getPluginManager().getPermission(permission.permission);
        if (bukkitPerm == null) {
            bukkitPerm = permission.getPermission();
            try {
                Bukkit.getServer().getPluginManager().addPermission(bukkitPerm);
            } catch (IllegalArgumentException ignored) {} // this should never happen
        }
        return bukkitPerm;
    }

    public org.bukkit.permissions.Permission getPermission() {
        return new org.bukkit.permissions.Permission(permission);
    }

    public String toString() {
        return permission;
    }
}
