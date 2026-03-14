package com.almajd.accounting.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.InvoiceItem;

import java.util.ArrayList;
import java.util.List;

public class InvoiceDao {

    private final DatabaseHelper dbHelper;

    public InvoiceDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    /**
     * Insert an invoice with its items in a single transaction.
     * If paidAmount > 0, a linked payment is also created.
     *
     * @return the newly generated invoice id, or -1 on failure
     */
    public long insert(Invoice invoice) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long invoiceId = -1;
        db.beginTransaction();
        try {
            // Insert invoice header
            ContentValues headerValues = new ContentValues();
            headerValues.put(DatabaseHelper.COL_STORE_NAME, invoice.getStoreName());
            headerValues.put(DatabaseHelper.COL_INVOICE_DATE, invoice.getInvoiceDate());
            headerValues.put(DatabaseHelper.COL_TOTAL_AMOUNT, invoice.getTotalAmount());
            headerValues.put(DatabaseHelper.COL_PAID_AMOUNT, invoice.getPaidAmount());
            headerValues.put(DatabaseHelper.COL_REMAINING, invoice.getRemaining());
            headerValues.put(DatabaseHelper.COL_NOTES, invoice.getNotes());

            invoiceId = db.insert(DatabaseHelper.TABLE_INVOICES, null, headerValues);
            if (invoiceId == -1) {
                return -1;
            }

            // Insert all invoice items
            if (invoice.getItems() != null) {
                for (InvoiceItem item : invoice.getItems()) {
                    ContentValues itemValues = new ContentValues();
                    itemValues.put(DatabaseHelper.COL_INVOICE_ID, invoiceId);
                    itemValues.put(DatabaseHelper.COL_ITEM_NAME, item.getItemName());
                    itemValues.put(DatabaseHelper.COL_QUANTITY, item.getQuantity());
                    itemValues.put(DatabaseHelper.COL_UNIT_PRICE, item.getUnitPrice());
                    itemValues.put(DatabaseHelper.COL_SUBTOTAL, item.getSubtotal());
                    long itemId = db.insert(DatabaseHelper.TABLE_INVOICE_ITEMS, null, itemValues);
                    if (itemId == -1) {
                        return -1;
                    }
                }
            }

            // If paidAmount > 0, create a linked payment
            if (invoice.getPaidAmount() > 0) {
                ContentValues paymentValues = new ContentValues();
                paymentValues.put(DatabaseHelper.COL_STORE_NAME, invoice.getStoreName());
                paymentValues.put(DatabaseHelper.COL_AMOUNT, invoice.getPaidAmount());
                paymentValues.put(DatabaseHelper.COL_PAYMENT_DATE, invoice.getInvoiceDate());
                paymentValues.put(DatabaseHelper.COL_NOTES, "Payment linked to invoice #" + invoiceId);
                paymentValues.put(DatabaseHelper.COL_INVOICE_ID, invoiceId);
                long paymentId = db.insert(DatabaseHelper.TABLE_PAYMENTS, null, paymentValues);
                if (paymentId == -1) {
                    return -1;
                }
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return invoiceId;
    }

    /**
     * Update an invoice: delete old items and linked payments, then re-insert.
     */
    public boolean update(Invoice invoice) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        boolean success = false;
        db.beginTransaction();
        try {
            // Delete old invoice items
            db.delete(DatabaseHelper.TABLE_INVOICE_ITEMS,
                    DatabaseHelper.COL_INVOICE_ID + " = ?",
                    new String[]{String.valueOf(invoice.getId())});

            // Delete old linked payments
            db.delete(DatabaseHelper.TABLE_PAYMENTS,
                    DatabaseHelper.COL_INVOICE_ID + " = ?",
                    new String[]{String.valueOf(invoice.getId())});

            // Update invoice header
            ContentValues headerValues = new ContentValues();
            headerValues.put(DatabaseHelper.COL_STORE_NAME, invoice.getStoreName());
            headerValues.put(DatabaseHelper.COL_INVOICE_DATE, invoice.getInvoiceDate());
            headerValues.put(DatabaseHelper.COL_TOTAL_AMOUNT, invoice.getTotalAmount());
            headerValues.put(DatabaseHelper.COL_PAID_AMOUNT, invoice.getPaidAmount());
            headerValues.put(DatabaseHelper.COL_REMAINING, invoice.getRemaining());
            headerValues.put(DatabaseHelper.COL_NOTES, invoice.getNotes());

            int rows = db.update(DatabaseHelper.TABLE_INVOICES, headerValues,
                    DatabaseHelper.COL_ID + " = ?",
                    new String[]{String.valueOf(invoice.getId())});

            if (rows <= 0) {
                return false;
            }

            // Re-insert new items
            if (invoice.getItems() != null) {
                for (InvoiceItem item : invoice.getItems()) {
                    ContentValues itemValues = new ContentValues();
                    itemValues.put(DatabaseHelper.COL_INVOICE_ID, invoice.getId());
                    itemValues.put(DatabaseHelper.COL_ITEM_NAME, item.getItemName());
                    itemValues.put(DatabaseHelper.COL_QUANTITY, item.getQuantity());
                    itemValues.put(DatabaseHelper.COL_UNIT_PRICE, item.getUnitPrice());
                    itemValues.put(DatabaseHelper.COL_SUBTOTAL, item.getSubtotal());
                    long itemId = db.insert(DatabaseHelper.TABLE_INVOICE_ITEMS, null, itemValues);
                    if (itemId == -1) {
                        return false;
                    }
                }
            }

            // If paidAmount > 0, create a new linked payment
            if (invoice.getPaidAmount() > 0) {
                ContentValues paymentValues = new ContentValues();
                paymentValues.put(DatabaseHelper.COL_STORE_NAME, invoice.getStoreName());
                paymentValues.put(DatabaseHelper.COL_AMOUNT, invoice.getPaidAmount());
                paymentValues.put(DatabaseHelper.COL_PAYMENT_DATE, invoice.getInvoiceDate());
                paymentValues.put(DatabaseHelper.COL_NOTES, "Payment linked to invoice #" + invoice.getId());
                paymentValues.put(DatabaseHelper.COL_INVOICE_ID, invoice.getId());
                long paymentId = db.insert(DatabaseHelper.TABLE_PAYMENTS, null, paymentValues);
                if (paymentId == -1) {
                    return false;
                }
            }

            db.setTransactionSuccessful();
            success = true;
        } finally {
            db.endTransaction();
        }
        return success;
    }

