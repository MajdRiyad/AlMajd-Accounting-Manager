package com.almajd.accounting.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.almajd.accounting.R;
import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.db.LedgerDao;
import com.almajd.accounting.ui.customer.CustomerListActivity;
import com.almajd.accounting.ui.invoice.InvoiceFormActivity;
import com.almajd.accounting.ui.payment.PaymentFormActivity;
import com.almajd.accounting.ui.report.ReportMenuActivity;
import com.almajd.accounting.util.NumberUtils;
import android.widget.ImageButton;

/**
 * Launcher / home screen for the AlMajd Communications accounting application.
 * <p>
 * Displays a greeting, a summary card (customer count and outstanding balance),
 * and four navigation buttons leading to the main feature areas.
 * <p>
 * Layout: activity_dashboard.xml
 * IDs: tv_header_title, tv_greeting, btn_add_customer, btn_new_invoice,
 *      btn_add_payment, btn_reports, tv_total_customers, tv_outstanding_balance
 */
public class DashboardActivity extends AppCompatActivity {

    private TextView tvTotalCustomers;
    private TextView tvOutstandingBalance;

    private CustomerDao customerDao;
    private LedgerDao ledgerDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // DAOs
        customerDao = new CustomerDao(this);
        ledgerDao = new LedgerDao(this);

        // Summary card views
        tvTotalCustomers = findViewById(R.id.tv_total_customers);
        tvOutstandingBalance = findViewById(R.id.tv_outstanding_balance);

        // Navigation buttons
        Button btnAddCustomer = findViewById(R.id.btn_add_customer);
        Button btnNewInvoice = findViewById(R.id.btn_new_invoice);
        Button btnAddPayment = findViewById(R.id.btn_add_payment);
        Button btnReports = findViewById(R.id.btn_reports);

        btnAddCustomer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, CustomerListActivity.class);
                startActivity(intent);
            }
        });

        btnNewInvoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, InvoiceFormActivity.class);
                startActivity(intent);
            }
        });

        btnAddPayment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, PaymentFormActivity.class);
                startActivity(intent);
            }
        });

        btnReports.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, ReportMenuActivity.class);
                startActivity(intent);
            }
        });

        // Settings button
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(DashboardActivity.this, SettingsActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSummary();
    }

    /**
     * Queries the database for the current customer count and the total outstanding
     * balance across all stores, then updates the summary card TextViews.
     */
    private void refreshSummary() {
        int customerCount = customerDao.getCount();
        double outstandingBalance = ledgerDao.getTotalOutstandingBalance();

        tvTotalCustomers.setText(getString(R.string.total_customers, customerCount));
        tvOutstandingBalance.setText(getString(R.string.outstanding_balance,
                NumberUtils.formatCurrency(outstandingBalance)));
    }
}
