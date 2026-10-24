package com.almajd.accounting.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.almajd.accounting.model.Payment;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain JVM tests for the Android-free payment search helper: store name,
 * amount, notes, linked order ids, on-account payments, Arabic-Indic input,
 * empty queries, and null safety.
 */
public class PaymentSearchTest {

    private static Payment payment(long id, String storeName, double amount,
                                   String notes, Long invoiceId) {
        return new Payment(id, storeName, amount, "2026-10-20", notes, invoiceId,
                "2026-10-20 10:00:00");
    }

    private static List<Payment> list(Payment... payments) {
        List<Payment> list = new ArrayList<>();
        for (Payment payment : payments) {
            list.add(payment);
        }
        return list;
    }

    @Test
    public void storeNameMatchIsPartialAndCaseInsensitive() {
        Payment payment = payment(1, "Al Noor Store", 500, null, null);

        assertTrue(PaymentSearch.matches(payment, "noor"));
        assertTrue(PaymentSearch.matches(payment, "NOOR STORE"));
        assertFalse(PaymentSearch.matches(payment, "Sara"));
    }

    @Test
    public void amountMatchUsesPlainNumberContains() {
        Payment first = payment(1, "A", 300.00, null, null);
        Payment second = payment(2, "B", 1300.00, null, null);
        Payment third = payment(3, "C", 999.00, null, null);

        List<Payment> results = PaymentSearch.filter(list(first, second, third), "300");

        assertEquals(2, results.size());
        assertTrue(results.contains(first));
        assertTrue(results.contains(second));
        assertFalse(results.contains(third));
    }

    @Test
    public void notesMatchIsCaseInsensitive() {
        Payment payment = payment(1, "A", 500, "VIP Customer Deposit", null);

        assertTrue(PaymentSearch.matches(payment, "vip"));
        assertTrue(PaymentSearch.matches(payment, "deposit"));
        assertFalse(PaymentSearch.matches(payment, "regular"));
    }

    @Test
    public void linkedOrderIdMatchesWithAndWithoutHash() {
        Payment linked = payment(1, "A", 500, null, 12L);

        assertTrue(PaymentSearch.matches(linked, "#12"));
        assertTrue(PaymentSearch.matches(linked, "12"));
        assertFalse(PaymentSearch.matches(linked, "#13"));
    }

    @Test
    public void onAccountPaymentDoesNotMatchExplicitOrderIdQuery() {
        Payment onAccount = payment(1, "A", 900, "on account", null);

        assertFalse(PaymentSearch.matches(onAccount, "#12"));
        assertFalse(PaymentSearch.matches(onAccount, "12"));
    }

    @Test
    public void arabicIndicDigitsMatchAmountsAndOrderIds() {
        Payment amountPayment = payment(1, "A", 300.00, null, null);
        Payment linkedPayment = payment(2, "B", 500, null, 12L);

        assertTrue(PaymentSearch.matches(amountPayment, "٣٠٠"));
        assertTrue(PaymentSearch.matches(linkedPayment, "#١٢"));
    }

    @Test
    public void emptyQueryReturnsEveryNonNullPayment() {
        Payment first = payment(1, "A", 100, null, null);
        Payment second = payment(2, "B", 200, "note", 7L);

        assertEquals(2, PaymentSearch.filter(list(first, second), "").size());
        assertEquals(2, PaymentSearch.filter(list(first, second), "   ").size());
        assertEquals(2, PaymentSearch.filter(list(first, second), null).size());
    }

    @Test
    public void nullInputIsSafe() {
        Payment nullFields = payment(1, null, 100, null, null);

        assertTrue(PaymentSearch.filter(null, "x").isEmpty());
        assertFalse(PaymentSearch.matches(null, "x"));
        assertEquals(1, PaymentSearch.filter(list(null, nullFields), null).size());
        assertEquals("", PaymentSearch.normalizeQuery(null));
    }
}
