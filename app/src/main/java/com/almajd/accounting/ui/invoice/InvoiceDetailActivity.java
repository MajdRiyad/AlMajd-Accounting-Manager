package com.almajd.accounting.ui.invoice;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.almajd.accounting.R;
import com.almajd.accounting.ui.BaseActivity;
import com.almajd.accounting.db.InvoiceDao;
import com.almajd.accounting.db.PaymentDao;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.InvoiceItem;
import com.almajd.accounting.model.Payment;
import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.ui.payment.PaymentFormActivity;
import com.almajd.accounting.ui.payment.PaymentListActivity;
import com.almajd.accounting.util.DateUtils;
import com.almajd.accounting.util.AppExecutors;
import com.almajd.accounting.util.NumberUtils;
import com.almajd.accounting.util.AppSettings;
import com.almajd.accounting.util.PdfExporter;
import com.almajd.accounting.util.ShareHelper;

import java.io.File;
import java.util.List;

/**
 * Detail view for a single invoice. Shows a bilingual business header,
 * store/date information, a dynamic items table, and footer totals.
 * <p>
 * Toolbar menu provides Edit, Delete, Share (PDF), and Export PDF actions.
 */
public class InvoiceDetailActivity extends BaseActivity {

    public static final String EXTRA_INVOICE_ID = "extra_invoice_id";

    private static final int REQUEST_EDIT_INVOICE = 200;
    private static final int REQUEST_ADD_PAYMENT = 201;

    private InvoiceDao invoiceDao;
    private PaymentDao paymentDao;
    private CustomerDao customerDao;
    private AppSettings appSettings;
    private Invoice invoice;
    private long invoiceId;

    /** Incremented on every load so stale background results are discarded. */
    private int loadToken = 0;

