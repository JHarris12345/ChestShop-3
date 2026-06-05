package com.Acrobot.ChestShop.Tests;

import com.Acrobot.ChestShop.Events.PreShopCreationEvent;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.math.BigDecimal;

import static com.Acrobot.ChestShop.Listeners.PreShopCreation.PriceChecker.onPreShopCreation;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

/**
 * Tests the abbreviated "$50k each" / "4.23 GC each" price grammar.
 */
@RunWith(JUnit4.class)
public class PriceCheckerTest {

    private static PreShopCreationEvent run(String price) {
        PreShopCreationEvent event = new PreShopCreationEvent(null, null,
                new String[]{ChestShopSign.BUY_LABEL, "Owner", "stone", price});
        onPreShopCreation(event);
        return event;
    }

    private static void assertPrice(String input, String expectedLine, long expectedValue) {
        PreShopCreationEvent event = run(input);
        assertFalse(event.isCancelled());
        assertEquals(ChestShopSign.BUY_COLOR + expectedLine, event.getSignLine(ChestShopSign.PRICE_LINE));
        assertTrue(BigDecimal.valueOf(expectedValue).compareTo(ChestShopSign.getExactPrice(event.getSignLines())) == 0);
    }

    @Test
    public void testThousands() {
        assertPrice("50000", "$50k each", 50000);
    }

    @Test
    public void testDecimalThousands() {
        assertPrice("5400", "$5.4k each", 5400);
    }

    @Test
    public void testMillions() {
        assertPrice("5250000", "$5.25m each", 5250000);
    }

    @Test
    public void testSmallNumber() {
        assertPrice("100", "$100 each", 100);
    }

    @Test
    public void testFreeIsZero() {
        assertPrice("0", "$0 each", 0);
    }

    @Test
    public void testCurrencySymbolAndCommasStripped() {
        assertPrice("$50,000", "$50k each", 50000);
    }

    @Test
    public void testShorthandInput() {
        assertPrice("50k", "$50k each", 50000);
    }

    @Test
    public void testGcSmall() {
        PreShopCreationEvent event = new PreShopCreationEvent(null, null,
                new String[]{ChestShopSign.BUY_LABEL, "Owner", "stone", "640 GC"});
        onPreShopCreation(event);
        assertFalse(event.isCancelled());
        assertEquals(ChestShopSign.BUY_COLOR + "640 GC each", event.getSignLine(ChestShopSign.PRICE_LINE));
        assertTrue(BigDecimal.valueOf(640).compareTo(ChestShopSign.getExactPrice(event.getSignLines())) == 0);
    }

    @Test
    public void testGcMillions() {
        PreShopCreationEvent event = new PreShopCreationEvent(null, null,
                new String[]{ChestShopSign.BUY_LABEL, "Owner", "stone", "4230000 gc"});
        onPreShopCreation(event);
        assertFalse(event.isCancelled());
        assertEquals(ChestShopSign.BUY_COLOR + "4.23m GC each", event.getSignLine(ChestShopSign.PRICE_LINE));
        assertTrue(BigDecimal.valueOf(4230000).compareTo(ChestShopSign.getExactPrice(event.getSignLines())) == 0);
    }

    @Test
    public void testInvalidPrices() {
        assertTrue(run("abc").isCancelled());
        assertTrue(run("").isCancelled());
        assertTrue(run("$").isCancelled());
        assertTrue(run(".").isCancelled());
    }
}
