package com.almajd.accounting.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Plain JVM tests for {@link StatementBuilder} (no Android dependencies).
 * <p>
 * Covers: no movements; invoices only; payments only; mixed ordering (same date
 * invoicing before payment); opening balance with a from date; to-date cutoff;
 * closing balance equals SUM(invoices) - SUM(payments) over all time; rounding;
 * a credit (negative) balance; linked vs unlinked payments; a customer with
 * payments but no invoices; and file-name sanitizing.
 */
public class StatementBuilderTest {

    private static final double DELTA = 0.0001;

    private static StatementBuilder.InvoiceMovement inv(long id, String date, double total) {
        return new StatementBuilder.InvoiceMovement(id, date, total);
    }

    private static StatementBuilder.PaymentMovement pay(long id, String date, double amount,
                                                       Long invoiceId, String note) {
        return new StatementBuilder.PaymentMovement(id, date, amount, invoiceId, note);
    }

    // ── Empty / single-sided ─────────────────────────────────────────────

    @Test
    public void noMovements_isEmptyAndZero() {
        StatementBuilder.Statement s =
                StatementBuilder.build(null, null, null, null);
        assertTrue(s.rows.isEmpty());
        assertEquals(0.0, s.openingBalance, DELTA);
        assertEquals(0.0, s.totalDebit, DELTA);
        assertEquals(0.0, s.totalCredit, DELTA);
        assertEquals(0.0, s.closingBalance, DELTA);
        assertFalse(s.openingRowShown);
        assertNull(s.from);
        assertNull(s.to);
    }

    @Test
    public void invoicesOnly_areDebits() {
        StatementBuilder.Statement s = StatementBuilder.build(
                Arrays.asList(inv(1, "2026-01-01", 100), inv(2, "2026-01-02", 50)),
                null, null, null);
        assertEquals(2, s.rows.size());
        assertEquals(StatementBuilder.Kind.INVOICE, s.rows.get(0).kind);
        assertEquals(100.0, s.rows.get(0).runningBalance, DELTA);
        assertEquals(150.0, s.rows.get(1).runningBalance, DELTA);
        assertEquals(150.0, s.totalDebit, DELTA);
        assertEquals(0.0, s.totalCredit, DELTA);
        assertEquals(150.0, s.closingBalance, DELTA);
    }

    @Test
    public void paymentsOnly_areCredits() {
        StatementBuilder.Statement s = StatementBuilder.build(
                null,
                Arrays.asList(pay(1, "2026-02-01", 30, null, "cash"),
                        pay(2, "2026-02-02", 20, 5L, null)),
                null, null);
        assertEquals(2, s.rows.size());
        assertEquals(0.0, s.totalDebit, DELTA);
        assertEquals(50.0, s.totalCredit, DELTA);
        assertEquals(-30.0, s.rows.get(0).runningBalance, DELTA);
        assertEquals(-50.0, s.rows.get(1).runningBalance, DELTA);
        assertEquals(-50.0, s.closingBalance, DELTA);
    }

    @Test
    public void paymentsButNoInvoices_yieldsCreditBalance() {
        StatementBuilder.Statement s = StatementBuilder.build(
                null,
                Collections.singletonList(pay(1, "2026-02-01", 200, null, null)),
                null, null);
        assertEquals(1, s.rows.size());
        assertEquals(-200.0, s.closingBalance, DELTA);
    }

    // ── Ordering ─────────────────────────────────────────────────────────

    @Test
    public void mixedOrdering_sameDateInvoiceComesBeforePaymentsThenById() {
        StatementBuilder.Statement s = StatementBuilder.build(
                Collections.singletonList(inv(10, "2026-03-01", 100)),
                Arrays.asList(pay(2, "2026-03-01", 40, 10L, null),
                        pay(1, "2026-03-01", 60, null, null)),
                null, null);
        assertEquals(3, s.rows.size());
        assertEquals(StatementBuilder.Kind.INVOICE, s.rows.get(0).kind);
        assertEquals(10L, s.rows.get(0).id);
        // payments follow, ordered by id
        assertEquals(StatementBuilder.Kind.PAYMENT, s.rows.get(1).kind);
        assertEquals(1L, s.rows.get(1).id);
        assertEquals(2L, s.rows.get(2).id);
        // running balance flows chronologically through the sorted list
        assertEquals(100.0, s.rows.get(0).runningBalance, DELTA);
        assertEquals(40.0, s.rows.get(1).runningBalance, DELTA);
        assertEquals(0.0, s.rows.get(2).runningBalance, DELTA);
    }

