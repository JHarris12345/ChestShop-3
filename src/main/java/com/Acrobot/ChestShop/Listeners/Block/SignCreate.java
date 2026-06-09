package com.Acrobot.ChestShop.Listeners.Block;

import com.Acrobot.Breeze.Utils.BlockUtil;
import com.Acrobot.Breeze.Utils.StringUtil;
import com.Acrobot.ChestShop.ChestShop;
import com.Acrobot.ChestShop.Configuration.Messages;
import com.Acrobot.ChestShop.Events.PreShopCreationEvent;
import com.Acrobot.ChestShop.Events.ShopCreatedEvent;
import com.Acrobot.ChestShop.Listeners.Block.Break.SignBreak;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import com.Acrobot.ChestShop.Utils.uBlock;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * @author Acrobot
 */
public class SignCreate implements Listener {

    private static boolean HAS_SIGN_SIDES;

    static {
        try {
            SignChangeEvent.class.getMethod("getSide");
            HAS_SIGN_SIDES = true;
        } catch (NoSuchMethodException e) {
            HAS_SIGN_SIDES = false;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public static void onSignChange(SignChangeEvent event) {
        Block signBlock = event.getBlock();

        if (!BlockUtil.isSign(signBlock)) {
            return;
        }

        Sign sign = (Sign) signBlock.getState();

        if (HAS_SIGN_SIDES && event.getSide() != Side.FRONT) {
            if (ChestShopSign.isValid(sign)) {
                event.setCancelled(true);
                Messages.CANNOT_CHANGE_SIGN_BACKSIDE.sendWithPrefix(event.getPlayer());
            }
            return;
        }

        String[] typed = StringUtil.stripColourCodes(event.getLines());

        // The first line decides the shop direction. Anything else is not a shop creation attempt.
        ChestShopSign.ShopType type = parseType(typed[ChestShopSign.TYPE_LINE]);
        if (type == null) {
            // The player may have edited a previously valid shop into something else
            if (ChestShopSign.isValid(sign)) {
                SignBreak.sendShopDestroyedEvent(sign, event.getPlayer());
            }
            return;
        }

        // Translate the typed input into the rendered layout. The player types:
        //   line 1: buy/sell      line 2: item      line 3: price      line 4: currency ($/gc)
        // The owner line is filled in by NameChecker, the item is rendered as "1x <item>"
        // and the price + currency are combined onto the price line.
        String itemInput = orEmpty(StringUtil.strip(typed[1]));
        String priceInput = orEmpty(StringUtil.strip(typed[2]));
        String currencyInput = orEmpty(StringUtil.strip(typed[3]));
        if (itemInput.isEmpty()) {
            itemInput = ChestShopSign.AUTOFILL_CODE;
        }

        // Mark the price line with a GC suffix so the rest of the pipeline knows the currency.
        String priceLine = priceInput;
        String currencyLower = currencyInput.toLowerCase(Locale.ROOT);
        if (currencyLower.equals("gc") || currencyLower.startsWith("giftcard") || currencyLower.startsWith("giftcards")) {
            priceLine = priceInput + " " + ChestShopSign.GC_SUFFIX;
        }

        String[] lines = new String[]{
                ChestShopSign.getLabel(type),
                "",
                ChestShopSign.LINE_COLOR + itemInput,
                priceLine
        };

        PreShopCreationEvent preEvent = new PreShopCreationEvent(event.getPlayer(), sign, lines);
        ChestShop.callEvent(preEvent);

        if (preEvent.getOutcome().shouldBreakSign()) {
            event.setCancelled(true);

            if (event.getPlayer().getGameMode() == GameMode.SURVIVAL) {
                String material = sign.getType().toString().replace("WALL_", "");
                event.getPlayer().getInventory().addItem(new ItemStack(Material.valueOf(material)));
            }

            signBlock.setType(Material.AIR);

            ChestShop.logDebug("Shop sign creation at " + sign.getLocation() + " by " + event.getPlayer().getName() + " was cancelled (creation outcome: " + preEvent.getOutcome() + ") and the sign broken");
            return;
        }

        for (byte i = 0; i < preEvent.getSignLines().length && i < 4; ++i) {
            event.setLine(i, preEvent.getSignLine(i));
        }

        if (preEvent.isCancelled()) {
            ChestShop.logDebug("Shop sign creation at " + sign.getLocation() + " by " + event.getPlayer().getName() + " was cancelled (creation outcome: " + preEvent.getOutcome() + ") and sign lines were set to " + String.join(", ", preEvent.getSignLines()));
            return;
        }

        ShopCreatedEvent postEvent = new ShopCreatedEvent(preEvent.getPlayer(), preEvent.getSign(), uBlock.findConnectedContainer(preEvent.getSign()), preEvent.getSignLines(), preEvent.getOwnerAccount());
        ChestShop.callEvent(postEvent);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static ChestShopSign.ShopType parseType(String line) {
        if (line == null) {
            return null;
        }
        String typeName = StringUtil.strip(line).toLowerCase(Locale.ROOT);
        if (typeName.equals("buy")) {
            return ChestShopSign.ShopType.BUY;
        }
        if (typeName.equals("sell")) {
            return ChestShopSign.ShopType.SELL;
        }
        return null;
    }
}
