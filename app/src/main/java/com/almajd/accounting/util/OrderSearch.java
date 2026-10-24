package com.almajd.accounting.util;

import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.InvoiceStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Android-free search and status-filter logic for the orders list.
 *
 * <p>The list activity loads the (optionally store-filtered) invoices from the
 * DAO first and then applies this helper in Java. This keeps the number of
 * database queries constant and preserves the effective status that the DAO
 * already computed after customer-credit allocation.</p>
 *
 * <p>A query is compared with the store name, the exact order id (with or
 * without a leading {@code #}), and -- when it looks numeric -- the net total
 * and the effective remaining amount. Amounts are compared by string-contains
 * on their plain, two-decimal representation, so {@code 300} matches both
 * {@code 300.00} and {@code 1300.00}.</p>
 */
public final class OrderSearch {

    /** The four choices of the order status chip row. */
    public enum StatusFilter {
        ALL,
        PAID,
        PARTIAL,
        UNPAID
    }

    private OrderSearch() {
    }

    /**
     * Returns the invoices that match both the text query and the status filter.
     * The input list is never modified. Null input, null invoices and null
     * filters are tolerated.
     */
    public static List<Invoice> filter(List<Invoice> invoices, String rawQuery,
                                       StatusFilter statusFilter) {
        List<Invoice> results = new ArrayList<>();
        if (invoices == null) {
            return results;
        }

        String query = normalizeQuery(rawQuery);
        StatusFilter status = statusFilter != null ? statusFilter : StatusFilter.ALL;
        for (Invoice invoice : invoices) {
            if (invoice != null
                    && matchesQuery(invoice, query)
                    && matchesStatus(invoice, status)) {
                results.add(invoice);
            }
        }
        return results;
    }

    /** Convenience method used by tests and by callers that need a single check. */
    public static boolean matches(Invoice invoice, String rawQuery) {
        return invoice != null && matchesQuery(invoice, normalizeQuery(rawQuery));
    }

    /** Returns whether the invoice's effective status matches the chip value. */
    public static boolean matchesStatus(Invoice invoice, StatusFilter statusFilter) {
        if (invoice == null) {
            return false;
        }
        if (statusFilter == null || statusFilter == StatusFilter.ALL) {
            return true;
        }

        InvoiceStatus actual = invoice.getStatus() != null
                ? invoice.getStatus()
                : InvoiceStatus.UNPAID;
        switch (statusFilter) {
            case PAID:
                return actual == InvoiceStatus.PAID;
            case PARTIAL:
                return actual == InvoiceStatus.PARTIAL;
            case UNPAID:
            default:
                return actual == InvoiceStatus.UNPAID;
        }
    }

    /** Whether the query or the status chip is narrowing the list. */
    public static boolean isFiltering(String rawQuery, StatusFilter statusFilter) {
        return hasQuery(rawQuery)
                || (statusFilter != null && statusFilter != StatusFilter.ALL);
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

    private static boolean matchesQuery(Invoice invoice, String query) {
        if (query.isEmpty()) {
            return true;
        }

        if (containsText(invoice.getStoreName(), query)) {
            return true;
        }

        if (query.startsWith("#")) {
            String idText = query.substring(1).trim();
            return isDigits(idText) && Long.toString(invoice.getId()).equals(idText);
        }

        if (Long.toString(invoice.getId()).equals(query)) {
            return true;
        }

        if (isAmountQuery(query)) {
            String amountQuery = toAmountQuery(query);
            return containsAmount(invoice.getTotalAmount(), amountQuery)
                    || containsAmount(invoice.getRemaining(), amountQuery);
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

    /**
     * A query is numeric enough for amount matching when it contains at least
     * one digit and only digits, a decimal point, or a thousands separator.
     */
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
