package com.almajd.accounting.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackupLimitsTest {

    @Test
    public void compressedLimitIsTwoHundredMegabytes() {
        assertEquals(200L * 1024 * 1024, BackupLimits.MAX_COMPRESSED_BYTES);
    }

    @Test
    public void acceptsExactlyTheCompressedLimitAndRejectsAbove() {
        assertTrue(BackupLimits.isCompressedSizeAllowed(BackupLimits.MAX_COMPRESSED_BYTES));
        assertFalse(BackupLimits.isCompressedSizeAllowed(BackupLimits.MAX_COMPRESSED_BYTES + 1));
    }

    @Test
    public void rejectsNegativeCompressedSize() {
        assertFalse(BackupLimits.isCompressedSizeAllowed(-1));
    }

    @Test
    public void databaseEntriesAllowUpToFiveHundredMegabytes() {
        long max = BackupLimits.maxUncompressedBytes(BackupLimits.Entry.DATABASE);
        assertEquals(500L * 1024 * 1024, max);
        assertTrue(BackupLimits.isUncompressedSizeAllowed(BackupLimits.Entry.DATABASE, max));
        assertFalse(BackupLimits.isUncompressedSizeAllowed(BackupLimits.Entry.DATABASE, max + 1));
    }

    @Test
    public void jsonEntriesAllowUpToOneMegabyte() {
        long max = BackupLimits.maxUncompressedBytes(BackupLimits.Entry.JSON);
        assertEquals(1L * 1024 * 1024, max);
        assertTrue(BackupLimits.isUncompressedSizeAllowed(BackupLimits.Entry.JSON, max));
        assertFalse(BackupLimits.isUncompressedSizeAllowed(BackupLimits.Entry.JSON, max + 1));
    }

    @Test
    public void requiredFreeSpaceIsTwiceTheDatabase() {
        assertEquals(2_000L, BackupLimits.requiredFreeBytes(1_000L));
        assertTrue(BackupLimits.hasEnoughFreeSpace(1_000L, 2_000L));
        assertFalse(BackupLimits.hasEnoughFreeSpace(1_000L, 1_999L));
    }

    @Test
    public void zeroSizeNeedsNoFreeSpace() {
        assertEquals(0L, BackupLimits.requiredFreeBytes(0L));
        assertTrue(BackupLimits.hasEnoughFreeSpace(0L, 0L));
    }

    @Test
    public void absurdSizeDoesNotOverflowToNegative() {
        assertTrue(BackupLimits.requiredFreeBytes(Long.MAX_VALUE) > 0L);
    }
}
