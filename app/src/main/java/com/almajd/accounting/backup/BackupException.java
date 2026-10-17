package com.almajd.accounting.backup;

/**
 * Thrown when a backup cannot be created, or when a chosen restore file is
 * rejected / a restore fails.
 * <p>
 * A {@link Kind} is attached so the UI can show a localized message instead of
 * the developer-facing English detail.
 */
public class BackupException extends Exception {

    public enum Kind {
        /** Uncategorized failure. */
        GENERIC,
        /** Not a readable zip, or required entries are missing/invalid. */
        INVALID_FILE,
        /** The backup belongs to a different app. */
        WRONG_APP,
        /** The backup was produced by a newer app version. */
        NEWER_VERSION,
        /** The backup is too old / has an unsupported schema version. */
        UNSUPPORTED_VERSION,
        /** The database inside the backup is corrupt. */
        DB_CORRUPT,
        /** The database entry is missing from the archive. */
        MISSING_DB,
        /** The backup file or one of its entries exceeds the allowed size. */
        TOO_LARGE,
        /** There is not enough free space to validate/replace the database. */
        NOT_ENOUGH_SPACE,
        /** Restoring failed; the previous data was kept. */
        RESTORE_FAILED,
        /** Creating the backup failed. */
        BACKUP_FAILED
    }

    private final Kind kind;

    public BackupException(String message) {
        this(Kind.GENERIC, message, null);
    }

    public BackupException(String message, Throwable cause) {
        this(Kind.GENERIC, message, cause);
    }

    public BackupException(Kind kind, String message) {
        this(kind, message, null);
    }

    public BackupException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
