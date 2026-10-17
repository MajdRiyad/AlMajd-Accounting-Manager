package com.almajd.accounting.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.almajd.accounting.model.InvoiceStatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain JVM tests for {@link CreditAllocator} (no Android dependencies).
 * <p>
 * Covers the required scenarios: no pool; pool smaller than the oldest invoice;
 * pool covering several invoices partially; pool larger than every debt (surplus
 * stays credit); invoices already paid being skipped; ordering by date then id;
 * rounding; and two customers staying independent.
 */
public class CreditAllocatorTest {

    private static final double DELTA = 0.0001;

    private static CreditAllocator.Debt debt(long id, String date, double total, double paidDirect) {
        return new CreditAllocator.Debt(id, date, total, paidDirect);
    }

    private static CreditAllocator.Allocation find(List<CreditAllocator.Allocation> list, long id) {
        for (CreditAllocator.Allocation a : list) {
            if (a.invoiceId == id) {
                return a;
            }
        }
        throw new AssertionError("no allocation for invoice " + id);
    }

    private static double sumAllocated(List<CreditAllocator.Allocation> list) {
        double sum = 0;
        for (CreditAllocator.Allocation a : list) {
            sum += a.allocatedFromCredit;
        }
        return NumberUtils.round2(sum);
    }

    // ── 1. no pool ──────────────────────────────────────────────────────

    @Test
    public void noPool_leavesEverythingAsItWas() {
        List<CreditAllocator.Debt> debts = Arrays.asList(
                debt(1, "2024-01-01", 500, 0),
                debt(2, "2024-02-01", 300, 120));

        List<CreditAllocator.Allocation> result = CreditAllocator.allocate(debts, 0);

        CreditAllocator.Allocation a = find(result, 1);
        assertEquals(0.0, a.allocatedFromCredit, DELTA);
        assertEquals(0.0, a.effectivePaid, DELTA);
        assertEquals(500.0, a.effectiveRemaining, DELTA);
        assertEquals(InvoiceStatus.UNPAID, a.status);

        CreditAllocator.Allocation b = find(result, 2);
        assertEquals(0.0, b.allocatedFromCredit, DELTA);
        assertEquals(120.0, b.effectivePaid, DELTA);
        assertEquals(180.0, b.effectiveRemaining, DELTA);
        assertEquals(InvoiceStatus.PARTIAL, b.status);
    }

    // ── 2. pool smaller than the oldest invoice ─────────────────────────

    @Test
    public void poolSmallerThanOldest_coversOnlyTheOldest() {
        List<CreditAllocator.Debt> debts = Arrays.asList(
                debt(1, "2024-01-01", 500, 0),
                debt(2, "2024-02-01", 300, 0));

        List<CreditAllocator.Allocation> result = CreditAllocator.allocate(debts, 200);

        CreditAllocator.Allocation oldest = find(result, 1);
        assertEquals(200.0, oldest.allocatedFromCredit, DELTA);
        assertEquals(200.0, oldest.effectivePaid, DELTA);
        assertEquals(300.0, oldest.effectiveRemaining, DELTA);
        assertEquals(InvoiceStatus.PARTIAL, oldest.status);

        CreditAllocator.Allocation newer = find(result, 2);
        assertEquals(0.0, newer.allocatedFromCredit, DELTA);
        assertEquals(300.0, newer.effectiveRemaining, DELTA);
        assertEquals(InvoiceStatus.UNPAID, newer.status);
    }

    // ── 3. pool covering several invoices partially ─────────────────────

