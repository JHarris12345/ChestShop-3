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
 * Tests the new "$" based price grammar.
 */
@RunWith(JUnit4.class)
public class PriceCheckerTest {

    private static String[] lines(String price) {
        return new String[]{ChestShopSign.BUY_LABEL, "Owner", "1x stone", price};
    }

    private static PreShopCreationEvent run(String price) {
        PreShopCreationEvent event = new PreShopCreationEvent(null, null, lines(price));
        onPreShopCreation(event);
        return event;
    }

    @Test
    public void testCurrencySymbolAndCommasAreStripped() {
        PreShopCreationEvent event = run("$5,400.34");
        assertFalse(event.isCancelled());
        assertEquals(ChestShopSign.LINE_COLOR + "$5,400.34", event.getSignLine(ChestShopSign.PRICE_LINE));
        assertTrue(BigDecimal.valueOf(5400.34).compareTo(ChestShopSign.getExactPrice(event.getSignLines())) == 0);
    }

    @Test
    public void testPlainNumber() {
        PreShopCreationEvent event = run("5400.34");
        assertFalse(event.isCancelled());
        assertEquals(ChestShopSign.LINE_COLOR + "$5,400.34", event.getSignLine(ChestShopSign.PRICE_LINE));
    }

    @Test
    public void testIntegerGetsTwoDecimals() {
        PreShopCreationEvent event = run("100");
        assertFalse(event.isCancelled());
        assertEquals(ChestShopSign.LINE_COLOR + "$100.00", event.getSignLine(ChestShopSign.PRICE_LINE));
    }

    @Test
    public void testFreeIsZero() {
        PreShopCreationEvent event = run("0");
        assertFalse(event.isCancelled());
        assertEquals(ChestShopSign.LINE_COLOR + "$0.00", event.getSignLine(ChestShopSign.PRICE_LINE));
    }

    @Test
    public void testRoundingHalfUp() {
        PreShopCreationEvent event = run("5.005");
        assertFalse(event.isCancelled());
        assertEquals(ChestShopSign.LINE_COLOR + "$5.01", event.getSignLine(ChestShopSign.PRICE_LINE));
    }

    @Test
    public void testInvalidPrices() {
        assertTrue(run("abc").isCancelled());
        assertTrue(run("").isCancelled());
        assertTrue(run("$").isCancelled());
        assertTrue(run(".").isCancelled());
    }
}
