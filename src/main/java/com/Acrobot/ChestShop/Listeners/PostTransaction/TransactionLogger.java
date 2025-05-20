package com.Acrobot.ChestShop.Listeners.PostTransaction;

import com.Acrobot.Breeze.Utils.LocationUtil;
import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Configuration.Properties;
import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.Acrobot.ChestShop.Logging.ShopLogFile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.text.DecimalFormat;

import static com.Acrobot.Breeze.Utils.InventoryUtil.mergeSimilarStacks;
import static com.Acrobot.ChestShop.Utils.ItemUtil.getName;
import static com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType.BUY;

/**
 * @author Acrobot
 */
public class TransactionLogger implements Listener {
    private static final String BUY_MESSAGE = "%1$s bought %2$s for %3$.2f from %4$s at %5$s";
    private static final String SELL_MESSAGE = "%1$s sold %2$s for %3$.2f to %4$s at %5$s";
    private static final DecimalFormat df = new DecimalFormat("#,###.##");

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public static void onTransaction(final TransactionEvent event) {
        String template = (event.getTransactionType() == BUY ? BUY_MESSAGE : SELL_MESSAGE);

        StringBuilder items = new StringBuilder(50);

        for (ItemStack item : mergeSimilarStacks(event.getStock())) {
            items.append(item.getAmount()).append(' ').append(getName(item));
        }

        String message = String.format(template,
                event.getClient().getName(),
                items.toString(),
                event.getExactPrice(),
                event.getOwnerAccount().getName(),
                LocationUtil.locationToString(event.getSign().getLocation()));

        double tax = Properties.TAX_AMOUNT;
        if (tax > 0) {
            double finalPrice = (event.getExactPrice().doubleValue() * (1-(tax/100)));
            message += " (" + df.format(finalPrice) + " after tax)";
        }

        ChestShop.getShopLogger().info(message);
        ShopLogFile.log(message);
    }
}
