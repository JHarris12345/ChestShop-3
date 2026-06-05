package com.Acrobot.ChestShop.Signs;

import com.Acrobot.Breeze.Utils.BlockUtil;
import com.Acrobot.Breeze.Utils.ImplementationAdapter;
import com.Acrobot.Breeze.Utils.PriceUtil;
import com.Acrobot.Breeze.Utils.StringUtil;
import com.Acrobot.ChestShop.Configuration.Properties;
import com.Acrobot.ChestShop.Containers.AdminInventory;
import com.Acrobot.ChestShop.Database.Account;
import com.Acrobot.ChestShop.Events.AccountQueryEvent;
import com.Acrobot.ChestShop.Permission;
import com.Acrobot.ChestShop.UUIDs.NameManager;
import com.Acrobot.ChestShop.Utils.uBlock;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.Acrobot.Breeze.Utils.ImplementationAdapter.getState;

/**
 * Reads shop signs that use the current layout:
 * <pre>
 *     line 0: shop type label ("[Click to buy]" / "[Click to sell]")
 *     line 1: owner name
 *     line 2: "&lt;amount&gt;x &lt;item&gt;"
 *     line 3: price ("$1,234.56")
 * </pre>
 *
 * Signs created with the old layout are still understood (and lazily migrated)
 * through {@link LegacyChestShopSign}.
 *
 * @author Acrobot
 */
public class ChestShopSign {
    public static final byte TYPE_LINE = 0;
    public static final byte NAME_LINE = 1;
    public static final byte QUANTITY_LINE = 2;
    // The item now shares the quantity line ("<amount>x <item>"); kept for compatibility.
    public static final byte PRICE_LINE = 3;
    public static final byte ITEM_LINE = QUANTITY_LINE;

    public static final String AUTOFILL_CODE = "?";

    public static final String BUY_LABEL_TEXT = "[Click to buy]";
    public static final String SELL_LABEL_TEXT = "[Click to sell]";

    /** Colour applied to the type label (&a&l). */
    public static final String LABEL_COLOR = ChatColor.GREEN.toString() + ChatColor.BOLD;
    /** Colour applied to the owner and item lines (&f). */
    public static final String LINE_COLOR = ChatColor.WHITE.toString();
    /** Colour applied to the price line (&a). */
    public static final String PRICE_COLOR = ChatColor.GREEN.toString();

    public static final String BUY_LABEL = LABEL_COLOR + BUY_LABEL_TEXT;
    public static final String SELL_LABEL = LABEL_COLOR + SELL_LABEL_TEXT;

    /** Suffix used to mark a giftcards (GC) price on a sign. */
    public static final String GC_SUFFIX = "GC";

