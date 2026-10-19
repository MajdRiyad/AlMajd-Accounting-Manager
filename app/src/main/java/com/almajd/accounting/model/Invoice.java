package com.almajd.accounting.model;

import com.almajd.accounting.util.NumberUtils;

import java.util.ArrayList;
import java.util.List;

public class Invoice {

    private long id;
    private String storeName;
    private String invoiceDate;

    /** Stored net amount: the order subtotal after the order-level discount. */
    private double totalAmount;

    /**
     * Stored fixed discount amount for the whole order (never a percentage).
     * The line-item subtotal is derived as {@code totalAmount + discount}.
     */
    private double discount;

    /**
     * Derived value -- NOT persisted. It is the <b>effective</b> paid amount:
     * the payments linked to this invoice (see {@link #paidDirect}) plus the part
     * of the customer's on-account credit that was allocated to it (see
     * {@link #allocatedFromCredit}).
     */
    private double paidAmount;

    /**
     * Derived value -- NOT persisted. Effective remaining =
     * max(totalAmount - paidAmount, 0) using the <b>effective</b> paid amount.
     */
    private double remaining;

    /** Derived status computed from the effective paid amount vs the total. */
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    /**
     * Derived value -- NOT persisted. The raw sum of payments directly linked to
     * this invoice ({@code invoice_view.paid_amount}), before any on-account
     * credit is allocated.
     */
    private double paidDirect;

    /**
     * Derived value -- NOT persisted. Portion of this invoice covered by the
     * customer's on-account payments (payments with {@code invoice_id IS NULL}).
     * Always 0 unless the customer had a surplus and this invoice was one of the
     * oldest still-unpaid ones. See {@code CreditAllocator}.
     */
    private double allocatedFromCredit;

    private String notes;
    private String createdAt;
    private transient List<InvoiceItem> items;

    public Invoice() {
        this.items = new ArrayList<>();
    }

    public Invoice(long id, String storeName, String invoiceDate, double totalAmount,
                   String notes, String createdAt) {
        this.id = id;
        this.storeName = storeName;
        this.invoiceDate = invoiceDate;
        this.totalAmount = totalAmount;
        this.notes = notes;
        this.createdAt = createdAt;
        this.items = new ArrayList<>();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(String invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public double getDiscount() {
        return discount;
    }

    public void setDiscount(double discount) {
        this.discount = discount;
    }

    /**
     * Derived value -- NOT persisted: the sum of the line items before the
     * order-level discount. {@code invoices.total_amount} remains the net amount,
     * so this is simply {@code totalAmount + discount}.
     */
    public double getSubtotal() {
        return NumberUtils.round2(totalAmount + discount);
    }

    public double getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(double paidAmount) {
        this.paidAmount = paidAmount;
    }

    public double getRemaining() {
        return remaining;
    }

    public void setRemaining(double remaining) {
        this.remaining = remaining;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status != null ? status : InvoiceStatus.UNPAID;
    }

    /**
     * @return the raw sum of payments linked to this invoice, ignoring any
     *         on-account credit allocated to it
     */
    public double getPaidDirect() {
        return paidDirect;
    }

    public void setPaidDirect(double paidDirect) {
        this.paidDirect = paidDirect;
    }

    /**
     * @return the amount of this invoice covered by the customer's on-account
     *         credit (0 when none was allocated); never negative
     */
    public double getAllocatedFromCredit() {
        return allocatedFromCredit;
    }

    public void setAllocatedFromCredit(double allocatedFromCredit) {
        this.allocatedFromCredit = allocatedFromCredit;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public List<InvoiceItem> getItems() {
        if (items == null) {
            items = new ArrayList<>();
        }
        return items;
    }

    public void setItems(List<InvoiceItem> items) {
        this.items = items;
    }
}
