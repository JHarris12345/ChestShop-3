package com.Acrobot.ChestShop.Signs;

import com.Acrobot.Breeze.Utils.PriceUtil;
import com.Acrobot.Breeze.Utils.QuantityUtil;
import com.Acrobot.Breeze.Utils.StringUtil;
import com.Acrobot.ChestShop.Configuration.Properties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and converts shop signs that use the <b>old</b> ChestShop layout:
 * <pre>
 *     line 0: owner name
 *     line 1: quantity (optionally with a stock counter)
 *     line 2: price line (e.g. "B 100 : S 80")
 *     line 3: item
 * </pre>
 *
 * This class exists purely so that shops created before the format change keep
 * working and can be migrated to the new layout (see {@link ChestShopSign}).
 */
public class LegacyChestShopSign {
    public static final byte NAME_LINE = 0;
    public static final byte QUANTITY_LINE = 1;
    public static final byte PRICE_LINE = 2;
    public static final byte ITEM_LINE = 3;

    public static final Pattern[][] SHOP_SIGN_PATTERN = {
            { Pattern.compile("^[1-9][0-9]{0,5}$"), QuantityUtil.QUANTITY_LINE_WITH_COUNTER_PATTERN },
            {
                Pattern.compile("(?i)^((\\d*([.e]\\d+)?)|free)$"),
                Pattern.compile("(?i)^([BS] *((\\d*([.e]\\d+)?)|free))( *: *([BS] *((\\d*([.e]\\d+)?)|free)))?$"),
                Pattern.compile("(?i)^(((\\d*([.e]\\d+)?)|free) *[BS])( *: *([BS] *((\\d*([.e]\\d+)?)|free)))?$"),
                Pattern.compile("(?i)^(((\\d*([.e]\\d+)?)|free) *[BS]) *: *(((\\d*([.e]\\d+)?)|free) *[BS])$"),
                Pattern.compile("(?i)^([BS] *((\\d*([.e]\\d+)?)|free)) *: *(((\\d*([.e]\\d+)?)|free) *[BS])$"),
            },
            { Pattern.compile("^[\\p{L}\\d_? #:\\-]+$") }
    };

    public static boolean isValid(String[] lines) {
        lines = StringUtil.stripColourCodes(lines);
        return isValidPreparedSign(lines)
                && (getPrice(lines).toUpperCase(Locale.ROOT).contains("B")
                        || getPrice(lines).toUpperCase(Locale.ROOT).contains("S"))
                && !getOwner(lines).isEmpty();
    }

    public static boolean isValidPreparedSign(String[] lines) {
        String playername = getOwner(lines);

        // If the shop owner is not blank (auto-filled) or the admin shop string, we need to validate it
        if ((!ChestShopSign.isAdminShop(playername)) && (playername.length() > 0)) {
            Pattern playernamePattern = Pattern.compile(Properties.VALID_PLAYERNAME_REGEXP);
            Matcher playernameWithIdMatcher = Pattern.compile("^(.+):[A-Za-z0-9]+$").matcher(playername);
            if (playernameWithIdMatcher.matches()) {
                playername = playernameWithIdMatcher.group(1);
            }

            if (!playernamePattern.matcher(playername).matches()) {
                return false;
            }
        }

        for (int i = 0; i < 3; i++) {
            boolean matches = false;
            for (Pattern pattern : SHOP_SIGN_PATTERN[i]) {
                if (pattern.matcher(StringUtil.strip(StringUtil.stripColourCodes(lines[i + 1]))).matches()) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return false;
            }
        }

        String priceLine = getPrice(lines);
        return priceLine.indexOf(':') == priceLine.lastIndexOf(':');
    }

    public static String getOwner(String[] lines) {
        return StringUtil.stripColourCodes(StringUtil.strip(StringUtil.stripColourCodes(lines[NAME_LINE])));
    }

    public static String getQuantityLine(String[] lines) {
        return lines.length > QUANTITY_LINE ? StringUtil.strip(StringUtil.stripColourCodes(lines[QUANTITY_LINE])) : "";
    }

    public static int getQuantity(String[] lines) throws IllegalArgumentException {
        return QuantityUtil.parseQuantity(getQuantityLine(lines));
    }

    public static String getPrice(String[] lines) {
        return lines.length > PRICE_LINE ? StringUtil.strip(StringUtil.stripColourCodes(lines[PRICE_LINE])) : "";
    }

    public static String getItem(String[] lines) {
        return lines.length > ITEM_LINE ? StringUtil.strip(StringUtil.stripColourCodes(lines[ITEM_LINE])) : "";
    }

    /**
     * Convert an old-format shop sign into the new layout.
     *
     * Dual buy+sell signs are converted into a single <b>buy</b> shop (the sell
     * price is dropped), as decided for the migration.
     *
     * @param lines The old (colour-stripped) sign lines
     * @return The new sign lines, or null if the sign could not be converted
     */
    public static String[] convert(String[] lines) {
        if (!isValid(lines)) {
            return null;
        }

        String owner = getOwner(lines);
        String item = getItem(lines);

        int amount;
        try {
            amount = getQuantity(lines);
        } catch (IllegalArgumentException invalidQuantity) {
            return null;
        }

        String priceLine = getPrice(lines);

        ChestShopSign.ShopType type;
        BigDecimal price;
        if (PriceUtil.hasBuyPrice(priceLine)) {
            type = ChestShopSign.ShopType.BUY;
            price = PriceUtil.getExactBuyPrice(priceLine);
        } else if (PriceUtil.hasSellPrice(priceLine)) {
            type = ChestShopSign.ShopType.SELL;
            price = PriceUtil.getExactSellPrice(priceLine);
        } else {
            return null;
        }

        if (price.compareTo(BigDecimal.ZERO) < 0) {
            price = BigDecimal.ZERO;
        }

        // New shops always trade one item at a time, so convert the old
        // "price for <amount> items" into a per-item price. Old shops were
        // always money based.
        if (amount > 1) {
            price = price.divide(BigDecimal.valueOf(amount), Math.max(Properties.PRICE_PRECISION, 0), RoundingMode.HALF_UP);
        }

        return ChestShopSign.buildSignLines(type, owner, item, price, ChestShopSign.Currency.MONEY);
    }
}
