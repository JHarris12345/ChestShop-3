package com.Acrobot.ChestShop.Listeners.PreShopCreation;

import com.Acrobot.Breeze.Utils.PriceUtil;
import com.Acrobot.ChestShop.Configuration.Properties;
import com.Acrobot.ChestShop.Events.PreShopCreationEvent;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.Acrobot.ChestShop.Events.PreShopCreationEvent.CreationOutcome.INVALID_PRICE;
import static com.Acrobot.ChestShop.Signs.ChestShopSign.PRICE_LINE;

/**
 * Validates and normalises the price line into the "$1,234.56" display format.
 *
 * The player may type the price in a number of ways - with or without a
 * currency symbol and with thousands separators (e.g. "$5,400.34" or
 * "5400.34"); everything but the digits and the decimal point is ignored.
 *
 * @author Acrobot
 */
public class PriceChecker implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public static void onPreShopCreation(PreShopCreationEvent event) {
        BigDecimal price = ChestShopSign.parseMoney(event.getSignLine(PRICE_LINE));

        if (price.compareTo(PriceUtil.NO_PRICE) == 0) {
            event.setOutcome(INVALID_PRICE);
            return;
        }

        int scale = Math.min(Math.max(Properties.PRICE_PRECISION, 0), 2);
        price = price.setScale(scale, RoundingMode.HALF_UP);

        event.setSignLine(PRICE_LINE, ChestShopSign.LINE_COLOR + ChestShopSign.formatPrice(price));
    }
}
