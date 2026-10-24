package com.almajd.accounting.backup;

/**
 * Contents of the {@code pre_restore/RESTORE_IN_PROGRESS} marker file.
 * <p>
 * The marker makes a restore recoverable: it is written once the safety copy is
 * complete and only removed when the new database is in place, the settings are
 * restored and the new database opens. If the app dies in between, the marker is
 * still there on the next launch.
 * <p>
 * The {@link #phase} tells recovery how far the swap got, so an interrupted
 * restore does not silently throw away data that was already installed. The
 * recorded {@link #safetySizeBytes} lets recovery verify that the safety copy is
 * complete before trusting it.
 * <p>
 * Pure Java (no Android APIs) so the parsing and the recovery decision can be
 * covered by plain JVM unit tests.
 */
public final class RestoreMarker {

    /** The safety copy is complete; the new database is not (knowably) in place yet. */
    public static final int PHASE_SAFETY_TAKEN = 1;

    /** The new database has been swapped in; settings/verification may still be pending. */
    public static final int PHASE_DB_INSTALLED = 2;

    private static final String HEADER = "RESTORE_IN_PROGRESS";
    private static final String KEY_PHASE = "phase";
    private static final String KEY_HAD_DB = "had_db";
    private static final String KEY_SAFETY_SIZE = "safety_size";

    public final int phase;
    public final boolean hadDatabase;
    public final long safetySizeBytes;

    public RestoreMarker(int phase, boolean hadDatabase, long safetySizeBytes) {
        this.phase = phase;
        this.hadDatabase = hadDatabase;
        this.safetySizeBytes = safetySizeBytes;
    }

    /** @return true when the new database has already been swapped in. */
    public boolean isDbInstalled() {
        return phase >= PHASE_DB_INSTALLED;
    }

    /** @return the same marker advanced to the "database installed" phase. */
    public RestoreMarker asDbInstalled() {
        return new RestoreMarker(PHASE_DB_INSTALLED, hadDatabase, safetySizeBytes);
    }

    /** @return the marker as text, written to the marker file. */
    public String toText() {
        return HEADER + "\n"
                + KEY_PHASE + "=" + phase + "\n"
                + KEY_HAD_DB + "=" + (hadDatabase ? 1 : 0) + "\n"
                + KEY_SAFETY_SIZE + "=" + safetySizeBytes + "\n";
    }

    /**
     * Parses the marker text.
     *
     * @return the parsed marker, or null when the text is missing/incomplete so
     *         the caller treats the situation as unknown and changes nothing.
     */
    public static RestoreMarker parse(String text) {
        if (text == null) {
            return null;
        }
        Integer phase = null;
        Boolean hadDb = null;
        Long safetySize = null;
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            try {
                if (KEY_PHASE.equals(key)) {
                    phase = Integer.parseInt(value);
                } else if (KEY_HAD_DB.equals(key)) {
                    hadDb = "1".equals(value);
                } else if (KEY_SAFETY_SIZE.equals(key)) {
                    safetySize = Long.parseLong(value);
                }
            } catch (NumberFormatException ignored) {
                // A garbled field leaves the corresponding value null.
            }
        }
        if (phase == null || hadDb == null || safetySize == null) {
            return null;
        }
        return new RestoreMarker(phase, hadDb, safetySize);
    }
}
