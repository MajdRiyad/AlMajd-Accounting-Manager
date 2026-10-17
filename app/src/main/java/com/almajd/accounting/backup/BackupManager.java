package com.almajd.accounting.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.db.DatabaseHelper;
import com.almajd.accounting.db.InvoiceDao;
import com.almajd.accounting.db.PaymentDao;
import com.almajd.accounting.util.AppSettings;
import com.almajd.accounting.util.DateUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
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

    private static final String TAG = "BackupManager";

    private static final String PREFS_NAME = "almajd_backup";
    private static final String KEY_LAST_BACKUP_TIME = "last_backup_time";
    private static final String KEY_PENDING_RECOVERY = "pending_recovery";

    private static final String EXPORTS_DIR = "exports";
    private static final String VALIDATION_DIR = "restore_validation";
    private static final String PRE_RESTORE_DIR = "pre_restore";

    /** Dropped inside {@code pre_restore/} while a restore is in progress. */
    private static final String RESTORE_MARKER_NAME = "RESTORE_IN_PROGRESS";

    private static final String ENTRY_DB = DatabaseHelper.DATABASE_NAME;
    private static final String ENTRY_MANIFEST = "manifest.json";
    private static final String ENTRY_SETTINGS = "settings.json";

    private static final String ZIP_MIME = "application/zip";

    /** No recovery message is pending. */
    public static final int RECOVERY_NONE = 0;
    /** A restore was interrupted before the new data was installed; the previous data was recovered. */
    public static final int RECOVERY_RECOVERED_PREVIOUS = 1;
    /** A restore was interrupted after the new data was installed; the restored data was kept. */
    public static final int RECOVERY_KEPT_RESTORED = 2;

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
                BackupNaming.fileName(System.currentTimeMillis()));

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

        // SIZE GUARD: reject an obviously oversized backup before reading it.
        // The declared size can be absent or wrong, so this is only a fast
        // pre-filter; the real enforcement counts real bytes while reading below.
        long declaredSourceSize = sourceSizeBytes(context, uri);
        if (declaredSourceSize > 0 && !BackupLimits.isCompressedSizeAllowed(declaredSourceSize)) {
            throw new BackupException(BackupException.Kind.TOO_LARGE,
                    "The backup file is larger than the allowed limit");
        }
        if (declaredSourceSize > 0
                && !BackupLimits.hasEnoughFreeSpace(declaredSourceSize, usableSpace(context))) {
            throw new BackupException(BackupException.Kind.NOT_ENOUGH_SPACE,
                    "Not enough free space to validate the backup");
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

        // Counts the compressed bytes actually read from the source, so the size
        // limit cannot be bypassed by a lying zip header.
        CountingInputStream counting = new CountingInputStream(new BufferedInputStream(raw));
        boolean extractionComplete = false;
        try (ZipInputStream zis = new ZipInputStream(counting)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // SECURITY: only exact, expected names are accepted and they map
                // to fixed paths, so crafted entry names cannot escape the
                // sandbox via path traversal.
                if (ENTRY_MANIFEST.equals(name)) {
                    manifestJson = readAllText(zis, BackupLimits.Entry.JSON, ENTRY_MANIFEST);
                } else if (ENTRY_SETTINGS.equals(name)) {
                    settingsJson = readAllText(zis, BackupLimits.Entry.JSON, ENTRY_SETTINGS);
                } else if (ENTRY_DB.equals(name)) {
                    copyStream(zis, tempDb, BackupLimits.Entry.DATABASE, ENTRY_DB);
                    databaseFound = true;
                }
                zis.closeEntry();
                // The whole compressed archive must stay within bounds.
                if (!BackupLimits.isCompressedSizeAllowed(counting.count)) {
                    throw new BackupException(BackupException.Kind.TOO_LARGE,
                            "The backup file is larger than the allowed limit");
                }
            }
            extractionComplete = true;
        } catch (BackupException e) {
            throw e;
        } catch (IOException e) {
            throw new BackupException(BackupException.Kind.INVALID_FILE,
                    "The file is not a valid zip archive", e);
        } finally {
            if (!extractionComplete) {
                // Never leave a partial (possibly huge) extraction behind.
                deleteIfExists(tempDb);
            }
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
     * Replaces the current database (and settings) with the validated backup in a
     * recoverable way.
     * <p>
     * A safety copy of the current database is taken first and a
     * {@code RESTORE_IN_PROGRESS} marker is dropped. The marker is only removed
     * once the new database is in place, the settings are restored and the new
     * database opens. If the app dies in between, {@link #recoverIfInterrupted}
     * finishes the job on the next launch. Failures roll back to the safety copy.
     * The caller must restart the app afterwards so the singleton is rebuilt.
     */
    public static void restoreBackup(Context context, ValidatedBackup backup) throws BackupException {
        File dbFile = context.getDatabasePath(DatabaseHelper.DATABASE_NAME);
        File dbDir = dbFile.getParentFile();
        File walFile = new File(dbFile.getPath() + "-wal");
        File shmFile = new File(dbFile.getPath() + "-shm");

        // FREE SPACE GUARD: the safety copy plus the staging copy each take about
        // a database's worth of space, so require roughly twice the incoming size
        // before touching anything.
        long incomingSize = backup.databaseFile.length();
        if (!BackupLimits.hasEnoughFreeSpace(incomingSize, usableSpace(context))) {
            throw new BackupException(BackupException.Kind.NOT_ENOUGH_SPACE,
                    "Not enough free space to restore the backup");
        }

        // Close the singleton / any open handle before touching the files.
        DatabaseHelper.closeInstance();

        File preRestoreDir = new File(context.getFilesDir(), PRE_RESTORE_DIR);
        deleteRecursively(preRestoreDir);
        if (!preRestoreDir.exists() && !preRestoreDir.mkdirs()) {
            throw new BackupException(BackupException.Kind.RESTORE_FAILED,
                    "Cannot create the safety backup directory");
        }
        File safetyDb = new File(preRestoreDir, DatabaseHelper.DATABASE_NAME);
        File marker = new File(preRestoreDir, RESTORE_MARKER_NAME);

        boolean hadDatabase = dbFile.exists();
        long safetySize = 0L;
        if (hadDatabase) {
            try {
                copyFile(dbFile, safetyDb);
                safetySize = safetyDb.length();
            } catch (IOException e) {
                throw new BackupException(BackupException.Kind.RESTORE_FAILED,
                        "Failed to back up the current data", e);
            }
        }

        File staging = new File(dbDir, DatabaseHelper.DATABASE_NAME + ".restore_tmp");
        try {
            // CRASH SAFETY #1: from here on an interrupted restore can be rolled
            // back. The marker records the safety copy's size so recovery can
            // confirm the copy is intact before trusting it.
            writeMarker(marker, new RestoreMarker(
                    RestoreMarker.PHASE_SAFETY_TAKEN, hadDatabase, safetySize));

            // Stage the validated database next to the target so the final move
            // is a single atomic rename.
            copyFile(backup.databaseFile, staging);

            deleteIfExists(dbFile);
            // The -wal/-shm side files belong to the old database; they must not
            // survive the swap.
            deleteIfExists(walFile);
            deleteIfExists(shmFile);

            if (!staging.renameTo(dbFile)) {
                copyFile(staging, dbFile);
                deleteIfExists(staging);
            }

            // CRASH SAFETY #2: the new database is now in place. Moving the marker
            // to this phase makes recovery keep the restored data instead of
            // rolling it back.
            writeMarker(marker, new RestoreMarker(
                    RestoreMarker.PHASE_DB_INSTALLED, hadDatabase, safetySize));

            if (backup.settings != null) {
                AppSettings appSettings = new AppSettings(context);
                appSettings.saveAll(backup.settings.companyNameAr, backup.settings.companyNameEn,
                        backup.settings.ownerNameAr, backup.settings.ownerNameEn,
                        backup.settings.phone);
            }

            // The marker may only go once the new database actually opens.
            verifyRestoredDatabase(context);

            deleteIfExists(marker);
        } catch (Exception e) {
            boolean rolledBack = rollback(dbFile, walFile, shmFile, safetyDb, hadDatabase);
            if (rolledBack) {
                // The old data is in place again; the marker must not trigger a
                // spurious recovery on the next launch.
                deleteIfExists(marker);
            } else {
                Log.e(TAG, "Restore failed and the rollback did not complete; "
                        + "recovery will run on the next launch");
            }
            if (e instanceof BackupException) {
                throw (BackupException) e;
            }
            throw new BackupException(BackupException.Kind.RESTORE_FAILED,
                    "Restore failed; the previous data was kept", e);
        } finally {
            deleteIfExists(staging);
        }
    }

    /**
     * Opens the just-installed database through the normal helper (so any pending
     * migration runs) and performs a cheap read, to prove the new file is usable.
     */
    private static void verifyRestoredDatabase(Context context) throws BackupException {
        try {
            SQLiteDatabase db = DatabaseHelper.getInstance(context).getWritableDatabase();
            Cursor cursor = db.rawQuery(
                    "SELECT count(*) FROM " + DatabaseHelper.TABLE_CUSTOMERS, null);
            if (cursor != null) {
                cursor.moveToFirst();
                cursor.close();
            }
        } catch (Exception e) {
            throw new BackupException(BackupException.Kind.RESTORE_FAILED,
                    "The restored database could not be opened", e);
        }
    }

    /**
     * Repairs an interrupted restore. Called at application startup, before any
     * database handle is opened.
     * <p>
     * Depending on the marker phase either the previous data is put back (the
     * swap had not happened) or the already-restored data is kept (the swap had
     * happened). A one-time message is queued for the next dashboard launch. If
     * the safety copy is missing or incomplete nothing is touched and the failure
     * is logged.
     */
    public static void recoverIfInterrupted(Context context) {
        File preRestoreDir = new File(context.getFilesDir(), PRE_RESTORE_DIR);
        File markerFile = new File(preRestoreDir, RESTORE_MARKER_NAME);
        if (!markerFile.exists()) {
            return;
        }

        RestoreMarker marker = RestoreMarker.parse(readTextFile(markerFile));
        if (marker == null) {
            Log.e(TAG, "The restore marker exists but could not be read; "
                    + "leaving the current database untouched");
            return;
        }

        File safetyDb = new File(preRestoreDir, DatabaseHelper.DATABASE_NAME);
        boolean safetyExists = safetyDb.exists();
        long safetySize = safetyExists ? safetyDb.length() : 0L;
        RestoreRecovery.Action action = RestoreRecovery.decide(marker, safetyExists, safetySize);

        File dbFile = context.getDatabasePath(DatabaseHelper.DATABASE_NAME);
        File walFile = new File(dbFile.getPath() + "-wal");
        File shmFile = new File(dbFile.getPath() + "-shm");

        try {
            switch (action) {
                case RECOVER_PREVIOUS: {
                    DatabaseHelper.closeInstance();
                    File dbDir = dbFile.getParentFile();
                    if (dbDir != null && !dbDir.exists() && !dbDir.mkdirs()) {
                        throw new IOException("Cannot create the database directory");
                    }
                    deleteIfExists(walFile);
                    deleteIfExists(shmFile);
                    copyFile(safetyDb, dbFile);
                    deleteIfExists(markerFile);
                    setPendingRecovery(context, RECOVERY_RECOVERED_PREVIOUS);
                    Log.i(TAG, "Interrupted restore recovered: the previous data was restored");
                    break;
                }
                case KEEP_RESTORED: {
                    deleteIfExists(markerFile);
                    setPendingRecovery(context, RECOVERY_KEPT_RESTORED);
                    Log.i(TAG, "Interrupted restore completed: the restored data was kept");
                    break;
                }
                case CLEAR_ONLY: {
                    deleteIfExists(markerFile);
                    Log.i(TAG, "Interrupted restore cleared; there was no previous data to restore");
                    break;
                }
                case ABORT: {
                    Log.e(TAG, "The interrupted restore could not be repaired: the safety copy "
                            + "is missing or incomplete; the current database was left untouched");
                    break;
                }
                case NOTHING:
                default:
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to recover an interrupted restore; "
                    + "the current database was left untouched", e);
        }
    }

    /**
     * Returns and clears the one-time message queued by recovery, so it is shown
     * exactly once (on the next dashboard launch).
     *
     * @return one of {@link #RECOVERY_NONE}, {@link #RECOVERY_RECOVERED_PREVIOUS}
     *         or {@link #RECOVERY_KEPT_RESTORED}.
     */
    public static int consumePendingRecoveryMessage(Context context) {
        int value = prefs(context).getInt(KEY_PENDING_RECOVERY, RECOVERY_NONE);
        if (value != RECOVERY_NONE) {
            prefs(context).edit().remove(KEY_PENDING_RECOVERY).apply();
        }
        return value;
    }

    private static void setPendingRecovery(Context context, int value) {
        prefs(context).edit().putInt(KEY_PENDING_RECOVERY, value).apply();
    }

    private static void writeMarker(File markerFile, RestoreMarker marker) throws IOException {
        try (FileOutputStream out = new FileOutputStream(markerFile)) {
            out.write(marker.toText().getBytes(StandardCharsets.UTF_8));
            out.flush();
            // Make the marker survive an abrupt process kill.
            out.getFD().sync();
        }
    }

    private static String readTextFile(File file) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            copyStream(in, buffer);
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Puts the safety copy back over the current database.
     *
     * @return true when the previous data was restored successfully
     */
    private static boolean rollback(File dbFile, File walFile, File shmFile,
                                    File safetyDb, boolean hadDatabase) {
        try {
            // Any handle opened by verification must be closed first.
            DatabaseHelper.closeInstance();
            deleteIfExists(dbFile);
            deleteIfExists(walFile);
            deleteIfExists(shmFile);
            if (hadDatabase) {
                if (!safetyDb.exists() || safetyDb.length() == 0L) {
                    return false;
                }
                copyFile(safetyDb, dbFile);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Rollback failed; the marker is kept so recovery runs next launch", e);
            return false;
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

    private static String readAllText(InputStream in, BackupLimits.Entry entry, String entryName)
            throws IOException, BackupException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        copyStreamLimited(in, buffer, BackupLimits.maxUncompressedBytes(entry), entryName);
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /**
     * Copies until EOF, aborting with {@link BackupException.Kind#TOO_LARGE} as
     * soon as more than {@code maxBytes} have been produced. Real bytes are
     * counted, so a zip header cannot understate the true size.
     */
    private static void copyStreamLimited(InputStream in, OutputStream out, long maxBytes,
                                          String entryName) throws IOException, BackupException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new BackupException(BackupException.Kind.TOO_LARGE,
                        "The backup entry is too large: " + entryName);
            }
            out.write(buffer, 0, read);
        }
    }

    private static void copyStream(InputStream in, File target, BackupLimits.Entry entry,
                                   String entryName) throws IOException, BackupException {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            copyStreamLimited(in, out, BackupLimits.maxUncompressedBytes(entry), entryName);
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            copyStream(in, out);
        }
    }

    /** @return the declared size of the picked file, or -1 when it cannot be read. */
    private static long sourceSizeBytes(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    uri, new String[]{OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (Exception ignored) {
            // Unknown size; the streaming size checks still apply.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return -1L;
    }

    /**
     * @return free space (bytes) usable for databases and caches, or
     *         {@link Long#MAX_VALUE} when it cannot be determined.
     */
    private static long usableSpace(Context context) {
        File dir = context.getFilesDir();
        if (dir == null) {
            return Long.MAX_VALUE;
        }
        return dir.getUsableSpace();
    }

    /** Counts the bytes actually read from the underlying stream. */
    private static final class CountingInputStream extends FilterInputStream {

        long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int result = super.read();
            if (result != -1) {
                count++;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int result = super.read(b, off, len);
            if (result > 0) {
                count += result;
            }
            return result;
        }

        @Override
        public long skip(long n) throws IOException {
            // Bypass FilterInputStream.skip so the skipped bytes are still counted.
            long skipped = in.skip(n);
            if (skipped > 0) {
                count += skipped;
            }
            return skipped;
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
