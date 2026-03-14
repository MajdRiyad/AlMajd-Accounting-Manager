package com.almajd.accounting.ui.report;

import android.graphics.Color;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.db.InvoiceDao;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.util.DateUtils;
import com.almajd.accounting.util.ExcelExporter;
import com.almajd.accounting.util.NumberUtils;
import com.almajd.accounting.util.PdfExporter;
import com.almajd.accounting.util.ShareHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays a filterable list of sales invoices with totals and export capabilities.
 * <p>
 * Filters: store name (Spinner), date range (from/to with DatePicker), filter button.
 * Each row shows: store name, invoice date, total amount, remaining amount.
 * Footer displays the sum of all displayed invoice totals.
 * <p>
 * Export options (overflow menu): PDF, Excel, CSV.
 * <p>
 * Layout: activity_sales_report.xml
 * IDs: toolbar, spinner_store, et_from_date, et_to_date, btn_filter, rv_report, tv_total
 */
public class SalesReportActivity extends AppCompatActivity {

    private static final String ALL_STORES_KEY = "__ALL__";

    private Spinner spinnerStore;
    private TextView etFromDate;
    private TextView etToDate;
    private RecyclerView rvReport;
    private TextView tvTotal;

    private InvoiceDao invoiceDao;
    private CustomerDao customerDao;

    private ReportAdapter adapter;
    private List<Invoice> currentInvoices = new ArrayList<>();
    private List<String> storeNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sales_report);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.sales_history);
        }

        // DAOs
        invoiceDao = new InvoiceDao(this);
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
                DateUtils.showDatePicker(SalesReportActivity.this, etFromDate);
            }
        });

        etToDate.setFocusable(false);
        etToDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DateUtils.showDatePicker(SalesReportActivity.this, etToDate);
            }
        });

        // RecyclerView
        rvReport.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter(new ArrayList<ReportAdapter.ReportRow>());
        rvReport.setAdapter(adapter);

        // Populate store spinner
        populateStoreSpinner();

        // Filter button
        btnFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyFilter();
            }
        });

        // Load all invoices initially
        applyFilter();
    }

    /**
     * Populates the store spinner with "All Stores" as the first entry,
     * followed by every customer's store name from the database.
     */
    private void populateStoreSpinner() {
        storeNames.clear();
        storeNames.add(getString(R.string.all_stores));

        List<Customer> customers = customerDao.getAll();
        if (customers != null) {
            for (Customer c : customers) {
                storeNames.add(c.getStoreName());
            }
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, storeNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStore.setAdapter(spinnerAdapter);
    }

    /**
     * Reads the current filter state (store selection and date range) and
     * queries the InvoiceDao accordingly. Updates the RecyclerView and footer total.
     */
    private void applyFilter() {
        int storeIndex = spinnerStore.getSelectedItemPosition();
        String fromDate = etFromDate.getText().toString().trim();
        String toDate = etToDate.getText().toString().trim();

        boolean isAllStores = (storeIndex <= 0);
        String selectedStore = isAllStores ? null : storeNames.get(storeIndex);
        boolean hasDates = !fromDate.isEmpty() && !toDate.isEmpty();

        if (isAllStores && !hasDates) {
            currentInvoices = invoiceDao.getAll();
        } else if (isAllStores) {
            currentInvoices = invoiceDao.getByDateRange(fromDate, toDate);
        } else if (!hasDates) {
            currentInvoices = invoiceDao.getByStoreName(selectedStore);
        } else {
            currentInvoices = invoiceDao.getByStoreAndDateRange(selectedStore, fromDate, toDate);
        }

        if (currentInvoices == null) {
            currentInvoices = new ArrayList<>();
        }

        // Build report rows
        List<ReportAdapter.ReportRow> rows = new ArrayList<>();
        double totalSum = 0;

        for (Invoice inv : currentInvoices) {
            ReportAdapter.ReportRow row = new ReportAdapter.ReportRow(
                    getString(R.string.store_name),
                    inv.getStoreName(),
                    getString(R.string.date),
                    DateUtils.formatForDisplay(inv.getInvoiceDate()),
                    getString(R.string.total),
                    NumberUtils.formatCurrency(inv.getTotalAmount())
            );

            // Color the value3 (total) red if there is a remaining balance
            if (inv.getRemaining() > 0) {
                row.value3Color = Color.RED;
            }

            rows.add(row);
            totalSum += inv.getTotalAmount();
        }

        adapter.updateList(rows);
        tvTotal.setText(getString(R.string.grand_total) + ": " + NumberUtils.formatCurrency(totalSum));
    }

    // ── Export helpers ────────────────────────────────────────────────────

    /**
     * Builds the header array and data rows list used by all three export formats.
     *
     * @return a two-element Object array: [0] = String[] headers, [1] = List of List of String rows
     */
    private Object[] buildExportData() {
        String[] headers = {
                getString(R.string.store_name),
                getString(R.string.date),
                getString(R.string.total),
                getString(R.string.remaining)
        };

        List<List<String>> dataRows = new ArrayList<>();
        double totalSum = 0;
        double totalRemaining = 0;

        for (Invoice inv : currentInvoices) {
            List<String> row = new ArrayList<>();
            row.add(inv.getStoreName());
            row.add(DateUtils.formatForDisplay(inv.getInvoiceDate()));
            row.add(NumberUtils.formatCurrency(inv.getTotalAmount()));
            row.add(NumberUtils.formatCurrency(inv.getRemaining()));
            dataRows.add(row);
            totalSum += inv.getTotalAmount();
            totalRemaining += inv.getRemaining();
        }

        String[] footer = {
                getString(R.string.grand_total),
                "",
                NumberUtils.formatCurrency(totalSum),
                NumberUtils.formatCurrency(totalRemaining)
        };

        return new Object[]{headers, dataRows, footer};
    }

    private void exportPdf() {
        try {
            Object[] data = buildExportData();
            String[] headers = (String[]) data[0];
            @SuppressWarnings("unchecked")
            List<List<String>> rows = (List<List<String>>) data[1];
            String[] footer = (String[]) data[2];

            File file = PdfExporter.generateReportPdf(this,
                    getString(R.string.sales_history), headers, rows, footer);
            ShareHelper.shareFile(this, file, "application/pdf");
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportExcel() {
        try {
            Object[] data = buildExportData();
            String[] headers = (String[]) data[0];
            @SuppressWarnings("unchecked")
            List<List<String>> rows = (List<List<String>>) data[1];

            File file = ExcelExporter.generateExcel(this, "Sales", headers, rows);
            ShareHelper.shareFile(this, file,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportCsv() {
        try {
            Object[] data = buildExportData();
            String[] headers = (String[]) data[0];
            @SuppressWarnings("unchecked")
            List<List<String>> rows = (List<List<String>>) data[1];

            File file = ExcelExporter.generateCsv(this, "sales_report", headers, rows);
            ShareHelper.shareFile(this, file, "text/csv");
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
