package com.almajd.accounting.model;

public class Payment {

    private long id;
    private String storeName;
    private double amount;
    private String paymentDate;
    private String notes;
    private Long invoiceId;
    private String createdAt;

    public Payment() {
    }

    public Payment(long id, String storeName, double amount, String paymentDate,
                   String notes, Long invoiceId, String createdAt) {
        this.id = id;
        this.storeName = storeName;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.notes = notes;
        this.invoiceId = invoiceId;
        this.createdAt = createdAt;
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

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(String paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
