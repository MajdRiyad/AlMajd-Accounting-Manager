package com.almajd.accounting.model;

public class InvoiceItem {

    private long id;
    private long invoiceId;
    private String itemName;
    private int quantity;
    private double unitPrice;
    private double subtotal;

    /**
     * Raw quantity/price text from the form. Kept in memory only so the form can
     * distinguish a blank field from invalid text (for example "abc") without
     * treating both as zero. Not persisted.
     */
    private String quantityText;
    private String unitPriceText;

    public InvoiceItem() {
    }

    public InvoiceItem(long id, long invoiceId, String itemName, int quantity,
                       double unitPrice, double subtotal) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public double getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(double subtotal) {
        this.subtotal = subtotal;
    }

    public String getQuantityText() {
        return quantityText;
    }

    public void setQuantityText(String quantityText) {
        this.quantityText = quantityText;
    }

    public String getUnitPriceText() {
        return unitPriceText;
    }

    public void setUnitPriceText(String unitPriceText) {
        this.unitPriceText = unitPriceText;
    }
}
