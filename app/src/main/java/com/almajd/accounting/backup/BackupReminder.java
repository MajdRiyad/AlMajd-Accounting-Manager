package com.almajd.accounting.backup;

/**
 * Decides whether the weekly backup reminder should be shown on the dashboard.
 * <p>
 * Pure Java (no Android APIs) so it can be covered by plain JVM unit tests.
 */
public final class BackupReminder {

    /** The reminder appears once the last backup is older than this interval. */
    public static final long REMINDER_INTERVAL_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    private BackupReminder() {
    }

    /**
     * @param hasData           the database contains at least one customer, invoice or payment
     * @param hasBackup         a backup has been created at least once on this device
     * @param lastBackupMillis  time of the last backup (ignored when {@code hasBackup} is false)
     * @param nowMillis         current time
     * @return true when the reminder banner should be shown
     */
    public static boolean shouldRemind(boolean hasData, boolean hasBackup,
                                       long lastBackupMillis, long nowMillis) {
        if (!hasData) {
            return false;
        }
        if (!hasBackup) {
            return true;
        }
        return (nowMillis - lastBackupMillis) > REMINDER_INTERVAL_MILLIS;
    }

    /** Whole days elapsed since the last backup (never negative). */
    public static long daysSince(long lastBackupMillis, long nowMillis) {
        long diff = nowMillis - lastBackupMillis;
        if (diff <= 0) {
            return 0;
        }
        return diff / (24L * 60L * 60L * 1000L);
    }
}
