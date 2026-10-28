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
     * Arabic-Indic (U+06F0-U+06F9) digits become 0-9, the Arabic decimal
     * separator (U+066B) becomes '.', and the Arabic thousands separator
     * (U+066C) becomes ','. Every other character is copied as-is.
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
            } else if (c == '\u066C') {                            // ٬ Arabic thousands separator
                sb.append(',');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Strictly parses a monetary amount typed by the user.
     * <p>
     * Accepted separators:
     * <ul>
     *   <li>{@code .} is a decimal separator; {@code ,} is a thousands separator
     *       only when it is followed by exactly three digits (for example
     *       {@code 1,234.56} or {@code 1,234}).</li>
     *   <li>When a comma is followed by one or two digits and the text has no
     *       dot, the comma is a decimal separator ({@code 12,50} = 12.50).</li>
     *   <li>When both separators are present, the last one is the decimal
     *       separator and the other is thousands ({@code 1.234,56} and
     *       {@code 1,234.56} both = 1234.56).</li>
     *   <li>Arabic-Indic / Extended Arabic-Indic digits, the Arabic decimal
     *       separator (U+066B), the Arabic thousands separator (U+066C), the
     *       currency symbol, surrounding whitespace and a leading sign are
     *       accepted.</li>
     * </ul>
     * Anything else (for example {@code "abc"}, {@code "1,23,456"} or an empty
     * string) is invalid. Unlike {@link #parseAmount(String)} this method does
     * not turn invalid input into zero, so forms can show a field error.
     *
     * @return the parsed value, or {@code null} for empty/invalid input
     */
    public static Double tryParseAmount(String text) {
        if (text == null) return null;

        String cleaned = toAsciiDigits(text)
                .replace("\u20AA", "");     // ₪

        StringBuilder compact = new StringBuilder(cleaned.length());
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (!Character.isWhitespace(c)) {
                compact.append(c);
            }
        }
        cleaned = compact.toString();
        if (cleaned.isEmpty()) return null;

        int sign = 1;
        if (cleaned.charAt(0) == '+' || cleaned.charAt(0) == '-') {
            if (cleaned.charAt(0) == '-') sign = -1;
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) return null;

        String normalized = normalizeAmountSeparators(cleaned);
        if (normalized == null) return null;

        try {
            return sign * Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Converts a sign-less amount to a plain {@code digits[.digits]} string,
     * applying the separator rules documented on {@link #tryParseAmount(String)}.
     *
     * @return the normalized number, or {@code null} when the separators or
     *         characters are invalid
     */
    private static String normalizeAmountSeparators(String value) {
        int lastDot = value.lastIndexOf('.');
        int lastComma = value.lastIndexOf(',');
        String normalized;

        if (lastDot < 0 && lastComma < 0) {
            return value.matches("[0-9]+") ? value : null;
        }

        if (lastDot >= 0 && lastComma >= 0) {
            // The last separator wins; every earlier separator is grouping.
            boolean dotIsDecimal = lastDot > lastComma;
            String integerPart = value.substring(0, dotIsDecimal ? lastDot : lastComma);
            String fractionPart = value.substring((dotIsDecimal ? lastDot : lastComma) + 1);
            if (fractionPart.isEmpty() || !fractionPart.matches("[0-9]+")) {
                return null;
            }
            char grouping = dotIsDecimal ? ',' : '.';
            integerPart = removeSeparator(integerPart, grouping);
            if (!integerPart.matches("[0-9]*")) {
                return null;
            }
            if (integerPart.isEmpty()) integerPart = "0";
            normalized = integerPart + "." + fractionPart;
        } else if (lastComma >= 0) {
            // Commas only. A final group of 1-2 digits is a decimal comma; a
            // final group of exactly 3 digits makes every comma a thousands
            // separator. Any other shape is ambiguous and therefore invalid.
            String[] groups = value.split(",", -1);
            String lastGroup = groups[groups.length - 1];
            if (lastGroup.isEmpty() || !lastGroup.matches("[0-9]+")) {
                return null;
            }
            if (lastGroup.length() == 3 && isThousandsGrouping(groups)) {
                normalized = value.replace(",", "");
            } else if (groups.length == 2
                    && (lastGroup.length() == 1 || lastGroup.length() == 2)
                    && groups[0].matches("[0-9]*")) {
                String integerPart = groups[0].isEmpty() ? "0" : groups[0];
                normalized = integerPart + "." + lastGroup;
            } else {
                return null;
            }
        } else {
            // Dots only: a single dot is the decimal separator. Multiple dots
            // are not a supported thousands form ('.' is decimal-only).
            if (lastDot != value.indexOf('.')) {
                return null;
            }
            String integerPart = value.substring(0, lastDot);
            String fractionPart = value.substring(lastDot + 1);
            if (fractionPart.isEmpty() || !fractionPart.matches("[0-9]+")) {
                return null;
            }
            if (!integerPart.matches("[0-9]*")) {
                return null;
            }
            if (integerPart.isEmpty()) integerPart = "0";
            normalized = integerPart + "." + fractionPart;
        }

        return normalized.matches("[0-9]+(\\.[0-9]+)?") ? normalized : null;
    }

    /** Removes every occurrence of {@code separator} from {@code value}. */
    private static String removeSeparator(String value, char separator) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != separator) sb.append(c);
        }
        return sb.toString();
    }

    /**
     * True when every comma-separated group after the first has exactly three
     * digits and the first group is a non-empty digit group.
     */
    private static boolean isThousandsGrouping(String[] groups) {
        if (groups.length < 2 || groups[0].isEmpty() || !groups[0].matches("[0-9]+")) {
            return false;
        }
        for (int i = 1; i < groups.length; i++) {
            if (!groups[i].matches("[0-9]{3}")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses a monetary amount typed by the user. This keeps the historical
     * signature for display/legacy callers: invalid or empty input becomes 0.0.
     * Valid input follows the same strict separator rules as
     * {@link #tryParseAmount(String)}.
     */
    public static double parseAmount(String text) {
        Double parsed = tryParseAmount(text);
        return parsed != null ? parsed : 0.0;
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