    @Test
    public void poolCoveringSeveral_partially_servesOldestFirst() {
        // The example from the task: A old 500 unpaid + B new 300 unpaid, 600 credit.
        List<CreditAllocator.Debt> debts = Arrays.asList(
                debt(1, "2024-01-01", 500, 0),
                debt(2, "2024-02-01", 300, 0));

        List<CreditAllocator.Allocation> result = CreditAllocator.allocate(debts, 600);

        CreditAllocator.Allocation a = find(result, 1);
        assertEquals(500.0, a.allocatedFromCredit, DELTA);
        assertEquals(0.0, a.effectiveRemaining, DELTA);
        assertEquals(InvoiceStatus.PAID, a.status);

        CreditAllocator.Allocation b = find(result, 2);
        assertEquals(100.0, b.allocatedFromCredit, DELTA);
        assertEquals(100.0, b.effectivePaid, DELTA);
        assertEquals(200.0, b.effectiveRemaining, DELTA);
        assertEquals(InvoiceStatus.PARTIAL, b.status);

        assertEquals(600.0, sumAllocated(result), DELTA);
    }

    // ── 4. pool larger than all debts (surplus stays credit) ────────────

    @Test
    public void poolLargerThanAllDebts_capsAtTotalAndKeepsSurplusAsCredit() {
        List<CreditAllocator.Debt> debts = Arrays.asList(
                debt(1, "2024-01-01", 500, 0),
                debt(2, "2024-02-01", 300, 0));

        List<CreditAllocator.Allocation> result = CreditAllocator.allocate(debts, 1000);

        for (CreditAllocator.Allocation a : result) {
            assertEquals(0.0, a.effectiveRemaining, DELTA);
            assertEquals(InvoiceStatus.PAID, a.status);
        }
        // Only what is owed is allocated; 200 stays as the customer's credit.
        assertEquals(800.0, sumAllocated(result), DELTA);
    }

    // ── 5. already fully-paid invoices are skipped ──────────────────────

    @Test
    public void alreadyPaidInvoices_areSkipped() {
        List<CreditAllocator.Debt> debts = Arrays.asList(
                debt(1, "2024-01-01", 500, 500),   // settled directly
                debt(2, "2024-02-01", 300, 0));

        List<CreditAllocator.Allocation> result = CreditAllocator.allocate(debts, 300);

        CreditAllocator.Allocation paid = find(result, 1);
        assertEquals(0.0, paid.allocatedFromCredit, DELTA);
        assertEquals(InvoiceStatus.PAID, paid.status);

        CreditAllocator.Allocation open = find(result, 2);
        assertEquals(300.0, open.allocatedFromCredit, DELTA);
        assertEquals(InvoiceStatus.PAID, open.status);
    }

    // ── 6. ordering: date first, then id ────────────────────────────────

    @Test
    public void ordering_unpaidInvoiceWithOlderDateWinsOverLowerId() {
        // Invoice 5 is dated earlier than invoice 1, so 5 must be served first.
        List<CreditAllocator.Debt> debts = Arrays.asList(
                debt(1, "2024-03-01", 100, 0),
                debt(5, "2024-01-01", 100, 0));

        List<CreditAllocator.Allocation> result = CreditAllocator.allocate(debts, 100);
        assertEquals(100.0, find(result, 5).allocatedFromCredit, DELTA);
        assertEquals(0.0, find(result, 1).allocatedFromCredit, DELTA);
    }

    @Test
    public void ordering_sameDateBreaksTiesByLowerIdFirst() {
        List<CreditAllocator.Debt> debts = Arrays.asList(
                debt(7, "2024-01-01", 100, 0),
                debt(3, "2024-01-01", 100, 0));

        List<CreditAllocator.Allocation> result = CreditAllocator.allocate(debts, 100);
        assertEquals(100.0, find(result, 3).allocatedFromCredit, DELTA);
        assertEquals(0.0, find(result, 7).allocatedFromCredit, DELTA);
    }

    // ── 7. rounding (0.1 + 0.2 cases) ───────────────────────────────────

    @Test
    public void rounding_singleInvoiceIsFullySettled() {
        // 0.3 - 0.1 = 0.2 (with float noise) and 0.1 + 0.2 must land exactly on 0.3.
        List<CreditAllocator.Debt> debts =
                Arrays.asList(debt(1, "2024-01-01", 0.3, 0.1));

        List<CreditAllocator.Allocation> result = CreditAllocator.allocate(debts, 0.2);

        CreditAllocator.Allocation a = find(result, 1);
        assertEquals(0.2, a.allocatedFromCredit, DELTA);
        assertEquals(0.3, a.effectivePaid, DELTA);
        assertEquals(0.0, a.effectiveRemaining, DELTA);
        assertEquals(InvoiceStatus.PAID, a.status);
    }

