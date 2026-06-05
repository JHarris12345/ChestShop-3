package com.Acrobot.ChestShop.Listeners.Economy;

import com.Acrobot.ChestShop.Economy.GiftcardsHelper;
import com.Acrobot.ChestShop.Events.Economy.CurrencyTransferEvent;
import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.math.BigDecimal;

import static com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType.BUY;

/**
 * Routes the money side of GC ("giftcards") shops through {@link GiftcardsHelper}
 * instead of Vault.
 *
 * It runs before {@link TaxModule} (LOW) and the Vault listener (NORMAL) and
 * marks the transfer as handled, so neither applies. GC trades are never taxed.
 *
 * @author Acrobot
 */
public class GiftcardsListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public static void onCurrencyTransfer(CurrencyTransferEvent event) {
        TransactionEvent transactionEvent = event.getTransactionEvent();
        if (event.wasHandled() || transactionEvent == null || transactionEvent.isCancelled()) {
            return;
        }

        if (ChestShopSign.getCurrency(transactionEvent.getSign()) != ChestShopSign.Currency.GC) {
            return;
        }

        BigDecimal amount = event.getAmountSent();

        String clientName = transactionEvent.getClient().getName();
        String ownerName = transactionEvent.getOwnerAccount().getName();

        if (transactionEvent.getTransactionType() == BUY) {
            // Customer buys from the shop: customer pays, owner receives
            GiftcardsHelper.take(clientName, amount.doubleValue());
            GiftcardsHelper.give(ownerName, amount.doubleValue());
        } else {
            // Customer sells to the shop: owner pays, customer receives
            GiftcardsHelper.take(ownerName, amount.doubleValue());
            GiftcardsHelper.give(clientName, amount.doubleValue());
        }

        event.setHandled(true);
    }
}
