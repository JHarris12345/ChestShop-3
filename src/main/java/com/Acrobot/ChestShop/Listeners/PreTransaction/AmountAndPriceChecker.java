package com.Acrobot.ChestShop.Listeners.PreTransaction;

import com.Acrobot.Breeze.Utils.InventoryUtil;
import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Economy.GiftcardsHelper;
import com.Acrobot.ChestShop.Events.Economy.CurrencyCheckEvent;
import com.Acrobot.ChestShop.Events.PreTransactionEvent;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import static com.Acrobot.ChestShop.Events.PreTransactionEvent.TransactionOutcome.*;
import static com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType.BUY;
import static com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType.SELL;

/**
 * @author Acrobot
 */
public class AmountAndPriceChecker implements Listener {

    @EventHandler(ignoreCancelled = true)
    public static void onBuyItemCheck(PreTransactionEvent event) {
        if (event.getTransactionType() != BUY) {
            return;
        }

        ItemStack[] stock = event.getStock();
        Inventory ownerInventory = event.getOwnerInventory();

        if (!hasEnoughMoney(event, true)) {
            event.setCancelled(CLIENT_DOES_NOT_HAVE_ENOUGH_MONEY);
            return;
        }

        if (!InventoryUtil.hasItems(stock, ownerInventory)) {
            event.setCancelled(NOT_ENOUGH_STOCK_IN_CHEST);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public static void onSellItemCheck(PreTransactionEvent event) {
        if (event.getTransactionType() != SELL) {
            return;
        }

        ItemStack[] stock = event.getStock();
        Inventory clientInventory = event.getClientInventory();

        if (!hasEnoughMoney(event, false)) {
            event.setCancelled(SHOP_DOES_NOT_HAVE_ENOUGH_MONEY);
            return;
        }

        if (!InventoryUtil.hasItems(stock, clientInventory)) {
            event.setCancelled(NOT_ENOUGH_STOCK_IN_INVENTORY);
        }
    }

    /**
     * Checks if the paying party can afford the transaction. For GC shops this
     * uses the giftcards balance; otherwise the regular (Vault) currency check.
     *
     * @param event The transaction
     * @param payerIsClient true if the paying party is the client (buy), false if it's the owner (sell)
     * @return true if the payer has enough
     */
    private static boolean hasEnoughMoney(PreTransactionEvent event, boolean payerIsClient) {
        if (ChestShopSign.getCurrency(event.getSign()) == ChestShopSign.Currency.GC) {
            double balance = payerIsClient
                    ? GiftcardsHelper.getBalance(event.getClient())
                    : GiftcardsHelper.getBalance(Bukkit.getOfflinePlayer(event.getOwnerAccount().getUuid()));
            return balance >= event.getExactPrice().doubleValue();
        }

        CurrencyCheckEvent currencyCheckEvent = payerIsClient
                ? new CurrencyCheckEvent(event.getExactPrice(), event.getClient())
                : new CurrencyCheckEvent(event.getExactPrice(), event.getOwnerAccount().getUuid(), event.getSign().getWorld());
        ChestShop.callEvent(currencyCheckEvent);
        return currencyCheckEvent.hasEnough();
    }
}
