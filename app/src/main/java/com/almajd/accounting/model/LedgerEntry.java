package com.almajd.accounting.model;

public class LedgerEntry {

    private String storeName;
    private String ownerName;
    private double grandTotalSales;
    private double totalPaid;
    private double balance;
    private String lastPaymentDate;

    public LedgerEntry() {
    }

    public LedgerEntry(String storeName, String ownerName, double grandTotalSales,
                       double totalPaid, double balance, String lastPaymentDate) {
        this.storeName = storeName;
        this.ownerName = ownerName;
        this.grandTotalSales = grandTotalSales;
        this.totalPaid = totalPaid;
        this.balance = balance;
        this.lastPaymentDate = lastPaymentDate;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public double getGrandTotalSales() {
        return grandTotalSales;
    }

    public void setGrandTotalSales(double grandTotalSales) {
        this.grandTotalSales = grandTotalSales;
    }

    public double getTotalPaid() {
        return totalPaid;
    }

    public void setTotalPaid(double totalPaid) {
        this.totalPaid = totalPaid;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public String getLastPaymentDate() {
        return lastPaymentDate;
    }

    public void setLastPaymentDate(String lastPaymentDate) {
        this.lastPaymentDate = lastPaymentDate;
    }
}
