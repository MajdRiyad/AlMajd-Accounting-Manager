package com.almajd.accounting.util;

import java.text.DecimalFormat;

public class NumberUtils {

    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##0.00");

    public static String formatCurrency(double amount) {
        return CURRENCY_FORMAT.format(amount);
    }

    public static double parseAmount(String text) {
        if (text == null || text.trim().isEmpty()) return 0.0;
        try {
            return Double.parseDouble(text.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
