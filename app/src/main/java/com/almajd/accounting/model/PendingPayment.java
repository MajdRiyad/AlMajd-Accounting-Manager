package com.almajd.accounting.model;

import com.almajd.accounting.util.NumberUtils;

/**
 * An in-memory payment row on the order edit screen.
 * <p>
 * Nothing is written to the payments table until the order is saved. Existing
 * rows keep their id so they can be updated or deleted; new rows have
 * {@code paymentId == 0}. The amount is stored as the user's text so an empty
 * new row can be ignored and an empty existing row can be rejected.
 * <p>
 * Pure Java (no Android APIs) so the totals helper is unit-testable.
 */
public class PendingPayment {

    private long paymentId;
    private String amountText;
    private String paymentDate;
    private boolean deleted;
    private boolean isNew;

    public PendingPayment() {
    }

    public PendingPayment(long paymentId, String amountText, String paymentDate,
                          boolean deleted, boolean isNew) {
        this.paymentId = paymentId;
        this.amountText = amountText;
        this.paymentDate = paymentDate;
        this.deleted = deleted;
        this.isNew = isNew;
    }

    /** Creates an editable copy (used for the save snapshot). */
    public PendingPayment copy() {
        return new PendingPayment(paymentId, amountText, paymentDate, deleted, isNew);
    }

    public long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(long paymentId) {
        this.paymentId = paymentId;
    }

    public String getAmountText() {
        return amountText;
    }

    public void setAmountText(String amountText) {
        this.amountText = amountText;
    }

    public String getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(String paymentDate) {
        this.paymentDate = paymentDate;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    /** @return true when the amount field contains no non-whitespace text. */
    public boolean hasEmptyAmount() {
        return amountText == null || amountText.trim().isEmpty();
    }

    /** @return the parsed amount rounded to two decimals (0 when invalid/empty). */
    public double getAmount() {
        return NumberUtils.round2(NumberUtils.parseAmount(amountText));
    }
}
