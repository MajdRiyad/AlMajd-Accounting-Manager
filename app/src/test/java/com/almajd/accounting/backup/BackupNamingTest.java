package com.almajd.accounting.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class BackupNamingTest {

    @Test
    public void fileNameMatchesTheExpectedFormat() {
        assertEquals("almajd-backup-2026-10-17_14-05-09.zip",
                BackupNaming.fileName("2026-10-17_14-05-09"));
    }

    @Test
    public void fileNameWithNullTimestampStillHasPrefixAndSuffix() {
        assertEquals("almajd-backup-.zip", BackupNaming.fileName((String) null));
    }

    @Test
    public void timestampHasFixedSortableWidth() {
        String timestamp = BackupNaming.timestamp(System.currentTimeMillis());
        assertEquals(19, timestamp.length());
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}"));
    }

    @Test
    public void twoBackupsInTheSameMinuteGetDifferentNames() {
        long first = millisAt(2026, Calendar.JANUARY, 2, 3, 4, 5);
        // 15 seconds later: same minute, different second.
        long second = first + 15_000L;

        assertEquals(BackupNaming.timestamp(first).substring(0, 16),
                BackupNaming.timestamp(second).substring(0, 16));
        assertNotEquals(BackupNaming.timestamp(first), BackupNaming.timestamp(second));
        assertNotEquals(BackupNaming.fileName(first), BackupNaming.fileName(second));
    }

    @Test
    public void laterBackupSortsAfterEarlier() {
        long earlier = millisAt(2026, Calendar.JANUARY, 2, 3, 4, 5);
        long later = millisAt(2026, Calendar.JANUARY, 2, 3, 4, 6);
        assertTrue(BackupNaming.fileName(earlier).compareTo(BackupNaming.fileName(later)) < 0);
    }

    private static long millisAt(int year, int month, int day, int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
