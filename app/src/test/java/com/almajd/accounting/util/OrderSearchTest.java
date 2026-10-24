package com.almajd.accounting.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.InvoiceStatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain JVM tests for the Android-free order search helper: store-name and id
 * matching, numeric amount matching, Arabic-Indic input, empty queries, status
 * chips, combined filters, and null safety.
 */
public class OrderSearchTest {

    private static Invoice invoice(long id, String storeName, double total,
                                   double remaining, InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setStoreName(storeName);
        invoice.setTotalAmount(total);
        invoice.setRemaining(remaining);
        invoice.setStatus(status);
        return invoice;
    }

    private static List<Invoice> list(Invoice... invoices) {
        List<Invoice> list = new ArrayList<>();
        for (Invoice invoice : invoices) {
            list.add(invoice);
        }
        return list;
    }

    @Test
    public void storeNameMatchIsPartialAndCaseInsensitive() {
        Invoice invoice = invoice(1, "Al Noor Store", 100, 100, InvoiceStatus.UNPAID);

        assertTrue(OrderSearch.matches(invoice, "noor"));
        assertTrue(OrderSearch.matches(invoice, "NOOR STORE"));
        assertFalse(OrderSearch.matches(invoice, "Sara"));
    }

    @Test
    public void orderIdMatchesWithAndWithoutHash() {
        Invoice invoice = invoice(12, "Al Noor", 500, 500, InvoiceStatus.UNPAID);

        assertTrue(OrderSearch.matches(invoice, "12"));
        assertTrue(OrderSearch.matches(invoice, "#12"));
        assertFalse(OrderSearch.matches(invoice, "13"));
        assertFalse(OrderSearch.matches(invoice, "#13"));
    }

    @Test
    public void numericQueryMatchesPlainAmountByContains() {
        Invoice first = invoice(1, "A", 300.00, 300.00, InvoiceStatus.UNPAID);
        Invoice second = invoice(2, "B", 1300.00, 1300.00, InvoiceStatus.UNPAID);
        Invoice third = invoice(3, "C", 999.00, 999.00, InvoiceStatus.UNPAID);

        List<Invoice> results = OrderSearch.filter(list(first, second, third), "300",
                OrderSearch.StatusFilter.ALL);

        assertEquals(2, results.size());
        assertTrue(results.contains(first));
        assertTrue(results.contains(second));
        assertFalse(results.contains(third));
    }

    @Test
    public void numericQueryAlsoMatchesEffectiveRemainingAmount() {
        Invoice invoice = invoice(1, "A", 500.00, 120.00, InvoiceStatus.PARTIAL);

        assertTrue(OrderSearch.matches(invoice, "120"));
    }

    @Test
    public void arabicIndicDigitsAreConvertedBeforeMatching() {
        Invoice invoice = invoice(1, "A", 300.50, 300.50, InvoiceStatus.UNPAID);

        assertTrue(OrderSearch.matches(invoice, "٣٠٠"));
        assertTrue(OrderSearch.matches(invoice, "٣٠٠٫٥٠"));
        assertTrue(OrderSearch.matches(invoice, "۳۰۰"));
    }

    @Test
    public void emptyQueryReturnsEveryNonNullInvoice() {
        Invoice first = invoice(1, "A", 100, 100, InvoiceStatus.UNPAID);
        Invoice second = invoice(2, "B", 200, 0, InvoiceStatus.PAID);

        assertEquals(2, OrderSearch.filter(list(first, second), "", OrderSearch.StatusFilter.ALL).size());
        assertEquals(2, OrderSearch.filter(list(first, second), "   ", OrderSearch.StatusFilter.ALL).size());
        assertEquals(2, OrderSearch.filter(list(first, second), null, OrderSearch.StatusFilter.ALL).size());
    }

    @Test
    public void everyStatusChipSelectsItsOwnEffectiveStatus() {
        Invoice paid = invoice(1, "A", 100, 0, InvoiceStatus.PAID);
        Invoice partial = invoice(2, "B", 100, 40, InvoiceStatus.PARTIAL);
        Invoice unpaid = invoice(3, "C", 100, 100, InvoiceStatus.UNPAID);
        List<Invoice> all = list(paid, partial, unpaid);

        assertEquals(paid, OrderSearch.filter(all, "", OrderSearch.StatusFilter.PAID).get(0));
        assertEquals(partial, OrderSearch.filter(all, "", OrderSearch.StatusFilter.PARTIAL).get(0));
        assertEquals(unpaid, OrderSearch.filter(all, "", OrderSearch.StatusFilter.UNPAID).get(0));
        assertEquals(3, OrderSearch.filter(all, "", OrderSearch.StatusFilter.ALL).size());
    }

    @Test
    public void queryAndStatusChipAreCombined() {
        Invoice matching = invoice(1, "Al Noor", 100, 0, InvoiceStatus.PAID);
        Invoice wrongStatus = invoice(2, "Al Noor", 100, 100, InvoiceStatus.UNPAID);
        Invoice wrongStore = invoice(3, "Sara", 100, 0, InvoiceStatus.PAID);
        List<Invoice> all = list(matching, wrongStatus, wrongStore);

        List<Invoice> results = OrderSearch.filter(all, "noor", OrderSearch.StatusFilter.PAID);

        assertEquals(1, results.size());
        assertEquals(matching, results.get(0));
    }

    @Test
    public void filteringFlagReflectsQueryOrStatus() {
        assertFalse(OrderSearch.isFiltering("", OrderSearch.StatusFilter.ALL));
        assertFalse(OrderSearch.isFiltering(null, OrderSearch.StatusFilter.ALL));
        assertTrue(OrderSearch.isFiltering("noor", OrderSearch.StatusFilter.ALL));
        assertTrue(OrderSearch.isFiltering("", OrderSearch.StatusFilter.PAID));
    }

    @Test
    public void nullInputIsSafe() {
        Invoice nullStore = invoice(1, null, 100, 100, InvoiceStatus.UNPAID);
        nullStore.setStatus(null);

        assertTrue(OrderSearch.filter(null, "x", OrderSearch.StatusFilter.ALL).isEmpty());
        assertFalse(OrderSearch.matches(null, "x"));
        assertFalse(OrderSearch.matchesStatus(null, OrderSearch.StatusFilter.ALL));
        assertEquals(1, OrderSearch.filter(list(null, nullStore), null,
                OrderSearch.StatusFilter.UNPAID).size());
        assertEquals(1, OrderSearch.filter(list(null, nullStore), null, null).size());
        assertEquals("", OrderSearch.normalizeQuery(null));
    }
}
