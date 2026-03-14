package com.almajd.accounting.ui;

import android.os.Bundle;
import android.widget.Toast;

import com.almajd.accounting.R;
import com.almajd.accounting.util.AppSettings;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Settings screen where the user can edit business information
 * (company name AR/EN, owner name AR/EN, phone number).
 * Values are persisted to SharedPreferences via AppSettings and
 * used by PdfExporter for the bilingual invoice/report headers.
 */
public class SettingsActivity extends BaseActivity {

    private TextInputEditText etCompanyNameAr;
    private TextInputEditText etCompanyNameEn;
    private TextInputEditText etOwnerNameAr;
    private TextInputEditText etOwnerNameEn;
    private TextInputEditText etPhone;

    private AppSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settings = new AppSettings(this);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Fields
        etCompanyNameAr = findViewById(R.id.et_company_name_ar);
        etCompanyNameEn = findViewById(R.id.et_company_name_en);
        etOwnerNameAr = findViewById(R.id.et_owner_name_ar);
        etOwnerNameEn = findViewById(R.id.et_owner_name_en);
        etPhone = findViewById(R.id.et_phone);

        // Load current values
        etCompanyNameAr.setText(settings.getCompanyNameAr());
        etCompanyNameEn.setText(settings.getCompanyNameEn());
        etOwnerNameAr.setText(settings.getOwnerNameAr());
        etOwnerNameEn.setText(settings.getOwnerNameEn());
        etPhone.setText(settings.getPhone());

        // Save button
        MaterialButton btnSave = findViewById(R.id.btn_save_settings);
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void saveSettings() {
        String companyAr = getText(etCompanyNameAr);
        String companyEn = getText(etCompanyNameEn);
        String ownerAr = getText(etOwnerNameAr);
        String ownerEn = getText(etOwnerNameEn);
        String phone = getText(etPhone);

        // Basic validation: company name (at least one language) is required
        if (companyAr.isEmpty() && companyEn.isEmpty()) {
            etCompanyNameAr.setError(getString(R.string.required_field));
            etCompanyNameAr.requestFocus();
            return;
        }

        settings.saveAll(companyAr, companyEn, ownerAr, ownerEn, phone);
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
