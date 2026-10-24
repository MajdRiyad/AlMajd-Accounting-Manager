package com.almajd.accounting.util;

/**
 * Pure (Android-free) order totals calculation.
 * <p>
 * The stored invoice amount is always the <b>net</b> amount after the order
 * discount. The subtotal is the sum of the line items, so:
 * <pre>
 *   subtotal = net + discount
 *   net      = round2(subtotal - discount)
 *   remaining = max(round2(net - paid), 0)
 * </pre>
 * A discount is one fixed currency amount (never a percentage). It is valid only
 * when it is zero or positive and does not exceed the subtotal.
 */
public final class InvoiceTotals {

    public final double subtotal;
    public final double discount;
    public final double netTotal;
    public final double paid;
    public final double remaining;

    private InvoiceTotals(double subtotal, double discount, double netTotal,
                          double paid, double remaining) {
        this.subtotal = subtotal;
        this.discount = discount;
        this.netTotal = netTotal;
        this.paid = paid;
        this.remaining = remaining;
    }

    /**
     * Calculates all displayed totals from the line-item subtotal, the fixed
     * discount and the paid amount. Every monetary value is rounded to two
     * decimals using {@link NumberUtils#round2(double)}.
     */
    public static InvoiceTotals calculate(double subtotal, double discount, double paid) {
        double roundedSubtotal = NumberUtils.round2(subtotal);
        double roundedDiscount = NumberUtils.round2(discount);
        double roundedPaid = NumberUtils.round2(paid);
        double net = NumberUtils.round2(roundedSubtotal - roundedDiscount);
        double remaining = NumberUtils.round2(net - roundedPaid);
        if (remaining < 0) {
            remaining = 0;
        }
        return new InvoiceTotals(roundedSubtotal, roundedDiscount, net,
                roundedPaid, remaining);
    }

    /**
     * @return true when the discount is a fixed amount between zero and the
     *         rounded subtotal (inclusive)
     */
    public static boolean isValidDiscount(double subtotal, double discount) {
        if (Double.isNaN(discount) || Double.isInfinite(discount)) {
            return false;
        }
        double roundedSubtotal = NumberUtils.round2(subtotal);
        double roundedDiscount = NumberUtils.round2(discount);
        return roundedDiscount >= 0 && roundedDiscount <= roundedSubtotal;
    }
}
