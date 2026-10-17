package com.almajd.accounting.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class NumberUtils {

    /**
     * Amounts are always formatted with ASCII digits and a '.' decimal
     * separator. The app forces an Arabic locale ({@link com.almajd.accounting.App}),
     * whose default {@link DecimalFormatSymbols} would otherwise emit Arabic-Indic
     * digits (٠-٩), so the symbols are pinned to {@link Locale#US}.
     */
    private static final DecimalFormat CURRENCY_FORMAT =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
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

    /**
     * Converts a user-entered string to ASCII so it can be parsed with the
     * locale-independent rules: Arabic-Indic (U+0660-U+0669) and Extended
     * Arabic-Indic (U+06F0-U+06F9) digits become 0-9, and the Arabic decimal
     * separator (U+066B) becomes '.'. Every other character is copied as-is.
     * Returns {@code null} when given {@code null}.
     */
    public static String toAsciiDigits(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0660' && c <= '\u0669') {                 // ٠-٩
                sb.append((char) ('0' + (c - '\u0660')));
            } else if (c >= '\u06F0' && c <= '\u06F9') {          // ۰-۹
                sb.append((char) ('0' + (c - '\u06F0')));
            } else if (c == '\u066B') {                            // ٫ Arabic decimal separator
                sb.append('.');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Parses a monetary amount typed by the user. Tolerates Arabic-Indic digits
     * (converted to ASCII first) as well as the currency symbol and both the
     * Latin ',' and Arabic '٬' (U+066C) thousands separators. Returns 0 when the
     * text is empty or cannot be parsed.
     */
    public static double parseAmount(String text) {
        if (text == null || text.trim().isEmpty()) return 0.0;
        try {
            String cleaned = toAsciiDigits(text)
                    .replace("\u20AA", "")     // ₪
                    .replace(",", "")
                    .replace("\u066C", "")     // ٬ Arabic thousands separator
                    .trim();
            if (cleaned.isEmpty()) return 0.0;
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Parses a whole number (for example a line-item quantity), tolerating
     * Arabic-Indic digits typed on an Arabic keyboard. Returns 0 when the text
     * is empty or not a valid integer.
     */
    public static int parseInt(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        try {
            String cleaned = toAsciiDigits(text)
                    .replace(",", "")
                    .replace("\u066C", "")
                    .trim();
            if (cleaned.isEmpty()) return 0;
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
