package com.almajd.accounting.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Provides access to app-wide settings stored in SharedPreferences.
 * Used by PdfExporter for bilingual invoice headers and by SettingsActivity
 * for the user-editable configuration screen.
 */
public class AppSettings {

    private static final String PREFS_NAME = "almajd_settings";

    private static final String KEY_COMPANY_NAME_AR = "company_name_ar";
    private static final String KEY_COMPANY_NAME_EN = "company_name_en";
    private static final String KEY_OWNER_NAME_AR = "owner_name_ar";
    private static final String KEY_OWNER_NAME_EN = "owner_name_en";
    private static final String KEY_PHONE = "phone";

    // Defaults
    private static final String DEF_COMPANY_NAME_AR = "\u0627\u0644\u0645\u062C\u062F \u0644\u0644\u0627\u062A\u0635\u0627\u0644\u0627\u062A";  // المجد للاتصالات
    private static final String DEF_COMPANY_NAME_EN = "AlMajd Communications";
    private static final String DEF_OWNER_NAME_AR = "\u0631\u064A\u0627\u0636 \u0628\u0631\u0647\u0645";  // رياض برهم
    private static final String DEF_OWNER_NAME_EN = "Riyad Barham";
    private static final String DEF_PHONE = "";

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Getters ---

    public String getCompanyNameAr() {
        return prefs.getString(KEY_COMPANY_NAME_AR, DEF_COMPANY_NAME_AR);
    }

    public String getCompanyNameEn() {
        return prefs.getString(KEY_COMPANY_NAME_EN, DEF_COMPANY_NAME_EN);
    }

    public String getOwnerNameAr() {
        return prefs.getString(KEY_OWNER_NAME_AR, DEF_OWNER_NAME_AR);
    }

    public String getOwnerNameEn() {
        return prefs.getString(KEY_OWNER_NAME_EN, DEF_OWNER_NAME_EN);
    }

    public String getPhone() {
        return prefs.getString(KEY_PHONE, DEF_PHONE);
    }

    // --- Setters ---

    public void setCompanyNameAr(String value) {
        prefs.edit().putString(KEY_COMPANY_NAME_AR, value).apply();
    }

    public void setCompanyNameEn(String value) {
        prefs.edit().putString(KEY_COMPANY_NAME_EN, value).apply();
    }

    public void setOwnerNameAr(String value) {
        prefs.edit().putString(KEY_OWNER_NAME_AR, value).apply();
    }

    public void setOwnerNameEn(String value) {
        prefs.edit().putString(KEY_OWNER_NAME_EN, value).apply();
    }

    public void setPhone(String value) {
        prefs.edit().putString(KEY_PHONE, value).apply();
    }

    /**
     * Saves all settings at once (more efficient than calling individual setters).
     */
    public void saveAll(String companyNameAr, String companyNameEn,
                        String ownerNameAr, String ownerNameEn, String phone) {
        prefs.edit()
                .putString(KEY_COMPANY_NAME_AR, companyNameAr)
                .putString(KEY_COMPANY_NAME_EN, companyNameEn)
                .putString(KEY_OWNER_NAME_AR, ownerNameAr)
                .putString(KEY_OWNER_NAME_EN, ownerNameEn)
                .putString(KEY_PHONE, phone)
                .apply();
    }
}
