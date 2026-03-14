package com.almajd.accounting.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.almajd.accounting.R;
import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.db.InvoiceDao;
import com.almajd.accounting.db.LedgerDao;
import com.almajd.accounting.db.PaymentDao;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.ui.customer.CustomerListActivity;
import com.almajd.accounting.ui.invoice.InvoiceFormActivity;
import com.almajd.accounting.ui.invoice.InvoiceListActivity;
import com.almajd.accounting.ui.payment.PaymentFormActivity;
import com.almajd.accounting.ui.report.ReportMenuActivity;
import com.almajd.accounting.util.NumberUtils;

import java.util.List;

/**
 * Launcher / home screen for the AlMajd Communications accounting application.
 * <p>
 * Displays a hero section, 6 stat cards (total sales, received, debts,
 * customer count, invoice count, payment count), a 2x2 navigation grid,
 * and a list of the 5 most recent invoices.
 * <p>
 * Layout: activity_dashboard.xml
 */
public class DashboardActivity extends AppCompatActivity {

    // Stat TextViews
    private TextView tvStatTotalSales;
    private TextView tvStatTotalReceived;
    private TextView tvStatTotalDebts;
    private TextView tvStatCustomerCount;
    private TextView tvStatInvoiceCount;
    private TextView tvStatPaymentCount;

    // Recent invoices
    private LinearLayout llRecentInvoices;
    private TextView tvNoRecentInvoices;

    // DAOs
    private CustomerDao customerDao;
    private InvoiceDao invoiceDao;
    private PaymentDao paymentDao;
    private LedgerDao ledgerDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // DAOs
        customerDao = new CustomerDao(this);
        invoiceDao = new InvoiceDao(this);
        paymentDao = new PaymentDao(this);
        ledgerDao = new LedgerDao(this);

        // Stat card views
        tvStatTotalSales = findViewById(R.id.tv_stat_total_sales);
        tvStatTotalReceived = findViewById(R.id.tv_stat_total_received);
        tvStatTotalDebts = findViewById(R.id.tv_stat_total_debts);
        tvStatCustomerCount = findViewById(R.id.tv_stat_customer_count);
        tvStatInvoiceCount = findViewById(R.id.tv_stat_invoice_count);
        tvStatPaymentCount = findViewById(R.id.tv_stat_payment_count);

        // Recent invoices
        llRecentInvoices = findViewById(R.id.ll_recent_invoices);
        tvNoRecentInvoices = findViewById(R.id.tv_no_recent_invoices);

        // Navigation cards (MaterialCardView, not Button)
        View btnAddCustomer = findViewById(R.id.btn_add_customer);
        View btnNewInvoice = findViewById(R.id.btn_new_invoice);
        View btnAddPayment = findViewById(R.id.btn_add_payment);
        View btnReports = findViewById(R.id.btn_reports);

        btnAddCustomer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DashboardActivity.this, CustomerListActivity.class));
            }
        });

        btnNewInvoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DashboardActivity.this, InvoiceFormActivity.class));
            }
        });

        btnAddPayment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DashboardActivity.this, PaymentFormActivity.class));
            }
        });

        btnReports.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DashboardActivity.this, ReportMenuActivity.class));
            }
        });

        // Settings button
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(DashboardActivity.this, SettingsActivity.class));
                }
            });
        }

        // "View All" link for recent invoices
        TextView tvViewAll = findViewById(R.id.tv_view_all_invoices);
        if (tvViewAll != null) {
            tvViewAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(DashboardActivity.this, InvoiceListActivity.class));
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
    }

    /**
     * Queries the database for all dashboard statistics and recent invoices,
     * then updates the UI.
     */
    private void refreshDashboard() {
        // Stat values
        double totalSales = invoiceDao.getTotalSales();
        double totalPaid = paymentDao.getTotalPayments();
        double totalDebts = ledgerDao.getTotalOutstandingBalance();
        int customerCount = customerDao.getCount();
        int invoiceCount = invoiceDao.getCount();
        int paymentCount = paymentDao.getCount();

        tvStatTotalSales.setText(NumberUtils.formatCurrency(totalSales));
        tvStatTotalReceived.setText(NumberUtils.formatCurrency(totalPaid));
        tvStatTotalDebts.setText(NumberUtils.formatCurrency(totalDebts));
        tvStatCustomerCount.setText(String.valueOf(customerCount));
        tvStatInvoiceCount.setText(String.valueOf(invoiceCount));
        tvStatPaymentCount.setText(String.valueOf(paymentCount));

        // Recent invoices (last 5)
        List<Invoice> recentInvoices = invoiceDao.getRecent(5);
        llRecentInvoices.removeAllViews();

        if (recentInvoices.isEmpty()) {
            tvNoRecentInvoices.setVisibility(View.VISIBLE);
            llRecentInvoices.setVisibility(View.GONE);
        } else {
            tvNoRecentInvoices.setVisibility(View.GONE);
            llRecentInvoices.setVisibility(View.VISIBLE);

            LayoutInflater inflater = LayoutInflater.from(this);
            for (int i = 0; i < recentInvoices.size(); i++) {
                Invoice inv = recentInvoices.get(i);
                View row = inflater.inflate(R.layout.item_recent_invoice, llRecentInvoices, false);

                TextView tvStore = row.findViewById(R.id.tv_recent_store);
                TextView tvDate = row.findViewById(R.id.tv_recent_date);
                TextView tvAmount = row.findViewById(R.id.tv_recent_amount);
                TextView tvRemaining = row.findViewById(R.id.tv_recent_remaining);

                tvStore.setText(inv.getStoreName());
                tvDate.setText(inv.getInvoiceDate());
                tvAmount.setText(NumberUtils.formatCurrency(inv.getTotalAmount()));

                if (inv.getRemaining() > 0) {
                    tvRemaining.setText(getString(R.string.remaining) + ": " +
                            NumberUtils.formatCurrency(inv.getRemaining()));
                    tvRemaining.setVisibility(View.VISIBLE);
                } else {
                    tvRemaining.setVisibility(View.GONE);
                }

                llRecentInvoices.addView(row);

                // Add divider between items (not after last)
                if (i < recentInvoices.size() - 1) {
                    View divider = new View(this);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(getResources().getColor(R.color.divider));
                    llRecentInvoices.addView(divider);
                }
            }
        }
    }
}
