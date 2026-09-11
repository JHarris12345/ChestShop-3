package com.Acrobot.ChestShop.Tests;

import com.Acrobot.ChestShop.Logging.TransactionLogEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Makes sure every log format that can be sitting in a server's logs folder is still readable.
 */
@RunWith(JUnit4.class)
public class TransactionLogEntryTest {

    private static final UUID CLIENT_UUID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID OWNER_UUID = UUID.fromString("61699b2e-d327-4a01-9f1e-0ea8c3f06bc6");

    @Test
    public void testCurrentFormat() {
        TransactionLogEntry entry = TransactionLogEntry.parse("[01-Aug-2026 00:38:14] .She_lvs_riku (" + CLIENT_UUID
                + ") bought 1 Netherite Upgrade Smithing Template for 500.00 $ from dahv (" + OWNER_UUID
                + ") at [world] -4530, 61, -12406 (475 after tax)");

        assertNotNull(entry);
        assertEquals(".She_lvs_riku", entry.getClientName());
        assertEquals(CLIENT_UUID, entry.getClientUuid());
        assertTrue(entry.hasBought());
        assertEquals("1 Netherite Upgrade Smithing Template", entry.getItems());
        assertEquals(500.00, entry.getPrice(), 0.001);
        assertFalse(entry.isGc());
        assertEquals("dahv", entry.getOwnerName());
        assertEquals(OWNER_UUID, entry.getOwnerUuid());
        assertEquals("[world] -4530, 61, -12406", entry.getLocation());
        assertEquals(475.0, entry.getAfterTax(), 0.001);
    }

    @Test
    public void testFormatWithoutUuids() {
        TransactionLogEntry entry = TransactionLogEntry.parse("[01-Aug-2026 00:38:14] .She_lvs_riku bought "
                + "1 Netherite Upgrade Smithing Template for 500.00 $ from dahv at [world] -4530, 61, -12406 (475 after tax)");

        assertNotNull(entry);
        assertEquals(".She_lvs_riku", entry.getClientName());
        assertNull(entry.getClientUuid());
        assertEquals("dahv", entry.getOwnerName());
        assertNull(entry.getOwnerUuid());
        assertEquals(500.00, entry.getPrice(), 0.001);
        assertEquals("[world] -4530, 61, -12406", entry.getLocation());
        assertEquals(475.0, entry.getAfterTax(), 0.001);
    }

    @Test
    public void testFormatWithoutCurrencyToken() {
        // Logs from before GC shops existed have no currency token at all
        TransactionLogEntry entry = TransactionLogEntry.parse("[01-Aug-2026 00:38:14] Notch bought 1 Stone "
                + "for 500.00 from dahv at [world] -4530, 61, -12406 (475 after tax)");

        assertNotNull(entry);
        assertEquals("Notch", entry.getClientName());
        assertFalse(entry.isGc());
        assertEquals(500.00, entry.getPrice(), 0.001);
        assertEquals(475.0, entry.getAfterTax(), 0.001);
    }

    @Test
    public void testSellWithUuids() {
        TransactionLogEntry entry = TransactionLogEntry.parse("[01-Aug-2026 00:38:14] Notch (" + CLIENT_UUID
                + ") sold 64 Stone for 1,250.50 $ to dahv (" + OWNER_UUID + ") at [world_nether] 10, 64, -20 (1,188 after tax)");

        assertNotNull(entry);
        assertFalse(entry.hasBought());
        assertEquals("64 Stone", entry.getItems());
        assertEquals(1250.50, entry.getPrice(), 0.001);
        assertEquals("dahv", entry.getOwnerName());
        assertEquals(OWNER_UUID, entry.getOwnerUuid());
        assertEquals("[world_nether] 10, 64, -20", entry.getLocation());
        assertEquals(1188.0, entry.getAfterTax(), 0.001);
    }

    @Test
    public void testGcTransactionHasNoTax() {
        TransactionLogEntry entry = TransactionLogEntry.parse("[01-Aug-2026 00:38:14] Notch (" + CLIENT_UUID
                + ") bought 1 Stone for 25.00 GC from dahv (" + OWNER_UUID + ") at [world] 1, 2, 3");

        assertNotNull(entry);
        assertTrue(entry.isGc());
        assertEquals(25.00, entry.getPrice(), 0.001);
        assertEquals("[world] 1, 2, 3", entry.getLocation());
        assertNull(entry.getAfterTax());
    }

    @Test
    public void testUntaxedMoneyTransaction() {
        TransactionLogEntry entry = TransactionLogEntry.parse("[01-Aug-2026 00:38:14] Notch bought 1 Stone "
                + "for 25.00 $ from dahv at [world] 1, 2, 3");

        assertNotNull(entry);
        assertFalse(entry.isGc());
        assertNull(entry.getAfterTax());
        assertEquals("[world] 1, 2, 3", entry.getLocation());
    }

    @Test
    public void testNonTransactionLinesAreIgnored() {
        assertNull(TransactionLogEntry.parse(null));
        assertNull(TransactionLogEntry.parse(""));
        assertNull(TransactionLogEntry.parse("[01-Aug-2026 00:38:14] Notch created a shop - 1 Stone - B 5 - at [world] 1, 2, 3"));
        assertNull(TransactionLogEntry.parse("[01-Aug-2026 00:38:14] A shop belonging to Notch was removed by dahv - 1 Stone - B 5 - at [world] 1, 2, 3"));
        assertNull(TransactionLogEntry.parse("not a log line at all"));
    }

    @Test
    public void testItemNameContainingTheWordFor() {
        TransactionLogEntry entry = TransactionLogEntry.parse("[01-Aug-2026 00:38:14] Notch (" + CLIENT_UUID
                + ") bought 1 Sword for Sale for 500.00 $ from dahv (" + OWNER_UUID + ") at [world] 1, 2, 3 (475 after tax)");

        assertNotNull(entry);
        assertEquals("1 Sword for Sale", entry.getItems());
        assertEquals(500.00, entry.getPrice(), 0.001);
    }
}
