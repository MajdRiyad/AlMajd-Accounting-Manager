package com.almajd.accounting.ui.payment;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.almajd.accounting.R;
import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.db.PaymentDao;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.model.Payment;
import com.almajd.accounting.util.DateUtils;
import com.almajd.accounting.util.NumberUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Form for creating or editing a payment.
 * <p>
 * Intent extras:
 * <ul>
 *   <li>{@link PaymentListActivity#EXTRA_PAYMENT_ID} (long) -- if present, the form
 *       loads the existing payment for editing.</li>
 *   <li>{@link PaymentListActivity#EXTRA_STORE_NAME} (String) -- if present, the store
 *       spinner is pre-selected to this store (used when adding a payment from
 *       CustomerDetailActivity or a filtered PaymentListActivity).</li>
 * </ul>
 * <p>
 * Layout: activity_payment_form.xml
 * Contains: Toolbar, Spinner (spinner_store), TextInputEditText fields
 *           (et_amount, et_date, et_notes), MaterialButton (btn_save)
 */
public class PaymentFormActivity extends AppCompatActivity {

    private Spinner spinnerStore;
    private TextInputEditText etAmount;
    private TextInputEditText etDate;
    private TextInputEditText etNotes;
    private TextInputLayout tilAmount;
    private MaterialButton btnSave;

    private PaymentDao paymentDao;
    private CustomerDao customerDao;

    /** Store names list backing the spinner; index 0 is the prompt. */
    private List<String> storeNames;

    /** Non-zero when editing an existing payment. */
    private long existingPaymentId = 0;

    /** True when the form is in edit mode. */
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_form);

        // Determine mode
        if (getIntent() != null && getIntent().hasExtra(PaymentListActivity.EXTRA_PAYMENT_ID)) {
            existingPaymentId = getIntent().getLongExtra(PaymentListActivity.EXTRA_PAYMENT_ID, 0);
            isEditMode = existingPaymentId > 0;
        }
        String preSelectedStore = null;
        if (getIntent() != null && getIntent().hasExtra(PaymentListActivity.EXTRA_STORE_NAME)) {
            preSelectedStore = getIntent().getStringExtra(PaymentListActivity.EXTRA_STORE_NAME);
        }

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(
                    isEditMode ? R.string.edit_payment : R.string.new_payment);
        }

        // DAOs
        paymentDao = new PaymentDao(this);
        customerDao = new CustomerDao(this);

        // Views
        spinnerStore = findViewById(R.id.spinner_store);
        etAmount = findViewById(R.id.et_amount);
        etDate = findViewById(R.id.et_date);
        etNotes = findViewById(R.id.et_notes);
        tilAmount = findViewById(R.id.til_amount);
        btnSave = findViewById(R.id.btn_save);

        // ── Store spinner setup ──────────────────────────────────────────
        setupStoreSpinner();

        // ── Date field setup ─────────────────────────────────────────────
        etDate.setText(DateUtils.todayAsDbFormat());
        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DateUtils.showDatePicker(PaymentFormActivity.this, etDate);
            }
        });

        // ── Edit mode: populate fields from existing payment ─────────────
        if (isEditMode) {
            populateForEdit();
        }

        // ── Pre-select store if provided ─────────────────────────────────
        if (!isEditMode && preSelectedStore != null && !preSelectedStore.isEmpty()) {
            selectStoreInSpinner(preSelectedStore);
        }

        // ── Save button ──────────────────────────────────────────────────
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateAndSave();
            }
        });
    }

    // ── Store spinner ────────────────────────────────────────────────────

    /**
     * Populates the store spinner with a prompt item followed by all customer
     * store names fetched from the database.
     */
    private void setupStoreSpinner() {
        storeNames = new ArrayList<>();
        storeNames.add(getString(R.string.select_store)); // index 0 = prompt

        List<Customer> customers = customerDao.getAll();
        if (customers != null) {
            for (Customer c : customers) {
                if (c.getStoreName() != null && !c.getStoreName().trim().isEmpty()) {
                    storeNames.add(c.getStoreName());
                }
            }
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, storeNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStore.setAdapter(spinnerAdapter);
    }

    /**
     * Selects the given store name in the spinner.
     * If the name is not found the spinner stays on the prompt.
     */
    private void selectStoreInSpinner(String storeName) {
        if (storeName == null || storeNames == null) {
            return;
        }
        for (int i = 0; i < storeNames.size(); i++) {
            if (storeName.equals(storeNames.get(i))) {
                spinnerStore.setSelection(i);
                return;
            }
        }
    }

    // ── Edit-mode population ─────────────────────────────────────────────

    /**
     * Loads the existing payment from the database and fills in all form fields.
     */
    private void populateForEdit() {
        Payment existing = paymentDao.getById(existingPaymentId);
        if (existing == null) {
            // Payment no longer exists -- fall back to add mode
            isEditMode = false;
            existingPaymentId = 0;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.new_payment);
            }
            return;
        }

        // Store
        selectStoreInSpinner(existing.getStoreName());

        // Amount
        etAmount.setText(String.valueOf(existing.getAmount()));

        // Date
        if (existing.getPaymentDate() != null && !existing.getPaymentDate().isEmpty()) {
            etDate.setText(existing.getPaymentDate());
        }

        // Notes
        if (existing.getNotes() != null) {
            etNotes.setText(existing.getNotes());
        }
    }

    // ── Validation & save ────────────────────────────────────────────────

    /**
     * Validates all required fields and, if valid, inserts or updates the payment.
     */
    private void validateAndSave() {
        boolean valid = true;

        // Validate store selection (must not be the prompt at position 0)
        if (spinnerStore.getSelectedItemPosition() == 0) {
            // The spinner prompt is selected -- show a toast because Spinner
            // does not have a built-in error indicator.
            Toast.makeText(this, R.string.select_store, Toast.LENGTH_SHORT).show();
            valid = false;
        }

        // Validate amount
        String amountText = etAmount.getText() != null
                ? etAmount.getText().toString().trim() : "";
        double amount = 0;
        if (amountText.isEmpty()) {
            tilAmount.setError(getString(R.string.required_field));
            valid = false;
        } else {
            try {
                amount = NumberUtils.parseAmount(amountText);
                if (amount <= 0) {
                    tilAmount.setError(getString(R.string.required_field));
                    valid = false;
                } else {
                    tilAmount.setError(null);
                }
            } catch (Exception e) {
                tilAmount.setError(getString(R.string.required_field));
                valid = false;
            }
        }

        if (!valid) {
            return;
        }

        // Build payment object
        String selectedStore = (String) spinnerStore.getSelectedItem();
        String date = etDate.getText() != null
                ? etDate.getText().toString().trim() : DateUtils.todayAsDbFormat();
        String notes = etNotes.getText() != null
                ? etNotes.getText().toString().trim() : "";

        Payment payment = new Payment();
        payment.setStoreName(selectedStore);
        payment.setAmount(amount);
        payment.setPaymentDate(date);
        payment.setNotes(notes);

        if (isEditMode) {
            payment.setId(existingPaymentId);
            paymentDao.update(payment);
        } else {
            paymentDao.insert(payment);
        }

        Toast.makeText(this, R.string.payment_saved, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    // ── Options menu (back navigation) ───────────────────────────────────

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
