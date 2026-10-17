package com.almajd.accounting.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public class NumberUtils {

    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##0.00");
    private static final String CURRENCY_SYMBOL = " \u20AA"; // ₪ NIS

    /**
     * Single rounding helper for all monetary amounts. Rounds to 2 decimals
     * using HALF_UP. Used when computing subtotals/totals and when saving
     * payments so stored values are always whole agorot.
     */
    public static double round2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static String formatCurrency(double amount) {
        // DecimalFormat is not thread-safe. Formatting now happens on both the
        // main thread (adapters/views) and the database worker thread (exports),
        // so access to the shared instance must be synchronized.
        synchronized (CURRENCY_FORMAT) {
            return CURRENCY_FORMAT.format(amount) + CURRENCY_SYMBOL;
        }
    }

    /**
     * Format currency without the symbol (for PDF table cells where space is tight).
     */
    public static String formatAmount(double amount) {
        synchronized (CURRENCY_FORMAT) {
            return CURRENCY_FORMAT.format(amount);
        }
    }

    public static double parseAmount(String text) {
        if (text == null || text.trim().isEmpty()) return 0.0;
        try {
            // Strip currency symbol and whitespace before parsing
            String cleaned = text.replace("\u20AA", "").replace(",", "").trim();
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
