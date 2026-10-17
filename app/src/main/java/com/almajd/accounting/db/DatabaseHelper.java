package com.almajd.accounting.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "almajd_accounting.db";

    /**
     * Current schema version: the single source of truth. It is passed to
     * SQLiteOpenHelper and recorded in backup manifests.
     * <p>
     * <b>To add a new migration (for example version 3):</b>
     * <ol>
     *   <li>bump {@link #SCHEMA_VERSION} to 3;</li>
     *   <li>add a step at index 2 of {@link #MIGRATIONS} that upgrades 2 &rarr; 3
     *       using only ALTER/CREATE/INSERT statements (never drop table data).</li>
     * </ol>
     * {@code MIGRATIONS.get(v)} must upgrade schema version {@code v} to
     * {@code v + 1}. Steps are run in order, inside the transaction that
     * SQLiteOpenHelper already wraps around onUpgrade().
     */
    public static final int SCHEMA_VERSION = 2;

    private static DatabaseHelper instance;

    // Table names
    public static final String TABLE_CUSTOMERS = "customers";
    public static final String TABLE_INVOICES = "invoices";
    public static final String TABLE_INVOICE_ITEMS = "invoice_items";
    public static final String TABLE_PAYMENTS = "payments";
    public static final String VIEW_LEDGER = "ledger_view";
    public static final String VIEW_INVOICE = "invoice_view";

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

    /** Derived status column exposed by {@link #VIEW_INVOICE}. */
    public static final String COL_STATUS = "status";

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
    // NOTE: paid_amount and remaining are NOT stored. They are derived from the
    // payments table by the invoice_view (see SQL_CREATE_INVOICE_VIEW).
    private static final String SQL_CREATE_INVOICES =
            "CREATE TABLE " + TABLE_INVOICES + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_STORE_NAME + " TEXT NOT NULL, " +
                    COL_INVOICE_DATE + " TEXT NOT NULL, " +
                    COL_TOTAL_AMOUNT + " REAL DEFAULT 0, " +
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

    // SQL: Create invoice_view
    // Derives paid / remaining / status for every invoice from the payments
    // table in a single aggregated query, so listing invoices never needs an
    // N+1 query for payments.
    private static final String SQL_CREATE_INVOICE_VIEW =
            "CREATE VIEW " + VIEW_INVOICE + " AS " +
                    "SELECT inv." + COL_ID + ", " +
                    "inv." + COL_STORE_NAME + ", " +
                    "inv." + COL_INVOICE_DATE + ", " +
                    "inv." + COL_TOTAL_AMOUNT + ", " +
                    "COALESCE(paid.total, 0) AS " + COL_PAID_AMOUNT + ", " +
                    "MAX(inv." + COL_TOTAL_AMOUNT + " - COALESCE(paid.total, 0), 0) AS " + COL_REMAINING + ", " +
                    "CASE " +
                    "WHEN COALESCE(paid.total, 0) >= inv." + COL_TOTAL_AMOUNT + " THEN 'paid' " +
                    "WHEN COALESCE(paid.total, 0) > 0 THEN 'partial' " +
                    "ELSE 'unpaid' END AS " + COL_STATUS + ", " +
                    "inv." + COL_NOTES + ", " +
                    "inv." + COL_CREATED_AT + " " +
                    "FROM " + TABLE_INVOICES + " inv " +
                    "LEFT JOIN (" +
                    "SELECT " + COL_INVOICE_ID + ", SUM(" + COL_AMOUNT + ") AS total " +
                    "FROM " + TABLE_PAYMENTS + " " +
                    "WHERE " + COL_INVOICE_ID + " IS NOT NULL " +
                    "GROUP BY " + COL_INVOICE_ID +
                    ") paid ON paid." + COL_INVOICE_ID + " = inv." + COL_ID;

    /** A single schema migration step. */
    public interface Migration {
        void apply(SQLiteDatabase db);
    }

    /**
     * Ordered migration steps. {@code MIGRATIONS.get(v)} upgrades schema version
     * {@code v} to {@code v + 1}.
     * <p>
     * Indices 0 and 1 are unused: version 1 is the legacy development schema and
     * is handled by the one-time destructive branch in {@link #onUpgrade}. The
     * next schema change adds a step at index 2 (2 &rarr; 3); see
     * {@link #SCHEMA_VERSION} for the full recipe.
     */
    private static final List<Migration> MIGRATIONS = buildMigrations();

    private static List<Migration> buildMigrations() {
        List<Migration> migrations = new ArrayList<>();
        migrations.add(null); // 0 -> 1 (never used)
        migrations.add(null); // 1 -> 2 (legacy schema, handled destructively)
        // Example for the next version:
        // migrations.add(db -> {
        //     db.execSQL("ALTER TABLE " + TABLE_CUSTOMERS + " ADD COLUMN note TEXT");
        //     recreateViews(db);   // whenever a view's definition changes
        // });
        return migrations;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, SCHEMA_VERSION);
    }

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Closes the singleton (and the underlying database handle) and forgets it,
     * so the next {@link #getInstance} call rebuilds it. Used by the restore
     * flow, which must replace the database file while no handle is open.
     */
    public static synchronized void closeInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
        createViews(db);
    }

    private void createTables(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CUSTOMERS);
        db.execSQL(SQL_CREATE_INVOICES);
        db.execSQL(SQL_CREATE_INVOICE_ITEMS);
        db.execSQL(SQL_CREATE_PAYMENTS);
    }

    private static void createViews(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_LEDGER_VIEW);
        db.execSQL(SQL_CREATE_INVOICE_VIEW);
    }

    /**
     * Drops and recreates both views. Migrations call this whenever a view's
     * definition changes, so view bodies can be updated without touching any
     * table data.
     */
    public static void recreateViews(SQLiteDatabase db) {
        db.execSQL("DROP VIEW IF EXISTS " + VIEW_INVOICE);
        db.execSQL("DROP VIEW IF EXISTS " + VIEW_LEDGER);
        createViews(db);
    }

    private static void dropAllObjects(SQLiteDatabase db) {
        db.execSQL("DROP VIEW IF EXISTS " + VIEW_INVOICE);
        db.execSQL("DROP VIEW IF EXISTS " + VIEW_LEDGER);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INVOICE_ITEMS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PAYMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INVOICES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CUSTOMERS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Runs inside the transaction SQLiteOpenHelper provides, so a failing
        // step rolls the whole upgrade back.
        if (oldVersion < 2) {
            // ONE-TIME EXCEPTION: version 1 was a development-only schema (it
            // stored invoices.paid_amount/remaining and had no invoice_view) and
            // has no incremental upgrade path. Recreate it from scratch. This
            // branch can be deleted once no version-1 database can exist.
            dropAllObjects(db);
            onCreate(db);
            return;
        }

        for (int version = oldVersion; version < newVersion; version++) {
            Migration migration = version < MIGRATIONS.size() ? MIGRATIONS.get(version) : null;
            if (migration == null) {
                throw new IllegalStateException(
                        "Missing database migration from version " + version
                                + " to " + (version + 1));
            }
            migration.apply(db);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Never wipe data because the user happened to install an older build.
        throw new IllegalStateException(
                "Database version " + oldVersion + " is newer than this app supports ("
                        + newVersion + "). Refusing to open to avoid data loss.");
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys = ON");
        }
    }
}