    // Views
    private TextView tvArHeader;
    private TextView tvEnHeader;
    private TextView tvStoreName;
    private TextView tvDate;
    private TableLayout tableItems;
    private TextView tvTotal;
    private TextView tvPaid;
    private TextView tvRemaining;
    private LinearLayout llInvoicePayments;
    private TextView tvNoInvoicePayments;
    private Button btnAddPayment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice_detail);

        // --- Toolbar ---
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.invoice_details);
        }

        // --- DAO ---
        invoiceDao = new InvoiceDao(this);
        paymentDao = new PaymentDao(this);
        customerDao = new CustomerDao(this);
        appSettings = new AppSettings(this);

        // --- Get invoice ID from intent ---
        if (getIntent() == null || !getIntent().hasExtra(EXTRA_INVOICE_ID)) {
            Toast.makeText(this, R.string.no_data, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        invoiceId = getIntent().getLongExtra(EXTRA_INVOICE_ID, -1);
        if (invoiceId <= 0) {
            Toast.makeText(this, R.string.no_data, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // --- View references ---
        tvArHeader = findViewById(R.id.tv_ar_header);
        tvEnHeader = findViewById(R.id.tv_en_header);
        tvStoreName = findViewById(R.id.tv_store_name);
        tvDate = findViewById(R.id.tv_date);
        tableItems = findViewById(R.id.table_items);
        tvTotal = findViewById(R.id.tv_total);
        tvPaid = findViewById(R.id.tv_paid);
        tvRemaining = findViewById(R.id.tv_remaining);
        llInvoicePayments = findViewById(R.id.ll_invoice_payments);
        tvNoInvoicePayments = findViewById(R.id.tv_no_invoice_payments);
        btnAddPayment = findViewById(R.id.btn_add_payment);

        btnAddPayment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (invoice == null) return;
                Intent intent = new Intent(InvoiceDetailActivity.this, PaymentFormActivity.class);
                intent.putExtra(PaymentListActivity.EXTRA_STORE_NAME, invoice.getStoreName());
                intent.putExtra(PaymentFormActivity.EXTRA_INVOICE_ID, invoice.getId());
                startActivityForResult(intent, REQUEST_ADD_PAYMENT);
            }
        });

        // --- Load and display ---
        loadInvoice();
    }

    /**
     * Loads the invoice (with items) and its linked payments on the background
     * thread, then populates all views on the main thread.
     */
    private void loadInvoice() {
        final int token = ++loadToken;
        AppExecutors.runOnDb(() -> {
            final Invoice loaded = invoiceDao.getById(invoiceId);
            final List<Payment> payments = loaded != null
                    ? paymentDao.getByInvoiceId(loaded.getId())
                    : null;

            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (token != loadToken) {
                    return;
                }
                if (loaded == null) {
                    Toast.makeText(InvoiceDetailActivity.this,
                            R.string.no_data, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                invoice = loaded;
                displayHeader();
                displayInvoiceInfo();
                displayItemsTable();
                displayFooter();
                displayPayments(payments);
            });
        });
    }

    /**
     * Displays the payments linked to this invoice (date, amount, notes).
     * Must be called on the main thread.
     */
    private void displayPayments(List<Payment> payments) {
        llInvoicePayments.removeAllViews();

        if (payments == null || payments.isEmpty()) {
            tvNoInvoicePayments.setVisibility(View.VISIBLE);
            return;
        }
        tvNoInvoicePayments.setVisibility(View.GONE);

        float smallSize = getResources().getDimension(R.dimen.text_size_small);
        float bodySize = getResources().getDimension(R.dimen.text_size_body);

        for (Payment payment : payments) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dpToPx(8), 0, dpToPx(8));

            // Left column: date + optional notes
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvRowDate = new TextView(this);
            tvRowDate.setText(DateUtils.formatForDisplay(payment.getPaymentDate()));
            tvRowDate.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
            tvRowDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallSize);
            left.addView(tvRowDate);

            if (payment.getNotes() != null && !payment.getNotes().trim().isEmpty()) {
                TextView tvRowNotes = new TextView(this);
                tvRowNotes.setText(payment.getNotes());
                tvRowNotes.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
                tvRowNotes.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallSize);
                left.addView(tvRowNotes);
            }

            row.addView(left);

            // Right column: amount
            TextView tvRowAmount = new TextView(this);
            tvRowAmount.setText(NumberUtils.formatCurrency(payment.getAmount()));
            tvRowAmount.setTextColor(ContextCompat.getColor(this, R.color.green));
            tvRowAmount.setTypeface(null, Typeface.BOLD);
            tvRowAmount.setTextSize(TypedValue.COMPLEX_UNIT_PX, bodySize);
            row.addView(tvRowAmount);

            llInvoicePayments.addView(row);

            // Thin divider between rows
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.dividerLight));
            llInvoicePayments.addView(divider);
        }
    }

    /**
     * Displays the bilingual business header using AppSettings.
     * Arabic side: company name, owner, phone.
     * English side: company name, owner, phone.
     */
    private void displayHeader() {
        String phone = appSettings.getPhone();
        String phoneDisplay = (phone != null && !phone.isEmpty()) ? phone : "";

        String arHeader = appSettings.getCompanyNameAr() + "\n"
                + appSettings.getOwnerNameAr();
        if (!phoneDisplay.isEmpty()) {
            arHeader += "\n" + phoneDisplay;
        }
        tvArHeader.setText(arHeader);

        String enHeader = appSettings.getCompanyNameEn() + "\n"
                + appSettings.getOwnerNameEn();
        if (!phoneDisplay.isEmpty()) {
            enHeader += "\n" + phoneDisplay;
        }
        tvEnHeader.setText(enHeader);
    }

    /**
     * Displays the store name and formatted date.
     */
    private void displayInvoiceInfo() {
        tvStoreName.setText(invoice.getStoreName() != null
                ? invoice.getStoreName() : "\u2014");

        if (invoice.getInvoiceDate() != null && !invoice.getInvoiceDate().isEmpty()) {
            tvDate.setText(DateUtils.formatForDisplay(invoice.getInvoiceDate()));
        } else {
            tvDate.setText("\u2014");
        }

        // Update toolbar subtitle with invoice number
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(
                    getString(R.string.invoice_number_format, invoice.getId()));
        }
    }

    /**
     * Dynamically builds a table of invoice line items.
     * Adds a header row followed by one row per item.
     */
    private void displayItemsTable() {
        // Clear any existing rows (except keep a possible XML-defined header)
        tableItems.removeAllViews();

        // --- Header row ---
        TableRow headerRow = new TableRow(this);
        headerRow.setBackgroundColor(ContextCompat.getColor(this, R.color.primaryColor));
        int headerPadding = dpToPx(8);

        headerRow.addView(createHeaderCell(getString(R.string.item_name), 2f, headerPadding));
        headerRow.addView(createHeaderCell(getString(R.string.quantity), 1f, headerPadding));
        headerRow.addView(createHeaderCell(getString(R.string.unit_price), 1f, headerPadding));
        headerRow.addView(createHeaderCell(getString(R.string.subtotal), 1f, headerPadding));

        tableItems.addView(headerRow);

        // --- Data rows ---
        List<InvoiceItem> items = invoice.getItems();
        if (items != null && !items.isEmpty()) {
            int rowPadding = dpToPx(6);
            boolean alternate = false;

            for (InvoiceItem item : items) {
                TableRow row = new TableRow(this);

                if (alternate) {
                    row.setBackgroundColor(
                            ContextCompat.getColor(this, R.color.lightGray));
                }
                alternate = !alternate;

                // Item name
                TextView tvName = createDataCell(
                        item.getItemName() != null ? item.getItemName() : "\u2014",
                        2f, rowPadding);
                tvName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                row.addView(tvName);

                // Quantity
                row.addView(createDataCell(
                        String.valueOf(item.getQuantity()), 1f, rowPadding));

                // Unit price
                row.addView(createDataCell(
                        NumberUtils.formatCurrency(item.getUnitPrice()), 1f, rowPadding));

                // Subtotal
                row.addView(createDataCell(
                        NumberUtils.formatCurrency(item.getSubtotal()), 1f, rowPadding));

                tableItems.addView(row);
            }
        }
    }

    /**
     * Displays the footer totals: total, paid, and remaining amounts.
     */
    private void displayFooter() {
        tvTotal.setText(NumberUtils.formatCurrency(invoice.getTotalAmount()));
        tvPaid.setText(NumberUtils.formatCurrency(invoice.getPaidAmount()));
        tvRemaining.setText(NumberUtils.formatCurrency(invoice.getRemaining()));

        // Remaining: red if outstanding, green if fully paid
        if (invoice.getRemaining() > 0) {
            tvRemaining.setTextColor(ContextCompat.getColor(this, R.color.red));
        } else {
            tvRemaining.setTextColor(ContextCompat.getColor(this, R.color.green));
        }
    }

    // --- Helper methods for building table cells ---

    /**
     * Creates a bold white header cell for the items table.
     */
    private TextView createHeaderCell(String text, float weight, int padding) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(this, R.color.white));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(padding, padding, padding, padding);

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                0, TableRow.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(params);

        return tv;
    }

    /**
     * Creates a data cell for the items table.
     */
    private TextView createDataCell(String text, float weight, int padding) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(padding, padding, padding, padding);

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                0, TableRow.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(params);

        return tv;
    }

    /**
     * Converts dp to pixels.
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // --- Toolbar menu ---

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_edit) {
            Intent editIntent = new Intent(this, InvoiceFormActivity.class);
            editIntent.putExtra(InvoiceFormActivity.EXTRA_INVOICE_ID, invoiceId);
            startActivityForResult(editIntent, REQUEST_EDIT_INVOICE);
            return true;

        } else if (id == R.id.action_delete) {
            confirmDelete();
            return true;

        } else if (id == R.id.action_share) {
            shareInvoicePdf();
            return true;

        } else if (id == R.id.action_export_pdf) {
            exportInvoicePdf();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Shows a confirmation dialog and deletes the invoice if confirmed.
     */
    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(R.string.confirm_delete_message)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AppExecutors.runOnDb(() -> {
                            final boolean deleted = invoiceDao.delete(invoiceId);
                            AppExecutors.runOnMain(() -> {
                                if (isFinishing() || isDestroyed()) {
                                    return;
                                }
                                if (deleted) {
                                    Toast.makeText(InvoiceDetailActivity.this,
                                            R.string.deleted_successfully,
                                            Toast.LENGTH_SHORT).show();
                                    setResult(RESULT_OK);
                                    finish();
                                } else {
                                    Toast.makeText(InvoiceDetailActivity.this,
                                            R.string.delete_failed,
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Generates a PDF of the invoice (background thread) and shares it via an
     * implicit intent (main thread).
     */
    private void shareInvoicePdf() {
        final Invoice current = invoice;
        if (current == null) return;

        AppExecutors.runOnDb(() -> {
            try {
                final Customer customer = customerDao.getByStoreName(current.getStoreName());
                final File pdfFile = PdfExporter.generateInvoicePdf(
                        InvoiceDetailActivity.this, current, customer);
                AppExecutors.runOnMain(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    ShareHelper.sharePdf(InvoiceDetailActivity.this, pdfFile);
                });
            } catch (final Exception e) {
                AppExecutors.runOnMain(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Toast.makeText(InvoiceDetailActivity.this,
                            "Error generating PDF: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Generates a PDF of the invoice (background thread) and exports it to
     * external storage by opening a viewer (main thread).
     */
    private void exportInvoicePdf() {
        final Invoice current = invoice;
        if (current == null) return;

        AppExecutors.runOnDb(() -> {
            try {
                final Customer customer = customerDao.getByStoreName(current.getStoreName());
                final File pdfFile = PdfExporter.generateInvoicePdf(
                        InvoiceDetailActivity.this, current, customer);
                final Uri uri = FileProvider.getUriForFile(InvoiceDetailActivity.this,
                        getApplicationContext().getPackageName() + ".fileprovider",
                        pdfFile);

                AppExecutors.runOnMain(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                    viewIntent.setDataAndType(uri, "application/pdf");
                    viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(viewIntent);
                });
            } catch (final Exception e) {
                AppExecutors.runOnMain(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Toast.makeText(InvoiceDetailActivity.this,
                            "Error exporting PDF: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_INVOICE && resultCode == RESULT_OK) {
            // Reload the invoice to reflect edits
            loadInvoice();
            setResult(RESULT_OK);
        } else if (requestCode == REQUEST_ADD_PAYMENT && resultCode == RESULT_OK) {
            // Reload to reflect the new linked payment
            loadInvoice();
            setResult(RESULT_OK);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
