package com.almajd.accounting.util;

import com.almajd.accounting.model.Payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Android-free search logic for the payments list.
 *
 * <p>The list activity loads the (optionally store-filtered) payments from the
 * DAO first and then applies this helper in Java, so the number of database
 * queries stays constant.</p>
 *
 * <p>A query matches the store name, the amount (string-contains on the plain
 * two-decimal number), the notes, or the linked order id. An explicit
 * {@code #12} query is treated as an order-id query only, so an on-account
 * payment (no linked order) never matches it.</p>
 */
public final class PaymentSearch {

    private PaymentSearch() {
    }

    /**
     * Returns the payments matching the text query. The input list is never
     * modified. Null input, null payments and null queries are tolerated.
     */
    public static List<Payment> filter(List<Payment> payments, String rawQuery) {
        List<Payment> results = new ArrayList<>();
        if (payments == null) {
            return results;
        }

        String query = normalizeQuery(rawQuery);
        for (Payment payment : payments) {
            if (payment != null && matchesQuery(payment, query)) {
                results.add(payment);
            }
        }
        return results;
    }

    /** Convenience method used by tests and by callers that need a single check. */
    public static boolean matches(Payment payment, String rawQuery) {
        return payment != null && matchesQuery(payment, normalizeQuery(rawQuery));
    }

    public static boolean hasQuery(String rawQuery) {
        return !normalizeQuery(rawQuery).isEmpty();
    }

    /**
     * Normalises user input: Arabic-Indic digits become ASCII, surrounding
     * whitespace is removed, and Latin letters are lower-cased for matching.
     */
    public static String normalizeQuery(String rawQuery) {
        if (rawQuery == null) {
            return "";
        }
        return NumberUtils.toAsciiDigits(rawQuery).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesQuery(Payment payment, String query) {
        if (query.isEmpty()) {
            return true;
        }

        // An explicit order reference is intentionally id-only: it must not
        // accidentally match notes or amounts, and on-account payments (which
        // have no invoice id) must not match it.
        if (query.startsWith("#")) {
            String idText = query.substring(1).trim();
            return isDigits(idText)
                    && payment.getInvoiceId() != null
                    && Long.toString(payment.getInvoiceId()).equals(idText);
        }

        if (containsText(payment.getStoreName(), query)) {
            return true;
        }
        if (containsText(payment.getNotes(), query)) {
            return true;
        }
        if (payment.getInvoiceId() != null
                && Long.toString(payment.getInvoiceId()).equals(query)) {
            return true;
        }

        if (isAmountQuery(query) && containsAmount(payment.getAmount(), toAmountQuery(query))) {
            return true;
        }
        return false;
    }

    private static boolean containsText(String value, String query) {
        if (value == null) {
            return false;
        }
        return NumberUtils.toAsciiDigits(value).toLowerCase(Locale.ROOT).contains(query);
    }

    private static boolean isDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAmountQuery(String query) {
        boolean hasDigit = false;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
            } else if (c != '.' && c != ',' && c != '\u066C') {
                return false;
            }
        }
        return hasDigit;
    }

    private static String toAmountQuery(String query) {
        return query.replace(",", "").replace("\u066C", "");
    }

    private static boolean containsAmount(double amount, String query) {
        if (query.isEmpty() || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return false;
        }
        String plainAmount = BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
        return plainAmount.contains(query);
    }
}
