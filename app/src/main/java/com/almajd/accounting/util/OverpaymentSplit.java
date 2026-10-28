package com.almajd.accounting.util;

import com.almajd.accounting.model.PendingPayment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure (Android-free) paid / remaining / overpayment calculation and carve-out
 * plan for the pending direct-payment rows on the order edit screen.
 * <p>
 * The customer's on-account credit that was allocated to this order is
 * <b>effective money</b>: it is included in {@link #paid} and reduces
 * {@link #remaining}. It is never carved or rewritten. When the direct rows
 * plus the allocated credit exceed the net total, only direct rows are reduced;
 * the excess is planned as a separate on-account payment so the linked rows sum
 * to exactly {@code netTotal - allocatedFromCredit}.
 * <p>
 * <b>Carve-out rule:</b> rows are reduced from the <b>last</b> pending row
 * backwards (the newest row in the list). Earlier rows are left intact. A row
 * that reaches zero is marked deleted by {@link #applyToRows()}: an existing
 * row is deleted on save and a new row is simply not inserted. No more than the
 * excess is ever removed.
 */
public final class OverpaymentSplit {

    /** Sum of the non-deleted, non-blank direct rows (rounded to 2 decimals). */
    public final double directPaid;
    /** Credit allocated to this order from the customer's on-account balance. */
    public final double allocatedFromCredit;
    /** Effective paid = direct payments + allocated credit. */
    public final double paid;
    /** Net order total after discount. */
    public final double netTotal;
    /** max(netTotal - paid, 0), rounded. */
    public final double remaining;
    /** max(paid - netTotal, 0), rounded. Used for the warning line. */
    public final double overpaidBy;
    /**
     * The part of {@link #overpaidBy} that can actually be carved from direct
     * rows. This is the amount moved to an on-account payment; it is never more
     * than {@link #directPaid}, so credit is never touched and no money is
     * invented.
     */
    public final double excess;
    /** Sum of the direct rows after applying the carve-outs. */
    public final double linkedPaidAfter;
    /** Rows to reduce, last row first. Empty when there is no excess. */
    public final List<Carve> carves;

    private OverpaymentSplit(double directPaid, double allocatedFromCredit,
                             double paid, double netTotal, double remaining,
                             double overpaidBy, double excess, double linkedPaidAfter,
                             List<Carve> carves) {
        this.directPaid = directPaid;
        this.allocatedFromCredit = allocatedFromCredit;
        this.paid = paid;
        this.netTotal = netTotal;
        this.remaining = remaining;
        this.overpaidBy = overpaidBy;
        this.excess = excess;
        this.linkedPaidAfter = linkedPaidAfter;
        this.carves = carves;
    }

    /** One row's planned reduction. */
    public static final class Carve {
        public final PendingPayment row;
        public final double originalAmount;
        /** Amount that remains linked to the order (0 means the row disappears). */
        public final double linkedAmount;
        /** Amount removed from this row (always positive). */
        public final double carvedAmount;

        private Carve(PendingPayment row, double originalAmount,
                      double linkedAmount, double carvedAmount) {
            this.row = row;
            this.originalAmount = originalAmount;
            this.linkedAmount = linkedAmount;
            this.carvedAmount = carvedAmount;
        }
    }

    /**
     * Calculates the effective totals and the carve-out plan.
     *
     * @param netTotal             net order total (after discount); negatives clamp to 0
     * @param allocatedFromCredit  customer credit allocated to this order; negatives clamp to 0
     * @param rows                 pending direct rows; may be null
     */
    public static OverpaymentSplit calculate(double netTotal, double allocatedFromCredit,
                                             List<PendingPayment> rows) {
        double roundedNet = Math.max(NumberUtils.round2(netTotal), 0.0);
        double credit = Math.max(NumberUtils.round2(allocatedFromCredit), 0.0);

        // Active rows, in list order (which is oldest first, new rows last).
        List<PendingPayment> activeRows = new ArrayList<>();
        List<Double> activeAmounts = new ArrayList<>();
        double directPaid = 0.0;
        if (rows != null) {
            for (PendingPayment row : rows) {
                if (row == null || row.isDeleted()) {
                    continue;
                }
                if (row.isNew() && row.hasEmptyAmount()) {
                    continue;
                }
                double amount = NumberUtils.round2(row.getAmount());
                if (amount <= 0) {
                    continue;
                }
                activeRows.add(row);
                activeAmounts.add(amount);
                directPaid += amount;
            }
        }
        directPaid = NumberUtils.round2(directPaid);

        double paid = NumberUtils.round2(directPaid + credit);
        double remaining = NumberUtils.round2(roundedNet - paid);
        if (remaining < 0) {
            remaining = 0;
        }
        double overpaidBy = NumberUtils.round2(paid - roundedNet);
        if (overpaidBy < 0) {
            overpaidBy = 0;
        }
        double excess = NumberUtils.round2(Math.min(overpaidBy, directPaid));
        if (excess < 0) {
            excess = 0;
        }

        // Carve from the last active row backwards. This is deliberately after
        // the forward sum so the newest row is reduced first.
        List<Carve> carves = new ArrayList<>();
        double left = excess;
        for (int i = activeRows.size() - 1; i >= 0 && left > 0; i--) {
            double amount = activeAmounts.get(i);
            double carved = NumberUtils.round2(Math.min(amount, left));
            if (carved <= 0) {
                continue;
            }
            double linkedAmount = NumberUtils.round2(amount - carved);
            if (linkedAmount < 0) {
                linkedAmount = 0;
            }
            carves.add(new Carve(activeRows.get(i), amount, linkedAmount, carved));
            left = NumberUtils.round2(left - carved);
        }

        double linkedPaidAfter = NumberUtils.round2(directPaid - excess);
        if (linkedPaidAfter < 0) {
            linkedPaidAfter = 0;
        }

        return new OverpaymentSplit(directPaid, credit, paid, roundedNet, remaining,
                overpaidBy, excess, linkedPaidAfter, Collections.unmodifiableList(carves));
    }

    /**
     * Applies the planned carve-outs to the supplied rows. Use this on the save
     * snapshot, never on the live editing list, so cancelling a dialog leaves
     * the form untouched.
     */
    public void applyToRows() {
        for (Carve carve : carves) {
            if (carve.linkedAmount <= 0) {
                // Existing rows are deleted on save; new rows are not inserted.
                carve.row.setDeleted(true);
            } else {
                carve.row.setAmountText(String.format(Locale.US, "%.2f", carve.linkedAmount));
            }
        }
    }
}
