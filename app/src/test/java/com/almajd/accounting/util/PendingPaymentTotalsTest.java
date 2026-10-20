package com.almajd.accounting.util;

import static org.junit.Assert.assertEquals;

import com.almajd.accounting.model.PendingPayment;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PendingPaymentTotalsTest {

    private static PendingPayment existing(long id, String amount) {
        return new PendingPayment(id, amount, "2026-10-20", false, false);
    }

    private static PendingPayment newRow(String amount) {
        return new PendingPayment(0, amount, "2026-10-20", false, true);
    }

    @Test
    public void editedAndNewRowsAreSummed() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "300"));
        rows.add(existing(2, "100"));
        rows.add(newRow("50"));

        PendingPaymentTotals totals = PendingPaymentTotals.calculate(1000, rows);

        assertEquals(450.0, totals.paid, 0.0001);
        assertEquals(550.0, totals.remaining, 0.0001);
        assertEquals(0.0, totals.overpaidBy, 0.0001);
    }

    @Test
    public void deletedRowsAreIgnored() {
        PendingPayment deleted = existing(1, "300");
        deleted.setDeleted(true);
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(deleted);
        rows.add(existing(2, "100"));

        PendingPaymentTotals totals = PendingPaymentTotals.calculate(1000, rows);

        assertEquals(100.0, totals.paid, 0.0001);
        assertEquals(900.0, totals.remaining, 0.0001);
    }

    @Test
    public void blankNewRowsAreIgnoredButOtherNewRowsCount() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "300"));
        rows.add(newRow(""));
        rows.add(newRow("  "));
        rows.add(newRow("100"));

        PendingPaymentTotals totals = PendingPaymentTotals.calculate(800, rows);

        assertEquals(400.0, totals.paid, 0.0001);
        assertEquals(400.0, totals.remaining, 0.0001);
    }

    @Test
    public void roundsTenthsBeforeComparingBalances() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "0.1"));
        rows.add(existing(2, "0.2"));

        PendingPaymentTotals totals = PendingPaymentTotals.calculate(1.0, rows);

        assertEquals(0.3, totals.paid, 0.0001);
        assertEquals(0.7, totals.remaining, 0.0001);
    }

    @Test
    public void overpaymentIsReportedWithoutNegativeRemaining() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "300"));
        rows.add(newRow("100"));

        PendingPaymentTotals totals = PendingPaymentTotals.calculate(250, rows);

        assertEquals(400.0, totals.paid, 0.0001);
        assertEquals(0.0, totals.remaining, 0.0001);
        assertEquals(150.0, totals.overpaidBy, 0.0001);
    }

    @Test
    public void nullRowsProduceZeroPaidAndFullRemaining() {
        PendingPaymentTotals totals = PendingPaymentTotals.calculate(500, null);

        assertEquals(0.0, totals.paid, 0.0001);
        assertEquals(500.0, totals.remaining, 0.0001);
        assertEquals(0.0, totals.overpaidBy, 0.0001);
    }
}
