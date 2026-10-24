package com.almajd.accounting.util;

import android.content.Context;

import com.almajd.accounting.R;

/**
 * Formats a customer balance for display so a negative number is never shown
 * on its own:
 * <ul>
 *   <li>positive  -> "owes us" label with the amount</li>
 *   <li>negative  -> "credit (له عندنا)" label with the absolute amount</li>
 *   <li>zero      -> "settled"</li>
 * </ul>
 */
public class BalanceUtils {

    private BalanceUtils() {
    }

    /** Human readable balance, e.g. "عليه ‎300.00 ₪" / "له عندنا ‎200.00 ₪" / "مسدد". */
    public static String format(Context context, double balance) {
        double rounded = NumberUtils.round2(balance);
        if (rounded > 0) {
            return context.getString(R.string.balance_owed, NumberUtils.formatCurrency(rounded));
        }
        if (rounded < 0) {
            return context.getString(R.string.balance_credit,
                    NumberUtils.formatCurrency(Math.abs(rounded)));
        }
        return context.getString(R.string.balance_settled);
    }

    public static boolean isCredit(double balance) {
        return NumberUtils.round2(balance) < 0;
    }

    public static boolean isSettled(double balance) {
        return NumberUtils.round2(balance) == 0;
    }
}
