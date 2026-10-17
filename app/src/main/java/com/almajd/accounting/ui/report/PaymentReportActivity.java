package com.almajd.accounting.ui.report;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
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
import com.almajd.accounting.db.PaymentDao;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.model.Payment;
import com.almajd.accounting.util.AppExecutors;
import com.almajd.accounting.util.DateUtils;
import com.almajd.accounting.util.ExcelExporter;
import com.almajd.accounting.util.NumberUtils;
import com.almajd.accounting.util.PdfExporter;
import com.almajd.accounting.util.ShareHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays a filterable list of payment records with totals and export capabilities.
 * <p>
 * Filters: store name (Spinner), date range (from/to with DatePicker), filter button.
 * Each row shows: store name, payment date, amount.
 * Footer displays the sum of all displayed payments.
 * <p>
 * Export options (overflow menu): PDF, Excel, CSV.
 * <p>
 * Layout: activity_payment_report.xml
 * IDs: toolbar, spinner_store, et_from_date, et_to_date, btn_filter, rv_report, tv_total
 */
public class PaymentReportActivity extends BaseActivity {

    private Spinner spinnerStore;
    private TextView etFromDate;
    private TextView etToDate;
    private RecyclerView rvReport;
    private TextView tvTotal;

    private PaymentDao paymentDao;
    private CustomerDao customerDao;

    private ReportAdapter adapter;
    private List<Payment> currentPayments = new ArrayList<>();
    private List<String> storeNames = new ArrayList<>();

