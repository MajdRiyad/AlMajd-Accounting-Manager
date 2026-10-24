package com.almajd.accounting.ui.report;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.widget.Toolbar;

import com.almajd.accounting.R;
import com.almajd.accounting.ui.BaseActivity;

/**
 * Simple menu screen that provides navigation to the three report types:
 * Sales Report, Payment Report, and Ledger Report.
 * <p>
 * Layout: activity_report_menu.xml
 * IDs: toolbar, btn_sales_report, btn_payment_report, btn_ledger_report
 */
public class ReportMenuActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_menu);

        // Toolbar with back button
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.reports);
        }

        // Navigation buttons
        Button btnSalesReport = findViewById(R.id.btn_sales_report);
        Button btnPaymentReport = findViewById(R.id.btn_payment_report);
        Button btnLedgerReport = findViewById(R.id.btn_ledger_report);

        btnSalesReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ReportMenuActivity.this, SalesReportActivity.class);
                startActivity(intent);
            }
        });

        btnPaymentReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ReportMenuActivity.this, PaymentReportActivity.class);
                startActivity(intent);
            }
        });

        btnLedgerReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ReportMenuActivity.this, LedgerReportActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
