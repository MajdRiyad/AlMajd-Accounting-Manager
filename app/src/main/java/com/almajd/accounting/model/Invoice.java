package com.almajd.accounting.model;

import java.util.ArrayList;
import java.util.List;

public class Invoice {

    private long id;
    private String storeName;
    private String invoiceDate;
    private double totalAmount;
    private double paidAmount;
    private double remaining;
    private String notes;
    private String createdAt;
    private transient List<InvoiceItem> items;

    public Invoice() {
        this.items = new ArrayList<>();
    }

    public Invoice(long id, String storeName, String invoiceDate, double totalAmount,
                   double paidAmount, double remaining, String notes, String createdAt) {
        this.id = id;
        this.storeName = storeName;
        this.invoiceDate = invoiceDate;
        this.totalAmount = totalAmount;
        this.paidAmount = paidAmount;
        this.remaining = remaining;
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
