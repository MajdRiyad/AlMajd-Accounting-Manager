package com.almajd.accounting.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackupReminderTest {

    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void noReminderWithoutData() {
        assertFalse(BackupReminder.shouldRemind(false, false, 0L, NOW));
        assertFalse(BackupReminder.shouldRemind(false, true, NOW - 30 * DAY, NOW));
    }

    @Test
    public void reminderShownWhenNeverBackedUp() {
        assertTrue(BackupReminder.shouldRemind(true, false, 0L, NOW));
    }

    @Test
    public void noReminderWhenBackupIsRecent() {
        assertFalse(BackupReminder.shouldRemind(true, true, NOW - 3 * DAY, NOW));
        assertFalse(BackupReminder.shouldRemind(true, true, NOW, NOW));
    }

    @Test
    public void exactlySevenDaysIsNotYetReminded() {
        assertFalse(BackupReminder.shouldRemind(true, true, NOW - 7 * DAY, NOW));
    }

    @Test
    public void reminderReappearsAfterSevenDays() {
        assertTrue(BackupReminder.shouldRemind(true, true, NOW - 7 * DAY - 1, NOW));
        assertTrue(BackupReminder.shouldRemind(true, true, NOW - 30 * DAY, NOW));
    }

    @Test
    public void reminderIntervalIsSevenDays() {
        assertEquals(7 * DAY, BackupReminder.REMINDER_INTERVAL_MILLIS);
    }

    @Test
    public void daysSinceIsRoundedDownAndNeverNegative() {
        assertEquals(0L, BackupReminder.daysSince(NOW, NOW));
        assertEquals(1L, BackupReminder.daysSince(NOW - DAY - 5, NOW));
        assertEquals(0L, BackupReminder.daysSince(NOW + DAY, NOW));
    }
}
