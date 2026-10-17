package com.almajd.accounting.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

/**
 * Verifies that amounts are always formatted/parsed with ASCII digits even when
 * the default locale is Arabic (the app forces Arabic in {@code App}).
 */
public class NumberUtilsTest {

    private Locale previousLocale;

    @Before
    public void forceArabicLocale() {
        previousLocale = Locale.getDefault();
        // Reproduce the app's forced Arabic locale. Without the Locale.US symbols
        // pinned in NumberUtils, DecimalFormat would emit Arabic-Indic digits.
        Locale.setDefault(new Locale("ar"));
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(previousLocale);
    }

    @Test
    public void formatCurrencyUsesAsciiDigitsUnderArabicLocale() {
        assertEquals("1,234.50 \u20AA", NumberUtils.formatCurrency(1234.5));
        assertEquals("0.00 \u20AA", NumberUtils.formatCurrency(0));
    }

    @Test
    public void formatAmountHasNoSymbol() {
        assertEquals("12,345.67", NumberUtils.formatAmount(12345.67));
    }

    @Test
    public void formattedOutputContainsOnlyAsciiDigits() {
        String s = NumberUtils.formatCurrency(9876543.21);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean allowed = (c >= '0' && c <= '9')
                    || c == ',' || c == '.' || c == ' ' || c == '\u20AA';
            assertTrue("unexpected character: " + c, allowed);
        }
    }

    @Test
    public void parseAmountAcceptsAsciiGroupingAndSymbol() {
        assertEquals(1234.5, NumberUtils.parseAmount("1,234.50 \u20AA"), 1e-9);
    }

    @Test
    public void parseAmountAcceptsArabicIndicDigits() {
        // ١٢٣٤٫٥  ->  1234.5  (Arabic-Indic digits + Arabic decimal separator)
        assertEquals(1234.5,
                NumberUtils.parseAmount("\u0661\u0662\u0663\u0664\u066B\u0665"), 1e-9);
        // ١٥٠٠ -> 1500
        assertEquals(1500.0,
                NumberUtils.parseAmount("\u0661\u0665\u0660\u0660"), 1e-9);
    }

    @Test
    public void parseAmountAcceptsExtendedArabicIndicDigits() {
        // ۱۲۳ -> 123
        assertEquals(123.0, NumberUtils.parseAmount("\u06F1\u06F2\u06F3"), 1e-9);
    }

    @Test
    public void parseAmountHandlesEmptyAndGarbage() {
        assertEquals(0.0, NumberUtils.parseAmount(null), 1e-9);
        assertEquals(0.0, NumberUtils.parseAmount("   "), 1e-9);
        assertEquals(0.0, NumberUtils.parseAmount("abc"), 1e-9);
    }

    @Test
    public void toAsciiDigitsMapsBothDigitRanges() {
        assertEquals("0123456789", NumberUtils.toAsciiDigits(
                "\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669"));
        assertEquals("0123456789", NumberUtils.toAsciiDigits(
                "\u06F0\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6\u06F7\u06F8\u06F9"));
        assertEquals("1.5", NumberUtils.toAsciiDigits("1\u066B5"));
    }

    @Test
    public void parseIntAcceptsArabicIndicDigits() {
        assertEquals(25, NumberUtils.parseInt("\u0662\u0665"));
        assertEquals(0, NumberUtils.parseInt("\u0662.5"));
        assertEquals(0, NumberUtils.parseInt(null));
        assertEquals(0, NumberUtils.parseInt(""));
    }
}