    // ── Date range ───────────────────────────────────────────────────────

    @Test
    public void openingBalance_withFromDate_countsOnlyEarlierMovements() {
        StatementBuilder.Statement s = StatementBuilder.build(
                Arrays.asList(inv(1, "2026-01-01", 100), inv(2, "2026-01-10", 200)),
                Arrays.asList(pay(1, "2026-01-05", 50, null, null),
                        pay(2, "2026-01-15", 25, 2L, null)),
                "2026-01-10", null);
        assertTrue(s.openingRowShown);
        assertEquals(50.0, s.openingBalance, DELTA);          // 100 - 50
        assertEquals(2, s.rows.size());                        // from is inclusive
        assertEquals(250.0, s.rows.get(0).runningBalance, DELTA);
        assertEquals(225.0, s.rows.get(1).runningBalance, DELTA);
        assertEquals(200.0, s.totalDebit, DELTA);
        assertEquals(25.0, s.totalCredit, DELTA);
        assertEquals(225.0, s.closingBalance, DELTA);
    }

    @Test
    public void toDateCutoff_excludesLaterMovements() {
        StatementBuilder.Statement s = StatementBuilder.build(
                Arrays.asList(inv(1, "2026-01-01", 100), inv(2, "2026-01-10", 200)),
                Arrays.asList(pay(1, "2026-01-05", 50, null, null),
                        pay(2, "2026-01-15", 25, 2L, null)),
                null, "2026-01-05");
        assertFalse(s.openingRowShown);
        assertEquals(0.0, s.openingBalance, DELTA);
        assertEquals(2, s.rows.size());
        assertEquals(100.0, s.rows.get(0).runningBalance, DELTA);
        assertEquals(50.0, s.rows.get(1).runningBalance, DELTA);
        assertEquals(50.0, s.closingBalance, DELTA);
    }

    @Test
    public void bothBounds_areInclusive() {
        StatementBuilder.Statement s = StatementBuilder.build(
                Arrays.asList(inv(1, "2026-01-01", 100), inv(2, "2026-01-10", 200)),
                Arrays.asList(pay(1, "2026-01-05", 50, null, null),
                        pay(2, "2026-01-15", 25, 2L, null)),
                "2026-01-05", "2026-01-10");
        assertEquals(100.0, s.openingBalance, DELTA);   // invoice 1 only (payment 1 is on the boundary)
        assertEquals(2, s.rows.size());                 // payment 1 and invoice 2
        assertEquals(50.0, s.rows.get(0).runningBalance, DELTA);
        assertEquals(250.0, s.rows.get(1).runningBalance, DELTA);
        assertEquals(250.0, s.closingBalance, DELTA);
    }

    // ── Closing balance invariant ────────────────────────────────────────

    @Test
    public void closingBalance_equalsAllTimeNet_whenUnfiltered() {
        List<StatementBuilder.InvoiceMovement> invoices = Arrays.asList(
                inv(1, "2026-01-01", 100.5), inv(2, "2026-02-01", 200.25),
                inv(3, "2026-03-01", 50));
        List<StatementBuilder.PaymentMovement> payments = Arrays.asList(
                pay(1, "2026-01-15", 75.75, null, null),
                pay(2, "2026-02-15", 30, 2L, "part"),
                pay(3, "2026-04-01", 100, null, null));

        StatementBuilder.Statement s = StatementBuilder.build(invoices, payments, null, null);

        double expected = 0;
        for (StatementBuilder.InvoiceMovement i : invoices) expected += i.total;
        for (StatementBuilder.PaymentMovement p : payments) expected -= p.amount;
        expected = NumberUtils.round2(expected);

        assertEquals(expected, s.closingBalance, DELTA);
        assertEquals(350.75, s.totalDebit, DELTA);
        assertEquals(205.75, s.totalCredit, DELTA);
        assertEquals(145.0, s.closingBalance, DELTA);
    }

    // ── Rounding ─────────────────────────────────────────────────────────

