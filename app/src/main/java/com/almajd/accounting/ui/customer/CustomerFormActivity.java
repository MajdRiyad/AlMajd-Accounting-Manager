package com.almajd.accounting.ui.customer;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.almajd.accounting.R;
import com.almajd.accounting.ui.BaseActivity;
import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.util.AppExecutors;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Form activity for adding a new customer or editing an existing one.
 * <p>
 * When {@link CustomerListActivity#EXTRA_STORE_NAME} is present in the
 * launching intent the activity opens in EDIT mode; otherwise it opens
 * in ADD mode.
 * <p>
 * Layout: activity_customer_form.xml
 */
public class CustomerFormActivity extends BaseActivity {

    /** Re-use the same extra key defined in the list activity. */
    public static final String EXTRA_STORE_NAME = "extra_store_name";

    private TextInputLayout tilOwnerName;
    private TextInputLayout tilStoreName;
    private TextInputLayout tilLocation;
    private TextInputLayout tilPhone;

    private EditText etOwnerName;
    private EditText etStoreName;
    private EditText etLocation;
    private EditText etPhone;

    private Button btnSave;
    private CustomerDao customerDao;

    /** True when editing an existing customer. */
    private boolean isEditMode;

    /** The original store name passed via intent (edit mode only). */
    private String originalStoreName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_form);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // DAO
        customerDao = new CustomerDao(this);

        // TextInputLayouts (for error messages)
        tilOwnerName = findViewById(R.id.til_owner_name);
        tilStoreName = findViewById(R.id.til_store_name);
        tilLocation = findViewById(R.id.til_location);
        tilPhone = findViewById(R.id.til_phone);

        // EditTexts
        etOwnerName = findViewById(R.id.et_owner_name);
        etStoreName = findViewById(R.id.et_store_name);
        etLocation = findViewById(R.id.et_location);
        etPhone = findViewById(R.id.et_phone);

        // Save button
        btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSaveClicked();
            }
        });

        // Determine mode
        originalStoreName = getIntent().getStringExtra(EXTRA_STORE_NAME);
        isEditMode = !TextUtils.isEmpty(originalStoreName);

        if (isEditMode) {
            // Edit mode: load existing customer and populate fields
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.edit_customer);
            }
            // Keep save disabled until the existing data has loaded.
            btnSave.setEnabled(false);
            populateForEdit();
        } else {
            // Add mode
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.add_customer);
            }
        }
    }

    /**
     * Loads the existing customer from the database (background thread) and
     * fills in the form on the main thread. The store name field is disabled
     * because it serves as the primary key.
     */
    private void populateForEdit() {
        final String storeName = originalStoreName;
        AppExecutors.runOnDb(() -> {
            final Customer customer = customerDao.getByStoreName(storeName);
            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (customer == null) {
                    // Customer no longer exists -- close gracefully
                    Toast.makeText(CustomerFormActivity.this,
                            R.string.no_data, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                etOwnerName.setText(customer.getOwnerName());
                etStoreName.setText(customer.getStoreName());
                etLocation.setText(customer.getLocation());
                etPhone.setText(customer.getPhone());

                // Disable store name editing (it is the unique key)
                etStoreName.setEnabled(false);
                etStoreName.setFocusable(false);

                btnSave.setEnabled(true);
            });
        });
    }

    /**
     * Validates input on the main thread, then persists the customer on the
     * background thread (including the add-mode uniqueness check) and reports
     * the result back on the main thread. The save button is disabled while the
     * write is in flight so a double tap can only insert once.
     */
    private void onSaveClicked() {
        // Clear previous errors
        tilOwnerName.setError(null);
        tilStoreName.setError(null);

        String ownerName = getText(etOwnerName);
        String storeName = getText(etStoreName);
        String location = getText(etLocation);
        String phone = getText(etPhone);

        // ── Validation ───────────────────────────────────────────────
        boolean valid = true;

        if (TextUtils.isEmpty(ownerName)) {
            tilOwnerName.setError(getString(R.string.required_field));
            valid = false;
        }

        if (TextUtils.isEmpty(storeName)) {
            tilStoreName.setError(getString(R.string.required_field));
            valid = false;
        }

        if (!valid) {
            return;
        }

        // ── Build and persist ────────────────────────────────────────
        final Customer customer = new Customer();
        customer.setOwnerName(ownerName);
        customer.setStoreName(storeName);
        customer.setLocation(location);
        customer.setPhone(phone);

        final boolean editMode = isEditMode;
        btnSave.setEnabled(false);

        AppExecutors.runOnDb(() -> {
            boolean duplicate = false;
            boolean success;
            if (editMode) {
                success = customerDao.update(customer);
            } else {
                // In ADD mode, verify uniqueness of the store name on this thread
                // so the check and the insert cannot race.
                Customer existing = customerDao.getByStoreName(storeName);
                if (existing != null) {
                    duplicate = true;
                    success = false;
                } else {
                    success = customerDao.insert(customer);
                }
            }

            final boolean isDuplicate = duplicate;
            final boolean saved = success;
            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (isDuplicate) {
                    tilStoreName.setError(getString(R.string.store_exists));
                    btnSave.setEnabled(true);
                    return;
                }
                if (saved) {
                    Toast.makeText(CustomerFormActivity.this,
                            R.string.customer_saved, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(CustomerFormActivity.this,
                            R.string.save_failed, Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                }
            });
        });
    }

    /**
     * Convenience: returns the trimmed text of an EditText, or empty string.
     */
    private String getText(EditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    // ── Navigation ───────────────────────────────────────────────────

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
