package com.almajd.accounting.backup;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A snapshot of the user-editable app settings (company/owner names and phone)
 * that is written to {@code settings.json} inside a backup and restored by
 * {@link BackupManager}.
 * <p>
 * Pure Java (no Android APIs); the mapping to/from {@code AppSettings} lives in
 * {@link BackupManager}.
 */
public class AppSettingsSnapshot {

    public String companyNameAr = "";
    public String companyNameEn = "";
    public String ownerNameAr = "";
    public String ownerNameEn = "";
    public String phone = "";

    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("company_name_ar", companyNameAr);
        map.put("company_name_en", companyNameEn);
        map.put("owner_name_ar", ownerNameAr);
        map.put("owner_name_en", ownerNameEn);
        map.put("phone", phone);
        return SimpleJson.write(map);
    }

    public static AppSettingsSnapshot fromJson(String json) throws BackupException {
        Map<String, Object> map;
        try {
            map = SimpleJson.read(json);
        } catch (JsonException e) {
            throw new BackupException(BackupException.Kind.INVALID_FILE,
                    "settings.json is not valid JSON", e);
        }
        AppSettingsSnapshot snapshot = new AppSettingsSnapshot();
        snapshot.companyNameAr = stringOrDefault(map, "company_name_ar", "");
        snapshot.companyNameEn = stringOrDefault(map, "company_name_en", "");
        snapshot.ownerNameAr = stringOrDefault(map, "owner_name_ar", "");
        snapshot.ownerNameEn = stringOrDefault(map, "owner_name_en", "");
        snapshot.phone = stringOrDefault(map, "phone", "");
        return snapshot;
    }

    private static String stringOrDefault(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : fallback;
    }
}
