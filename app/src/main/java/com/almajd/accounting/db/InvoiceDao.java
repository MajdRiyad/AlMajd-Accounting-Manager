package com.almajd.accounting.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.almajd.accounting.R;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.InvoiceItem;
import com.almajd.accounting.model.InvoiceStatus;
import com.almajd.accounting.util.ChunkUtils;
import com.almajd.accounting.util.NumberUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InvoiceDao {

    private final Context context;
    private final DatabaseHelper dbHelper;

    public InvoiceDao(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    /**
     * Insert an invoice with its items in a single transaction.
     * If {@code paidNow > 0}, a normal payment linked to the new invoice is also
     * created (same date as the invoice). The invoice stores no paid/remaining
     * columns -- those are derived by invoice_view.
     *
     * @return the newly generated invoice id, or -1 on failure
     */
    public long insert(Invoice invoice, double paidNow) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long invoiceId = -1;
        db.beginTransaction();
        try {
            // Insert invoice header (no paid_amount / remaining columns)
            ContentValues headerValues = new ContentValues();
            headerValues.put(DatabaseHelper.COL_STORE_NAME, invoice.getStoreName());
            headerValues.put(DatabaseHelper.COL_INVOICE_DATE, invoice.getInvoiceDate());
            headerValues.put(DatabaseHelper.COL_TOTAL_AMOUNT, NumberUtils.round2(invoice.getTotalAmount()));
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
                    itemValues.put(DatabaseHelper.COL_UNIT_PRICE, NumberUtils.round2(item.getUnitPrice()));
                    itemValues.put(DatabaseHelper.COL_SUBTOTAL, NumberUtils.round2(item.getSubtotal()));
                    long itemId = db.insert(DatabaseHelper.TABLE_INVOICE_ITEMS, null, itemValues);
                    if (itemId == -1) {
                        return -1;
                    }
                }
            }

            // Paid-now: create a normal linked payment (create path only)
            double roundedPaid = NumberUtils.round2(paidNow);
            if (roundedPaid > 0) {
                ContentValues paymentValues = new ContentValues();
                paymentValues.put(DatabaseHelper.COL_STORE_NAME, invoice.getStoreName());
                paymentValues.put(DatabaseHelper.COL_AMOUNT, roundedPaid);
                paymentValues.put(DatabaseHelper.COL_PAYMENT_DATE, invoice.getInvoiceDate());
                paymentValues.put(DatabaseHelper.COL_NOTES,
                        context.getString(R.string.payment_linked_note, invoiceId));
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
     * Update an invoice header and its items. Existing payments are NOT touched
     * (the invoice form must never recreate or delete payments).
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

            // Update invoice header (no paid_amount / remaining columns)
            ContentValues headerValues = new ContentValues();
            headerValues.put(DatabaseHelper.COL_STORE_NAME, invoice.getStoreName());
            headerValues.put(DatabaseHelper.COL_INVOICE_DATE, invoice.getInvoiceDate());
            headerValues.put(DatabaseHelper.COL_TOTAL_AMOUNT, NumberUtils.round2(invoice.getTotalAmount()));
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
                    itemValues.put(DatabaseHelper.COL_UNIT_PRICE, NumberUtils.round2(item.getUnitPrice()));
                    itemValues.put(DatabaseHelper.COL_SUBTOTAL, NumberUtils.round2(item.getSubtotal()));
                    long itemId = db.insert(DatabaseHelper.TABLE_INVOICE_ITEMS, null, itemValues);
                    if (itemId == -1) {
                        return false;
                    }
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
     * Delete an invoice by id. Items cascade. Linked payments are NOT deleted:
     * the payments.invoice_id foreign key is ON DELETE SET NULL, so the money
     * received stays in the customer's balance as an on-account payment.
     */
    public boolean delete(long invoiceId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
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
            cursor = db.query(DatabaseHelper.VIEW_INVOICE, null,
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
     * Maximum number of invoice ids per {@code IN (...)} clause. Keeps the number
     * of SQL host parameters well below SQLite's default limit (999).
     */
    private static final int ITEM_ID_CHUNK_SIZE = 500;

    /**
     * Get all invoices ordered by created_at descending, including their items.
     */
    public List<Invoice> getAll() {
        return getAll(true);
    }

    /**
     * Get all invoices ordered by created_at descending.
     *
     * @param loadItems when false, line items are not loaded at all (1 query);
     *                  when true they are loaded with a single batched query per
     *                  chunk of ids (constant number of queries, never N+1).
     */
    public List<Invoice> getAll(boolean loadItems) {
        return queryInvoices(null, null, DatabaseHelper.COL_CREATED_AT + " DESC", loadItems);
    }

    /**
     * Get all invoices for a specific store, including their items.
     */
    public List<Invoice> getByStoreName(String storeName) {
        return getByStoreName(storeName, true);
    }

    /**
     * Get all invoices for a specific store.
     *
     * @param loadItems see {@link #getAll(boolean)}
     */
    public List<Invoice> getByStoreName(String storeName, boolean loadItems) {
        return queryInvoices(DatabaseHelper.COL_STORE_NAME + " = ?",
                new String[]{storeName},
                DatabaseHelper.COL_CREATED_AT + " DESC",
                loadItems);
    }

    /**
     * Get invoices within a date range (inclusive), including their items.
     */
    public List<Invoice> getByDateRange(String from, String to) {
        return getByDateRange(from, to, true);
    }

    /**
     * Get invoices within a date range (inclusive).
     *
     * @param loadItems see {@link #getAll(boolean)}
     */
    public List<Invoice> getByDateRange(String from, String to, boolean loadItems) {
        return queryInvoices(
                DatabaseHelper.COL_INVOICE_DATE + " >= ? AND " + DatabaseHelper.COL_INVOICE_DATE + " <= ?",
                new String[]{from, to},
                DatabaseHelper.COL_INVOICE_DATE + " DESC",
                loadItems);
    }

    /**
     * Get invoices for a specific store within a date range, including items.
     */
    public List<Invoice> getByStoreAndDateRange(String store, String from, String to) {
        return getByStoreAndDateRange(store, from, to, true);
    }

    /**
     * Get invoices for a specific store within a date range.
     *
     * @param loadItems see {@link #getAll(boolean)}
     */
    public List<Invoice> getByStoreAndDateRange(String store, String from, String to, boolean loadItems) {
        return queryInvoices(
                DatabaseHelper.COL_STORE_NAME + " = ? AND " +
                        DatabaseHelper.COL_INVOICE_DATE + " >= ? AND " +
                        DatabaseHelper.COL_INVOICE_DATE + " <= ?",
                new String[]{store, from, to},
                DatabaseHelper.COL_INVOICE_DATE + " DESC",
                loadItems);
    }

    /**
     * Get a store's invoices that still have a remaining amount greater than 0.
     * Used by the payment form's invoice selector. Items are not loaded.
     */
    public List<Invoice> getOpenInvoicesByStore(String storeName) {
        return queryInvoices(
                DatabaseHelper.COL_STORE_NAME + " = ? AND " + DatabaseHelper.COL_REMAINING + " > 0",
                new String[]{storeName},
                DatabaseHelper.COL_CREATED_AT + " DESC",
                false);
    }

    /**
     * Shared query helper: runs a single aggregated query against invoice_view
     * (paid/remaining/status are computed by the view, never N+1) and, when
     * requested, attaches all line items with a constant number of extra queries.
     */
    private List<Invoice> queryInvoices(String selection, String[] args, String orderBy, boolean loadItems) {
        List<Invoice> invoices = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.VIEW_INVOICE, null, selection, args, null, null, orderBy);
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
        if (loadItems) {
            attachItems(db, invoices);
        }
        return invoices;
    }

    /**
     * Loads the line items for every invoice in {@code invoices} using ONE
     * query per chunk of ids ({@code invoice_id IN (...)}), then groups them in
     * memory. This replaces the previous one-query-per-invoice N+1 pattern:
     * the number of queries is constant regardless of how many invoices there are.
     */
    private void attachItems(SQLiteDatabase db, List<Invoice> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return;
        }

        List<Long> ids = new ArrayList<>(invoices.size());
        for (Invoice invoice : invoices) {
            ids.add(invoice.getId());
        }

        List<InvoiceItem> allItems = new ArrayList<>();
        for (List<Long> chunk : ChunkUtils.chunk(ids, ITEM_ID_CHUNK_SIZE)) {
            allItems.addAll(queryItemsForIds(db, chunk));
        }

        Map<Long, List<InvoiceItem>> grouped = ChunkUtils.groupItemsByInvoiceId(allItems);
        for (Invoice invoice : invoices) {
            List<InvoiceItem> items = grouped.get(invoice.getId());
            invoice.setItems(items != null ? items : new ArrayList<InvoiceItem>());
        }
    }

    /**
     * Loads all invoice items whose {@code invoice_id} is in {@code ids}
     * (one query). {@code ids} must be non-empty and small enough to fit in a
     * single SQLite {@code IN} clause.
     */
    private List<InvoiceItem> queryItemsForIds(SQLiteDatabase db, List<Long> ids) {
        List<InvoiceItem> items = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return items;
        }

        StringBuilder placeholders = new StringBuilder();
        String[] args = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
            args[i] = String.valueOf(ids.get(i));
        }

        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_INVOICE_ITEMS, null,
                    DatabaseHelper.COL_INVOICE_ID + " IN (" + placeholders + ")",
                    args, null, null, DatabaseHelper.COL_ID + " ASC");
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
     * Get the most recent invoices, limited to the given count, including items.
     */
    public List<Invoice> getRecent(int limit) {
        return getRecent(limit, true);
    }

    /**
     * Get the most recent invoices, limited to the given count.
     *
     * @param loadItems see {@link #getAll(boolean)}
     */
    public List<Invoice> getRecent(int limit, boolean loadItems) {
        List<Invoice> invoices = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.VIEW_INVOICE, null,
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
        if (loadItems) {
            attachItems(db, invoices);
        }
        return invoices;
    }

    /**
     * Convert a cursor row (from invoice_view) to an Invoice object.
     */
    private Invoice cursorToInvoice(Cursor cursor) {
        Invoice invoice = new Invoice();
        invoice.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID)));
        invoice.setStoreName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STORE_NAME)));
        invoice.setInvoiceDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_INVOICE_DATE)));
        invoice.setTotalAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_AMOUNT)));
        invoice.setPaidAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PAID_AMOUNT)));
        invoice.setRemaining(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_REMAINING)));
        invoice.setStatus(InvoiceStatus.fromDb(
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STATUS))));
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
