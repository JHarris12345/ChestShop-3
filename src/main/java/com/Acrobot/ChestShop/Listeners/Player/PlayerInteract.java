package com.Acrobot.ChestShop.Listeners.Player;

import com.Acrobot.Breeze.Utils.*;
import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Commands.AccessToggle;
import com.Acrobot.ChestShop.Configuration.Messages;
import com.Acrobot.ChestShop.Configuration.Properties;
import com.Acrobot.ChestShop.Containers.AdminInventory;
import com.Acrobot.ChestShop.Database.Account;
import com.Acrobot.ChestShop.Economy.GiftcardsHelper;
import com.Acrobot.ChestShop.Events.AccountQueryEvent;
import com.Acrobot.ChestShop.Events.Economy.AccountCheckEvent;
import com.Acrobot.ChestShop.Events.ItemParseEvent;
import com.Acrobot.ChestShop.Events.PreTransactionEvent;
import com.Acrobot.ChestShop.Events.ShopInfoEvent;
import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.Acrobot.ChestShop.Inventories.AmountAnvilGUI;
import com.Acrobot.ChestShop.Permission;
import com.Acrobot.ChestShop.Security;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import com.Acrobot.ChestShop.Utils.ItemUtil;
import com.Acrobot.ChestShop.Utils.uBlock;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import static com.Acrobot.Breeze.Utils.ImplementationAdapter.getState;
import static com.Acrobot.Breeze.Utils.BlockUtil.isSign;
import static com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType;
import static com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType.BUY;
import static com.Acrobot.ChestShop.Events.TransactionEvent.TransactionType.SELL;
import static com.Acrobot.ChestShop.Permission.OTHER_NAME_CREATE;
import static com.Acrobot.ChestShop.Signs.ChestShopSign.*;
import static org.bukkit.event.block.Action.LEFT_CLICK_BLOCK;
import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

/**
 * Left-click performs the shop's transaction (one item); shift-left-click opens
 * the amount anvil to trade a custom amount; right-click shows the shop's
 * information in chat.
 *
 * @author Acrobot
 */
public class PlayerInteract implements Listener {

