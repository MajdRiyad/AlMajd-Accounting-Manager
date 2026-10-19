package com.almajd.accounting.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Plain JVM tests for the Android-free order totals helper. Covers no discount,
 * a discount equal to the subtotal, an excessive or negative discount, rounding,
 * and "paid now" larger than the net total.
 */
public class InvoiceTotalsTest {

    private static final double DELTA = 0.0001;

    @Test
    public void noDiscount_netEqualsSubtotal() {
        InvoiceTotals totals = InvoiceTotals.calculate(100, 0, 40);
        assertEquals(100.0, totals.subtotal, DELTA);
        assertEquals(0.0, totals.discount, DELTA);
        assertEquals(100.0, totals.netTotal, DELTA);
        assertEquals(40.0, totals.paid, DELTA);
        assertEquals(60.0, totals.remaining, DELTA);
    }

    @Test
    public void discountEqualToSubtotal_yieldsZeroNetAndRemaining() {
        InvoiceTotals totals = InvoiceTotals.calculate(75.5, 75.5, 0);
        assertEquals(75.5, totals.subtotal, DELTA);
        assertEquals(75.5, totals.discount, DELTA);
        assertEquals(0.0, totals.netTotal, DELTA);
        assertEquals(0.0, totals.remaining, DELTA);
        assertTrue(InvoiceTotals.isValidDiscount(75.5, 75.5));
    }

    @Test
    public void discountGreaterThanSubtotal_isInvalid() {
        assertFalse(InvoiceTotals.isValidDiscount(100, 100.01));
        assertFalse(InvoiceTotals.isValidDiscount(100, 150));
    }

    @Test
    public void negativeDiscount_isInvalid() {
        assertFalse(InvoiceTotals.isValidDiscount(100, -0.01));
        assertFalse(InvoiceTotals.isValidDiscount(100, -50));
    }

    @Test
    public void rounding_usesTwoDecimalsAtEveryStep() {
        // 0.1 + 0.2 is 0.30000000000000004 in binary floating point.
        InvoiceTotals subtotal = InvoiceTotals.calculate(0.1 + 0.2, 0, 0);
        assertEquals(0.3, subtotal.subtotal, DELTA);
        assertEquals(0.3, subtotal.netTotal, DELTA);

        InvoiceTotals totals = InvoiceTotals.calculate(0.3, 0.1, 0.2);
        assertEquals(0.2, totals.netTotal, DELTA);
        assertEquals(0.0, totals.remaining, DELTA);
    }

    @Test
    public void paidNowGreaterThanNetTotal_neverYieldsNegativeRemaining() {
        InvoiceTotals totals = InvoiceTotals.calculate(100, 20, 150);
        assertEquals(80.0, totals.netTotal, DELTA);
        assertEquals(150.0, totals.paid, DELTA);
        assertEquals(0.0, totals.remaining, DELTA);
    }
}
