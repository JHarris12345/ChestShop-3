package com.Acrobot.ChestShop.Listeners.PreShopCreation;

import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Events.ItemParseEvent;
import com.Acrobot.ChestShop.Events.PreShopCreationEvent;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import com.Acrobot.ChestShop.Utils.ContainerItemUtil;
import com.Acrobot.ChestShop.Utils.Utils;
import com.Acrobot.ChestShop.Utils.uBlock;
import org.bukkit.Bukkit;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import static com.Acrobot.ChestShop.Events.PreShopCreationEvent.CreationOutcome.OTHER_BREAK;

/**
 * Refuses the one shop layout that can produce a shulker box inside a shulker
 * box: a shop whose container is itself a shulker box and which takes shulker
 * boxes <i>in</i> (a "sell" shop, where the client hands the item over).
 *
 * <p>Everything else about shulker shops keeps working. A shulker box shop
 * that sells shulker boxes out is fine (the item leaves the container), and a
 * chest or barrel shop that takes shulker boxes in is fine (a chest is allowed
 * to hold them).</p>
 *
 * <p>Already existing shops are not covered by this check, so the same
 * combination is refused again at trade time by
 * {@link com.Acrobot.ChestShop.Listeners.PreTransaction.NestedShulkerChecker}.</p>
 *
 * @author Claude
 */
public class ShulkerShopChecker implements Listener {

    public static final String DENIAL_MESSAGE = "&c[Shop] &fYou can't make a shulker box shop that takes shulker boxes in,"
            + " because that would put a shulker box inside a shulker box. Use another container for the shop,"
            + " or make it a &abuy &fshop so the shulker boxes leave the container instead";

    /**
     * Runs after {@link ItemChecker} (LOWEST) has normalised the item line, and
     * before the sign is written.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public static void onPreShopCreation(PreShopCreationEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!ChestShopSign.takesItemsIn(event.getSignLines())) {
            return;
        }

        Container container = uBlock.findConnectedContainer(event.getSign().getBlock());
        if (container == null || !ContainerItemUtil.isShulkerBox(container.getBlock().getType())) {
            return;
        }

        if (!sellsShulkerBox(ChestShopSign.getItem(event.getSignLines()))) {
            return;
        }

        event.setOutcome(OTHER_BREAK);
        denied(event.getPlayer());
    }

    /**
     * Sends the denial message. Deliberately a hard coded string rather than a
     * {@link com.Acrobot.ChestShop.Configuration.Messages} key: the language
     * files already on the live servers predate this check, and a missing key
     * resolves to nothing useful, so a new key would show the player an empty
     * or broken line. This matches how the rest of this fork's own messages
     * are sent (see {@code PlayerInteract}).
     *
     * @param player The player to tell
     */
    public static void denied(Player player) {
        if (player != null) {
            player.sendMessage(Utils.colour(DENIAL_MESSAGE));
        }
    }

    /**
     * Resolves a sign item code and reports whether it is a shulker box.
     *
     * @param itemCode The item line of the shop sign
     * @return Whether the shop's item is a shulker box
     */
    public static boolean sellsShulkerBox(String itemCode) {
        if (itemCode == null || itemCode.isEmpty()) {
            return false;
        }

        ItemParseEvent parseEvent = new ItemParseEvent(itemCode);
        Bukkit.getPluginManager().callEvent(parseEvent);
        ItemStack item = parseEvent.getItem();

        if (item == null) {
            // An unparseable item line is INVALID_ITEM's problem, not ours.
            ChestShop.logDebug("Could not parse '" + itemCode + "' while checking a shop for nested shulker boxes");
            return false;
        }

        return ContainerItemUtil.isShulkerBox(item);
    }
}
