package com.Acrobot.ChestShop.Listeners.PreTransaction;

import com.Acrobot.ChestShop.Events.PreTransactionEvent;
import com.Acrobot.ChestShop.Utils.ContainerItemUtil;
import com.Acrobot.ChestShop.Utils.Utils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import static com.Acrobot.ChestShop.Events.PreTransactionEvent.TransactionOutcome.OTHER;
import static com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType.SELL;

/**
 * Refuses a trade that would put a shulker box inside a shulker box.
 *
 * <p>{@link com.Acrobot.ChestShop.Listeners.PreShopCreation.ShulkerShopChecker}
 * stops such a shop being made, but it cannot do anything about the shops that
 * already exist, so the same combination is refused here at trade time as
 * well. These are the only two checks; there is no material blacklist in this
 * plugin.</p>
 *
 * <p>Only the direction that fills the container is touched. A client buying a
 * shulker box <i>out</i> of a shulker box shop is a BUY transaction and is
 * left completely alone, as is selling anything that is not a shulker box into
 * a shulker box shop, and selling a shulker box into a chest or barrel
 * shop.</p>
 *
 * @author Claude
 */
public class NestedShulkerChecker implements Listener {

    public static final String DENIAL_MESSAGE = "&c[Shop] &fYou can't sell a shulker box into this shop,"
            + " because the shop container is a shulker box and a shulker box can't hold another one";

    /**
     * Runs at NORMAL so it is after {@link PartialTransactionModule} (LOW) has
     * finished adjusting the stock.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public static void onPreTransaction(PreTransactionEvent event) {
        if (event.isCancelled() || event.getTransactionType() != SELL) {
            return;
        }

        Inventory destination = event.getOwnerInventory();
        if (!ContainerItemUtil.isShulkerBoxInventory(destination)) {
            return;
        }

        for (ItemStack item : event.getStock()) {
            if (ContainerItemUtil.isShulkerBox(item)) {
                event.setCancelled(OTHER);
                event.getClient().sendMessage(Utils.colour(DENIAL_MESSAGE));
                return;
            }
        }
    }
}