    // Spam-click hint tracking: nudge players who keep buying/selling one at a
    // time that they can shift-click to enter a custom amount.
    private static final Map<UUID, Long> LAST_TRADE_TIME = new HashMap<>();
    private static final Map<UUID, Integer> TRADE_STREAK = new HashMap<>();
    private static final Map<UUID, Long> LAST_HINT_TIME = new HashMap<>();
    private static final long STREAK_WINDOW_MS = 5000;
    private static final int STREAK_THRESHOLD = 4;
    private static final long HINT_COOLDOWN_MS = 120000;

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public static void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null)
            return;

        Action action = event.getAction();
        Player player = event.getPlayer();

        if (Properties.USE_BUILT_IN_PROTECTION && uBlock.couldBeShopContainer(block)) {
            Sign sign = uBlock.getConnectedSign(block);
            if (sign != null) {

                if (!Security.canView(player, block, Properties.TURN_OFF_DEFAULT_PROTECTION_WHEN_PROTECTED_EXTERNALLY)) {
                    if (Permission.has(player, Permission.SHOPINFO)) {
                        ChestShop.callEvent(new ShopInfoEvent(player, sign));
                        event.setCancelled(true);
                    } else if (!Properties.TURN_OFF_DEFAULT_PROTECTION_WHEN_PROTECTED_EXTERNALLY) {
                        Messages.ACCESS_DENIED.send(player);
                        event.setCancelled(true);
                    }
                }

                return;
            }
        }

        if (!isSign(block))
            return;

        Sign sign = (Sign) getState(block, false);
        if (!ChestShopSign.isValid(sign)) {
            return;
        }

        // Old-format shops must be converted by their owner before they can be
        // used - there is no automatic conversion.
        if (!ChestShopSign.isNewFormat(StringUtil.stripColourCodes(sign.getLines()))) {
            event.setCancelled(true);
            if (ChestShopSign.isOwner(player, sign)) {
                if (ChestShopSign.convertIfLegacy(sign)) {
                    player.sendMessage(ChatColor.GREEN + "Your shop has been updated to the new format.");
                } else {
                    player.sendMessage(ChatColor.RED + "This shop couldn't be updated automatically (its per-item price would round below $0.01). Please break and remake it.");
                }
            } else if (action == RIGHT_CLICK_BLOCK) {
                ChestShop.callEvent(new ShopInfoEvent(player, sign));
            } else {
                player.sendMessage(ChatColor.RED + "This shop still uses the old format and must be updated by its owner before you can use it.");
            }
            return;
        }

        if (Properties.ALLOW_AUTO_ITEM_FILL && ChatColor.stripColor(ChestShopSign.getItem(sign)).equals(AUTOFILL_CODE)) {
            if (ChestShopSign.hasPermission(player, OTHER_NAME_CREATE, sign)) {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (!MaterialUtil.isEmpty(item)) {
                    event.setCancelled(true);
                    String itemCode;
                    try {
                        itemCode = ItemUtil.getSignName(item);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ChatColor.RED + "Error while generating shop sign item name. Please contact an admin or take a look at the console/log!");
                        ChestShop.getPlugin().getLogger().log(Level.SEVERE, "Error while generating shop sign item name", e);
                        return;
                    }

                    if (StringUtil.getMinecraftStringWidth(itemCode) > MaterialUtil.MAXIMUM_SIGN_WIDTH) {
                        Messages.INVALID_SHOP_DETECTED.sendWithPrefix(player);
                        return;
                    }

                    sign.setLine(ITEM_LINE, ChestShopSign.LINE_COLOR + itemCode);
                    sign.update();
                } else {
                    Messages.NO_ITEM_IN_HAND.sendWithPrefix(player);
                }
            } else {
                Messages.ACCESS_DENIED.sendWithPrefix(player);
            }
            return;
        }

        if (!AccessToggle.isIgnoring(player) && ChestShopSign.canAccess(player, sign) && !ChestShopSign.isAdminShop(sign)) {
            if (Properties.IGNORE_ACCESS_PERMS || ChestShopSign.isOwner(player, sign)) {
                if (player.getInventory().getItemInMainHand().getType().name().contains("SIGN") && action == RIGHT_CLICK_BLOCK) {
                    // Allow editing of sign (if supported)
                    return;
                } else if ((player.getInventory().getItemInMainHand().getType().name().endsWith("DYE")
                        || player.getInventory().getItemInMainHand().getType().name().endsWith("INK_SAC"))
                        && action == RIGHT_CLICK_BLOCK) {
                    if (Properties.SIGN_DYING) {
                        return;
                    } else {
                        event.setCancelled(true);
                    }
                }
                if (Properties.ALLOW_SIGN_CHEST_OPEN && !(Properties.IGNORE_CREATIVE_MODE && player.getGameMode() == GameMode.CREATIVE)) {
                    if (player.isSneaking() || player.isInsideVehicle()
                            || (Properties.ALLOW_LEFT_CLICK_DESTROYING && action == LEFT_CLICK_BLOCK)) {
                        return;
                    }
                    event.setCancelled(true);
                    showChestGUI(player, block, sign);
                    return;
                }
                // don't allow owners or people with access to buy/sell at this shop
                Messages.TRADE_DENIED_ACCESS_PERMS.sendWithPrefix(player);
                if (action == RIGHT_CLICK_BLOCK) {
                    // don't allow editing
                    event.setCancelled(true);
                }
                return;
            }
        }

        // Right-click shows shop information in chat
        if (action == RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            ChestShop.callEvent(new ShopInfoEvent(player, sign));
            return;
        }

        // Anything that is not a left-click is not a trade
        if (action != LEFT_CLICK_BLOCK) {
            return;
        }

        if (!Properties.TURN_OFF_SIGN_PROTECTION && !ChestShopSign.canAccess(player, sign)) {
            event.setCancelled(true);
        }

        if (Properties.CHECK_ACCESS_FOR_SHOP_USE && !Security.canAccess(player, block, true)) {
            Messages.TRADE_DENIED.sendWithPrefix(player);
            return;
        }

        // Shift-left-click opens the amount anvil so the player can trade a custom amount
        if (player.isSneaking()) {
            event.setCancelled(true);
            final Block signBlock = block;
            Bukkit.getScheduler().runTask(ChestShop.getPlugin(),
                    () -> new AmountAnvilGUI(ChestShop.getPlugin(), player, signBlock).open());
            return;
        }

        // Plain left-click trades a single item
        event.setCancelled(true);
        if (executeTransaction(sign, player, 1)) {
            trackSingleTrade(player);
        }
    }

    /**
     * Run a buy/sell transaction at the given shop for the given amount of items.
     *
     * @param sign     The shop sign
     * @param player   The player trading
     * @param quantity The number of items to trade
     * @return true if the transaction was not cancelled
     */
    public static boolean executeTransaction(Sign sign, Player player, int quantity) {
        PreTransactionEvent pEvent = preparePreTransactionEvent(sign, player, quantity);
        if (pEvent == null)
            return false;

        Bukkit.getPluginManager().callEvent(pEvent);
        if (pEvent.isCancelled())
            return false;

        TransactionEvent tEvent = new TransactionEvent(pEvent, sign);
        Bukkit.getPluginManager().callEvent(tEvent);
        return true;
    }

    private static PreTransactionEvent preparePreTransactionEvent(Sign sign, Player player, int quantity) {
        String name = ChestShopSign.getOwner(sign);
        String prices = ChestShopSign.getPrice(sign);
        String material = ChestShopSign.getItem(sign);
        ChestShopSign.Currency currency = ChestShopSign.getCurrency(sign);

        AccountQueryEvent accountQueryEvent = new AccountQueryEvent(name);
        Bukkit.getPluginManager().callEvent(accountQueryEvent);
        Account account = accountQueryEvent.getAccount();
        if (account == null) {
            Messages.PLAYER_NOT_FOUND.sendWithPrefix(player);
            return null;
        }

        boolean adminShop = ChestShopSign.isAdminShop(sign);

        if (currency == ChestShopSign.Currency.GC) {
            if (!GiftcardsHelper.isAvailable()) {
                player.sendMessage(ChatColor.RED + "GC shops are currently unavailable.");
                return null;
            }
        } else if (!adminShop) {
            // check if the owner exists in the (Vault) economy
            AccountCheckEvent event = new AccountCheckEvent(account.getUuid(), player.getWorld());
            Bukkit.getPluginManager().callEvent(event);
            if (!event.hasAccount()) {
                Messages.NO_ECONOMY_ACCOUNT.sendWithPrefix(player);
                return null;
            }
        }

        boolean buy = ChestShopSign.getShopType(sign) != ChestShopSign.ShopType.SELL;
        BigDecimal unitPrice = (buy ? PriceUtil.getExactBuyPrice(prices) : PriceUtil.getExactSellPrice(prices));

        Container shopBlock = uBlock.findConnectedContainer(sign);
        Inventory ownerInventory = shopBlock != null ? shopBlock.getInventory() : null;

        ItemParseEvent parseEvent = new ItemParseEvent(material);
        Bukkit.getPluginManager().callEvent(parseEvent);
        ItemStack item = parseEvent.getItem();
        if (item == null) {
            Messages.INVALID_SHOP_DETECTED.sendWithPrefix(player);
            return null;
        }

        if (quantity < 1) {
            return null;
        }
        if (quantity > Properties.MAX_SHOP_AMOUNT) {
            player.sendMessage(ChatColor.RED + "You can trade at most " + Properties.MAX_SHOP_AMOUNT + " at a time.");
            return null;
        }

        BigDecimal price = unitPrice.equals(PriceUtil.NO_PRICE)
                ? PriceUtil.NO_PRICE
                : unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(Properties.PRICE_PRECISION, RoundingMode.HALF_UP);

        item.setAmount(quantity);

        ItemStack[] items = InventoryUtil.getItemsStacked(item);

        // Create virtual admin inventory if
        // - it's an admin shop
        // - there is no container for the shop sign
        // - the config doesn't force unlimited admin shop stock
        if (adminShop && (ownerInventory == null || Properties.FORCE_UNLIMITED_ADMIN_SHOP)) {
            ownerInventory = new AdminInventory(buy ? Arrays.stream(items).map(ItemStack::clone).toArray(ItemStack[]::new) : new ItemStack[0]);
        }

        TransactionType transactionType = (buy ? BUY : SELL);
        return new PreTransactionEvent(ownerInventory, player.getInventory(), items, price, player, account, sign, transactionType);
    }

    private static void trackSingleTrade(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        long last = LAST_TRADE_TIME.getOrDefault(id, 0L);
        int streak = (now - last <= STREAK_WINDOW_MS) ? TRADE_STREAK.getOrDefault(id, 0) + 1 : 1;
        TRADE_STREAK.put(id, streak);
        LAST_TRADE_TIME.put(id, now);

        if (streak >= STREAK_THRESHOLD && now - LAST_HINT_TIME.getOrDefault(id, 0L) >= HINT_COOLDOWN_MS) {
            LAST_HINT_TIME.put(id, now);
            TRADE_STREAK.put(id, 0);
            player.sendMessage(ChatColor.YELLOW + "Tip: " + ChatColor.GOLD + "shift-left-click" + ChatColor.YELLOW + " the shop to buy or sell a custom amount at once.");
        }
    }

    /**
     * @deprecated Use {@link ChestShopSign#hasPermission(Player, Permission, Sign)} with {@link Permission#OTHER_NAME_ACCESS}
     */
    @Deprecated
    public static boolean canOpenOtherShops(Player player) {
        return Permission.has(player, Permission.OTHER_NAME_ACCESS + ".*");
    }

    private static void showChestGUI(Player player, Block signBlock, Sign sign) {
        Container container = uBlock.findConnectedContainer(sign);

        if (container == null) {
            Messages.NO_CHEST_DETECTED.sendWithPrefix(player);
            return;
        }

        if (!Security.canAccess(player, signBlock)) {
            return;
        }

        if (!Security.canAccess(player, container.getBlock())) {
            return;
        }

        BlockUtil.openBlockGUI(container, player);
    }
}