    /**
     * Delete an invoice by id. Cascade handles items.
     * Also explicitly delete linked payments.
     */
    public boolean delete(long invoiceId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // Delete linked payments first
        db.delete(DatabaseHelper.TABLE_PAYMENTS,
                DatabaseHelper.COL_INVOICE_ID + " = ?",
                new String[]{String.valueOf(invoiceId)});
        int rows = db.delete(DatabaseHelper.TABLE_INVOICES,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(invoiceId)});
        return rows > 0;
    }

    /**
     * Get a single invoice by id, including its items.
     */
    public Invoice getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_INVOICES, null,
                    DatabaseHelper.COL_ID + " = ?",
                    new String[]{String.valueOf(id)}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                Invoice invoice = cursorToInvoice(cursor);
                invoice.setItems(getItemsForInvoice(invoice.getId()));
                return invoice;
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Get all invoices ordered by created_at descending.
     */
    public List<Invoice> getAll() {
        List<Invoice> invoices = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_INVOICES, null,
                    null, null, null, null,
                    DatabaseHelper.COL_CREATED_AT + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Invoice invoice = cursorToInvoice(cursor);
                    invoice.setItems(getItemsForInvoice(invoice.getId()));
                    invoices.add(invoice);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return invoices;
    }

    /**
     * Get all invoices for a specific store.
     */
    public List<Invoice> getByStoreName(String storeName) {
        List<Invoice> invoices = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_INVOICES, null,
                    DatabaseHelper.COL_STORE_NAME + " = ?",
                    new String[]{storeName}, null, null,
                    DatabaseHelper.COL_CREATED_AT + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Invoice invoice = cursorToInvoice(cursor);
                    invoice.setItems(getItemsForInvoice(invoice.getId()));
                    invoices.add(invoice);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return invoices;
    }

    /**
     * Get invoices within a date range (inclusive).
     */
    public List<Invoice> getByDateRange(String from, String to) {
        List<Invoice> invoices = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_INVOICES, null,
                    DatabaseHelper.COL_INVOICE_DATE + " >= ? AND " +
                            DatabaseHelper.COL_INVOICE_DATE + " <= ?",
                    new String[]{from, to}, null, null,
                    DatabaseHelper.COL_INVOICE_DATE + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Invoice invoice = cursorToInvoice(cursor);
                    invoice.setItems(getItemsForInvoice(invoice.getId()));
                    invoices.add(invoice);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return invoices;
    }

    /**
     * Get invoices for a specific store within a date range.
     */
    public List<Invoice> getByStoreAndDateRange(String store, String from, String to) {
        List<Invoice> invoices = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_INVOICES, null,
                    DatabaseHelper.COL_STORE_NAME + " = ? AND " +
                            DatabaseHelper.COL_INVOICE_DATE + " >= ? AND " +
                            DatabaseHelper.COL_INVOICE_DATE + " <= ?",
                    new String[]{store, from, to}, null, null,
                    DatabaseHelper.COL_INVOICE_DATE + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Invoice invoice = cursorToInvoice(cursor);
                    invoice.setItems(getItemsForInvoice(invoice.getId()));
                    invoices.add(invoice);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return invoices;
    }

    /**
     * Get the total sales amount across all invoices.
     */
    public double getTotalSales() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COL_TOTAL_AMOUNT + "), 0) FROM " +
                    DatabaseHelper.TABLE_INVOICES, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }
            return 0.0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Get the total sales amount for a specific store.
     */
    public double getTotalSalesByStore(String store) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COL_TOTAL_AMOUNT + "), 0) FROM " +
                    DatabaseHelper.TABLE_INVOICES + " WHERE " + DatabaseHelper.COL_STORE_NAME + " = ?",
                    new String[]{store});
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }
            return 0.0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Get the total count of invoices.
     */
    public int getCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_INVOICES, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Get the most recent invoices, limited to the given count.
     */
    public List<Invoice> getRecent(int limit) {
        List<Invoice> invoices = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_INVOICES, null,
                    null, null, null, null,
                    DatabaseHelper.COL_CREATED_AT + " DESC",
                    String.valueOf(limit));
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    invoices.add(cursorToInvoice(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return invoices;
    }

    /**
     * Convert a cursor row to an Invoice object.
     */
    private Invoice cursorToInvoice(Cursor cursor) {
        Invoice invoice = new Invoice();
        invoice.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID)));
        invoice.setStoreName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STORE_NAME)));
        invoice.setInvoiceDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_INVOICE_DATE)));
        invoice.setTotalAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_AMOUNT)));
        invoice.setPaidAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PAID_AMOUNT)));
        invoice.setRemaining(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REMAINING)));
        invoice.setNotes(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOTES)));
        invoice.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CREATED_AT)));
        return invoice;
    }

    /**
     * Convert a cursor row to an InvoiceItem object.
     */
    private InvoiceItem cursorToItem(Cursor cursor) {
        InvoiceItem item = new InvoiceItem();
        item.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID)));
        item.setInvoiceId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_INVOICE_ID)));
        item.setItemName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ITEM_NAME)));
        item.setQuantity(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_QUANTITY)));
        item.setUnitPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT_PRICE)));
        item.setSubtotal(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SUBTOTAL)));
        return item;
    }

    /**
     * Get all items for a given invoice id.
     */
    private List<InvoiceItem> getItemsForInvoice(long invoiceId) {
        List<InvoiceItem> items = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_INVOICE_ITEMS, null,
                    DatabaseHelper.COL_INVOICE_ID + " = ?",
                    new String[]{String.valueOf(invoiceId)}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    items.add(cursorToItem(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return items;
    }
}
