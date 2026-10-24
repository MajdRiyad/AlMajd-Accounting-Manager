package com.almajd.accounting.backup;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The contents of {@code manifest.json} inside a backup file.
 * <p>
 * Pure Java (no Android APIs) so the parsing and the version-compatibility
 * rules can be covered by plain JVM unit tests.
 */
public class BackupManifest {

    /** Identifier written into every backup; used to reject foreign files. */
    public static final String APP_ID = "com.almajd.accounting";

    /**
     * The oldest schema version this build is able to restore. Version 1 was a
     * development-only schema whose upgrade path is destructive, so backups
     * claiming anything below this are refused instead of wiping the data.
     */
    public static final int MIN_SUPPORTED_SCHEMA_VERSION = 2;

    public String appId;
    public int schemaVersion;
    public String createdAt;
    public String appVersionName;
    public long customerCount;
    public long invoiceCount;
    public long paymentCount;

    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("app_id", appId);
        map.put("schema_version", schemaVersion);
        map.put("created_at", createdAt == null ? "" : createdAt);
        map.put("app_version_name", appVersionName == null ? "" : appVersionName);
        map.put("customers", customerCount);
        map.put("invoices", invoiceCount);
        map.put("payments", paymentCount);
        return SimpleJson.write(map);
    }

    public static BackupManifest fromJson(String json) throws BackupException {
        Map<String, Object> map;
        try {
            map = SimpleJson.read(json);
        } catch (JsonException e) {
            throw new BackupException(BackupException.Kind.INVALID_FILE,
                    "manifest.json is not valid JSON", e);
        }

        BackupManifest manifest = new BackupManifest();
        manifest.appId = requireString(map, "app_id");
        manifest.schemaVersion = (int) requireLong(map, "schema_version");
        manifest.createdAt = optionalString(map, "created_at");
        manifest.appVersionName = optionalString(map, "app_version_name");
        manifest.customerCount = optionalLong(map, "customers");
        manifest.invoiceCount = optionalLong(map, "invoices");
        manifest.paymentCount = optionalLong(map, "payments");
        return manifest;
    }

    /**
     * Returns true when a backup whose schema version is {@code manifestVersion}
     * can safely be restored by an app whose schema version is
     * {@code appSchemaVersion}.
     * <p>
     * Rule: the backup must not be newer than the app (a newer backup may
     * contain columns this build does not understand), and it must not be older
     * than {@link #MIN_SUPPORTED_SCHEMA_VERSION} (older versions can only be
     * "upgraded" destructively).
     */
    public static boolean isSchemaSupported(int manifestVersion, int appSchemaVersion) {
        return manifestVersion >= MIN_SUPPORTED_SCHEMA_VERSION
                && manifestVersion <= appSchemaVersion;
    }

    /** Validates this manifest against the running app. */
    public void validateForApp(int appSchemaVersion) throws BackupException {
        if (!APP_ID.equals(appId)) {
            throw new BackupException(BackupException.Kind.WRONG_APP,
                    "Backup belongs to a different app: " + appId);
        }
        if (schemaVersion > appSchemaVersion) {
            throw new BackupException(BackupException.Kind.NEWER_VERSION,
                    "Backup schema " + schemaVersion + " is newer than app schema " + appSchemaVersion);
        }
        if (schemaVersion < MIN_SUPPORTED_SCHEMA_VERSION) {
            throw new BackupException(BackupException.Kind.UNSUPPORTED_VERSION,
                    "Backup schema " + schemaVersion + " is not supported");
        }
    }

    private static String requireString(Map<String, Object> map, String key) throws BackupException {
        Object value = map.get(key);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new BackupException(BackupException.Kind.INVALID_FILE,
                    "manifest.json is missing a valid '" + key + "' field");
        }
        return (String) value;
    }

    private static String optionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : "";
    }

    private static long requireLong(Map<String, Object> map, String key) throws BackupException {
        Object value = map.get(key);
        if (!(value instanceof Number)) {
            throw new BackupException(BackupException.Kind.INVALID_FILE,
                    "manifest.json is missing a valid '" + key + "' field");
        }
        return ((Number) value).longValue();
    }

    private static long optionalLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
