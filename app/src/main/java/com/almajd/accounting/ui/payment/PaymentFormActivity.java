package com.almajd.accounting.ui.payment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import com.almajd.accounting.R;
import com.almajd.accounting.ui.BaseActivity;
import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.db.InvoiceDao;
import com.almajd.accounting.db.PaymentDao;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.Payment;
import com.almajd.accounting.util.AppExecutors;
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
 * A payment can be linked to one invoice or be "on account" (no invoice). When the
 * entered amount is greater than the remaining of the selected invoice, a dialog
 * asks whether to apply the remaining to the invoice and record the excess on
 * account (two payment rows) or to cancel.
 * <p>
 * Intent extras:
 * <ul>
 *   <li>{@link PaymentListActivity#EXTRA_PAYMENT_ID} (long) -- if present, the form
 *       loads the existing payment for editing (its invoice link is preserved).</li>
 *   <li>{@link PaymentListActivity#EXTRA_STORE_NAME} (String) -- if present, the store
 *       spinner is pre-selected to this store.</li>
 *   <li>{@link #EXTRA_INVOICE_ID} (long) -- if present, the invoice spinner is
 *       pre-selected to this invoice (used by the invoice detail "add payment").</li>
 * </ul>
 * <p>
 * Layout: activity_payment_form.xml
 */
public class PaymentFormActivity extends BaseActivity {

    /** Optional preselected invoice id (used when adding a payment from an invoice). */
    public static final String EXTRA_INVOICE_ID = "extra_invoice_id";

    /** Spinner position 0 is always "on account" (invoice_id = NULL). */
    private static final int ON_ACCOUNT_POSITION = 0;

    private Spinner spinnerStore;
    private Spinner spinnerInvoice;
    private TextInputEditText etAmount;
    private TextInputEditText etDate;
    private TextInputEditText etNotes;
    private TextInputLayout tilAmount;
    private MaterialButton btnSave;

    private PaymentDao paymentDao;
    private CustomerDao customerDao;
    private InvoiceDao invoiceDao;

    /** Store names list backing the spinner; index 0 is the prompt. */
    private List<String> storeNames;

    /** Invoice options parallel to the invoice spinner; index 0 is null (on account). */
    private final List<Invoice> invoiceOptions = new ArrayList<>();

    /** Labels parallel to {@link #invoiceOptions}. */
    private final List<String> invoiceLabels = new ArrayList<>();

    private ArrayAdapter<String> invoiceSpinnerAdapter;

    /** Store the invoice spinner was last rebuilt for (to avoid clobbering selection). */
    private String builtForStore = null;

    /** Non-zero when editing an existing payment. */
    private long existingPaymentId = 0;

    /** True when the form is in edit mode. */
    private boolean isEditMode = false;

    /** Incremented on every invoice-spinner rebuild so stale results are dropped. */
    private int invoiceLoadToken = 0;

    /** Guards against attaching the store spinner listener more than once. */
    private boolean storeListenerAttached = false;

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
        long preSelectedInvoiceId = 0;
        if (getIntent() != null && getIntent().hasExtra(EXTRA_INVOICE_ID)) {
            preSelectedInvoiceId = getIntent().getLongExtra(EXTRA_INVOICE_ID, 0);
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
        invoiceDao = new InvoiceDao(this);

        // Views
        spinnerStore = findViewById(R.id.spinner_store);
        spinnerInvoice = findViewById(R.id.spinner_invoice);
        etAmount = findViewById(R.id.et_amount);
        etDate = findViewById(R.id.et_date);
        etNotes = findViewById(R.id.et_notes);
        tilAmount = findViewById(R.id.til_amount);
        btnSave = findViewById(R.id.btn_save);

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

        // ── Save button ──────────────────────────────────────────────────
        // Disabled until the store / invoice lists have loaded so a
        // half-initialised form can never be saved.
        btnSave.setEnabled(false);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateAndSave();
            }
        });

        // ── Load store list, edited payment and invoice options (background) ─
        loadInitialData(preSelectedStore, preSelectedInvoiceId);
    }

    // ── Store spinner ────────────────────────────────────────────────────

    /**
     * Loads the customer list, the edited payment (if any) and the initial
     * invoice options on the background thread, then applies them on the main
     * thread. All reads go through the single DB executor so their order is kept.
     */
    private void loadInitialData(final String preSelectedStore, final long preSelectedInvoiceId) {
        AppExecutors.runOnDb(() -> {
            final List<Customer> customers = customerDao.getAll();
            final Payment existing = isEditMode ? paymentDao.getById(existingPaymentId) : null;

            // Store that should end up selected (edited payment wins).
            String desiredStore = null;
            if (existing != null) {
                desiredStore = existing.getStoreName();
            } else if (preSelectedStore != null && !preSelectedStore.isEmpty()) {
                desiredStore = preSelectedStore;
            }

            // Invoice that should end up selected.
            Long preselectInvoiceId = null;
            if (existing != null) {
                preselectInvoiceId = existing.getInvoiceId();
            } else if (preSelectedInvoiceId > 0) {
                preselectInvoiceId = preSelectedInvoiceId;
            }

            final Object[] invoiceData = readInvoiceOptions(desiredStore, preselectInvoiceId);

            final Payment existingFinal = existing;
            final String storeFinal = desiredStore;
            final Long preselectFinal = preselectInvoiceId;
            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                applyInitialData(customers, existingFinal, storeFinal, preselectFinal, invoiceData);
            });
        });
    }

    /**
     * Applies the data loaded by {@link #loadInitialData(String, long)}. Must be
     * called on the main thread.
     */
    private void applyInitialData(List<Customer> customers, Payment existing, String desiredStore,
                                  Long preselectInvoiceId, Object[] invoiceData) {
        // Build the store spinner.
        storeNames = new ArrayList<>();
        storeNames.add(getString(R.string.select_store)); // index 0 = prompt
        if (customers != null) {
            for (Customer c : customers) {
                if (c.getStoreName() != null && !c.getStoreName().trim().isEmpty()) {
                    storeNames.add(c.getStoreName());
                }
            }
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                PaymentFormActivity.this, android.R.layout.simple_spinner_item, storeNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStore.setAdapter(spinnerAdapter);

        if (isEditMode) {
            if (existing == null) {
                // Payment no longer exists -- fall back to add mode
                isEditMode = false;
                existingPaymentId = 0;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(R.string.new_payment);
                }
            } else {
                // Store, amount, date and notes of the edited payment.
                selectStoreInSpinner(existing.getStoreName());
                etAmount.setText(String.valueOf(existing.getAmount()));
                if (existing.getPaymentDate() != null && !existing.getPaymentDate().isEmpty()) {
                    etDate.setText(existing.getPaymentDate());
                }
                if (existing.getNotes() != null) {
                    etNotes.setText(existing.getNotes());
                }
            }
        } else if (desiredStore != null && !desiredStore.isEmpty()) {
            selectStoreInSpinner(desiredStore);
        }

        // Apply the invoice options that were built on the DB thread.
        @SuppressWarnings("unchecked")
        List<Invoice> openInvoices = (List<Invoice>) invoiceData[0];
        Invoice extra = (Invoice) invoiceData[1];
        applyInvoiceOptions(openInvoices, extra, preselectInvoiceId, desiredStore);

        // Only now attach the change listener, so setup does not trigger an
        // extra load and the user's later store changes rebuild the invoices.
        attachStoreListener();

        btnSave.setEnabled(true);
    }

    /**
     * Reads the invoice options for a store: the store's open invoices plus,
     * when needed, the preselected invoice even if it is fully paid. Runs on the
     * background thread.
     *
     * @return {@code [0] = List<Invoice> openInvoices, [1] = Invoice extra (or null)}
     */
    private Object[] readInvoiceOptions(String store, Long preselectInvoiceId) {
        List<Invoice> openInvoices = new ArrayList<>();
        Invoice extra = null;

        if (store != null && !store.isEmpty()) {
            openInvoices = invoiceDao.getOpenInvoicesByStore(store);

            if (preselectInvoiceId != null && preselectInvoiceId > 0) {
                boolean present = false;
                for (Invoice inv : openInvoices) {
                    if (inv.getId() == preselectInvoiceId) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    Invoice inv = invoiceDao.getById(preselectInvoiceId);
                    if (inv != null && store.equals(inv.getStoreName())) {
                        extra = inv;
                    }
                }
            }
        }
        return new Object[]{openInvoices, extra};
    }

    /**
     * Rebuilds the invoice spinner from pre-read data. Must be called on the
     * main thread.
     */
    private void applyInvoiceOptions(List<Invoice> openInvoices, Invoice extra,
                                     Long preselectInvoiceId, String store) {
        invoiceOptions.clear();
        invoiceLabels.clear();

        // Index 0 is always "on account" (no invoice).
        invoiceOptions.add(null);
        invoiceLabels.add(getString(R.string.on_account));

        if (openInvoices != null) {
            for (Invoice inv : openInvoices) {
                invoiceOptions.add(inv);
                invoiceLabels.add(buildInvoiceLabel(inv));
            }
        }

        // Ensure the preselected invoice is present even if it is fully paid
        // (keeps an edited payment's invoice_id).
        if (extra != null && !containsInvoice(extra.getId())) {
            invoiceOptions.add(extra);
            invoiceLabels.add(buildInvoiceLabel(extra));
        }

        invoiceSpinnerAdapter = new ArrayAdapter<>(
                PaymentFormActivity.this, android.R.layout.simple_spinner_item, invoiceLabels);
        invoiceSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInvoice.setAdapter(invoiceSpinnerAdapter);

        int selection = ON_ACCOUNT_POSITION;
        if (preselectInvoiceId != null) {
            for (int i = 1; i < invoiceOptions.size(); i++) {
                Invoice inv = invoiceOptions.get(i);
                if (inv != null && inv.getId() == preselectInvoiceId) {
                    selection = i;
                    break;
                }
            }
        }
        spinnerInvoice.setSelection(selection);
        builtForStore = store;
    }

    /**
     * Attaches the store-change listener exactly once, after the initial setup.
     */
    private void attachStoreListener() {
        if (storeListenerAttached) {
            return;
        }
        storeListenerAttached = true;

        spinnerStore.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String store = getSelectedStoreName();
                if (store == null) {
                    if (builtForStore == null) {
                        return;
                    }
                    rebuildInvoiceSpinner(null, null);
                    return;
                }
                // Avoid clobbering the current selection with a redundant rebuild.
                if (store.equals(builtForStore)) {
                    return;
                }
                rebuildInvoiceSpinner(store, null);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (builtForStore == null) {
                    return;
                }
                rebuildInvoiceSpinner(null, null);
            }
        });
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

    /** @return the currently selected store name, or null when the prompt is selected. */
    private String getSelectedStoreName() {
        int pos = spinnerStore.getSelectedItemPosition();
        if (pos <= ON_ACCOUNT_POSITION) {
            return null;
        }
        Object item = spinnerStore.getItemAtPosition(pos);
        return item != null ? item.toString() : null;
    }

    // ── Invoice spinner ──────────────────────────────────────────────────

    /**
     * Rebuilds the invoice spinner for the given store on the background thread:
     * an "on account" option followed by the store's invoices that still have a
     * remaining amount > 0. When {@code preselectInvoiceId} is given it is
     * selected if present (it is also added to the list even if fully paid, so an
     * edited payment keeps its invoice link).
     */
    private void rebuildInvoiceSpinner(final String store, final Long preselectInvoiceId) {
        final int token = ++invoiceLoadToken;
        AppExecutors.runOnDb(() -> {
            final Object[] invoiceData = readInvoiceOptions(store, preselectInvoiceId);
            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (token != invoiceLoadToken) {
                    return;
                }
                @SuppressWarnings("unchecked")
                List<Invoice> openInvoices = (List<Invoice>) invoiceData[0];
                Invoice extra = (Invoice) invoiceData[1];
                applyInvoiceOptions(openInvoices, extra, preselectInvoiceId, store);
            });
        });
    }

    private boolean containsInvoice(long invoiceId) {
        for (Invoice inv : invoiceOptions) {
            if (inv != null && inv.getId() == invoiceId) {
                return true;
            }
        }
        return false;
    }

    private String buildInvoiceLabel(Invoice invoice) {
        return getString(R.string.invoice_option_format,
                invoice.getId(),
                DateUtils.formatForDisplay(invoice.getInvoiceDate()),
                NumberUtils.formatCurrency(invoice.getRemaining()));
    }

    /** @return the invoice linked by the current spinner selection, or null for on-account. */
    private Invoice getSelectedInvoice() {
        int pos = spinnerInvoice.getSelectedItemPosition();
        if (pos <= ON_ACCOUNT_POSITION || pos >= invoiceOptions.size()) {
            return null;
        }
        return invoiceOptions.get(pos);
    }

    // ── Validation & save ────────────────────────────────────────────────

    /**
     * Validates all required fields and, if valid, inserts or updates the payment.
     * Handles the overpayment-against-an-invoice case with a confirmation dialog.
     */
    private void validateAndSave() {
        boolean valid = true;

        // Validate store selection (must not be the prompt at position 0)
        if (spinnerStore.getSelectedItemPosition() == 0) {
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

        // Round the amount to 2 decimals before saving.
        final double finalAmount = NumberUtils.round2(amount);

        final String selectedStore = getSelectedStoreName();
        final String date = etDate.getText() != null
                ? etDate.getText().toString().trim() : DateUtils.todayAsDbFormat();
        final String notes = etNotes.getText() != null
                ? etNotes.getText().toString().trim() : null;

        final Invoice selectedInvoice = getSelectedInvoice();

        // Disable the save button for the whole flow (including the DB read that
        // decides whether an overpayment split is needed) to prevent double taps.
        btnSave.setEnabled(false);

        if (selectedInvoice == null) {
            // On-account payment: reduces the customer's overall balance only.
            saveSingle(buildPayment(selectedStore, finalAmount, date, notes, null));
            return;
        }

        // How much can still be applied to this invoice, ignoring the payment
        // being edited (its old amount must not be counted twice). Read on the
        // background thread.
        final long excludeId = isEditMode ? existingPaymentId : -1;
        AppExecutors.runOnDb(() -> {
            final double paidExcluding = paymentDao.getPaidForInvoiceExcluding(
                    selectedInvoice.getId(), excludeId);
            // Subtract the part of the invoice already settled from the customer's
            // on-account credit as well: an invoice that is already covered by
            // credit has no room left for a directly-linked payment, so the excess
            // goes on account instead of double-counting the same money.
            double available = NumberUtils.round2(selectedInvoice.getTotalAmount()
                    - paidExcluding
                    - selectedInvoice.getAllocatedFromCredit());
            if (available < 0) {
                available = 0;
            }
            final double availableFinal = available;

            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalAmount > availableFinal + 0.0001) {
                    // Overpayment: ask the user before splitting the amount.
                    showOverpaymentDialog(selectedInvoice, finalAmount, availableFinal,
                            date, notes, selectedStore);
                } else {
                    // Normal linked payment.
                    saveSingle(buildPayment(selectedStore, finalAmount, date, notes,
                            selectedInvoice.getId()));
                }
            });
        });
    }

    /**
     * Confirmation dialog shown when the entered amount exceeds the invoice's
     * remaining. Accepting splits the amount, cancelling leaves the form open.
     */
    private void showOverpaymentDialog(final Invoice invoice, final double amount,
                                       final double available, final String date,
                                       final String notes, final String store) {
        final double excess = NumberUtils.round2(amount - available);
        new AlertDialog.Builder(this)
                .setTitle(R.string.overpayment_title)
                .setMessage(getString(R.string.overpayment_message,
                        NumberUtils.formatCurrency(available),
                        NumberUtils.formatCurrency(excess)))
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        applyOverpayment(invoice, available, excess, date, notes, store);
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
     * Applies the confirmed overpayment: up to {@code available} on the invoice
     * and {@code excess} as an on-account payment. When nothing can be applied
     * to the invoice the whole amount is recorded on account.
     */
    private void applyOverpayment(final Invoice invoice, final double available, final double excess,
                                  final String date, final String notes, final String store) {
        if (available <= 0) {
            // The invoice is already settled: record everything on account.
            saveSingle(buildPayment(store, NumberUtils.round2(excess), date, notes, null));
            return;
        }

        final Payment linked = buildPayment(store, available, date, notes, invoice.getId());
        final Payment onAccount = buildPayment(store, excess, date,
                getString(R.string.payment_excess_note, invoice.getId()), null);

        final boolean edit = isEditMode;
        AppExecutors.runOnDb(() -> {
            final boolean ok;
            if (edit) {
                linked.setId(existingPaymentId);
                ok = paymentDao.updateAndInsert(linked, onAccount);
            } else {
                ok = paymentDao.insertTwo(linked, onAccount);
            }

            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (ok) {
                    finishSaved();
                } else {
                    Toast.makeText(PaymentFormActivity.this,
                            R.string.save_failed, Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                }
            });
        });
    }

    private Payment buildPayment(String store, double amount, String date, String notes, Long invoiceId) {
        Payment payment = new Payment();
        payment.setStoreName(store);
        payment.setAmount(NumberUtils.round2(amount));
        payment.setPaymentDate(date);
        payment.setNotes(notes);
        payment.setInvoiceId(invoiceId);
        return payment;
    }

    /**
     * Inserts or updates a single payment on the background thread and reports
     * success/failure on the main thread. A failed write never claims success.
     */
    private void saveSingle(final Payment payment) {
        final boolean edit = isEditMode;
        final long editId = existingPaymentId;
        AppExecutors.runOnDb(() -> {
            final boolean ok;
            if (edit) {
                payment.setId(editId);
                ok = paymentDao.update(payment);
            } else {
                ok = paymentDao.insert(payment) != -1;
            }

            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (ok) {
                    finishSaved();
                } else {
                    Toast.makeText(PaymentFormActivity.this,
                            R.string.save_failed, Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                }
            });
        });
    }

    private void finishSaved() {
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
