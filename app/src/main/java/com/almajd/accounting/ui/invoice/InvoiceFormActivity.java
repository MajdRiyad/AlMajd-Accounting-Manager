package com.almajd.accounting.ui.invoice;

import android.os.Bundle;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.ui.BaseActivity;
import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.db.InvoiceDao;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.InvoiceItem;
import com.almajd.accounting.util.AppExecutors;
import com.almajd.accounting.util.DateUtils;
import com.almajd.accounting.util.NumberUtils;
import com.almajd.accounting.util.PdfExporter;
import com.almajd.accounting.util.ShareHelper;

import java.io.File;
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

    // Saved-instance-state keys (rotation / process death).
    private static final String STATE_ITEM_NAMES = "state_item_names";
    private static final String STATE_ITEM_QUANTITIES = "state_item_quantities";
    private static final String STATE_ITEM_PRICES = "state_item_prices";
    private static final String STATE_ITEM_SUBTOTALS = "state_item_subtotals";
    private static final String STATE_STORE_NAME = "state_store_name";

    /**
     * Id of the invoice saved before a configuration change. Persisted so that
     * rotating the device while the PDF/share sheet is being prepared cannot
     * re-enable Save and let the user create a duplicate.
     */
    private static final String STATE_SAVED_INVOICE_ID = "state_saved_invoice_id";

    private Spinner spinnerStore;
    private EditText etDate;
    private EditText etPaid;
    private View rowPaidNow;
    private TextView tvTotal;
    private TextView tvRemaining;
    private RecyclerView rvInvoiceItems;
    private NestedScrollView nsvForm;
    private Button btnAddItem;
    private Button btnSave;

    private InvoiceItemAdapter itemAdapter;
    private InvoiceDao invoiceDao;
    private CustomerDao customerDao;

    /** Store names list backing the spinner adapter. */
    private List<String> storeNames;

    /** Current grand total calculated from line items. */
    private double currentTotal = 0.0;

    /** Derived paid amount of the invoice being edited (edit mode only). */
    private double existingPaid = 0.0;

    /** True when editing an existing invoice; false when creating a new one. */
    private boolean editMode = false;

    /** ID of the invoice being edited (only valid when editMode is true). */
    private long editInvoiceId = -1;

    /**
     * ID of the invoice saved by this (create-mode) form, or -1 before a
     * successful save. Once it is set the form only reports success and closes;
     * it must never save again.
     */
    private long savedInvoiceId = -1;

    /** True when create-mode line items were restored from saved state (rotation). */
    private boolean restoredItems = false;

    /** Store name captured in saved instance state (rotation); may be null. */
    private String restoredStoreName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice_form);

        // Recovery: the invoice was already saved before the device rotated while
        // the share sheet was being prepared. The form must not save again, so it
        // closes immediately and still reports the saved invoice to the caller.
        if (savedInstanceState != null
                && savedInstanceState.getLong(STATE_SAVED_INVOICE_ID, -1) > 0) {
            setResult(RESULT_OK);
            finish();
            return;
        }

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
        rowPaidNow = findViewById(R.id.row_paid_now);
        tvTotal = findViewById(R.id.tv_total);
        tvRemaining = findViewById(R.id.tv_remaining);
        rvInvoiceItems = findViewById(R.id.rv_invoice_items);
        nsvForm = findViewById(R.id.nsv_form);
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
        // On rotation, create-mode items are restored from saved state so no work
        // is lost. Edit mode reloads from the database instead (see
        // onStoreSpinnerReady), so it never restores here.
        List<InvoiceItem> initialItems = new ArrayList<>();
        if (!editMode && savedInstanceState != null) {
            List<InvoiceItem> restored = restoreItems(savedInstanceState);
            if (restored != null) {
                initialItems.addAll(restored);
                restoredItems = true;
                restoredStoreName = savedInstanceState.getString(STATE_STORE_NAME);
            }
        }
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
                // Add the row, then scroll it into view and focus its name field.
                addItemAndReveal(itemAdapter.addItem());
            }
        });

        // --- Save button ---
        // Disabled until the initial data (store list / existing invoice) has
        // loaded, so a half-initialised form can never be saved.
        btnSave.setEnabled(false);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateAndSave();
            }
        });

        // Initialize totals display
        tvTotal.setText(NumberUtils.formatCurrency(0));
        tvRemaining.setText(NumberUtils.formatCurrency(0));

        // --- Populate store spinner ---
        // Runs on the background thread; when it finishes it either loads the
        // invoice (edit mode) or pre-selects the store and adds a first blank
        // line item (add mode).
        setupStoreSpinner();
    }

    /**
     * Populates the store spinner with store names from the customer database.
     * The customers are read on the background thread; the adapter is built on
     * the main thread. The first entry is a prompt string ("Select store").
     */
    private void setupStoreSpinner() {
        AppExecutors.runOnDb(() -> {
            final List<Customer> customers = customerDao.getAll();
            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                storeNames = new ArrayList<>();
                storeNames.add(getString(R.string.select_store)); // index 0 = prompt

                if (customers != null) {
                    for (Customer c : customers) {
                        if (c.getStoreName() != null && !c.getStoreName().isEmpty()) {
                            storeNames.add(c.getStoreName());
                        }
                    }
                }

                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                        InvoiceFormActivity.this, android.R.layout.simple_spinner_item, storeNames);
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerStore.setAdapter(spinnerAdapter);

                onStoreSpinnerReady();
            });
        });
    }

    /**
     * Continues the initialisation once the store list is available: in edit
     * mode the existing invoice is loaded, in add mode the store is pre-selected
     * (when passed via intent) and one blank line item is added.
     */
    private void onStoreSpinnerReady() {
        if (editMode) {
            // Edit mode always reloads the invoice from the database; the
            // "paid now" row stays hidden (payments are managed elsewhere).
            if (rowPaidNow != null) {
                rowPaidNow.setVisibility(View.GONE);
            }
            loadExistingInvoice();
            return;
        }

        // Create mode, restored after rotation: keep the in-progress line items
        // instead of starting over with a single blank row.
        if (restoredItems) {
            selectStoreInSpinner(restoredStoreName);
            refreshTotalsFromAdapter();
            btnSave.setEnabled(true);
            return;
        }

        // In add mode, if store name was passed, pre-select it
        String preSelectStore = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_STORE_NAME) : null;
        if (preSelectStore != null && !preSelectStore.isEmpty()) {
            selectStoreInSpinner(preSelectStore);
        }

        // Start with one empty line item
        itemAdapter.addItem();

        btnSave.setEnabled(true);
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
     * Loads an existing invoice by its ID on the background thread and
     * populates all form fields on the main thread.
     */
    private void loadExistingInvoice() {
        final long id = editInvoiceId;
        AppExecutors.runOnDb(() -> {
            final Invoice invoice = invoiceDao.getById(id);
            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (invoice == null) {
                    Toast.makeText(InvoiceFormActivity.this,
                            R.string.no_data, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                bindExistingInvoice(invoice);
                btnSave.setEnabled(true);
            });
        });
    }

    /**
     * Binds a loaded invoice to the form. Must be called on the main thread.
     */
    private void bindExistingInvoice(Invoice invoice) {
        // Store selection
        selectStoreInSpinner(invoice.getStoreName());

        // Date
        if (invoice.getInvoiceDate() != null) {
            etDate.setText(invoice.getInvoiceDate());
        }

        // Paid amount is DERIVED from linked payments and is managed from the
        // invoice detail screen / payment form. The invoice form must never
        // recreate or duplicate payments, so the "paid now" row is hidden and
        // not saved in edit mode.
        existingPaid = invoice.getPaidAmount();
        if (rowPaidNow != null) {
            rowPaidNow.setVisibility(View.GONE);
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
     * In edit mode the paid amount is the derived value read from the invoice view
     * (the "paid now" row is hidden and never saved).
     */
    private void recalculateRemaining() {
        double paid;
        if (editMode) {
            paid = existingPaid;
        } else {
            paid = 0.0;
            try {
                String paidStr = etPaid.getText().toString().trim();
                if (!paidStr.isEmpty()) {
                    paid = NumberUtils.parseAmount(paidStr);
                }
            } catch (Exception ignored) { }
        }

        double remaining = NumberUtils.round2(currentTotal - paid);
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

        // 4. Calculate totals (rounded to 2 decimals)
        double total = 0.0;
        for (InvoiceItem item : validItems) {
            double sub = NumberUtils.round2(item.getQuantity() * item.getUnitPrice());
            item.setSubtotal(sub);
            total += sub;
        }
        total = NumberUtils.round2(total);

        // 5. "Paid now" applies on create only. On edit the form must not touch
        // existing payments, and paid/remaining are derived from them.
        double paidNow = 0.0;
        if (!editMode) {
            try {
                String paidStr = etPaid.getText().toString().trim();
                if (!paidStr.isEmpty()) {
                    paidNow = NumberUtils.round2(NumberUtils.parseAmount(paidStr));
                }
            } catch (Exception ignored) { }
            if (paidNow < 0) {
                paidNow = 0;
            }
        }

        // 6. Build the Invoice object (paid/remaining are derived, not persisted)
        Invoice invoice = new Invoice();
        invoice.setStoreName(storeName);
        invoice.setInvoiceDate(date);
        invoice.setTotalAmount(total);
        invoice.setItems(validItems);

        // 7. Save on the background thread. The save button is disabled first so
        // a second tap while the write is in flight cannot insert a duplicate.
        final boolean isEdit = editMode;
        if (isEdit) {
            invoice.setId(editInvoiceId);
        }

        // On create, a "paid now" larger than the invoice total is never applied
        // silently: ask first, then split the amount (invoice total linked to the
        // invoice + excess on account) in the same transaction as the insert.
        if (!isEdit && paidNow > total + 0.0001) {
            showPaidNowOverpaymentDialog(invoice, total, paidNow);
            return;
        }

        saveInvoice(invoice, isEdit, paidNow, 0.0);
    }

    /**
     * Confirmation dialog shown when "paid now" on CREATE is greater than the
     * invoice total. Confirming records the invoice total as a payment linked to
     * the invoice and the excess as a separate on-account payment (both in the
     * same transaction as the invoice insert). Cancelling saves nothing and
     * leaves the form open with Save re-enabled.
     */
    private void showPaidNowOverpaymentDialog(final Invoice invoice, final double total,
                                              final double paidNow) {
        final double excess = NumberUtils.round2(paidNow - total);
        new AlertDialog.Builder(this)
                .setTitle(R.string.paid_now_overpayment_title)
                .setMessage(getString(R.string.paid_now_overpayment_message,
                        NumberUtils.formatCurrency(total),
                        NumberUtils.formatCurrency(excess)))
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveInvoice(invoice, false, total, excess);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Cancelled: leave the form open and allow another save.
                        btnSave.setEnabled(true);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        btnSave.setEnabled(true);
                    }
                })
                .show();
    }

    /**
     * Persists the invoice on the background thread, then reports the result on
     * the main thread. The save button is disabled for the whole write so a
     * second tap cannot insert a duplicate. {@code linkedPaid} is recorded as a
     * payment linked to the invoice; {@code onAccountPaid} (the excess of
     * "paid now" over the total) as a separate on-account payment.
     * <p>
     * In create mode a successful save also generates the invoice PDF and opens
     * the system share sheet (see {@link #generateAndShare}). Edit mode keeps its
     * previous behaviour and never opens the share sheet.
     */
    private void saveInvoice(final Invoice invoice, final boolean isEdit,
                             final double linkedPaid, final double onAccountPaid) {
        btnSave.setEnabled(false);
        AppExecutors.runOnDb(() -> {
            final long invoiceId;
            if (isEdit) {
                invoiceId = invoiceDao.update(invoice) ? invoice.getId() : -1L;
            } else {
                invoiceId = invoiceDao.insert(invoice, linkedPaid, onAccountPaid);
            }

            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (invoiceId <= 0) {
                    Toast.makeText(InvoiceFormActivity.this,
                            R.string.save_failed, Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                    return;
                }
                Toast.makeText(InvoiceFormActivity.this,
                        R.string.invoice_saved, Toast.LENGTH_SHORT).show();
                // The invoice is committed: report success *now*, so the caller
                // refreshes even if the user leaves while the PDF is generated.
                setResult(RESULT_OK);
                savedInvoiceId = invoiceId;

                if (isEdit) {
                    // Edit mode: unchanged -- just close.
                    finish();
                } else {
                    // Create mode: build the PDF off the main thread and open the
                    // system share sheet. Save stays disabled meanwhile.
                    generateAndShare(invoiceId);
                }
            });
        });
    }

    /**
     * Re-loads the freshly saved invoice (so it carries its items and its
     * effective paid/remaining values), generates the invoice PDF with the same
     * code {@code InvoiceDetailActivity} uses, and opens the system share sheet.
     * <p>
     * The DB read and the PDF generation run on the shared background thread; the
     * share sheet is launched on the main thread. The invoice is already saved, so
     * a generation failure only shows {@code pdf_generation_failed} and the form
     * still closes with {@code RESULT_OK} -- the save is never reported as failed.
     */
    private void generateAndShare(final long invoiceId) {
        AppExecutors.runOnDb(() -> {
            File pdfFile = null;
            boolean failed = false;
            try {
                final Invoice saved = invoiceDao.getById(invoiceId);
                if (saved != null) {
                    final Customer customer =
                            customerDao.getByStoreName(saved.getStoreName());
                    pdfFile = PdfExporter.generateInvoicePdf(
                            InvoiceFormActivity.this, saved, customer);
                } else {
                    failed = true;
                }
            } catch (Exception e) {
                failed = true;
            }

            final File file = pdfFile;
            final boolean error = failed || file == null;
            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (error) {
                    Toast.makeText(InvoiceFormActivity.this,
                            R.string.pdf_generation_failed, Toast.LENGTH_SHORT).show();
                } else {
                    ShareHelper.sharePdf(InvoiceFormActivity.this, file,
                            getString(R.string.invoice_number_format, invoiceId));
                }
                // The chooser is a separate activity and the PDF is shared from
                // cacheDir through the FileProvider, so nothing here depends on
                // this Activity staying alive. Finishing straight after launching
                // is safe and guarantees the caller refreshes even when the user
                // never comes back through the form.
                finish();
            });
        });
    }

    // ── Scrolling / focus ────────────────────────────────────────────────

    /**
     * Scrolls the form so the just-added line item is visible and focuses its
     * name field. The item RecyclerView is intentionally non-scrolling (its
     * height is {@code wrap_content}), so scrolling is done on the surrounding
     * {@link NestedScrollView} to avoid nested-scroll conflicts.
     *
     * @param position adapter position of the new item
     */
    private void addItemAndReveal(final int position) {
        rvInvoiceItems.post(new Runnable() {
            @Override
            public void run() {
                RecyclerView.ViewHolder holder =
                        rvInvoiceItems.findViewHolderForAdapterPosition(position);
                if (holder == null) {
                    // Not laid out yet: fall back to scrolling to the list bottom.
                    nsvForm.smoothScrollTo(0, rvInvoiceItems.getBottom());
                    return;
                }
                View row = holder.itemView;
                nsvForm.smoothScrollTo(0, rvInvoiceItems.getTop() + row.getTop());
                View nameField = row.findViewById(R.id.et_item_name);
                if (nameField != null) {
                    nameField.requestFocus();
                }
            }
        });
    }

    // ── Rotation / process-death state ───────────────────────────────────

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Edit mode reloads from the database, so only create mode saves state.
        if (editMode) {
            return;
        }
        // Remember a completed save so rotation during PDF generation / share
        // cannot re-enable Save and create a duplicate.
        outState.putLong(STATE_SAVED_INVOICE_ID, savedInvoiceId);
        if (itemAdapter == null) {
            return;
        }
        // Persist the in-progress line items so rotating the device does not lose
        // them.
        List<InvoiceItem> items = itemAdapter.getAllItems();
        String[] names = new String[items.size()];
        int[] quantities = new int[items.size()];
        double[] prices = new double[items.size()];
        double[] subtotals = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            InvoiceItem item = items.get(i);
            names[i] = item.getItemName();
            quantities[i] = item.getQuantity();
            prices[i] = item.getUnitPrice();
            subtotals[i] = item.getSubtotal();
        }
        outState.putStringArray(STATE_ITEM_NAMES, names);
        outState.putIntArray(STATE_ITEM_QUANTITIES, quantities);
        outState.putDoubleArray(STATE_ITEM_PRICES, prices);
        outState.putDoubleArray(STATE_ITEM_SUBTOTALS, subtotals);
        outState.putString(STATE_STORE_NAME, getSelectedStoreName());
    }

    /**
     * Rebuilds the line items from saved instance state.
     *
     * @return the restored items, or {@code null} when the state contains none
     *         (fresh start, so the caller should add a blank row)
     */
    private List<InvoiceItem> restoreItems(Bundle state) {
        String[] names = state.getStringArray(STATE_ITEM_NAMES);
        if (names == null) {
            return null;
        }
        int[] quantities = state.getIntArray(STATE_ITEM_QUANTITIES);
        double[] prices = state.getDoubleArray(STATE_ITEM_PRICES);
        double[] subtotals = state.getDoubleArray(STATE_ITEM_SUBTOTALS);

        List<InvoiceItem> items = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            InvoiceItem item = new InvoiceItem();
            item.setItemName(names[i] != null ? names[i] : "");
            item.setQuantity(quantities != null && i < quantities.length ? quantities[i] : 0);
            item.setUnitPrice(prices != null && i < prices.length ? prices[i] : 0.0);
            item.setSubtotal(subtotals != null && i < subtotals.length ? subtotals[i] : 0.0);
            items.add(item);
        }
        return items;
    }

    /**
     * Recomputes the displayed grand total (and remaining) from the adapter's
     * current items. Used when the items are restored rather than re-typed.
     */
    private void refreshTotalsFromAdapter() {
        double total = 0.0;
        for (InvoiceItem item : itemAdapter.getAllItems()) {
            total += item.getSubtotal();
        }
        onTotalsChanged(NumberUtils.round2(total));
    }

    /** @return the currently selected store name, or null when none is selected. */
    private String getSelectedStoreName() {
        if (storeNames == null) {
            return null;
        }
        int pos = spinnerStore.getSelectedItemPosition();
        if (pos <= 0 || pos >= storeNames.size()) {
            return null;
        }
        return storeNames.get(pos);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
