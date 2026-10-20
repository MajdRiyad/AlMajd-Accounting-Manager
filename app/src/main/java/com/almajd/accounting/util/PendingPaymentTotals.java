package com.almajd.accounting.util;

import com.almajd.accounting.model.PendingPayment;

import java.util.List;

/**
 * Pure (Android-free) paid / remaining / overpaid calculation for the pending
 * payment rows on the order edit screen.
 * <p>
 * Deleted rows and empty new rows are ignored. Existing rows with an empty or
 * non-positive amount are also ignored here; the form validates them separately
 * so they cannot be saved silently.
 */
public final class PendingPaymentTotals {

    public final double paid;
    public final double remaining;
    public final double overpaidBy;

    private PendingPaymentTotals(double paid, double remaining, double overpaidBy) {
        this.paid = paid;
        this.remaining = remaining;
        this.overpaidBy = overpaidBy;
    }

    /**
     * @param netTotal net order total (after discount)
     * @param rows     pending rows; may be null
     */
    public static PendingPaymentTotals calculate(double netTotal, List<PendingPayment> rows) {
        double roundedNet = NumberUtils.round2(netTotal);
        double paid = 0.0;
        if (rows != null) {
            for (PendingPayment row : rows) {
                if (row == null || row.isDeleted()) {
                    continue;
                }
                if (row.isNew() && row.hasEmptyAmount()) {
                    continue;
                }
                double amount = row.getAmount();
                if (amount > 0) {
                    paid += amount;
                }
            }
        }
        paid = NumberUtils.round2(paid);
        double remaining = NumberUtils.round2(roundedNet - paid);
        if (remaining < 0) {
            remaining = 0;
        }
        double overpaidBy = NumberUtils.round2(paid - roundedNet);
        if (overpaidBy < 0) {
            overpaidBy = 0;
        }
        return new PendingPaymentTotals(paid, remaining, overpaidBy);
    }
}
