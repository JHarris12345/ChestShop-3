package com.Acrobot.ChestShop.Inventories;

import com.Acrobot.ChestShop.Listeners.Player.PlayerInteract;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

import static com.Acrobot.Breeze.Utils.ImplementationAdapter.getState;

/**
 * An anvil GUI that lets a player enter how many items they want to buy/sell at
 * a shop. The result slot shows a paper icon describing the amount and the total
 * cost, and confirming runs the transaction for that amount.
 *
 * Mirrors the anvil pattern used by InsanityCore (giftcards / trade).
 */
public class AmountAnvilGUI implements Listener {

    private final Plugin plugin;
    private final Player player;
    private final Block signBlock;

    private AnvilView view;
    private boolean confirmed = false;

    public AmountAnvilGUI(Plugin plugin, Player player, Block signBlock) {
        this.plugin = plugin;
        this.player = player;
        this.signBlock = signBlock;
    }

    public void open() {
        Sign sign = currentSign();
        if (sign == null) {
            return;
        }

        String title = ChestShopSign.getShopType(sign) == ChestShopSign.ShopType.SELL ? "Amount to sell" : "Amount to buy";
        Component titleComponent = LegacyComponentSerializer.legacySection().deserialize(ChatColor.BLACK + title);

        this.view = MenuType.ANVIL.create(player, titleComponent);

        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(view);

        ItemStack prompt = new ItemStack(Material.PAPER);
        ItemMeta promptMeta = prompt.getItemMeta();
        promptMeta.setDisplayName(" ");
        prompt.setItemMeta(promptMeta);
        view.getTopInventory().setItem(0, prompt);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (event.getView() != view) {
            return;
        }

        event.getView().setRepairCost(0);
        event.getView().setMaximumRepairCost(0);

        // Requires a one tick delay to take effect
        new BukkitRunnable() {
            @Override
            public void run() {
                if (view != null) {
                    updateResult();
                }
            }
        }.runTaskLater(plugin, 1);
    }

    private void updateResult() {
        ItemStack result = new ItemStack(Material.PAPER);
        ItemMeta meta = result.getItemMeta();

        Sign sign = currentSign();
        int quantity = parseQuantity(view.getRenameText());

        if (sign == null) {
            meta.setDisplayName(ChatColor.RED + "This shop no longer exists");
        } else if (quantity < 1) {
            meta.setDisplayName(ChatColor.RED + "Enter an amount");
        } else {
            boolean sell = ChestShopSign.getShopType(sign) == ChestShopSign.ShopType.SELL;
            ChestShopSign.Currency currency = ChestShopSign.getCurrency(sign);
            BigDecimal unitPrice = ChestShopSign.getExactPrice(sign);
            BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

            meta.setDisplayName(ChatColor.GREEN + (sell ? "Sell " : "Buy ") + quantity + "x " + ChestShopSign.getItem(sign));
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Total: " + ChatColor.GREEN + ChestShopSign.formatPrice(total, currency),
                    ChatColor.YELLOW + "Click to confirm"
            ));
        }

        result.setItemMeta(meta);
        view.getTopInventory().setItem(2, result);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView() != view) {
            return;
        }
        event.setCancelled(true);

        if (event.getRawSlot() != 0 && event.getRawSlot() != 2) {
            return;
        }

        int quantity = parseQuantity(view.getRenameText());
        if (quantity < 1) {
            player.sendMessage(ChatColor.RED + "Enter a valid amount first.");
            return;
        }

        Sign sign = currentSign();
        confirmed = true;
        player.closeInventory();

        if (sign == null) {
            player.sendMessage(ChatColor.RED + "This shop no longer exists.");
            return;
        }

        final int finalQuantity = quantity;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                Sign current = currentSign();
                if (current != null) {
                    PlayerInteract.executeTransaction(current, player, finalQuantity);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView() != view) {
            return;
        }

        // Wipe the anvil slots so the prompt/result papers aren't returned to the player
        view.getTopInventory().clear();
        HandlerList.unregisterAll(this);
    }

    private Sign currentSign() {
        if (signBlock == null || !com.Acrobot.Breeze.Utils.BlockUtil.isSign(signBlock)) {
            return null;
        }
        Sign sign = (Sign) getState(signBlock, false);
        return ChestShopSign.isValid(sign) ? sign : null;
    }

    private static int parseQuantity(String input) {
        if (input == null) {
            return -1;
        }
        input = input.trim().replace(",", "");
        if (input.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
