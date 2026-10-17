package com.almajd.accounting.backup;

import java.io.File;

/**
 * The result of validating a chosen backup file, ready to be applied by
 * {@link BackupManager#restoreBackup}.
 * <p>
 * {@link #databaseFile} is a plain SQLite database extracted to a private temp
 * location (fixed name, never derived from the zip entry name).
 */
public class ValidatedBackup {

    public File databaseFile;
    public BackupManifest manifest;
    public AppSettingsSnapshot settings;
}
