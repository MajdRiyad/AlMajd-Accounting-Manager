package com.almajd.accounting.backup;

/**
 * Size limits and free-space rules used while validating and restoring a backup.
 * <p>
 * Pure Java (no Android APIs) so the checks can be covered by plain JVM unit
 * tests. The limits protect against zip bombs and unexpectedly huge files.
 */
public final class BackupLimits {

    /** Maximum size of the backup zip file itself (compressed bytes). 200 MB. */
    public static final long MAX_COMPRESSED_BYTES = 200L * 1024L * 1024L;

    /** Maximum uncompressed size of the database entry. 500 MB. */
    public static final long MAX_DB_UNCOMPRESSED_BYTES = 500L * 1024L * 1024L;

    /** Maximum uncompressed size of a JSON entry (manifest/settings). 1 MB. */
    public static final long MAX_JSON_UNCOMPRESSED_BYTES = 1L * 1024L * 1024L;

    /**
     * Free space needed relative to the database size before extracting/replacing.
     * Two copies are involved (the safety copy of the current DB and the staging
     * copy of the incoming DB), so roughly 2x is required.
     */
    public static final long FREE_SPACE_MULTIPLIER = 2L;

    /** The kind of archive entry a size limit applies to. */
    public enum Entry {
        DATABASE,
        JSON
    }

    private BackupLimits() {
    }

    /** @return the uncompressed size limit for the given entry kind, in bytes. */
    public static long maxUncompressedBytes(Entry entry) {
        return entry == Entry.DATABASE ? MAX_DB_UNCOMPRESSED_BYTES : MAX_JSON_UNCOMPRESSED_BYTES;
    }

    /** @return true when a backup file of {@code compressedBytes} is allowed. */
    public static boolean isCompressedSizeAllowed(long compressedBytes) {
        return compressedBytes >= 0 && compressedBytes <= MAX_COMPRESSED_BYTES;
    }

    /** @return true when an extracted entry of {@code uncompressedBytes} is allowed. */
    public static boolean isUncompressedSizeAllowed(Entry entry, long uncompressedBytes) {
        return uncompressedBytes >= 0 && uncompressedBytes <= maxUncompressedBytes(entry);
    }

    /** @return the free space (bytes) required to safely extract a DB of {@code databaseBytes}. */
    public static long requiredFreeBytes(long databaseBytes) {
        if (databaseBytes <= 0) {
            return 0L;
        }
        // Guard against overflow for absurd inputs; the DB cap makes this moot.
        long required = databaseBytes * FREE_SPACE_MULTIPLIER;
        if (required < databaseBytes) {
            return Long.MAX_VALUE;
        }
        return required;
    }

    /** @return true when {@code freeBytes} is enough for a DB of {@code databaseBytes}. */
    public static boolean hasEnoughFreeSpace(long databaseBytes, long freeBytes) {
        return freeBytes >= requiredFreeBytes(databaseBytes);
    }
}
