package com.almajd.accounting.backup;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Builds the backup file name.
 * <p>
 * The name is {@code almajd-backup-YYYY-MM-DD_HH-mm-ss.zip}. Seconds are
 * included so two backups taken within the same minute get different names
 * (the previous minute-resolution name collided), while the timestamp stays
 * lexicographically sortable.
 * <p>
 * Pure Java (no Android APIs) so the format can be covered by plain JVM unit
 * tests. Uses a fixed {@link Locale#US} locale so the digits are always ASCII
 * regardless of the forced Arabic app locale.
 */
public final class BackupNaming {

    public static final String PREFIX = "almajd-backup-";
    public static final String SUFFIX = ".zip";

    /** Timestamp pattern; seconds included so same-minute backups differ. */
    public static final String TIMESTAMP_PATTERN = "yyyy-MM-dd_HH-mm-ss";

    private BackupNaming() {
    }

    /** @return the timestamp part for the given instant (never null). */
    public static String timestamp(long epochMillis) {
        return new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).format(new Date(epochMillis));
    }

    /** @return the full backup file name for the given timestamp string. */
    public static String fileName(String timestamp) {
        return PREFIX + (timestamp == null ? "" : timestamp) + SUFFIX;
    }

    /** @return the full backup file name for the given instant. */
    public static String fileName(long epochMillis) {
        return fileName(timestamp(epochMillis));
    }
}