    /** Incremented on every filter so stale background results are discarded. */
    private int loadToken = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_report);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.payment_history);
        }

        // DAOs
        paymentDao = new PaymentDao(this);
        customerDao = new CustomerDao(this);

        // Views
        spinnerStore = findViewById(R.id.spinner_store_filter);
        etFromDate = findViewById(R.id.tv_from_date);
        etToDate = findViewById(R.id.tv_to_date);
        Button btnFilter = findViewById(R.id.btn_filter);
        rvReport = findViewById(R.id.rv_report);
        tvTotal = findViewById(R.id.tv_grand_total);

        // Date pickers
        etFromDate.setFocusable(false);
        etFromDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DateUtils.showDatePicker(PaymentReportActivity.this, etFromDate);
            }
        });

        etToDate.setFocusable(false);
        etToDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DateUtils.showDatePicker(PaymentReportActivity.this, etToDate);
            }
        });

        // RecyclerView
        rvReport.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter(new ArrayList<ReportAdapter.ReportRow>());
        rvReport.setAdapter(adapter);

        // Filter button
        btnFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyFilter();
            }
        });

        // Populate the store spinner on the background thread; once it is ready
        // it triggers the initial (all stores) filter.
        populateStoreSpinner();
    }

    /**
     * Populates the store spinner with "All Stores" as the first entry,
     * followed by every customer's store name from the database. The customer
     * list is read on the background thread; the adapter is built on main.
     */
    private void populateStoreSpinner() {
        AppExecutors.runOnDb(() -> {
            final List<Customer> customers = customerDao.getAll();
            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                storeNames.clear();
                storeNames.add(getString(R.string.all_stores));
                if (customers != null) {
                    for (Customer c : customers) {
                        storeNames.add(c.getStoreName());
                    }
                }

                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                        PaymentReportActivity.this, android.R.layout.simple_spinner_item, storeNames);
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerStore.setAdapter(spinnerAdapter);

                // Load all payments initially (now that the spinner is ready).
                applyFilter();
            });
        });
    }

    /**
     * Reads the current filter state (store selection and date range) on the
     * main thread and queries the PaymentDao on the background thread. Updates
     * the RecyclerView and footer total on the main thread.
     */
    private void applyFilter() {
        final int storeIndex = spinnerStore.getSelectedItemPosition();
        final String fromDate = etFromDate.getText().toString().trim();
        final String toDate = etToDate.getText().toString().trim();

        final boolean isAllStores = (storeIndex <= 0);
        final String selectedStore =
                (isAllStores || storeIndex >= storeNames.size()) ? null : storeNames.get(storeIndex);
        final boolean hasDates = !fromDate.isEmpty() && !toDate.isEmpty();
        final int token = ++loadToken;

        AppExecutors.runOnDb(() -> {
            final List<Payment> result;
            if (selectedStore == null && !hasDates) {
                result = paymentDao.getAll();
            } else if (selectedStore == null) {
                result = paymentDao.getByDateRange(fromDate, toDate);
            } else if (!hasDates) {
                result = paymentDao.getByStoreName(selectedStore);
            } else {
                result = paymentDao.getByStoreAndDateRange(selectedStore, fromDate, toDate);
            }
            final List<Payment> payments = result != null ? result : new ArrayList<Payment>();

            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (token != loadToken) {
                    return;
                }
                currentPayments = payments;
                renderPayments();
            });
        });
    }

    /**
     * Rebuilds the report rows and footer total from {@link #currentPayments}.
     * Must be called on the main thread.
     */
    private void renderPayments() {
        List<ReportAdapter.ReportRow> rows = new ArrayList<>();
        double totalSum = 0;

        for (Payment p : currentPayments) {
            ReportAdapter.ReportRow row = new ReportAdapter.ReportRow(
                    getString(R.string.store_name),
                    p.getStoreName(),
                    getString(R.string.date),
                    DateUtils.formatForDisplay(p.getPaymentDate()),
                    getString(R.string.amount),
                    NumberUtils.formatCurrency(p.getAmount())
            );
            rows.add(row);
            totalSum += p.getAmount();
        }

        adapter.updateList(rows);
        tvTotal.setText(getString(R.string.grand_total) + ": " + NumberUtils.formatCurrency(totalSum));
    }

    // ── Export helpers ────────────────────────────────────────────────────

    /**
     * Builds the header array and data rows list used by all three export formats.
     *
     * @param payments snapshot of the payments to export
     * @return a three-element Object array: [0] = String[] headers,
     *         [1] = List of List of String rows, [2] = String[] footer
     */
    private Object[] buildExportData(List<Payment> payments) {
        String[] headers = {
                getString(R.string.store_name),
                getString(R.string.date),
                getString(R.string.amount),
                getString(R.string.notes)
        };

        List<List<String>> dataRows = new ArrayList<>();
        double totalSum = 0;

        for (Payment p : payments) {
            List<String> row = new ArrayList<>();
            row.add(p.getStoreName());
            row.add(DateUtils.formatForDisplay(p.getPaymentDate()));
            row.add(NumberUtils.formatCurrency(p.getAmount()));
            row.add(p.getNotes() != null ? p.getNotes() : "");
            dataRows.add(row);
            totalSum += p.getAmount();
        }

        String[] footer = {
                getString(R.string.grand_total),
                "",
                NumberUtils.formatCurrency(totalSum),
                ""
        };

        return new Object[]{headers, dataRows, footer};
    }

    private void exportPdf() {
        final List<Payment> snapshot = new ArrayList<>(currentPayments);
        AppExecutors.runOnDb(() -> {
            try {
                Object[] data = buildExportData(snapshot);
                String[] headers = (String[]) data[0];
                @SuppressWarnings("unchecked")
                List<List<String>> rows = (List<List<String>>) data[1];
                String[] footer = (String[]) data[2];

                final File file = PdfExporter.generateReportPdf(PaymentReportActivity.this,
                        getString(R.string.payment_history), headers, rows, footer);
                AppExecutors.runOnMain(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    ShareHelper.shareFile(PaymentReportActivity.this, file, "application/pdf");
                });
            } catch (final Exception e) {
                AppExecutors.runOnMain(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Toast.makeText(PaymentReportActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void exportExcel() {
        final List<Payment> snapshot = new ArrayList<>(currentPayments);
        AppExecutors.runOnDb(() -> {
            try {
                Object[] data = buildExportData(snapshot);
                String[] headers = (String[]) data[0];
                @SuppressWarnings("unchecked")
                List<List<String>> rows = (List<List<String>>) data[1];

                final File file = ExcelExporter.generateExcel(PaymentReportActivity.this, "Payments", headers, rows);
                AppExecutors.runOnMain(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    ShareHelper.shareFile(PaymentReportActivity.this, file,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                });
            } catch (final Exception e) {
                AppExecutors.runOnMain(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Toast.makeText(PaymentReportActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void exportCsv() {
        final List<Payment> snapshot = new ArrayList<>(currentPayments);
        AppExecutors.runOnDb(() -> {
            try {
                Object[] data = buildExportData(snapshot);
                String[] headers = (String[]) data[0];
                @SuppressWarnings("unchecked")
                List<List<String>> rows = (List<List<String>>) data[1];

                final File file = ExcelExporter.generateCsv(PaymentReportActivity.this, "payment_report", headers, rows);
                AppExecutors.runOnMain(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    ShareHelper.shareFile(PaymentReportActivity.this, file, "text/csv");
                });
            } catch (final Exception e) {
                AppExecutors.runOnMain(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Toast.makeText(PaymentReportActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ── Options menu ─────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, 1, R.string.export_pdf);
        menu.add(Menu.NONE, 2, 2, R.string.export_excel);
        menu.add(Menu.NONE, 3, 3, R.string.export_csv);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case 1:
                exportPdf();
                return true;
            case 2:
                exportExcel();
                return true;
            case 3:
                exportCsv();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
