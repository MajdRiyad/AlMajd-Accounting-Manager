package com.almajd.accounting.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.almajd.accounting.R;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.InvoiceItem;
import com.almajd.accounting.model.InvoiceStatus;
import com.almajd.accounting.model.PendingPayment;
import com.almajd.accounting.util.ChunkUtils;
import com.almajd.accounting.util.CreditAllocator;
import com.almajd.accounting.util.DateUtils;
import com.almajd.accounting.util.NumberUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InvoiceDao {

    private final Context context;
    private final DatabaseHelper dbHelper;

    /**
     * Maximum number of store names per {@code IN (...)} clause when loading a
     * customer's invoice set or on-account pool. Keeps the host-parameter count
     * well below SQLite's limit.
     */
    private static final int STORE_CHUNK_SIZE = 500;

    public InvoiceDao(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    /**
     * Insert an invoice with its items in a single transaction and, if
     * {@code paidNow > 0}, a linked payment for the whole amount (same date as
     * the invoice). The invoice stores no paid/remaining columns -- those are
     * derived by invoice_view.
     *
     * @return the newly generated invoice id, or -1 on failure
     */
    public long insert(Invoice invoice, double paidNow) {
        return insert(invoice, paidNow, 0.0);
    }

    /**
     * Insert an invoice with its items, plus up to two payments, in a single
     * transaction:
     * <ul>
     *   <li>{@code linkedPaid} -- a payment linked to the new invoice</li>
     *   <li>{@code onAccountPaid} -- an on-account payment (invoice_id NULL),
     *       used for the excess of "paid now" over the invoice total</li>
     * </ul>
     * Everything is written in one transaction, so either the whole thing is
     * saved or nothing is (the excess is never silently dropped).
     *
     * @return the newly generated invoice id, or -1 on failure
     */
    public long insert(Invoice invoice, double linkedPaid, double onAccountPaid) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long invoiceId = -1;
        db.beginTransaction();
        try {
            // Insert invoice header (no paid_amount / remaining columns)
            ContentValues headerValues = new ContentValues();
            headerValues.put(DatabaseHelper.COL_STORE_NAME, invoice.getStoreName());
            headerValues.put(DatabaseHelper.COL_INVOICE_DATE, invoice.getInvoiceDate());
            headerValues.put(DatabaseHelper.COL_TOTAL_AMOUNT, NumberUtils.round2(invoice.getTotalAmount()));
            headerValues.put(DatabaseHelper.COL_DISCOUNT, NumberUtils.round2(invoice.getDiscount()));
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

            // Paid-now applied to the invoice: a normal linked payment.
            double roundedLinked = NumberUtils.round2(linkedPaid);
            if (roundedLinked > 0) {
                ContentValues paymentValues = new ContentValues();
                paymentValues.put(DatabaseHelper.COL_STORE_NAME, invoice.getStoreName());
                paymentValues.put(DatabaseHelper.COL_AMOUNT, roundedLinked);
                paymentValues.put(DatabaseHelper.COL_PAYMENT_DATE, invoice.getInvoiceDate());
                paymentValues.put(DatabaseHelper.COL_NOTES,
                        context.getString(R.string.payment_linked_note, invoiceId));
                paymentValues.put(DatabaseHelper.COL_INVOICE_ID, invoiceId);
                long paymentId = db.insert(DatabaseHelper.TABLE_PAYMENTS, null, paymentValues);
                if (paymentId == -1) {
                    return -1;
                }
            }

            // Paid-now excess: a separate on-account payment (invoice_id NULL).
            double roundedOnAccount = NumberUtils.round2(onAccountPaid);
            if (roundedOnAccount > 0) {
                ContentValues excessValues = new ContentValues();
                excessValues.put(DatabaseHelper.COL_STORE_NAME, invoice.getStoreName());
                excessValues.put(DatabaseHelper.COL_AMOUNT, roundedOnAccount);
                excessValues.put(DatabaseHelper.COL_PAYMENT_DATE, invoice.getInvoiceDate());
                excessValues.put(DatabaseHelper.COL_NOTES,
                        context.getString(R.string.payment_excess_note, invoiceId));
                excessValues.putNull(DatabaseHelper.COL_INVOICE_ID);
                long excessId = db.insert(DatabaseHelper.TABLE_PAYMENTS, null, excessValues);
                if (excessId == -1) {
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
            headerValues.put(DatabaseHelper.COL_DISCOUNT, NumberUtils.round2(invoice.getDiscount()));
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
     * Update an invoice header and its items, then apply pending linked-payment
     * changes, all in one transaction.
     * <p>
     * Existing rows update only their amount and date: {@code invoice_id} and the
     * original note are deliberately left untouched. New rows are inserted with
     * this invoice's id and the standard {@code payment_linked_note}. Deleted rows
     * are removed. A new row with an empty amount is ignored, so the user can add
     * a blank row and leave it unused.
     *
     * @return true when the whole transaction committed
     */
    public boolean updateWithPayments(Invoice invoice, List<PendingPayment> pendingPayments) {
        return updateWithPayments(invoice, pendingPayments, 0.0);
    }

    /**
     * Same as {@link #updateWithPayments(Invoice, List)} plus an optional
     * on-account payment for the direct overpayment carved out on the edit
     * screen. The carve-out is persisted in the same transaction as the order,
     * its items and its linked payments. The new payment has {@code invoice_id
     * NULL}, the order's store, today's date and the standard
     * {@code payment_excess_note}.
     *
     * @param onAccountPaid excess carved out of the direct rows; 0 means none
     * @return true when the whole transaction committed
     */
    public boolean updateWithPayments(Invoice invoice, List<PendingPayment> pendingPayments,
                                      double onAccountPaid) {
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
            headerValues.put(DatabaseHelper.COL_DISCOUNT, NumberUtils.round2(invoice.getDiscount()));
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

            // Apply the in-memory payment rows.
            if (pendingPayments != null) {
                String linkedNote = context.getString(
                        R.string.payment_linked_note, invoice.getId());
                for (PendingPayment row : pendingPayments) {
                    if (row == null) {
                        continue;
                    }
                    if (row.isDeleted()) {
                        if (!row.isNew() && row.getPaymentId() > 0) {
                            int deleted = db.delete(DatabaseHelper.TABLE_PAYMENTS,
                                    DatabaseHelper.COL_ID + " = ? AND " +
                                            DatabaseHelper.COL_INVOICE_ID + " = ?",
                                    new String[]{
                                            String.valueOf(row.getPaymentId()),
                                            String.valueOf(invoice.getId())
                                    });
                            if (deleted <= 0) {
                                return false;
                            }
                        }
                        continue;
                    }

                    double amount = NumberUtils.round2(row.getAmount());
                    if (row.isNew()) {
                        if (row.hasEmptyAmount()) {
                            continue; // unused blank row
                        }
                        if (amount <= 0) {
                            return false;
                        }
                        ContentValues paymentValues = new ContentValues();
                        paymentValues.put(DatabaseHelper.COL_STORE_NAME, invoice.getStoreName());
                        paymentValues.put(DatabaseHelper.COL_AMOUNT, amount);
                        paymentValues.put(DatabaseHelper.COL_PAYMENT_DATE, row.getPaymentDate());
                        paymentValues.put(DatabaseHelper.COL_NOTES, linkedNote);
                        paymentValues.put(DatabaseHelper.COL_INVOICE_ID, invoice.getId());
                        if (db.insert(DatabaseHelper.TABLE_PAYMENTS, null, paymentValues) == -1) {
                            return false;
                        }
                    } else {
                        if (amount <= 0 || row.getPaymentId() <= 0) {
                            return false;
                        }
                        // Only amount + date: keep invoice_id and note untouched.
                        ContentValues paymentValues = new ContentValues();
                        paymentValues.put(DatabaseHelper.COL_AMOUNT, amount);
                        paymentValues.put(DatabaseHelper.COL_PAYMENT_DATE, row.getPaymentDate());
                        int updated = db.update(DatabaseHelper.TABLE_PAYMENTS, paymentValues,
                                DatabaseHelper.COL_ID + " = ? AND " +
                                        DatabaseHelper.COL_INVOICE_ID + " = ?",
                                new String[]{
                                        String.valueOf(row.getPaymentId()),
                                        String.valueOf(invoice.getId())
                                });
                        if (updated <= 0) {
                            return false;
                        }
                    }
                }
            }

            // Excess carved out of the direct rows: separate on-account money.
            // It is not linked to the order, so it reduces the customer balance
            // without being counted twice in the order's paid total.
            double roundedOnAccount = NumberUtils.round2(onAccountPaid);
            if (roundedOnAccount > 0) {
                ContentValues excessValues = new ContentValues();
                excessValues.put(DatabaseHelper.COL_STORE_NAME, invoice.getStoreName());
                excessValues.put(DatabaseHelper.COL_AMOUNT, roundedOnAccount);
                excessValues.put(DatabaseHelper.COL_PAYMENT_DATE,
                        DateUtils.todayAsDbFormat());
                excessValues.put(DatabaseHelper.COL_NOTES,
                        context.getString(R.string.payment_excess_note, invoice.getId()));
                excessValues.putNull(DatabaseHelper.COL_INVOICE_ID);
                if (db.insert(DatabaseHelper.TABLE_PAYMENTS, null, excessValues) == -1) {
                    return false;
                }
            }

            // Defense in depth for A1: no linked payment may belong to a
            // different store than the order. This is checked after all deletes/
            // updates/inserts, so removing a bad row is allowed, but changing an
            // order's store while history remains is rolled back as a whole.
            if (hasLinkedPaymentStoreMismatch(db, invoice.getId(), invoice.getStoreName())) {
                return false;
            }

            db.setTransactionSuccessful();
            success = true;
        } finally {
            db.endTransaction();
        }
        return success;
    }

    /**
     * True when at least one payment linked to {@code invoiceId} has a different
     * (or missing) store name. Runs inside the caller's transaction.
     */
    private boolean hasLinkedPaymentStoreMismatch(SQLiteDatabase db, long invoiceId,
                                                   String storeName) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_PAYMENTS +
                            " WHERE " + DatabaseHelper.COL_INVOICE_ID + " = ?" +
                            " AND (" + DatabaseHelper.COL_STORE_NAME + " IS NULL" +
                            " OR " + DatabaseHelper.COL_STORE_NAME + " <> ?)",
                    new String[]{String.valueOf(invoiceId), storeName});
            return cursor != null && cursor.moveToFirst() && cursor.getInt(0) > 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
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
     * Get a single invoice by id, including its items. The derived paid /
     * remaining / status values are the <b>effective</b> ones (direct payments
     * plus any on-account credit allocated to this invoice), so the detail screen
     * matches the lists.
     */
    public Invoice getById(long id) {
        Invoice invoice = queryRawInvoiceById(id);
        if (invoice == null) {
            return null;
        }

        // Credit is spread oldest-first across a customer's invoices, so an
        // invoice's share depends on its siblings. Re-use the store query (which
        // applies the allocation) and pick our row out of it.
        List<Invoice> siblings = queryInvoices(
                DatabaseHelper.COL_STORE_NAME + " = ?",
                new String[]{invoice.getStoreName()},
                DatabaseHelper.COL_CREATED_AT + " DESC",
                false);
        for (Invoice sibling : siblings) {
            if (sibling.getId() == id) {
                invoice = sibling;
                break;
            }
        }

        invoice.setItems(getItemsForInvoice(id));
        return invoice;
    }

    /** Reads one invoice row from the view, with no items and no credit allocation. */
    private Invoice queryRawInvoiceById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.VIEW_INVOICE, null,
                    DatabaseHelper.COL_ID + " = ?",
                    new String[]{String.valueOf(id)}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursorToInvoice(cursor);
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
     * All invoices of a store with just the fields the account statement needs
     * (id, store, date, net total, discount, notes), oldest first.
     * <p>
     * Deliberately reads the invoices table directly rather than the
     * {@code invoice_view}: the statement is about raw movements, so the
     * display-only credit allocation is not needed and no items are loaded. One
     * query, no N+1.
     */
    public List<Invoice> getStatementMovements(String storeName) {
        List<Invoice> invoices = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_INVOICES,
                    new String[]{
                            DatabaseHelper.COL_ID,
                            DatabaseHelper.COL_STORE_NAME,
                            DatabaseHelper.COL_INVOICE_DATE,
                            DatabaseHelper.COL_TOTAL_AMOUNT,
                            DatabaseHelper.COL_DISCOUNT,
                            DatabaseHelper.COL_NOTES
                    },
                    DatabaseHelper.COL_STORE_NAME + " = ?",
                    new String[]{storeName}, null, null,
                    DatabaseHelper.COL_INVOICE_DATE + " ASC, " + DatabaseHelper.COL_ID + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Invoice invoice = new Invoice();
                    invoice.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID)));
                    invoice.setStoreName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STORE_NAME)));
                    invoice.setInvoiceDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_INVOICE_DATE)));
                    invoice.setTotalAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_AMOUNT)));
                    invoice.setDiscount(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DISCOUNT)));
                    invoice.setNotes(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOTES)));
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
     * Get invoices within a date range (inclusive), including their items.
     */
    public List<Invoice> getByDateRange(String from, String to) {
        return getByDateRange(from, to, true);
    }

    /**
     * Get invoices within a date range (inclusive). An empty/null {@code from}
     * or {@code to} leaves that side of the range open, so the same query serves
     * "from only", "to only" and "both".
     */
    public List<Invoice> getByDateRange(String from, String to, boolean loadItems) {
        return queryInvoices(
                DatabaseHelper.COL_INVOICE_DATE + " >= ? AND " + DatabaseHelper.COL_INVOICE_DATE + " <= ?",
                new String[]{DateUtils.rangeLower(from), DateUtils.rangeUpper(to)},
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
     * Get invoices for a specific store within a date range. An empty/null
     * {@code from} or {@code to} leaves that side of the range open.
     */
    public List<Invoice> getByStoreAndDateRange(String store, String from, String to, boolean loadItems) {
        return queryInvoices(
                DatabaseHelper.COL_STORE_NAME + " = ? AND " +
                        DatabaseHelper.COL_INVOICE_DATE + " >= ? AND " +
                        DatabaseHelper.COL_INVOICE_DATE + " <= ?",
                new String[]{store, DateUtils.rangeLower(from), DateUtils.rangeUpper(to)},
                DatabaseHelper.COL_INVOICE_DATE + " DESC",
                loadItems);
    }

    /**
     * Get a store's invoices that still have an <b>effective</b> remaining amount
     * greater than 0 (i.e. after their direct payments and any on-account credit
     * were applied). Used by the payment form's invoice selector so an invoice
     * already settled from the customer's credit is not offered as unpaid.
     * Items are not loaded.
     */
    public List<Invoice> getOpenInvoicesByStore(String storeName) {
        // Load the store's invoices (the allocation is applied by queryInvoices),
        // then keep only those with a remaining balance. Filtering in Java -- not
        // in SQL -- is deliberate: invoice_view.remaining only counts direct
        // payments, while the effective remaining also subtracts allocated credit.
        List<Invoice> invoices = queryInvoices(
                DatabaseHelper.COL_STORE_NAME + " = ?",
                new String[]{storeName},
                DatabaseHelper.COL_CREATED_AT + " DESC",
                false);
        List<Invoice> open = new ArrayList<>();
        for (Invoice invoice : invoices) {
            if (invoice.getRemaining() > 0) {
                open.add(invoice);
            }
        }
        return open;
    }

    /**
     * Shared query helper: runs a single aggregated query against invoice_view
     * (paid/remaining/status are computed by the view, never N+1), then applies
     * the customer-credit allocation so every caller sees the <b>effective</b>
     * paid/remaining/status, and finally (when requested) attaches all line items
     * with a constant number of extra queries.
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
        applyCreditAllocation(invoices);
        if (loadItems) {
            attachItems(db, invoices);
        }
        return invoices;
    }

    // ── Customer-credit allocation (display-only derivation) ─────────────

    /**
     * Overrides each invoice's paid/remaining/status with the values produced by
     * {@link CreditAllocator}: the customer's on-account money is spread, oldest
     * invoice first, over their still-unpaid invoices. Stored payments are never
     * modified -- this is a read-time derivation only.
     * <p>
     * Correctness needs the customer's <em>whole</em> invoice set (a specific
     * invoice's share depends on its siblings), so the set is reloaded for every
     * distinct customer in {@code invoices} with one query per chunk of stores.
     * The on-account pool comes from a single grouped query. Nothing here is N+1
     * in the number of invoices.
     */
    private void applyCreditAllocation(List<Invoice> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return;
        }

        Set<String> stores = new LinkedHashSet<>();
        for (Invoice invoice : invoices) {
            if (invoice != null && invoice.getStoreName() != null) {
                stores.add(invoice.getStoreName());
            }
        }
        if (stores.isEmpty()) {
            return;
        }

        List<Invoice> customerInvoices = queryRawInvoicesForStores(stores);

        Map<String, List<CreditAllocator.Debt>> debtsByCustomer = new LinkedHashMap<>();
        for (Invoice invoice : customerInvoices) {
            String store = invoice.getStoreName();
            if (store == null) {
                continue;
            }
            List<CreditAllocator.Debt> debts = debtsByCustomer.get(store);
            if (debts == null) {
                debts = new ArrayList<>();
                debtsByCustomer.put(store, debts);
            }
            // paidAmount is still the raw value from the view here (direct payments).
            debts.add(new CreditAllocator.Debt(invoice.getId(), invoice.getInvoiceDate(),
                    invoice.getTotalAmount(), invoice.getPaidAmount()));
        }

        Map<String, Double> pools = queryOnAccountPool(stores);
        Map<String, List<CreditAllocator.Allocation>> allocations =
                CreditAllocator.allocateByCustomer(debtsByCustomer, pools);

        Map<Long, CreditAllocator.Allocation> byId = new HashMap<>();
        for (List<CreditAllocator.Allocation> customerAllocations : allocations.values()) {
            for (CreditAllocator.Allocation allocation : customerAllocations) {
                byId.put(allocation.invoiceId, allocation);
            }
        }

        for (Invoice invoice : invoices) {
            if (invoice == null) {
                continue;
            }
            CreditAllocator.Allocation allocation = byId.get(invoice.getId());
            if (allocation == null) {
                continue;
            }
            invoice.setPaidDirect(allocation.paidDirect);
            invoice.setAllocatedFromCredit(allocation.allocatedFromCredit);
            invoice.setPaidAmount(allocation.effectivePaid);
            invoice.setRemaining(allocation.effectiveRemaining);
            invoice.setStatus(allocation.status);
        }
    }

    /**
     * Loads every invoice (no items, no allocation) for the given stores, one
     * query per chunk of stores.
     */
    private List<Invoice> queryRawInvoicesForStores(Collection<String> stores) {
        List<Invoice> invoices = new ArrayList<>();
        if (stores == null || stores.isEmpty()) {
            return invoices;
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        for (List<String> chunk : ChunkUtils.chunk(new ArrayList<>(stores), STORE_CHUNK_SIZE)) {
            StringBuilder placeholders = new StringBuilder();
            String[] args = new String[chunk.size()];
            for (int i = 0; i < chunk.size(); i++) {
                if (i > 0) {
                    placeholders.append(',');
                }
                placeholders.append('?');
                args[i] = chunk.get(i);
            }
            Cursor cursor = null;
            try {
                cursor = db.query(DatabaseHelper.VIEW_INVOICE, null,
                        DatabaseHelper.COL_STORE_NAME + " IN (" + placeholders + ")",
                        args, null, null, null);
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
        }
        return invoices;
    }

    /**
     * Sum of on-account payments ({@code invoice_id IS NULL}) per store, with one
     * grouped query per chunk of stores. Stores with no on-account money are
     * simply absent from the returned map (treated as 0).
     */
    private Map<String, Double> queryOnAccountPool(Collection<String> stores) {
        Map<String, Double> pools = new HashMap<>();
        if (stores == null || stores.isEmpty()) {
            return pools;
        }
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        for (List<String> chunk : ChunkUtils.chunk(new ArrayList<>(stores), STORE_CHUNK_SIZE)) {
            StringBuilder placeholders = new StringBuilder();
            String[] args = new String[chunk.size()];
            for (int i = 0; i < chunk.size(); i++) {
                if (i > 0) {
                    placeholders.append(',');
                }
                placeholders.append('?');
                args[i] = chunk.get(i);
            }
            Cursor cursor = null;
            try {
                cursor = db.rawQuery(
                        "SELECT " + DatabaseHelper.COL_STORE_NAME + ", SUM(" + DatabaseHelper.COL_AMOUNT + ")"
                                + " FROM " + DatabaseHelper.TABLE_PAYMENTS
                                + " WHERE " + DatabaseHelper.COL_INVOICE_ID + " IS NULL"
                                + " AND " + DatabaseHelper.COL_STORE_NAME + " IN (" + placeholders + ")"
                                + " GROUP BY " + DatabaseHelper.COL_STORE_NAME,
                        args);
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        pools.put(cursor.getString(0), cursor.getDouble(1));
                    } while (cursor.moveToNext());
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return pools;
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
        applyCreditAllocation(invoices);
        if (loadItems) {
            attachItems(db, invoices);
        }
        return invoices;
    }

    /**
     * Convert a cursor row (from invoice_view) to an Invoice object.
     * <p>
     * {@code paidAmount} starts as the raw value from the view (direct payments)
     * and is later replaced with the effective value once
     * {@link #applyCreditAllocation(List)} runs; {@code paidDirect} always keeps
     * the raw value.
     */
    private Invoice cursorToInvoice(Cursor cursor) {
        Invoice invoice = new Invoice();
        invoice.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID)));
        invoice.setStoreName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STORE_NAME)));
        invoice.setInvoiceDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_INVOICE_DATE)));
        invoice.setTotalAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_AMOUNT)));
        invoice.setDiscount(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DISCOUNT)));
        double paidDirect = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PAID_AMOUNT));
        invoice.setPaidAmount(paidDirect);
        invoice.setPaidDirect(paidDirect);
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
