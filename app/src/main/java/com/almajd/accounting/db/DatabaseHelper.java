package com.almajd.accounting.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "almajd_accounting.db";
    private static final int DATABASE_VERSION = 1;

    private static DatabaseHelper instance;

    // Table names
    public static final String TABLE_CUSTOMERS = "customers";
    public static final String TABLE_INVOICES = "invoices";
    public static final String TABLE_INVOICE_ITEMS = "invoice_items";
    public static final String TABLE_PAYMENTS = "payments";
    public static final String VIEW_LEDGER = "ledger_view";

    // Customers columns
    public static final String COL_STORE_NAME = "store_name";
    public static final String COL_OWNER_NAME = "owner_name";
    public static final String COL_LOCATION = "location";
    public static final String COL_PHONE = "phone";
    public static final String COL_CREATED_AT = "created_at";

    // Invoices columns
    public static final String COL_ID = "id";
    public static final String COL_INVOICE_DATE = "invoice_date";
    public static final String COL_TOTAL_AMOUNT = "total_amount";
    public static final String COL_PAID_AMOUNT = "paid_amount";
    public static final String COL_REMAINING = "remaining";
    public static final String COL_NOTES = "notes";

    // Invoice items columns
    public static final String COL_INVOICE_ID = "invoice_id";
    public static final String COL_ITEM_NAME = "item_name";
    public static final String COL_QUANTITY = "quantity";
    public static final String COL_UNIT_PRICE = "unit_price";
    public static final String COL_SUBTOTAL = "subtotal";

    // Payments columns
    public static final String COL_AMOUNT = "amount";
    public static final String COL_PAYMENT_DATE = "payment_date";

    // Ledger view columns
    public static final String COL_GRAND_TOTAL_SALES = "grand_total_sales";
    public static final String COL_TOTAL_PAID = "total_paid";
    public static final String COL_BALANCE = "balance";
    public static final String COL_LAST_PAYMENT_DATE = "last_payment_date";

    // SQL: Create customers table
    private static final String SQL_CREATE_CUSTOMERS =
            "CREATE TABLE " + TABLE_CUSTOMERS + " (" +
                    COL_STORE_NAME + " TEXT PRIMARY KEY, " +
                    COL_OWNER_NAME + " TEXT NOT NULL, " +
                    COL_LOCATION + " TEXT, " +
                    COL_PHONE + " TEXT, " +
                    COL_CREATED_AT + " TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")";

    // SQL: Create invoices table
    private static final String SQL_CREATE_INVOICES =
            "CREATE TABLE " + TABLE_INVOICES + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_STORE_NAME + " TEXT NOT NULL, " +
                    COL_INVOICE_DATE + " TEXT NOT NULL, " +
                    COL_TOTAL_AMOUNT + " REAL DEFAULT 0, " +
                    COL_PAID_AMOUNT + " REAL DEFAULT 0, " +
                    COL_REMAINING + " REAL DEFAULT 0, " +
                    COL_NOTES + " TEXT, " +
                    COL_CREATED_AT + " TEXT DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (" + COL_STORE_NAME + ") REFERENCES " +
                    TABLE_CUSTOMERS + "(" + COL_STORE_NAME + ") " +
                    "ON UPDATE CASCADE ON DELETE CASCADE" +
                    ")";

    // SQL: Create invoice_items table
    private static final String SQL_CREATE_INVOICE_ITEMS =
            "CREATE TABLE " + TABLE_INVOICE_ITEMS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_INVOICE_ID + " INTEGER NOT NULL, " +
                    COL_ITEM_NAME + " TEXT NOT NULL, " +
                    COL_QUANTITY + " INTEGER DEFAULT 1, " +
                    COL_UNIT_PRICE + " REAL DEFAULT 0, " +
                    COL_SUBTOTAL + " REAL DEFAULT 0, " +
                    "FOREIGN KEY (" + COL_INVOICE_ID + ") REFERENCES " +
                    TABLE_INVOICES + "(" + COL_ID + ") ON DELETE CASCADE" +
                    ")";

    // SQL: Create payments table
    private static final String SQL_CREATE_PAYMENTS =
            "CREATE TABLE " + TABLE_PAYMENTS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_STORE_NAME + " TEXT NOT NULL, " +
                    COL_AMOUNT + " REAL NOT NULL, " +
                    COL_PAYMENT_DATE + " TEXT NOT NULL, " +
                    COL_NOTES + " TEXT, " +
                    COL_INVOICE_ID + " INTEGER, " +
                    COL_CREATED_AT + " TEXT DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (" + COL_STORE_NAME + ") REFERENCES " +
                    TABLE_CUSTOMERS + "(" + COL_STORE_NAME + ") " +
                    "ON UPDATE CASCADE ON DELETE CASCADE, " +
                    "FOREIGN KEY (" + COL_INVOICE_ID + ") REFERENCES " +
                    TABLE_INVOICES + "(" + COL_ID + ") ON DELETE SET NULL" +
                    ")";

    // SQL: Create ledger_view
    private static final String SQL_CREATE_LEDGER_VIEW =
            "CREATE VIEW " + VIEW_LEDGER + " AS " +
                    "SELECT c." + COL_STORE_NAME + ", " +
                    "c." + COL_OWNER_NAME + ", " +
                    "COALESCE(SUM(inv." + COL_TOTAL_AMOUNT + "), 0) AS " + COL_GRAND_TOTAL_SALES + ", " +
                    "COALESCE((SELECT SUM(p." + COL_AMOUNT + ") FROM " + TABLE_PAYMENTS + " p " +
                    "WHERE p." + COL_STORE_NAME + " = c." + COL_STORE_NAME + "), 0) AS " + COL_TOTAL_PAID + ", " +
                    "COALESCE(SUM(inv." + COL_TOTAL_AMOUNT + "), 0) - " +
                    "COALESCE((SELECT SUM(p." + COL_AMOUNT + ") FROM " + TABLE_PAYMENTS + " p " +
                    "WHERE p." + COL_STORE_NAME + " = c." + COL_STORE_NAME + "), 0) AS " + COL_BALANCE + ", " +
                    "(SELECT MAX(p2." + COL_PAYMENT_DATE + ") FROM " + TABLE_PAYMENTS + " p2 " +
                    "WHERE p2." + COL_STORE_NAME + " = c." + COL_STORE_NAME + ") AS " + COL_LAST_PAYMENT_DATE + " " +
                    "FROM " + TABLE_CUSTOMERS + " c " +
                    "LEFT JOIN " + TABLE_INVOICES + " inv ON c." + COL_STORE_NAME + " = inv." + COL_STORE_NAME + " " +
                    "GROUP BY c." + COL_STORE_NAME + ", c." + COL_OWNER_NAME;

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CUSTOMERS);
        db.execSQL(SQL_CREATE_INVOICES);
        db.execSQL(SQL_CREATE_INVOICE_ITEMS);
        db.execSQL(SQL_CREATE_PAYMENTS);
        db.execSQL(SQL_CREATE_LEDGER_VIEW);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP VIEW IF EXISTS " + VIEW_LEDGER);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INVOICE_ITEMS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PAYMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INVOICES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CUSTOMERS);
        onCreate(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys = ON");
        }
    }
}
