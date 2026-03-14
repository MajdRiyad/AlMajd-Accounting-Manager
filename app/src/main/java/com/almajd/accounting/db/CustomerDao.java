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

    public boolean delete(String storeName) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete(DatabaseHelper.TABLE_CUSTOMERS,
                DatabaseHelper.COL_STORE_NAME + " = ?",
                new String[]{storeName});
        return rows > 0;
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
                            DatabaseHelper.COL_OWNER_NAME + " LIKE ?",
                    new String[]{likeQuery, likeQuery}, null, null,
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
