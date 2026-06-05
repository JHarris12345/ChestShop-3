package com.Acrobot.ChestShop.Listeners.PostTransaction;

import com.Acrobot.Breeze.Utils.LocationUtil;
import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Configuration.Properties;
import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.Acrobot.ChestShop.Logging.ShopLogFile;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.Locale;

import static com.Acrobot.Breeze.Utils.InventoryUtil.mergeSimilarStacks;
import static com.Acrobot.ChestShop.Utils.ItemUtil.getName;
import static com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType.BUY;

/**
 * @author Acrobot
 */
public class TransactionLogger implements Listener {
    // Price is followed by the currency token ("$" or "GC") so stats can separate currencies.
    private static final String BUY_MESSAGE = "%1$s bought %2$s for %3$s %4$s from %5$s at %6$s";
    private static final String SELL_MESSAGE = "%1$s sold %2$s for %3$s %4$s to %5$s at %6$s";
    private static final DecimalFormat df = new DecimalFormat("#,###.##");

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public static void onTransaction(final TransactionEvent event) {
        String template = (event.getTransactionType() == BUY ? BUY_MESSAGE : SELL_MESSAGE);

        StringBuilder items = new StringBuilder(50);

        for (ItemStack item : mergeSimilarStacks(event.getStock())) {
            items.append(item.getAmount()).append(' ').append(getName(item));
        }

        ChestShopSign.Currency currency = ChestShopSign.getCurrency(event.getSign());
        String currencyToken = currency == ChestShopSign.Currency.GC ? ChestShopSign.GC_SUFFIX : "$";

        String message = String.format(Locale.US, template,
                event.getClient().getName(),
                items.toString(),
                String.format(Locale.US, "%.2f", event.getExactPrice()),
                currencyToken,
                event.getOwnerAccount().getName(),
                LocationUtil.locationToString(event.getSign().getLocation()));

        // GC trades are never taxed
        double tax = Properties.TAX_AMOUNT;
        if (currency != ChestShopSign.Currency.GC && tax > 0) {
            double finalPrice = (event.getExactPrice().doubleValue() * (1-(tax/100)));
            message += " (" + df.format(finalPrice) + " after tax)";
        }

        ChestShop.getShopLogger().info(message);
        ShopLogFile.log(message);
    }
}
