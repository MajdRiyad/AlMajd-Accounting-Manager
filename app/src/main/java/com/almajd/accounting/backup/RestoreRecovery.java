package com.almajd.accounting.backup;

/**
 * Decides what to do on startup when a {@code RESTORE_IN_PROGRESS} marker is
 * found, i.e. when a previous restore was interrupted.
 * <p>
 * Pure Java (no Android APIs) so every branch can be covered by plain JVM unit
 * tests; {@link BackupManager#recoverIfInterrupted} only performs the file work
 * the decision asks for.
 */
public final class RestoreRecovery {

    /** What the caller must do with the current files. */
    public enum Action {
        /** No marker: nothing to do. */
        NOTHING,
        /** Marker exists but there was no previous database: just remove the marker. */
        CLEAR_ONLY,
        /** Put the previous database back from the safety copy and remove the marker. */
        RECOVER_PREVIOUS,
        /** The new database is already in place: keep it and remove the marker. */
        KEEP_RESTORED,
        /** The safety copy is missing/incomplete: change nothing at all (and log). */
        ABORT
    }

    private RestoreRecovery() {
    }

    /**
     * @param marker             the parsed marker (null when absent/unreadable)
     * @param safetyFileExists   whether the safety database copy exists
     * @param safetyFileSizeBytes the safety copy's actual size, in bytes
     * @return the action the caller must take
     */
    public static Action decide(RestoreMarker marker,
                                boolean safetyFileExists,
                                long safetyFileSizeBytes) {
        if (marker == null) {
            return Action.NOTHING;
        }
        // The swap already happened: never roll back the freshly restored data.
        if (marker.isDbInstalled()) {
            return Action.KEEP_RESTORED;
        }
        // Phase "safety taken": the previous database must be recoverable.
        if (!marker.hadDatabase) {
            // There was no previous database, so there is nothing to roll back
            // to; dropping the marker lets the app create a fresh database.
            return Action.CLEAR_ONLY;
        }
        if (!safetyFileExists) {
            return Action.ABORT;
        }
        if (marker.safetySizeBytes <= 0 || safetyFileSizeBytes != marker.safetySizeBytes) {
            return Action.ABORT;
        }
        return Action.RECOVER_PREVIOUS;
    }
}
