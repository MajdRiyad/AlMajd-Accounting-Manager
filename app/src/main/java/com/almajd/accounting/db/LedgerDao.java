package com.almajd.accounting.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.almajd.accounting.model.LedgerEntry;

import java.util.ArrayList;
import java.util.List;

public class LedgerDao {

    private final DatabaseHelper dbHelper;

    public LedgerDao(Context context) {
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    /**
     * Get all ledger entries from the ledger_view.
     */
    public List<LedgerEntry> getAll() {
        List<LedgerEntry> entries = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT * FROM " + DatabaseHelper.VIEW_LEDGER, null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    entries.add(cursorToLedgerEntry(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return entries;
    }

    /**
     * Get a single ledger entry for a specific store.
     */
    public LedgerEntry getByStoreName(String storeName) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT * FROM " + DatabaseHelper.VIEW_LEDGER +
                            " WHERE " + DatabaseHelper.COL_STORE_NAME + " = ?",
                    new String[]{storeName});
            if (cursor != null && cursor.moveToFirst()) {
                return cursorToLedgerEntry(cursor);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Get the total outstanding balance across all customers.
     */
    public double getTotalOutstandingBalance() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COALESCE(SUM(" + DatabaseHelper.COL_BALANCE + "), 0) FROM " +
                    DatabaseHelper.VIEW_LEDGER, null);
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
     * Convert a cursor row to a LedgerEntry object.
     */
    private LedgerEntry cursorToLedgerEntry(Cursor cursor) {
        LedgerEntry entry = new LedgerEntry();
        entry.setStoreName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STORE_NAME)));
        entry.setOwnerName(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_OWNER_NAME)));
        entry.setGrandTotalSales(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GRAND_TOTAL_SALES)));
        entry.setTotalPaid(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_PAID)));
        entry.setBalance(cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_BALANCE)));

        int lastPaymentIndex = cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LAST_PAYMENT_DATE);
        if (cursor.isNull(lastPaymentIndex)) {
            entry.setLastPaymentDate(null);
        } else {
            entry.setLastPaymentDate(cursor.getString(lastPaymentIndex));
        }

        return entry;
    }
}
