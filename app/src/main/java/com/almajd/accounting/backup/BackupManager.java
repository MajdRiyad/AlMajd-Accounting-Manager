package com.almajd.accounting.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.db.DatabaseHelper;
import com.almajd.accounting.db.InvoiceDao;
import com.almajd.accounting.db.PaymentDao;
import com.almajd.accounting.util.AppSettings;
import com.almajd.accounting.util.DateUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Creates and restores the single-file backups ({@code almajd-backup-*.zip}).
 * <p>
 * Backup format: a zip containing the SQLite database
 * ({@link DatabaseHelper#DATABASE_NAME}), {@code manifest.json} and
 * {@code settings.json}.
 * <p>
 * File work is synchronous on purpose; callers run it on a background thread.
 */
public class BackupManager {

    private static final String PREFS_NAME = "almajd_backup";
    private static final String KEY_LAST_BACKUP_TIME = "last_backup_time";

    private static final String EXPORTS_DIR = "exports";
    private static final String VALIDATION_DIR = "restore_validation";
    private static final String PRE_RESTORE_DIR = "pre_restore";

    private static final String ENTRY_DB = DatabaseHelper.DATABASE_NAME;
    private static final String ENTRY_MANIFEST = "manifest.json";
    private static final String ENTRY_SETTINGS = "settings.json";

    private static final String ZIP_MIME = "application/zip";

    private BackupManager() {
    }

    public static String backupMimeType() {
        return ZIP_MIME;
    }

    // ------------------------------------------------------------------
    // Last backup time
    // ------------------------------------------------------------------

    /** @return the last successful backup time in millis, or 0 if there was never one. */
    public static long getLastBackupTimeMillis(Context context) {
        return prefs(context).getLong(KEY_LAST_BACKUP_TIME, 0L);
    }

    /** Records that a backup has just been shared successfully. */
    public static void setLastBackupTimeMillis(Context context, long millis) {
        prefs(context).edit().putLong(KEY_LAST_BACKUP_TIME, millis).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------
    // Backup
    // ------------------------------------------------------------------

    /**
     * Creates a backup zip in {@code cacheDir/exports/} and returns it. Older
     * exports are deleted first so the cache does not grow.
     */
    public static File createBackup(Context context) throws BackupException {
        File exportsDir = new File(context.getCacheDir(), EXPORTS_DIR);
        if (!exportsDir.exists() && !exportsDir.mkdirs()) {
            throw new BackupException(BackupException.Kind.BACKUP_FAILED,
                    "Cannot create the exports directory");
        }
        deleteContents(exportsDir);

        checkpointDatabase(context);

        File dbFile = context.getDatabasePath(DatabaseHelper.DATABASE_NAME);
        if (dbFile == null || !dbFile.exists()) {
            throw new BackupException(BackupException.Kind.BACKUP_FAILED,
                    "Database file not found");
        }

        BackupManifest manifest = new BackupManifest();
        manifest.appId = BackupManifest.APP_ID;
        manifest.schemaVersion = DatabaseHelper.SCHEMA_VERSION;
        manifest.createdAt = DateUtils.humanTimestamp();
        manifest.appVersionName = appVersionName(context);
        manifest.customerCount = new CustomerDao(context).getCount();
        manifest.invoiceCount = new InvoiceDao(context).getCount();
        manifest.paymentCount = new PaymentDao(context).getCount();

        AppSettingsSnapshot settings = new AppSettingsSnapshot();
        AppSettings appSettings = new AppSettings(context);
        settings.companyNameAr = appSettings.getCompanyNameAr();
        settings.companyNameEn = appSettings.getCompanyNameEn();
        settings.ownerNameAr = appSettings.getOwnerNameAr();
        settings.ownerNameEn = appSettings.getOwnerNameEn();
        settings.phone = appSettings.getPhone();

        File zipFile = new File(exportsDir,
                "almajd-backup-" + DateUtils.fileTimestamp() + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            putFile(zos, ENTRY_DB, dbFile);
            putBytes(zos, ENTRY_MANIFEST, manifest.toJson());
            putBytes(zos, ENTRY_SETTINGS, settings.toJson());
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            zipFile.delete();
            throw new BackupException(BackupException.Kind.BACKUP_FAILED,
                    "Failed to write the backup zip", e);
        }

        return zipFile;
    }

    /**
     * Makes sure nothing is left only in the {@code -wal}/{@code -shm} side
     * files before the database file is copied out.
     */
    private static void checkpointDatabase(Context context) throws BackupException {
        try {
            SQLiteDatabase db = DatabaseHelper.getInstance(context).getWritableDatabase();
            Cursor cursor = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null);
            if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            throw new BackupException(BackupException.Kind.BACKUP_FAILED,
                    "Failed to flush the database", e);
        }
    }

    private static String appVersionName(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // Restore - validation
    // ------------------------------------------------------------------

    /**
     * Validates a chosen file without touching the current data.
     *
     * @throws BackupException if the file is not a usable backup.
     */
    public static ValidatedBackup validateBackup(Context context, Uri uri) throws BackupException {
        InputStream raw;
        try {
            raw = context.getContentResolver().openInputStream(uri);
        } catch (Exception e) {
            throw new BackupException(BackupException.Kind.INVALID_FILE,
                    "Cannot open the selected file", e);
        }
        if (raw == null) {
            throw new BackupException(BackupException.Kind.INVALID_FILE,
                    "Cannot open the selected file");
        }

        File validationDir = new File(context.getCacheDir(), VALIDATION_DIR);
        deleteRecursively(validationDir);
        if (!validationDir.exists() && !validationDir.mkdirs()) {
            throw new BackupException(BackupException.Kind.INVALID_FILE,
                    "Cannot create a temporary directory");
        }
        // Fixed target name - never derived from the zip entry name.
        File tempDb = new File(validationDir, DatabaseHelper.DATABASE_NAME);

        String manifestJson = null;
        String settingsJson = null;
        boolean databaseFound = false;

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // SECURITY: only exact, expected names are accepted and they map
                // to fixed paths, so crafted entry names cannot escape the
                // sandbox via path traversal.
                if (ENTRY_MANIFEST.equals(name)) {
                    manifestJson = readAllText(zis);
                } else if (ENTRY_SETTINGS.equals(name)) {
                    settingsJson = readAllText(zis);
                } else if (ENTRY_DB.equals(name)) {
                    copyStream(zis, tempDb);
                    databaseFound = true;
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new BackupException(BackupException.Kind.INVALID_FILE,
                    "The file is not a valid zip archive", e);
        }

        if (manifestJson == null) {
            throw new BackupException(BackupException.Kind.INVALID_FILE,
                    "manifest.json is missing from the backup");
        }
        if (!databaseFound) {
            throw new BackupException(BackupException.Kind.MISSING_DB,
                    "The database is missing from the backup");
        }

        BackupManifest manifest = BackupManifest.fromJson(manifestJson);
        manifest.validateForApp(DatabaseHelper.SCHEMA_VERSION);
        verifyDatabase(tempDb, DatabaseHelper.SCHEMA_VERSION);

        AppSettingsSnapshot settings = null;
        if (settingsJson != null) {
            try {
                settings = AppSettingsSnapshot.fromJson(settingsJson);
            } catch (BackupException ignored) {
                // Optional: a backup without usable settings is still valid.
            }
        }

        ValidatedBackup result = new ValidatedBackup();
        result.databaseFile = tempDb;
        result.manifest = manifest;
        result.settings = settings;
        return result;
    }

    private static void verifyDatabase(File dbFile, int appSchemaVersion) throws BackupException {
        if (!dbFile.exists() || dbFile.length() == 0L) {
            throw new BackupException(BackupException.Kind.DB_CORRUPT,
                    "The database in the backup is empty");
        }
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);

            Cursor integrity = db.rawQuery("PRAGMA integrity_check", null);
            String integrityResult = null;
            if (integrity != null && integrity.moveToFirst()) {
                integrityResult = integrity.getString(0);
            }
            if (integrity != null) {
                integrity.close();
            }
            if (integrityResult == null || !"ok".equalsIgnoreCase(integrityResult.trim())) {
                throw new BackupException(BackupException.Kind.DB_CORRUPT,
                        "The database failed its integrity check");
            }

            int userVersion = 0;
            Cursor versionCursor = db.rawQuery("PRAGMA user_version", null);
            if (versionCursor != null && versionCursor.moveToFirst()) {
                userVersion = versionCursor.getInt(0);
            }
            if (versionCursor != null) {
                versionCursor.close();
            }
            if (userVersion > appSchemaVersion) {
                throw new BackupException(BackupException.Kind.NEWER_VERSION,
                        "The database comes from a newer app version");
            }
            if (userVersion < BackupManifest.MIN_SUPPORTED_SCHEMA_VERSION) {
                throw new BackupException(BackupException.Kind.UNSUPPORTED_VERSION,
                        "The database version is not supported");
            }

            String[] requiredTables = {"customers", "invoices", "invoice_items", "payments"};
            for (String table : requiredTables) {
                if (!tableExists(db, table)) {
                    throw new BackupException(BackupException.Kind.DB_CORRUPT,
                            "A required table is missing: " + table);
                }
            }
        } catch (BackupException e) {
            throw e;
        } catch (Exception e) {
            throw new BackupException(BackupException.Kind.DB_CORRUPT,
                    "The database in the backup could not be read", e);
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean tableExists(SQLiteDatabase db, String name) {
        Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{name});
        boolean exists = cursor != null && cursor.moveToFirst();
        if (cursor != null) {
            cursor.close();
        }
        return exists;
    }

    // ------------------------------------------------------------------
    // Restore - replacement
    // ------------------------------------------------------------------

    /**
     * Replaces the current database (and settings) with the validated backup.
     * <p>
     * A safety copy of the current database is taken first; if anything fails
     * afterwards the safety copy is put back, so the app is never left without a
     * database. The caller must restart the app afterwards so the singleton is
     * rebuilt.
     */
    public static void restoreBackup(Context context, ValidatedBackup backup) throws BackupException {
        File dbFile = context.getDatabasePath(DatabaseHelper.DATABASE_NAME);
        File dbDir = dbFile.getParentFile();
        File walFile = new File(dbFile.getPath() + "-wal");
        File shmFile = new File(dbFile.getPath() + "-shm");

        // Close the singleton / any open handle before touching the files.
        DatabaseHelper.closeInstance();

        File preRestoreDir = new File(context.getFilesDir(), PRE_RESTORE_DIR);
        deleteRecursively(preRestoreDir);
        if (!preRestoreDir.exists() && !preRestoreDir.mkdirs()) {
            throw new BackupException(BackupException.Kind.RESTORE_FAILED,
                    "Cannot create the safety backup directory");
        }
        File safetyDb = new File(preRestoreDir, DatabaseHelper.DATABASE_NAME);
        boolean hadDatabase = dbFile.exists();
        if (hadDatabase) {
            try {
                copyFile(dbFile, safetyDb);
            } catch (IOException e) {
                throw new BackupException(BackupException.Kind.RESTORE_FAILED,
                        "Failed to back up the current data", e);
            }
        }

        File staging = new File(dbDir, DatabaseHelper.DATABASE_NAME + ".restore_tmp");
        try {
            // Stage the validated database next to the target so the final move
            // is a single atomic rename.
            copyFile(backup.databaseFile, staging);

            deleteIfExists(dbFile);
            deleteIfExists(walFile);
            deleteIfExists(shmFile);

            if (!staging.renameTo(dbFile)) {
                copyFile(staging, dbFile);
                deleteIfExists(staging);
            }

            if (backup.settings != null) {
                AppSettings appSettings = new AppSettings(context);
                appSettings.saveAll(backup.settings.companyNameAr, backup.settings.companyNameEn,
                        backup.settings.ownerNameAr, backup.settings.ownerNameEn,
                        backup.settings.phone);
            }
        } catch (Exception e) {
            rollback(dbFile, walFile, shmFile, safetyDb, hadDatabase);
            if (e instanceof BackupException) {
                throw (BackupException) e;
            }
            throw new BackupException(BackupException.Kind.RESTORE_FAILED,
                    "Restore failed; the previous data was kept", e);
        } finally {
            deleteIfExists(staging);
        }
    }

    private static void rollback(File dbFile, File walFile, File shmFile,
                                 File safetyDb, boolean hadDatabase) {
        try {
            deleteIfExists(dbFile);
            deleteIfExists(walFile);
            deleteIfExists(shmFile);
            if (hadDatabase && safetyDb.exists()) {
                copyFile(safetyDb, dbFile);
            }
        } catch (Exception ignored) {
            // Nothing more can be done here; the original error is reported.
        }
    }

    // ------------------------------------------------------------------
    // File helpers
    // ------------------------------------------------------------------

    private static void putFile(ZipOutputStream zos, String entryName, File source) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = new BufferedInputStream(new FileInputStream(source))) {
            copyStream(in, zos);
        }
        zos.closeEntry();
    }

    private static void putBytes(ZipOutputStream zos, String entryName, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String readAllText(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        copyStream(in, buffer);
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static void copyStream(InputStream in, File target) throws IOException {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            copyStream(in, out);
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source))) {
            copyStream(in, target);
        }
    }

    private static void deleteContents(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                deleteRecursively(child);
            } else {
                //noinspection ResultOfMethodCallIgnored
                child.delete();
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static void deleteIfExists(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
