package com.Acrobot.ChestShop.Listeners.PreTransaction;

import com.Acrobot.ChestShop.Containers.AdminInventory;
import com.Acrobot.ChestShop.Events.ChestShopReloadEvent;
import com.Acrobot.ChestShop.Events.PreTransactionEvent;
import com.Acrobot.ChestShop.Plugins.ExploitFixerBookLimit;
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
 * Caps how many books a shop container may end up holding, using
 * ExploitFixer's own {@code limit-per-storage} value rather than a number of
 * our own, so the two cannot drift. See {@link ExploitFixerBookLimit}.
 *
 * <p>Books are counted the way the limit means it: written books and
 * books-and-quills, and the books held inside a shulker box count as well,
 * both for what is already in the container and for what is being traded in.
 * Counting nested books is the point of the check, because a shulker full of
 * high-data books is the payload that makes the receiving client struggle.</p>
 *
 * <p>What this does not do: it caps books per shop container, nothing more. It
 * does not stop deep container nesting on its own (that is
 * {@link NestedShulkerChecker} and
 * {@link com.Acrobot.ChestShop.Listeners.PreShopCreation.ShulkerShopChecker}),
 * it does not touch books already sitting in a container, and it does not look
 * at the books a client takes out of a shop into their own inventory, since
 * ExploitFixer's limit is about storage containers.</p>
 *
 * @author Claude
 */
public class BookLimitChecker implements Listener {

    /**
     * Runs at HIGH so {@link PartialTransactionModule} (LOW) has already
     * settled how many items are actually changing hands.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public static void onPreTransaction(PreTransactionEvent event) {
        if (event.isCancelled() || event.getTransactionType() != SELL) {
            return;
        }

        int limit = ExploitFixerBookLimit.getLimitPerStorage();
        if (limit <= ExploitFixerBookLimit.UNLIMITED) {
            return;
        }

        Inventory destination = event.getOwnerInventory();
        if (destination == null || destination instanceof AdminInventory) {
            // An admin shop's stock is virtual, so there is no container to cap.
            return;
        }

        int incoming = 0;
        for (ItemStack item : event.getStock()) {
            incoming += ContainerItemUtil.countBooks(item);
        }

        if (incoming < 1) {
            return;
        }

        int alreadyInside = ContainerItemUtil.countBooks(destination);
        if (alreadyInside + incoming <= limit) {
            return;
        }

        event.setCancelled(OTHER);
        event.getClient().sendMessage(Utils.colour("&c[Shop] &fThis shop's container can only hold "
                + limit + " book" + (limit == 1 ? "" : "s") + " and already holds " + alreadyInside
                + ", so this trade would go over the limit"));
    }

    /**
     * Re-read ExploitFixer's limit on a ChestShop reload. Between reloads the
     * value is cached and only refreshed once its time to live has passed, so
     * at worst one transaction every few minutes reads the small module file
     * rather than every transaction reading it.
     */
    @EventHandler
    public static void onReload(ChestShopReloadEvent event) {
        ExploitFixerBookLimit.invalidate();
    }
}
