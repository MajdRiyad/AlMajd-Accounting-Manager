package com.almajd.accounting.util;

import java.text.DecimalFormat;

public class NumberUtils {

    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##0.00");
    private static final String CURRENCY_SYMBOL = " \u20AA"; // ₪ NIS

    public static String formatCurrency(double amount) {
        return CURRENCY_FORMAT.format(amount) + CURRENCY_SYMBOL;
    }

    /**
     * Format currency without the symbol (for PDF table cells where space is tight).
     */
    public static String formatAmount(double amount) {
        return CURRENCY_FORMAT.format(amount);
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