    @Test
    public void rounding_isAppliedAtEveryStep() {
        StatementBuilder.Statement s = StatementBuilder.build(
                Arrays.asList(inv(1, "2026-01-01", 0.1), inv(2, "2026-01-02", 0.2)),
                Collections.singletonList(pay(1, "2026-01-03", 0.3, null, null)),
                null, null);
        assertEquals(0.1, s.rows.get(0).runningBalance, DELTA);
        assertEquals(0.3, s.rows.get(1).runningBalance, DELTA);
        assertEquals(0.3, s.totalDebit, DELTA);
        assertEquals(0.0, s.rows.get(2).runningBalance, DELTA);
        assertEquals(0.0, s.closingBalance, DELTA);
    }

    // ── Credit (negative) balance ────────────────────────────────────────

    @Test
    public void creditBalance_closesNegative() {
        StatementBuilder.Statement s = StatementBuilder.build(
                Collections.singletonList(inv(1, "2026-01-01", 50)),
                Collections.singletonList(pay(1, "2026-01-02", 200, null, null)),
                null, null);
        assertEquals(-150.0, s.closingBalance, DELTA);
    }

    // ── Payment descriptions (fields the exporter turns into text) ───────

    @Test
    public void linkedVsUnlinkedPayments_exposeCorrectFields() {
        StatementBuilder.Statement s = StatementBuilder.build(
                null,
                Arrays.asList(pay(1, "2026-02-01", 10, null, "cash"),
                        pay(2, "2026-02-02", 20, 7L, null)),
                null, null);
        assertNull(s.rows.get(0).linkedInvoiceId);
        assertEquals("cash", s.rows.get(0).note);
        assertEquals(Long.valueOf(7L), s.rows.get(1).linkedInvoiceId);
        assertNull(s.rows.get(1).note);
    }

    // ── Discount annotation ──────────────────────────────────────────────

    @Test
    public void invoiceDiscount_isCarriedOnTheRowWithoutChangingDebitOrBalance() {
        StatementBuilder.Statement s = StatementBuilder.build(
                Arrays.asList(new StatementBuilder.InvoiceMovement(
                                7, "2026-01-01", 85.0, 15.0)),
                Collections.singletonList(pay(1, "2026-01-02", 35, 7L, null)),
                null, null);

        assertEquals(2, s.rows.size());
        StatementBuilder.Row invoiceRow = s.rows.get(0);
        assertEquals(StatementBuilder.Kind.INVOICE, invoiceRow.kind);
        assertEquals(15.0, invoiceRow.discount, DELTA);
        // The debit and the running balance stay on the NET invoice amount.
        assertEquals(85.0, invoiceRow.debit, DELTA);
        assertEquals(85.0, invoiceRow.runningBalance, DELTA);
        assertEquals(0.0, s.rows.get(1).discount, DELTA);
        assertEquals(85.0, s.totalDebit, DELTA);
        assertEquals(35.0, s.totalCredit, DELTA);
        assertEquals(50.0, s.closingBalance, DELTA);
    }

    // ── File name sanitizing ─────────────────────────────────────────────

    @Test
    public void sanitizeFileName_keepsArabicAndReplacesInvalidCharacters() {
        assertEquals("محل_الشرق", StatementBuilder.sanitizeFileName("محل/الشرق"));
        assertEquals("محل الشرق", StatementBuilder.sanitizeFileName("محل الشرق"));
        assertEquals("a_b_c_d_e_f_g_h_i_j",
                StatementBuilder.sanitizeFileName("a/b\\c:d*e?f\"g<h>i|j"));
        assertEquals("", StatementBuilder.sanitizeFileName(null));
        assertEquals("abc", StatementBuilder.sanitizeFileName("abc. "));
    }

    // ── Robustness ───────────────────────────────────────────────────────

    @Test
    public void blankBounds_areTreatedAsOpen() {
        StatementBuilder.Statement s = StatementBuilder.build(
                Collections.singletonList(inv(1, "2026-01-01", 100)),
                null, "  ", "  ");
        assertFalse(s.openingRowShown);
        assertNull(s.from);
        assertNull(s.to);
        assertEquals(1, s.rows.size());
    }

    @Test
    public void nullEntries_areIgnored() {
        List<StatementBuilder.InvoiceMovement> invoices = new ArrayList<>();
        invoices.add(inv(1, "2026-01-01", 100));
        invoices.add(null);
        StatementBuilder.Statement s = StatementBuilder.build(invoices, null, null, null);
        assertEquals(1, s.rows.size());
        assertEquals(100.0, s.closingBalance, DELTA);
    }
}
