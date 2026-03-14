package com.almajd.accounting.ui.invoice;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.ui.BaseActivity;
import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.db.InvoiceDao;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.InvoiceItem;
import com.almajd.accounting.util.DateUtils;
import com.almajd.accounting.util.NumberUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Form activity for creating or editing an invoice.
 * <p>
 * Features:
 * <ul>
 *   <li>Store selection via Spinner (populated from CustomerDao)</li>
 *   <li>Date picker (defaults to today)</li>
 *   <li>Dynamic line items via InvoiceItemAdapter</li>
 *   <li>Real-time total / remaining calculation</li>
 *   <li>Validation before save</li>
 * </ul>
 * <p>
 * Extras:
 * <ul>
 *   <li>{@code EXTRA_INVOICE_ID} (long) - edit mode: loads existing invoice</li>
 *   <li>{@code EXTRA_STORE_NAME} (String) - add mode: pre-selects the store</li>
 * </ul>
 */
public class InvoiceFormActivity extends BaseActivity
        implements InvoiceItemAdapter.OnTotalsChangedListener {

    public static final String EXTRA_INVOICE_ID = "extra_invoice_id";
    public static final String EXTRA_STORE_NAME = "extra_store_name";

    private Spinner spinnerStore;
    private EditText etDate;
    private EditText etPaid;
    private TextView tvTotal;
    private TextView tvRemaining;
    private RecyclerView rvInvoiceItems;
    private Button btnAddItem;
    private Button btnSave;

    private InvoiceItemAdapter itemAdapter;
    private InvoiceDao invoiceDao;
    private CustomerDao customerDao;

    /** Store names list backing the spinner adapter. */
    private List<String> storeNames;

    /** Current grand total calculated from line items. */
    private double currentTotal = 0.0;

    /** True when editing an existing invoice; false when creating a new one. */
    private boolean editMode = false;

    /** ID of the invoice being edited (only valid when editMode is true). */
    private long editInvoiceId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice_form);

        // --- Toolbar ---
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // --- DAOs ---
        invoiceDao = new InvoiceDao(this);
        customerDao = new CustomerDao(this);

        // --- View references ---
        spinnerStore = findViewById(R.id.spinner_store);
        etDate = findViewById(R.id.et_date);
        etPaid = findViewById(R.id.et_paid);
        tvTotal = findViewById(R.id.tv_total);
        tvRemaining = findViewById(R.id.tv_remaining);
        rvInvoiceItems = findViewById(R.id.rv_invoice_items);
        btnAddItem = findViewById(R.id.btn_add_item);
        btnSave = findViewById(R.id.btn_save);

        // --- Determine mode ---
        if (getIntent() != null && getIntent().hasExtra(EXTRA_INVOICE_ID)) {
            editInvoiceId = getIntent().getLongExtra(EXTRA_INVOICE_ID, -1);
            if (editInvoiceId > 0) {
                editMode = true;
            }
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(editMode ? R.string.edit_invoice : R.string.new_invoice);
        }

        // --- Populate store spinner ---
        setupStoreSpinner();

        // --- Date field ---
        etDate.setText(DateUtils.todayAsDbFormat());
        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DateUtils.showDatePicker(InvoiceFormActivity.this, etDate);
            }
        });

        // --- Invoice items RecyclerView ---
        List<InvoiceItem> initialItems = new ArrayList<>();
        itemAdapter = new InvoiceItemAdapter(initialItems, this);
        rvInvoiceItems.setLayoutManager(new LinearLayoutManager(this));
        rvInvoiceItems.setAdapter(itemAdapter);

        // --- Paid amount watcher -> recalculate remaining ---
        etPaid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                recalculateRemaining();
            }
        });

        // --- Add item button ---
        btnAddItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                itemAdapter.addItem();
                // Scroll to the newly added item
                rvInvoiceItems.smoothScrollToPosition(itemAdapter.getItemCount() - 1);
            }
        });

        // --- Save button ---
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateAndSave();
            }
        });

        // --- Load existing invoice if in edit mode ---
        if (editMode) {
            loadExistingInvoice();
        } else {
            // In add mode, if store name was passed, pre-select it
            String preSelectStore = getIntent() != null
                    ? getIntent().getStringExtra(EXTRA_STORE_NAME) : null;
            if (preSelectStore != null && !preSelectStore.isEmpty()) {
                selectStoreInSpinner(preSelectStore);
            }

            // Start with one empty line item
            itemAdapter.addItem();
        }

        // Initialize totals display
        tvTotal.setText(NumberUtils.formatCurrency(0));
        tvRemaining.setText(NumberUtils.formatCurrency(0));
    }

    /**
     * Populates the store spinner with store names from the customer database.
     * The first entry is a prompt string ("Select store").
     */
    private void setupStoreSpinner() {
        storeNames = new ArrayList<>();
        storeNames.add(getString(R.string.select_store)); // index 0 = prompt

        List<Customer> customers = customerDao.getAll();
        if (customers != null) {
            for (Customer c : customers) {
                if (c.getStoreName() != null && !c.getStoreName().isEmpty()) {
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
     * Selects the given store name in the spinner, if present.
     *
     * @param storeName the store to select
     */
    private void selectStoreInSpinner(String storeName) {
        if (storeName == null) return;
        for (int i = 0; i < storeNames.size(); i++) {
            if (storeName.equals(storeNames.get(i))) {
                spinnerStore.setSelection(i);
                return;
            }
        }
    }

    /**
     * Loads an existing invoice by its ID and populates all form fields.
     */
    private void loadExistingInvoice() {
        Invoice invoice = invoiceDao.getById(editInvoiceId);
        if (invoice == null) {
            Toast.makeText(this, R.string.no_data, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Store selection
        selectStoreInSpinner(invoice.getStoreName());

        // Date
        if (invoice.getInvoiceDate() != null) {
            etDate.setText(invoice.getInvoiceDate());
        }

        // Paid amount
        if (invoice.getPaidAmount() > 0) {
            etPaid.setText(String.valueOf(invoice.getPaidAmount()));
        }

        // Line items
        List<InvoiceItem> existingItems = invoice.getItems();
        if (existingItems != null && !existingItems.isEmpty()) {
            // Replace adapter with loaded items
            itemAdapter = new InvoiceItemAdapter(new ArrayList<>(existingItems), this);
            rvInvoiceItems.setAdapter(itemAdapter);
        } else {
            // No items stored, add one empty row
            itemAdapter.addItem();
        }

        // Update totals from the loaded invoice
        currentTotal = invoice.getTotalAmount();
        tvTotal.setText(NumberUtils.formatCurrency(currentTotal));
        recalculateRemaining();
    }

    // --- InvoiceItemAdapter.OnTotalsChangedListener ---

    @Override
    public void onTotalsChanged(double newTotal) {
        currentTotal = newTotal;
        tvTotal.setText(NumberUtils.formatCurrency(currentTotal));
        recalculateRemaining();
    }

    /**
     * Recalculates the remaining balance as total minus paid and updates the display.
     */
    private void recalculateRemaining() {
        double paid = 0.0;
        try {
            String paidStr = etPaid.getText().toString().trim();
            if (!paidStr.isEmpty()) {
                paid = NumberUtils.parseAmount(paidStr);
            }
        } catch (Exception ignored) { }

        double remaining = currentTotal - paid;
        if (remaining < 0) {
            remaining = 0;
        }
        tvRemaining.setText(NumberUtils.formatCurrency(remaining));
    }

    /**
     * Validates the form inputs and saves the invoice (insert or update).
     */
    private void validateAndSave() {
        // 1. Validate store selection (index 0 is the prompt, not a valid choice)
        int storePos = spinnerStore.getSelectedItemPosition();
        if (storePos <= 0) {
            Toast.makeText(this, R.string.select_store, Toast.LENGTH_SHORT).show();
            return;
        }
        String storeName = storeNames.get(storePos);

        // 2. Validate date
        String date = etDate.getText().toString().trim();
        if (date.isEmpty()) {
            etDate.setError(getString(R.string.required_field));
            etDate.requestFocus();
            return;
        }

        // 3. Get items and validate at least one valid item
        List<InvoiceItem> items = itemAdapter.getAllItems();
        List<InvoiceItem> validItems = new ArrayList<>();
        for (InvoiceItem item : items) {
            if (item.getItemName() != null && !item.getItemName().trim().isEmpty()
                    && item.getQuantity() > 0) {
                validItems.add(item);
            }
        }

        if (validItems.isEmpty()) {
            Toast.makeText(this, R.string.add_at_least_one_item, Toast.LENGTH_SHORT).show();
            return;
        }

        // 4. Calculate totals
        double total = 0.0;
        for (InvoiceItem item : validItems) {
            double sub = item.getQuantity() * item.getUnitPrice();
            item.setSubtotal(sub);
            total += sub;
        }

        double paid = 0.0;
        try {
            String paidStr = etPaid.getText().toString().trim();
            if (!paidStr.isEmpty()) {
                paid = NumberUtils.parseAmount(paidStr);
            }
        } catch (Exception ignored) { }

        double remaining = total - paid;
        if (remaining < 0) {
            remaining = 0;
        }

        // 5. Build the Invoice object
        Invoice invoice = new Invoice();
        invoice.setStoreName(storeName);
        invoice.setInvoiceDate(date);
        invoice.setTotalAmount(total);
        invoice.setPaidAmount(paid);
        invoice.setRemaining(remaining);
        invoice.setItems(validItems);

        // 6. Save
        if (editMode) {
            invoice.setId(editInvoiceId);
            boolean updated = invoiceDao.update(invoice);
            if (updated) {
                Toast.makeText(this, R.string.invoice_saved, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
        } else {
            long newId = invoiceDao.insert(invoice);
            if (newId > 0) {
                Toast.makeText(this, R.string.invoice_saved, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