    @Test
    public void rounding_poolSpreadsExactlyWithNoLeftover() {
        List<CreditAllocator.Debt> debts = Arrays.asList(
                debt(1, "2024-01-01", 0.1, 0),
                debt(2, "2024-02-01", 0.1, 0));

        List<CreditAllocator.Allocation> result = CreditAllocator.allocate(debts, 0.2);

        assertEquals(0.1, find(result, 1).allocatedFromCredit, DELTA);
        assertEquals(0.1, find(result, 2).allocatedFromCredit, DELTA);
        assertEquals(0.2, sumAllocated(result), DELTA);
        assertEquals(InvoiceStatus.PAID, find(result, 1).status);
        assertEquals(InvoiceStatus.PAID, find(result, 2).status);
    }

    @Test
    public void negativePool_isTreatedAsZero() {
        List<CreditAllocator.Debt> debts =
                Arrays.asList(debt(1, "2024-01-01", 500, 0));

        List<CreditAllocator.Allocation> result = CreditAllocator.allocate(debts, -50);

        assertEquals(0.0, find(result, 1).allocatedFromCredit, DELTA);
        assertEquals(500.0, find(result, 1).effectiveRemaining, DELTA);
    }

    // ── 8. several customers are independent ────────────────────────────

    @Test
    public void customers_areIndependent() {
        Map<String, List<CreditAllocator.Debt>> debtsByCustomer = new LinkedHashMap<>();
        debtsByCustomer.put("Alpha", Arrays.asList(
                debt(1, "2024-01-01", 500, 0),
                debt(2, "2024-02-01", 300, 0)));
        debtsByCustomer.put("Beta", Arrays.asList(
                debt(3, "2024-01-01", 100, 0)));

        Map<String, Double> pools = new HashMap<>();
        pools.put("Alpha", 600.0);
        pools.put("Beta", 0.0);

        Map<String, List<CreditAllocator.Allocation>> result =
                CreditAllocator.allocateByCustomer(debtsByCustomer, pools);

        // Beta has no credit, so its only invoice stays unpaid...
        assertEquals(0.0, find(result.get("Beta"), 3).allocatedFromCredit, DELTA);
        assertEquals(InvoiceStatus.UNPAID, find(result.get("Beta"), 3).status);

        // ...while Alpha's pool is spread over Alpha's invoices only.
        assertEquals(500.0, find(result.get("Alpha"), 1).allocatedFromCredit, DELTA);
        assertEquals(100.0, find(result.get("Alpha"), 2).allocatedFromCredit, DELTA);
    }

    @Test
    public void customers_emptyInputs_returnEmptyResults() {
        assertTrue(CreditAllocator.allocate(new ArrayList<CreditAllocator.Debt>(), 100).isEmpty());
        assertTrue(CreditAllocator.allocate(null, 100).isEmpty());
        assertTrue(CreditAllocator.allocateByCustomer(null, null).isEmpty());
        assertTrue(CreditAllocator.allocateByCustomer(
                new LinkedHashMap<String, List<CreditAllocator.Debt>>(), null).isEmpty());
    }

    // ── Ordering of the returned list ───────────────────────────────────

    @Test
    public void resultListKeepsInputOrder() {
        List<CreditAllocator.Debt> debts = Arrays.asList(
                debt(9, "2024-03-01", 100, 0),
                debt(4, "2024-01-01", 100, 0));

        List<CreditAllocator.Allocation> result = CreditAllocator.allocate(debts, 50);

        assertEquals(9L, result.get(0).invoiceId);
        assertEquals(4L, result.get(1).invoiceId);
        // Even though invoice 4 (older) received the money.
        assertEquals(50.0, find(result, 4).allocatedFromCredit, DELTA);
    }
}
