package com.almajd.accounting.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackupManifestTest {

    private static BackupManifest sample() {
        BackupManifest manifest = new BackupManifest();
        manifest.appId = BackupManifest.APP_ID;
        manifest.schemaVersion = 2;
        manifest.createdAt = "2026-10-17 16:45";
        manifest.appVersionName = "1.0";
        manifest.customerCount = 4;
        manifest.invoiceCount = 7;
        manifest.paymentCount = 9;
        return manifest;
    }

    @Test
    public void jsonRoundTrip() throws Exception {
        BackupManifest original = sample();
        BackupManifest parsed = BackupManifest.fromJson(original.toJson());

        assertEquals(original.appId, parsed.appId);
        assertEquals(original.schemaVersion, parsed.schemaVersion);
        assertEquals(original.createdAt, parsed.createdAt);
        assertEquals(original.appVersionName, parsed.appVersionName);
        assertEquals(original.customerCount, parsed.customerCount);
        assertEquals(original.invoiceCount, parsed.invoiceCount);
        assertEquals(original.paymentCount, parsed.paymentCount);
    }

    @Test
    public void missingRequiredFieldIsRejected() {
        assertThrows(BackupException.class,
                () -> BackupManifest.fromJson("{\"schema_version\":2}"));
        assertThrows(BackupException.class,
                () -> BackupManifest.fromJson("{\"app_id\":\"com.almajd.accounting\"}"));
    }

    @Test
    public void invalidJsonIsRejected() {
        assertThrows(BackupException.class, () -> BackupManifest.fromJson("not json"));
    }

    @Test
    public void schemaVersionRules() {
        // A backup must not be newer than the app.
        assertTrue(BackupManifest.isSchemaSupported(2, 2));
        assertTrue(BackupManifest.isSchemaSupported(2, 3));
        assertFalse(BackupManifest.isSchemaSupported(3, 2));
        // Version 1 has no safe (non-destructive) upgrade path.
        assertFalse(BackupManifest.isSchemaSupported(1, 2));
        assertFalse(BackupManifest.isSchemaSupported(0, 2));
    }

    @Test
    public void validateForAppAcceptsCurrentAndOlderVersions() throws Exception {
        BackupManifest manifest = sample();
        manifest.validateForApp(2); // same version

        manifest.schemaVersion = 2;
        manifest.validateForApp(3); // app upgraded: allowed, migration brings it up
    }

    @Test
    public void validateForAppRejectsNewerVersion() {
        BackupManifest manifest = sample();
        manifest.schemaVersion = 3;
        BackupException e = assertThrows(BackupException.class, () -> manifest.validateForApp(2));
        assertEquals(BackupException.Kind.NEWER_VERSION, e.getKind());
    }

    @Test
    public void validateForAppRejectsUnsupportedOldVersion() {
        BackupManifest manifest = sample();
        manifest.schemaVersion = 1;
        BackupException e = assertThrows(BackupException.class, () -> manifest.validateForApp(2));
        assertEquals(BackupException.Kind.UNSUPPORTED_VERSION, e.getKind());
    }

    @Test
    public void validateForAppRejectsForeignApp() {
        BackupManifest manifest = sample();
        manifest.appId = "com.example.other";
        BackupException e = assertThrows(BackupException.class, () -> manifest.validateForApp(2));
        assertEquals(BackupException.Kind.WRONG_APP, e.getKind());
    }
}
