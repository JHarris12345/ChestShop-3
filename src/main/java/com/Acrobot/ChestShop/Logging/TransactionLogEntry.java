package com.Acrobot.ChestShop.Logging;

import com.Acrobot.ChestShop.Utils.Utils;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A single parsed transaction line from a shop log file.
 *
 * <p>Log lines are written by {@link com.Acrobot.ChestShop.Listeners.PostTransaction.TransactionLogger}
 * and are read back by the stats commands. Several formats exist in the wild, so every one of them
 * has to stay readable:</p>
 *
 * <ul>
 *     <li>Current: {@code [01-Aug-2026 00:38:14] Name (uuid) bought 1 Stone for 500.00 $ from Owner (uuid) at [world] 1, 2, 3 (475 after tax)}</li>
 *     <li>Without UUIDs: {@code [01-Aug-2026 00:38:14] Name bought 1 Stone for 500.00 $ from Owner at [world] 1, 2, 3 (475 after tax)}</li>
 *     <li>Without the currency token (pre-GC logs): {@code [01-Aug-2026 00:38:14] Name bought 1 Stone for 500.00 from Owner at [world] 1, 2, 3 (475 after tax)}</li>
 * </ul>
 *
 * <p>Both the UUID and the currency token are therefore optional when parsing, and the two can be
 * mixed freely - a log file can contain lines from every format at once.</p>
 */
public class TransactionLogEntry {

    /** The UUID a player name can be followed by. Kept loose on purpose so an odd entry never kills a whole log. */
    private static final String UUID_PART = " \\((?<%s>[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\)";

    private static final Pattern PATTERN = Pattern.compile(
            "^(?<time>\\[\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2}:\\d{2}\\]) "
                    + "(?<client>\\S+?)(?:" + String.format(UUID_PART, "clientUuid") + ")? "
                    + "(?<action>bought|sold) "
                    + "(?<items>.+?) "
                    + "for (?<price>-?[\\d,]+(?:\\.\\d+)?)"
                    + "(?: (?<currency>\\$|GC))? "
                    + "(?:from|to) "
                    + "(?<owner>\\S+?)(?:" + String.format(UUID_PART, "ownerUuid") + ")? "
                    + "at (?<location>.+?)"
                    + "(?: \\((?<afterTax>-?[\\d,]+(?:\\.\\d+)?) after tax\\))?$");

    private final long time;
    private final String clientName;
    private final UUID clientUuid;
    private final boolean bought;
    private final String items;
    private final double price;
    private final boolean gc;
    private final String ownerName;
    private final UUID ownerUuid;
    private final String location;
    private final Double afterTax;

    private TransactionLogEntry(long time, String clientName, UUID clientUuid, boolean bought, String items,
                                double price, boolean gc, String ownerName, UUID ownerUuid, String location, Double afterTax) {
        this.time = time;
        this.clientName = clientName;
        this.clientUuid = clientUuid;
        this.bought = bought;
        this.items = items;
        this.price = price;
        this.gc = gc;
        this.ownerName = ownerName;
        this.ownerUuid = ownerUuid;
        this.location = location;
        this.afterTax = afterTax;
    }

    /**
     * Parse a line of a shop log file.
     *
     * @param line The raw line
     * @return The parsed transaction, or null if the line isn't a transaction (shop creations/removals,
     *         blank lines and anything unrecognised)
     */
    public static TransactionLogEntry parse(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }

        Matcher matcher = PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        long time = Utils.getLongTimeFromLogTime(matcher.group("time"));
        if (time == 0) {
            return null;
        }

        String currency = matcher.group("currency");

        return new TransactionLogEntry(
                time,
                matcher.group("client"),
                parseUuid(matcher.group("clientUuid")),
                "bought".equals(matcher.group("action")),
                matcher.group("items"),
                parseNumber(matcher.group("price")),
                currency != null && currency.equalsIgnoreCase("GC"),
                matcher.group("owner"),
                parseUuid(matcher.group("ownerUuid")),
                matcher.group("location"),
                matcher.group("afterTax") != null ? parseNumber(matcher.group("afterTax")) : null);
    }

    private static UUID parseUuid(String uuid) {
        if (uuid == null) {
            return null;
        }

        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double parseNumber(String number) {
        return Double.parseDouble(number.replace(",", ""));
    }

    /** @return When the transaction happened, in milliseconds since the epoch */
    public long getTime() {
        return time;
    }

    /** @return The name of the player who used the shop */
    public String getClientName() {
        return clientName;
    }

    /** @return The UUID of the player who used the shop, or null for logs written before UUIDs were added */
    public UUID getClientUuid() {
        return clientUuid;
    }

    /** @return True if the client bought from the shop (so the shop owner earned money), false if they sold to it */
    public boolean hasBought() {
        return bought;
    }

    /** @return The traded items, e.g. "1 Netherite Upgrade Smithing Template" */
    public String getItems() {
        return items;
    }

    /** @return The price the transaction went through at */
    public double getPrice() {
        return price;
    }

    /** @return True if the shop traded in GC, false if it traded in the server economy */
    public boolean isGc() {
        return gc;
    }

    /** @return The name of the shop's owner */
    public String getOwnerName() {
        return ownerName;
    }

    /** @return The UUID of the shop's owner, or null for logs written before UUIDs were added */
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    /** @return The shop's location, e.g. "[world] -4530, 61, -12406" */
    public String getLocation() {
        return location;
    }

    /** @return What the owner was left with after tax, or null if the line has no tax section (GC and untaxed trades) */
    public Double getAfterTax() {
        return afterTax;
    }
}
