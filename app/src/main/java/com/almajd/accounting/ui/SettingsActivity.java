package com.almajd.accounting.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.almajd.accounting.R;
import com.almajd.accounting.backup.BackupException;
import com.almajd.accounting.backup.BackupManager;
import com.almajd.accounting.backup.ValidatedBackup;
import com.almajd.accounting.util.AppSettings;
import com.almajd.accounting.util.DateUtils;
import com.almajd.accounting.util.ShareHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Settings screen where the user can edit business information
 * (company name AR/EN, owner name AR/EN, phone number).
 * Values are persisted to SharedPreferences via AppSettings and
 * used by PdfExporter for the bilingual invoice/report headers.
 * <p>
 * Also hosts the backup / restore section.
 */
public class SettingsActivity extends BaseActivity {

    private static final int REQUEST_RESTORE_BACKUP = 1001;

    private TextInputEditText etCompanyNameAr;
    private TextInputEditText etCompanyNameEn;
    private TextInputEditText etOwnerNameAr;
    private TextInputEditText etOwnerNameEn;
    private TextInputEditText etPhone;

    private TextView tvLastBackup;
    private MaterialButton btnCreateBackup;
    private MaterialButton btnRestoreBackup;

    private AppSettings settings;

    /** Single background thread for backup/restore file work. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

        // Backup & restore
        tvLastBackup = findViewById(R.id.tv_last_backup);
        btnCreateBackup = findViewById(R.id.btn_create_backup);
        btnRestoreBackup = findViewById(R.id.btn_restore_backup);
        btnCreateBackup.setOnClickListener(v -> startBackup());
        btnRestoreBackup.setOnClickListener(v -> openRestorePicker());
        refreshLastBackupText();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
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

    // ------------------------------------------------------------------
    // Backup
    // ------------------------------------------------------------------

    private void refreshLastBackupText() {
        long lastBackup = BackupManager.getLastBackupTimeMillis(this);
        if (lastBackup <= 0) {
            tvLastBackup.setText(R.string.backup_last_never);
        } else {
            tvLastBackup.setText(getString(R.string.backup_last_at,
                    DateUtils.humanTimestamp(lastBackup)));
        }
    }

    private void startBackup() {
        setBackupButtonsEnabled(false);
        executor.execute(() -> {
            try {
                File zip = BackupManager.createBackup(getApplicationContext());
                runOnUiThread(() -> shareBackup(zip));
            } catch (BackupException e) {
                runOnUiThread(() -> {
                    setBackupButtonsEnabled(true);
                    toastFor(e);
                });
            }
        });
    }

    private void shareBackup(File zip) {
        boolean launched = ShareHelper.shareFile(this, zip, BackupManager.backupMimeType());
        setBackupButtonsEnabled(true);
        if (launched) {
            // Only remember the backup once the user actually got a chance to
            // save it somewhere outside the app.
            BackupManager.setLastBackupTimeMillis(this, System.currentTimeMillis());
            refreshLastBackupText();
        }
    }

    // ------------------------------------------------------------------
    // Restore
    // ------------------------------------------------------------------

    private void openRestorePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream"
        });
        startActivityForResult(intent, REQUEST_RESTORE_BACKUP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RESTORE_BACKUP
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {
            validateAndConfirmRestore(data.getData());
        }
    }

    private void validateAndConfirmRestore(Uri uri) {
        setBackupButtonsEnabled(false);
        executor.execute(() -> {
            try {
                ValidatedBackup backup = BackupManager.validateBackup(getApplicationContext(), uri);
                runOnUiThread(() -> {
                    setBackupButtonsEnabled(true);
                    showRestoreConfirmation(backup);
                });
            } catch (BackupException e) {
                runOnUiThread(() -> {
                    setBackupButtonsEnabled(true);
                    toastFor(e);
                });
            }
        });
    }

    private void showRestoreConfirmation(ValidatedBackup backup) {
        String message = getString(R.string.restore_confirm_message,
                backup.manifest.createdAt,
                backup.manifest.customerCount,
                backup.manifest.invoiceCount,
                backup.manifest.paymentCount);
        new AlertDialog.Builder(this)
                .setTitle(R.string.restore_confirm_title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> performRestore(backup))
                .show();
    }

    private void performRestore(ValidatedBackup backup) {
        setBackupButtonsEnabled(false);
        executor.execute(() -> {
            try {
                BackupManager.restoreBackup(getApplicationContext(), backup);
                runOnUiThread(this::onRestoreSucceeded);
            } catch (BackupException e) {
                runOnUiThread(() -> {
                    setBackupButtonsEnabled(true);
                    toastFor(e);
                });
            }
        });
    }

    private void onRestoreSucceeded() {
        Toast.makeText(this, R.string.restore_success, Toast.LENGTH_LONG).show();
        // Restart cleanly so the DatabaseHelper singleton is rebuilt.
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }

    private void setBackupButtonsEnabled(boolean enabled) {
        if (btnCreateBackup != null) {
            btnCreateBackup.setEnabled(enabled);
        }
        if (btnRestoreBackup != null) {
            btnRestoreBackup.setEnabled(enabled);
        }
    }

    /** Maps a backup failure to a localized message. */
    private void toastFor(BackupException e) {
        int messageRes;
        switch (e.getKind()) {
            case WRONG_APP:
                messageRes = R.string.restore_wrong_app;
                break;
            case NEWER_VERSION:
                messageRes = R.string.restore_newer_version;
                break;
            case UNSUPPORTED_VERSION:
                messageRes = R.string.restore_unsupported_version;
                break;
            case MISSING_DB:
                messageRes = R.string.restore_missing_db;
                break;
            case DB_CORRUPT:
                messageRes = R.string.restore_db_corrupt;
                break;
            case TOO_LARGE:
                messageRes = R.string.restore_too_large;
                break;
            case NOT_ENOUGH_SPACE:
                messageRes = R.string.restore_not_enough_space;
                break;
            case RESTORE_FAILED:
                messageRes = R.string.restore_failed_rolled_back;
                break;
            case BACKUP_FAILED:
                messageRes = R.string.backup_failed;
                break;
            case INVALID_FILE:
            default:
                messageRes = R.string.restore_invalid_file;
                break;
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
    }
}