    private static final Pattern MERGED_ITEM_PATTERN = Pattern.compile("(?i)^\\s*([0-9]+)\\s*x\\s*(.*)$");

    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.US));

    /**
     * The currency a shop trades in.
     */
    public enum Currency {
        /** The server economy (Vault), displayed as "$1,234.56". */
        MONEY,
        /** Giftcards (InsanityCore), displayed as "1,234.56 GC". */
        GC
    }

    /**
     * Direction of a shop. A single sign can only be one of these.
     */
    public enum ShopType {
        /** Customers buy items from the shop ("[Click to buy]"). */
        BUY,
        /** Customers sell items to the shop ("[Click to sell]"). */
        SELL
    }

    public static boolean isAdminShop(Inventory ownerInventory) {
        return ownerInventory instanceof AdminInventory;
    }

    public static boolean isAdminShop(String owner) {
        return owner.replace(" ", "").equalsIgnoreCase(Properties.ADMIN_SHOP_NAME.replace(" ", ""));
    }

    public static boolean isAdminShop(Sign sign) {
        return isAdminShop(sign.getLines());
    }

    public static boolean isAdminShop(String[] lines) {
        return isAdminShop(getOwner(lines));
    }

    public static boolean isValid(Sign sign) {
        return isValid(sign.getLines());
    }

    public static boolean isValid(String[] lines) {
        String[] stripped = StringUtil.stripColourCodes(lines);
        if (isNewFormat(stripped)) {
            return isValidNewFormat(stripped);
        }
        return LegacyChestShopSign.isValid(stripped);
    }

    public static boolean isValid(Block sign) {
        return BlockUtil.isSign(sign) && isValid((Sign) getState(sign, false));
    }

    /**
     * @return true if the (colour-stripped) lines use the new shop layout
     */
    public static boolean isNewFormat(String[] lines) {
        if (lines.length <= TYPE_LINE || lines[TYPE_LINE] == null) {
            return false;
        }
        String label = StringUtil.strip(StringUtil.stripColourCodes(lines[TYPE_LINE]));
        return label.equalsIgnoreCase(BUY_LABEL_TEXT) || label.equalsIgnoreCase(SELL_LABEL_TEXT);
    }

    private static boolean isValidNewFormat(String[] lines) {
        if (getOwner(lines).isEmpty()) {
            return false;
        }

        try {
            if (getQuantity(lines) < 1) {
                return false;
            }
        } catch (NumberFormatException invalidQuantity) {
            return false;
        }

        if (getItem(lines).isEmpty()) {
            return false;
        }

        BigDecimal price = getExactPrice(lines);
        return price.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * @deprecated Use {@link #isShopBlock(Block}
     */
    @Deprecated
    public static boolean isShopChest(Block chest) {
        if (!BlockUtil.isChest(chest)) {
            return false;
        }

        return uBlock.getConnectedSign(chest) != null;
    }

    public static boolean isShopBlock(Block block) {
        if (!uBlock.couldBeShopContainer(block)) {
            return false;
        }

        return uBlock.getConnectedSign(block) != null;
    }

    /**
     * @deprecated Use {@link #isShopBlock(InventoryHolder}
     */
    @Deprecated
    public static boolean isShopChest(InventoryHolder holder) {
        if (!BlockUtil.isChest(holder)) {
            return false;
        }

        if (holder instanceof DoubleChest) {
            return isShopChest(((DoubleChest) holder).getLocation().getBlock());
        } else if (holder instanceof Chest) {
            return isShopChest(((Chest) holder).getBlock());
        } else {
            return false;
        }
    }

    public static boolean isShopBlock(InventoryHolder holder) {
        if (holder instanceof DoubleChest) {
            return isShopBlock(ImplementationAdapter.getLeftSide((DoubleChest) holder, false))
                    || isShopBlock(ImplementationAdapter.getRightSide((DoubleChest) holder, false));
        } else if (holder instanceof BlockState) {
            return isShopBlock(((BlockState) holder).getBlock());
        }
        return false;
    }

    public static Block getShopBlock(InventoryHolder holder) {
        if (holder instanceof DoubleChest) {
            return Optional.ofNullable(getShopBlock(ImplementationAdapter.getLeftSide((DoubleChest) holder, false)))
                    .orElse(getShopBlock(ImplementationAdapter.getRightSide((DoubleChest) holder, false)));
        } else if (holder instanceof BlockState) {
            return ((BlockState) holder).getBlock();
        }
        return null;
    }

    public static boolean canAccess(Player player, Sign sign) {
        return hasPermission(player, Permission.OTHER_NAME_ACCESS, sign);
    }

    public static boolean hasPermission(Player player, Permission base, Sign sign) {
        if (player == null) return false;
        if (sign == null) return true;

        String name = getOwner(sign);
        if (name == null || name.isEmpty()) return true;

        return NameManager.canUseName(player, base, name);
    }

    public static boolean isOwner(Player player, Sign sign) {
        if (player == null || sign == null) return false;

        String name = getOwner(sign);
        if (name == null || name.isEmpty()) return false;

        AccountQueryEvent accountQueryEvent = new AccountQueryEvent(name);
        Bukkit.getPluginManager().callEvent(accountQueryEvent);
        Account account = accountQueryEvent.getAccount();
        if (account == null) {
            return player.getName().equalsIgnoreCase(name);
        }
        return account.getUuid().equals(player.getUniqueId());
    }

    /**
     * Get the owner string of a shop sign
     * @param sign The sign
     * @return The owner string
     */
    public static String getOwner(Sign sign) {
        return getOwner(sign.getLines());
    }

    /**
     * Get the owner string of a shop sign
     * @param lines The sign lines
     * @return The owner string
     */
    public static String getOwner(String[] lines) {
        if (!isNewFormat(lines)) {
            return LegacyChestShopSign.getOwner(lines);
        }
        return StringUtil.stripColourCodes(StringUtil.strip(StringUtil.stripColourCodes(lines[NAME_LINE])));
    }

    /**
     * Get the type (direction) of a shop sign
     * @param sign The sign
     * @return The shop type, or null if it cannot be determined
     */
    public static ShopType getShopType(Sign sign) {
        return getShopType(sign.getLines());
    }

    /**
     * Get the type (direction) of a shop sign
     * @param lines The sign lines
     * @return The shop type, or null if it cannot be determined
     */
    public static ShopType getShopType(String[] lines) {
        if (isNewFormat(lines)) {
            String label = StringUtil.strip(StringUtil.stripColourCodes(lines[TYPE_LINE]));
            return label.equalsIgnoreCase(BUY_LABEL_TEXT) ? ShopType.BUY : ShopType.SELL;
        }

        // Legacy fallback based on the available price(s)
        String priceLine = LegacyChestShopSign.getPrice(lines);
        if (PriceUtil.hasBuyPrice(priceLine)) {
            return ShopType.BUY;
        }
        if (PriceUtil.hasSellPrice(priceLine)) {
            return ShopType.SELL;
        }
        return null;
    }

    public static String getQuantityLine(Sign sign) throws IllegalArgumentException {
        return getQuantityLine(sign.getLines());
    }

    public static String getQuantityLine(String[] lines) throws IllegalArgumentException {
        if (!isNewFormat(lines)) {
            return LegacyChestShopSign.getQuantityLine(lines);
        }
        return lines.length > QUANTITY_LINE ? StringUtil.strip(StringUtil.stripColourCodes(lines[QUANTITY_LINE])) : "";
    }

    public static int getQuantity(Sign sign) throws IllegalArgumentException {
        return getQuantity(sign.getLines());
    }

    public static int getQuantity(String[] lines) throws IllegalArgumentException {
        if (!isNewFormat(lines)) {
            return LegacyChestShopSign.getQuantity(lines);
        }
        Matcher matcher = MERGED_ITEM_PATTERN.matcher(getQuantityLine(lines));
        if (!matcher.matches()) {
            throw new NumberFormatException("No quantity on the shop sign");
        }
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * Get the (synthesised) price line of a shop sign.
     *
     * For new-format signs this returns a "B &lt;price&gt;" or "S &lt;price&gt;"
     * string so that the existing {@link PriceUtil} based consumers keep working
     * even though only a single direction is ever present.
     *
     * @param sign The sign
     * @return The price line
     */
    public static String getPrice(Sign sign) {
        return getPrice(sign.getLines());
    }

    public static String getPrice(String[] lines) {
        if (!isNewFormat(lines)) {
            return LegacyChestShopSign.getPrice(lines);
        }
        BigDecimal price = getExactPrice(lines);
        if (price.compareTo(PriceUtil.NO_PRICE) == 0) {
            return "";
        }
        String indicator = getShopType(lines) == ShopType.SELL ? "S " : "B ";
        return indicator + price.toPlainString();
    }

    /**
     * Get the numeric (per item) price of a new-format shop sign.
     * @param sign The sign
     * @return The price, or {@link PriceUtil#NO_PRICE} if it cannot be parsed
     */
    public static BigDecimal getExactPrice(Sign sign) {
        return getExactPrice(sign.getLines());
    }

    /**
     * Get the numeric (per item) price of a new-format shop sign.
     * @param lines The sign lines
     * @return The price, or {@link PriceUtil#NO_PRICE} if it cannot be parsed
     */
    public static BigDecimal getExactPrice(String[] lines) {
        if (lines.length <= PRICE_LINE) {
            return PriceUtil.NO_PRICE;
        }
        return parseMoney(lines[PRICE_LINE]);
    }

    /**
     * Get the item line of the shop sign
     * @param sign The sign
     * @return The item line
     */
    public static String getItem(Sign sign) {
        return getItem(sign.getLines());
    }

    /**
     * Get the item line of sign lines
     * @param lines The sign lines
     * @return The item line
     */
    public static String getItem(String[] lines) {
        if (!isNewFormat(lines)) {
            return LegacyChestShopSign.getItem(lines);
        }
        String line = getQuantityLine(lines);
        Matcher matcher = MERGED_ITEM_PATTERN.matcher(line);
        if (matcher.matches()) {
            return StringUtil.strip(matcher.group(2));
        }
        return line;
    }

    /**
     * Parse a money value off a price line, ignoring any currency symbol,
     * grouping separators and surrounding text (e.g. "$1,234.56" -&gt; 1234.56).
     *
     * @param line The raw price line
     * @return The parsed value, or {@link PriceUtil#NO_PRICE} if it is not a number
     */
    public static BigDecimal parseMoney(String line) {
        if (line == null) {
            return PriceUtil.NO_PRICE;
        }
        String cleaned = StringUtil.stripColourCodes(line).replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty() || cleaned.equals(".")) {
            return PriceUtil.NO_PRICE;
        }
        try {
            BigDecimal value = new BigDecimal(cleaned);
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(PriceUtil.MAX) > 0) {
                return PriceUtil.NO_PRICE;
            }
            return value;
        } catch (NumberFormatException notANumber) {
            return PriceUtil.NO_PRICE;
        }
    }

    /**
     * Format a money price for display, e.g. "$1,234.56".
     * @param value The value
     * @return The formatted price
     */
    public static String formatPrice(BigDecimal value) {
        return formatPrice(value, Currency.MONEY);
    }

    /**
     * Format a price for display in the given currency, e.g. "$1,234.56" or
     * "1,234.56 GC".
     * @param value The value
     * @param currency The currency
     * @return The formatted price
     */
    public static String formatPrice(BigDecimal value, Currency currency) {
        if (currency == Currency.GC) {
            return PRICE_FORMAT.format(value) + " " + GC_SUFFIX;
        }
        return "$" + PRICE_FORMAT.format(value);
    }

    /**
     * Get the currency a shop trades in.
     * @param sign The sign
     * @return The currency
     */
    public static Currency getCurrency(Sign sign) {
        return getCurrency(sign.getLines());
    }

    /**
     * Get the currency a shop trades in, based on its price line.
     * @param lines The sign lines
     * @return The currency
     */
    public static Currency getCurrency(String[] lines) {
        if (lines.length <= PRICE_LINE || lines[PRICE_LINE] == null) {
            return Currency.MONEY;
        }
        String priceLine = StringUtil.stripColourCodes(lines[PRICE_LINE]).toLowerCase(Locale.ROOT);
        if (priceLine.contains("gc") || priceLine.contains("giftcard")) {
            return Currency.GC;
        }
        return Currency.MONEY;
    }

    /**
     * @return the colour-coded label for a shop type
     */
    public static String getLabel(ShopType type) {
        return type == ShopType.SELL ? SELL_LABEL : BUY_LABEL;
    }

    /**
     * Build the four rendered lines for a shop sign. Shops always trade one
     * item at a time ("1x &lt;item&gt;"); larger amounts are chosen at purchase
     * time through the amount anvil.
     *
     * @param type     The shop direction
     * @param owner    The owner name
     * @param item     The item sign code
     * @param price    The price (per item)
     * @param currency The currency
     * @return The rendered (colour-coded) sign lines
     */
    public static String[] buildSignLines(ShopType type, String owner, String item, BigDecimal price, Currency currency) {
        return new String[]{
                getLabel(type),
                LINE_COLOR + owner,
                LINE_COLOR + "1x " + item,
                PRICE_COLOR + formatPrice(price, currency)
        };
    }

    /**
     * Migrate a sign to the new layout if it still uses the old one.
     *
     * @param sign The sign to convert
     * @return true if the sign was converted
     */
    public static boolean convertIfLegacy(Sign sign) {
        if (sign == null) {
            return false;
        }
        String[] stripped = StringUtil.stripColourCodes(sign.getLines());
        if (isNewFormat(stripped) || !LegacyChestShopSign.isValid(stripped)) {
            return false;
        }

        String[] newLines = LegacyChestShopSign.convert(stripped);
        if (newLines == null) {
            return false;
        }

        for (int i = 0; i < newLines.length && i < 4; i++) {
            sign.setLine(i, newLines[i]);
        }
        return sign.update(true);
    }
}
