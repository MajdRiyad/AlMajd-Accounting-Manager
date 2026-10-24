package com.almajd.accounting.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.almajd.accounting.model.Payment;
import com.almajd.accounting.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

public class PaymentDao {

    private final DatabaseHelper dbHelper;

    public PaymentDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    /**
     * Insert a new payment.
     *
     * @return the newly generated payment id, or -1 on failure
     */
    public long insert(Payment payment) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return insertInternal(db, payment);
    }

    /**
     * Update an existing payment.
     */
    public boolean update(Payment payment) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return updateInternal(db, payment);
    }

    /**
     * Delete a payment by id.
     */
    public boolean delete(long paymentId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete(DatabaseHelper.TABLE_PAYMENTS,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(paymentId)});
        return rows > 0;
    }

    /**
     * Get all payments linked to a specific invoice, newest first.
     */
    public List<Payment> getByInvoiceId(long invoiceId) {
        List<Payment> payments = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_PAYMENTS, null,
                    DatabaseHelper.COL_INVOICE_ID + " = ?",
                    new String[]{String.valueOf(invoiceId)}, null, null,
                    DatabaseHelper.COL_PAYMENT_DATE + " ASC, " +
                            DatabaseHelper.COL_ID + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    payments.add(cursorToPayment(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return payments;
    }

    /**
     * Sum of payments linked to an invoice.
     */
    public double getPaidForInvoice(long invoiceId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COL_AMOUNT + "), 0) FROM " +
                            DatabaseHelper.TABLE_PAYMENTS + " WHERE " + DatabaseHelper.COL_INVOICE_ID + " = ?",
                    new String[]{String.valueOf(invoiceId)});
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
     * Sum of payments linked to an invoice, excluding one payment id (used when
     * editing a payment so the edited amount is not counted twice). Pass
     * {@code excludePaymentId <= 0} to include every payment.
     */
    public double getPaidForInvoiceExcluding(long invoiceId, long excludePaymentId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COL_AMOUNT + "), 0) FROM " +
                            DatabaseHelper.TABLE_PAYMENTS + " WHERE " + DatabaseHelper.COL_INVOICE_ID + " = ?" +
                            " AND " + DatabaseHelper.COL_ID + " <> ?",
                    new String[]{String.valueOf(invoiceId), String.valueOf(excludePaymentId)});
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
     * Insert two payments in a single transaction (used to split an overpayment
     * into an invoice-linked part and an on-account part). Either both are
     * written or neither is.
     *
     * @return true when both inserts succeeded
     */
    public boolean insertTwo(Payment first, Payment second) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            if (insertInternal(db, first) == -1) return false;
            if (second != null && insertInternal(db, second) == -1) return false;
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Update one payment and insert another in a single transaction (used to
     * split an edited overpayment). Either both succeed or neither is written.
     */
    public boolean updateAndInsert(Payment updated, Payment inserted) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            if (!updateInternal(db, updated)) return false;
            if (inserted != null && insertInternal(db, inserted) == -1) return false;
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private long insertInternal(SQLiteDatabase db, Payment payment) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_STORE_NAME, payment.getStoreName());
        values.put(DatabaseHelper.COL_AMOUNT, payment.getAmount());
        values.put(DatabaseHelper.COL_PAYMENT_DATE, payment.getPaymentDate());
        values.put(DatabaseHelper.COL_NOTES, payment.getNotes());
        if (payment.getInvoiceId() != null) {
            values.put(DatabaseHelper.COL_INVOICE_ID, payment.getInvoiceId());
        } else {
            values.putNull(DatabaseHelper.COL_INVOICE_ID);
        }
        return db.insert(DatabaseHelper.TABLE_PAYMENTS, null, values);
    }

    private boolean updateInternal(SQLiteDatabase db, Payment payment) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_STORE_NAME, payment.getStoreName());
        values.put(DatabaseHelper.COL_AMOUNT, payment.getAmount());
        values.put(DatabaseHelper.COL_PAYMENT_DATE, payment.getPaymentDate());
        values.put(DatabaseHelper.COL_NOTES, payment.getNotes());
        if (payment.getInvoiceId() != null) {
            values.put(DatabaseHelper.COL_INVOICE_ID, payment.getInvoiceId());
        } else {
            values.putNull(DatabaseHelper.COL_INVOICE_ID);
        }
        int rows = db.update(DatabaseHelper.TABLE_PAYMENTS, values,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(payment.getId())});
        return rows > 0;
    }

    /**
     * Get a single payment by id.
     */
    public Payment getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_PAYMENTS, null,
                    DatabaseHelper.COL_ID + " = ?",
                    new String[]{String.valueOf(id)}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursorToPayment(cursor);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Get all payments ordered by created_at descending.
     */
    public List<Payment> getAll() {
        List<Payment> payments = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_PAYMENTS, null,
                    null, null, null, null,
                    DatabaseHelper.COL_CREATED_AT + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    payments.add(cursorToPayment(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return payments;
    }

    /**
     * Get all payments for a specific store.
     */
    public List<Payment> getByStoreName(String storeName) {
        List<Payment> payments = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_PAYMENTS, null,
                    DatabaseHelper.COL_STORE_NAME + " = ?",
                    new String[]{storeName}, null, null,
                    DatabaseHelper.COL_CREATED_AT + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    payments.add(cursorToPayment(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return payments;
    }

    /**
     * All payments of a store, oldest first (payment date, then id), with the
     * fields the account statement needs. One query, no N+1.
     */
    public List<Payment> getStatementMovements(String storeName) {
        List<Payment> payments = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_PAYMENTS, null,
                    DatabaseHelper.COL_STORE_NAME + " = ?",
                    new String[]{storeName}, null, null,
                    DatabaseHelper.COL_PAYMENT_DATE + " ASC, " +
                            DatabaseHelper.COL_ID + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    payments.add(cursorToPayment(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return payments;
    }

    /**
     * Get payments within a date range (inclusive). An empty/null {@code from}
     * or {@code to} leaves that side of the range open, so the same query serves
     * "from only", "to only" and "both".
     */
    public List<Payment> getByDateRange(String from, String to) {
        List<Payment> payments = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_PAYMENTS, null,
                    DatabaseHelper.COL_PAYMENT_DATE + " >= ? AND " +
                            DatabaseHelper.COL_PAYMENT_DATE + " <= ?",
                    new String[]{DateUtils.rangeLower(from), DateUtils.rangeUpper(to)}, null, null,
                    DatabaseHelper.COL_PAYMENT_DATE + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    payments.add(cursorToPayment(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return payments;
    }

    /**
     * Get payments for a specific store within a date range. An empty/null
     * {@code from} or {@code to} leaves that side of the range open.
     */
    public List<Payment> getByStoreAndDateRange(String store, String from, String to) {
        List<Payment> payments = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_PAYMENTS, null,
                    DatabaseHelper.COL_STORE_NAME + " = ? AND " +
                            DatabaseHelper.COL_PAYMENT_DATE + " >= ? AND " +
                            DatabaseHelper.COL_PAYMENT_DATE + " <= ?",
                    new String[]{store, DateUtils.rangeLower(from), DateUtils.rangeUpper(to)}, null, null,
                    DatabaseHelper.COL_PAYMENT_DATE + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    payments.add(cursorToPayment(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return payments;
    }

    /**
     * Get the total payments amount across all payments.
     */
    public double getTotalPayments() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COL_AMOUNT + "), 0) FROM " +
                    DatabaseHelper.TABLE_PAYMENTS, null);
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
     * Get the total payments amount for a specific store.
     */
    public double getTotalPaymentsByStore(String store) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COL_AMOUNT + "), 0) FROM " +
                    DatabaseHelper.TABLE_PAYMENTS + " WHERE " + DatabaseHelper.COL_STORE_NAME + " = ?",
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
     * Get the total count of payments.
     */
    public int getCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_PAYMENTS, null);
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
     * Convert a cursor row to a Payment object.
     */
    private Payment cursorToPayment(Cursor cursor) {
        Payment payment = new Payment();
        payment.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID)));
        payment.setStoreName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STORE_NAME)));
        payment.setAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_AMOUNT)));
        payment.setPaymentDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PAYMENT_DATE)));
        payment.setNotes(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NOTES)));

        int invoiceIdIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_INVOICE_ID);
        if (cursor.isNull(invoiceIdIndex)) {
            payment.setInvoiceId(null);
        } else {
            payment.setInvoiceId(cursor.getLong(invoiceIdIndex));
        }

        payment.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CREATED_AT)));
        return payment;
    }
}
