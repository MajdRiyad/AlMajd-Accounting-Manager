package com.almajd.accounting.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.almajd.accounting.model.Customer;

import java.util.ArrayList;
import java.util.List;

public class CustomerDao {

    private final DatabaseHelper dbHelper;

    public CustomerDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    public boolean insert(Customer customer) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_STORE_NAME, customer.getStoreName());
        values.put(DatabaseHelper.COL_OWNER_NAME, customer.getOwnerName());
        values.put(DatabaseHelper.COL_LOCATION, customer.getLocation());
        values.put(DatabaseHelper.COL_PHONE, customer.getPhone());
        long result = db.insert(DatabaseHelper.TABLE_CUSTOMERS, null, values);
        return result != -1;
    }

    public boolean update(Customer customer) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_OWNER_NAME, customer.getOwnerName());
        values.put(DatabaseHelper.COL_LOCATION, customer.getLocation());
        values.put(DatabaseHelper.COL_PHONE, customer.getPhone());
        int rows = db.update(DatabaseHelper.TABLE_CUSTOMERS, values,
                DatabaseHelper.COL_STORE_NAME + " = ?",
                new String[]{customer.getStoreName()});
        return rows > 0;
    }

    /**
     * Deletes a customer only when they have no movement history. The check and
     * delete share one transaction, so no invoice or payment can be inserted
     * between them. The ON DELETE CASCADE schema is intentionally untouched:
     * this guard prevents any path from silently cascading history away.
     */
    public boolean delete(String storeName) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            if (getMovementCounts(db, storeName).hasAny()) {
                return false;
            }
            int rows = db.delete(DatabaseHelper.TABLE_CUSTOMERS,
                    DatabaseHelper.COL_STORE_NAME + " = ?",
                    new String[]{storeName});
            if (rows > 0) {
                db.setTransactionSuccessful();
            }
            return rows > 0;
        } finally {
            db.endTransaction();
        }
    }

    /** Orders + payments for one customer (used for the delete guard). */
    public int countMovements(String storeName) {
        MovementCounts counts = getMovementCounts(storeName);
        return counts.orders + counts.payments;
    }

    /** True when the customer has at least one order or one payment. */
    public boolean hasMovements(String storeName) {
        return getMovementCounts(storeName).hasAny();
    }

    /** Separate order/payment counts for the blocked-delete message. */
    public MovementCounts getMovementCounts(String storeName) {
        return getMovementCounts(dbHelper.getReadableDatabase(), storeName);
    }

    private MovementCounts getMovementCounts(SQLiteDatabase db, String storeName) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT " +
                            "(SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_INVOICES +
                            " WHERE " + DatabaseHelper.COL_STORE_NAME + " = ?), " +
                            "(SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_PAYMENTS +
                            " WHERE " + DatabaseHelper.COL_STORE_NAME + " = ?)",
                    new String[]{storeName, storeName});
            if (cursor != null && cursor.moveToFirst()) {
                return new MovementCounts(cursor.getInt(0), cursor.getInt(1));
            }
            return new MovementCounts(0, 0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /** Immutable order/payment counts for a customer. */
    public static final class MovementCounts {
        public final int orders;
        public final int payments;

        public MovementCounts(int orders, int payments) {
            this.orders = orders;
            this.payments = payments;
        }

        public boolean hasAny() {
            return orders > 0 || payments > 0;
        }
    }

    public Customer getByStoreName(String storeName) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_CUSTOMERS, null,
                    DatabaseHelper.COL_STORE_NAME + " = ?",
                    new String[]{storeName}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursorToCustomer(cursor);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public List<Customer> getAll() {
        List<Customer> customers = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.TABLE_CUSTOMERS, null,
                    null, null, null, null,
                    DatabaseHelper.COL_OWNER_NAME + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    customers.add(cursorToCustomer(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return customers;
    }

    public List<Customer> search(String query) {
        List<Customer> customers = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            String likeQuery = "%" + query + "%";
            cursor = db.query(DatabaseHelper.TABLE_CUSTOMERS, null,
                    DatabaseHelper.COL_STORE_NAME + " LIKE ? OR " +
                            DatabaseHelper.COL_OWNER_NAME + " LIKE ? OR " +
                            DatabaseHelper.COL_PHONE + " LIKE ? OR " +
                            DatabaseHelper.COL_LOCATION + " LIKE ?",
                    new String[]{likeQuery, likeQuery, likeQuery, likeQuery}, null, null,
                    DatabaseHelper.COL_OWNER_NAME + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    customers.add(cursorToCustomer(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return customers;
    }

    public int getCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_CUSTOMERS, null);
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

    private Customer cursorToCustomer(Cursor cursor) {
        Customer customer = new Customer();
        customer.setStoreName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STORE_NAME)));
        customer.setOwnerName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_OWNER_NAME)));
        customer.setLocation(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LOCATION)));
        customer.setPhone(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PHONE)));
        customer.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CREATED_AT)));
        return customer;
    }
}
