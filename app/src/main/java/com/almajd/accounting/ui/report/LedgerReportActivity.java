package com.almajd.accounting.ui.report;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.ui.BaseActivity;
import com.almajd.accounting.db.LedgerDao;
import com.almajd.accounting.model.LedgerEntry;
import com.almajd.accounting.util.DateUtils;
import com.almajd.accounting.util.ExcelExporter;
import com.almajd.accounting.util.NumberUtils;
import com.almajd.accounting.util.PdfExporter;
import com.almajd.accounting.util.ShareHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays a full ledger of all customer accounts showing sales, payments, and balances.
 * <p>
 * No filter section -- all stores are shown at once.
 * Each row shows: store name, grand total sales, total paid, balance (colored), last payment date.
 * Footer card shows aggregate totals across all stores.
 * <p>
 * Export options (overflow menu): PDF, Excel, CSV.
 * <p>
 * Layout: activity_ledger_report.xml
 * IDs: toolbar, rv_report, tv_total_sales, tv_total_paid, tv_total_balance
 */
public class LedgerReportActivity extends BaseActivity {

    private RecyclerView rvReport;
    private TextView tvTotalSales;
    private TextView tvTotalPaid;
    private TextView tvTotalBalance;

    private LedgerDao ledgerDao;

    private ReportAdapter adapter;
    private List<LedgerEntry> currentEntries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ledger_report);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.accounts_ledger);
        }

        // DAO
        ledgerDao = new LedgerDao(this);

        // Views
        rvReport = findViewById(R.id.rv_report);
        tvTotalSales = findViewById(R.id.tv_total_sales);
        tvTotalPaid = findViewById(R.id.tv_total_paid);
        tvTotalBalance = findViewById(R.id.tv_balance);

        // RecyclerView
        rvReport.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter(new ArrayList<ReportAdapter.ReportRow>());
        rvReport.setAdapter(adapter);

        // Load data
        loadLedger();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLedger();
    }

    /**
     * Loads all ledger entries from the database and populates the RecyclerView
     * and the footer totals card.
     */
    private void loadLedger() {
        currentEntries = ledgerDao.getAll();
        if (currentEntries == null) {
            currentEntries = new ArrayList<>();
        }

        List<ReportAdapter.ReportRow> rows = new ArrayList<>();
        double sumSales = 0;
        double sumPaid = 0;
        double sumBalance = 0;

        for (LedgerEntry entry : currentEntries) {
            ReportAdapter.ReportRow row = new ReportAdapter.ReportRow(
                    getString(R.string.store_name),
                    entry.getStoreName(),
                    getString(R.string.total_sales),
                    NumberUtils.formatCurrency(entry.getGrandTotalSales()),
                    getString(R.string.balance),
                    NumberUtils.formatCurrency(entry.getBalance())
            );

            // Color the balance: red if positive (money owed), green if zero or negative
            if (entry.getBalance() > 0) {
                row.value3Color = Color.RED;
            } else {
                row.value3Color = Color.parseColor("#2E7D32"); // dark green
            }

            rows.add(row);

            sumSales += entry.getGrandTotalSales();
            sumPaid += entry.getTotalPaid();
            sumBalance += entry.getBalance();
        }

        adapter.updateList(rows);

        // Update footer totals
        tvTotalSales.setText(getString(R.string.total_sales) + ": "
                + NumberUtils.formatCurrency(sumSales));
        tvTotalPaid.setText(getString(R.string.total_paid) + ": "
                + NumberUtils.formatCurrency(sumPaid));
        tvTotalBalance.setText(getString(R.string.current_balance) + ": "
                + NumberUtils.formatCurrency(sumBalance));

        // Color the balance footer text
        if (sumBalance > 0) {
            tvTotalBalance.setTextColor(Color.RED);
        } else {
            tvTotalBalance.setTextColor(Color.parseColor("#2E7D32"));
        }
    }

    // ── Export helpers ────────────────────────────────────────────────────

    /**
     * Builds the header array and data rows list used by all three export formats.
     *
     * @return a three-element Object array: [0] = String[] headers,
     *         [1] = List of List of String rows, [2] = String[] footer
     */
    private Object[] buildExportData() {
        String[] headers = {
                getString(R.string.store_name),
                getString(R.string.total_sales),
                getString(R.string.total_paid),
                getString(R.string.balance),
                getString(R.string.last_payment_date)
        };

        List<List<String>> dataRows = new ArrayList<>();
        double sumSales = 0;
        double sumPaid = 0;
        double sumBalance = 0;

        for (LedgerEntry entry : currentEntries) {
            List<String> row = new ArrayList<>();
            row.add(entry.getStoreName());
            row.add(NumberUtils.formatCurrency(entry.getGrandTotalSales()));
            row.add(NumberUtils.formatCurrency(entry.getTotalPaid()));
            row.add(NumberUtils.formatCurrency(entry.getBalance()));

            String lastDate = entry.getLastPaymentDate();
            row.add(lastDate != null && !lastDate.isEmpty()
                    ? DateUtils.formatForDisplay(lastDate) : "-");

            dataRows.add(row);

            sumSales += entry.getGrandTotalSales();
            sumPaid += entry.getTotalPaid();
            sumBalance += entry.getBalance();
        }

        String[] footer = {
                getString(R.string.grand_total),
                NumberUtils.formatCurrency(sumSales),
                NumberUtils.formatCurrency(sumPaid),
                NumberUtils.formatCurrency(sumBalance),
                ""
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
                    getString(R.string.accounts_ledger), headers, rows, footer);
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

            File file = ExcelExporter.generateExcel(this, "Ledger", headers, rows);
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

            File file = ExcelExporter.generateCsv(this, "ledger_report", headers, rows);
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
