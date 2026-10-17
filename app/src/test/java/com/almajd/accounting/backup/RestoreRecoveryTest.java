package com.almajd.accounting.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RestoreRecoveryTest {

    private static RestoreMarker safetyTaken(boolean hadDatabase, long size) {
        return new RestoreMarker(RestoreMarker.PHASE_SAFETY_TAKEN, hadDatabase, size);
    }

    @Test
    public void markerRoundTripsThroughText() {
        RestoreMarker marker = safetyTaken(true, 4096L);
        RestoreMarker parsed = RestoreMarker.parse(marker.toText());
        assertEquals(marker.phase, parsed.phase);
        assertEquals(marker.hadDatabase, parsed.hadDatabase);
        assertEquals(marker.safetySizeBytes, parsed.safetySizeBytes);
    }

    @Test
    public void malformedMarkerDoesNotParse() {
        assertNull(RestoreMarker.parse(null));
        assertNull(RestoreMarker.parse(""));
        assertNull(RestoreMarker.parse("garbage"));
        assertNull(RestoreMarker.parse("phase=1\nhad_db=1")); // safety_size missing
    }

    @Test
    public void noMarkerMeansNothingToDo() {
        assertEquals(RestoreRecovery.Action.NOTHING,
                RestoreRecovery.decide(null, true, 100L));
    }

    @Test
    public void safetyTakenWithIntactCopyRecoversPreviousData() {
        assertEquals(RestoreRecovery.Action.RECOVER_PREVIOUS,
                RestoreRecovery.decide(safetyTaken(true, 500L), true, 500L));
    }

    @Test
    public void safetyTakenWithMissingCopyAborts() {
        assertEquals(RestoreRecovery.Action.ABORT,
                RestoreRecovery.decide(safetyTaken(true, 500L), false, 0L));
    }

    @Test
    public void safetyTakenWithWrongSizeAborts() {
        assertEquals(RestoreRecovery.Action.ABORT,
                RestoreRecovery.decide(safetyTaken(true, 500L), true, 499L));
        assertEquals(RestoreRecovery.Action.ABORT,
                RestoreRecovery.decide(safetyTaken(true, 0L), true, 0L));
    }

    @Test
    public void dbInstalledKeepsRestoredData() {
        RestoreMarker installed = safetyTaken(true, 500L).asDbInstalled();
        assertEquals(RestoreRecovery.Action.KEEP_RESTORED,
                RestoreRecovery.decide(installed, true, 500L));
        // Even if the safety copy vanished, the freshly restored data is kept.
        assertEquals(RestoreRecovery.Action.KEEP_RESTORED,
                RestoreRecovery.decide(installed, false, 0L));
    }

    @Test
    public void noPreviousDatabaseJustClearsTheMarker() {
        assertEquals(RestoreRecovery.Action.CLEAR_ONLY,
                RestoreRecovery.decide(safetyTaken(false, 0L), false, 0L));
    }
}
