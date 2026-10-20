package com.almajd.accounting.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.almajd.accounting.R;
import com.almajd.accounting.backup.BackupManager;
import com.almajd.accounting.backup.BackupReminder;
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
import com.almajd.accounting.util.AppExecutors;
import com.almajd.accounting.util.DateUtils;
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
public class DashboardActivity extends BaseActivity {

    // Stat TextViews
    private TextView tvStatTotalSales;
    private TextView tvStatTotalReceived;
    private TextView tvStatTotalDebts;
    private TextView tvStatTotalCredit;
    private TextView tvStatCustomerCount;
    private TextView tvStatInvoiceCount;
    private TextView tvStatPaymentCount;

    // Recent invoices
    private LinearLayout llRecentInvoices;
    private TextView tvNoRecentInvoices;

    // Backup reminder banner
    private View cardBackupReminder;
    private TextView tvBackupReminder;
    private boolean backupReminderDismissed;

    // DAOs
    private CustomerDao customerDao;
    private InvoiceDao invoiceDao;
    private PaymentDao paymentDao;
    private LedgerDao ledgerDao;

    /** Incremented on every refresh so stale background results are discarded. */
    private int loadToken = 0;

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
        tvStatTotalCredit = findViewById(R.id.tv_stat_total_credit);
        tvStatCustomerCount = findViewById(R.id.tv_stat_customer_count);
        tvStatInvoiceCount = findViewById(R.id.tv_stat_invoice_count);
        tvStatPaymentCount = findViewById(R.id.tv_stat_payment_count);

        // Recent invoices
        llRecentInvoices = findViewById(R.id.ll_recent_invoices);
        tvNoRecentInvoices = findViewById(R.id.tv_no_recent_invoices);

        // Backup reminder banner
        cardBackupReminder = findViewById(R.id.card_backup_reminder);
        tvBackupReminder = findViewById(R.id.tv_backup_reminder);
        View btnBackupNow = findViewById(R.id.btn_backup_now);
        View btnBackupReminderDismiss = findViewById(R.id.btn_backup_reminder_dismiss);
        if (btnBackupNow != null) {
            btnBackupNow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(DashboardActivity.this, SettingsActivity.class));
                }
            });
        }
        if (btnBackupReminderDismiss != null) {
            btnBackupReminderDismiss.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    backupReminderDismissed = true;
                    if (cardBackupReminder != null) {
                        cardBackupReminder.setVisibility(View.GONE);
                    }
                }
            });
        }

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

        showPendingRecoveryMessage();
    }

    /**
     * Shows the one-time message queued by {@link BackupManager#recoverIfInterrupted}
     * when a restore was interrupted, then clears it so it is never shown twice.
     */
    private void showPendingRecoveryMessage() {
        int pending = BackupManager.consumePendingRecoveryMessage(this);
        if (pending == BackupManager.RECOVERY_RECOVERED_PREVIOUS) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.restore_recovered_title)
                    .setMessage(R.string.restore_recovered_message)
                    .setPositiveButton(R.string.dismiss, null)
                    .show();
        } else if (pending == BackupManager.RECOVERY_KEPT_RESTORED) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.restore_completed_title)
                    .setMessage(R.string.restore_completed_message)
                    .setPositiveButton(R.string.dismiss, null)
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
    }

    /**
     * Queries the database for all dashboard statistics and recent invoices on
     * the background database thread, then updates the UI on the main thread.
     * A request token makes sure only the newest refresh can update the views.
     */
    private void refreshDashboard() {
        final int token = ++loadToken;
        AppExecutors.runOnDb(() -> {
            final double totalSales = invoiceDao.getTotalSales();
            final double totalPaid = paymentDao.getTotalPayments();
            // Debts = sum of only the POSITIVE customer balances (money owed to us).
            final double totalDebts = ledgerDao.getTotalDebt();
            // Credit = sum of only the NEGATIVE balances (money customers have with us).
            final double totalCredit = ledgerDao.getTotalCredit();
            final int customerCount = customerDao.getCount();
            final int invoiceCount = invoiceDao.getCount();
            final int paymentCount = paymentDao.getCount();
            final List<Invoice> recentInvoices = invoiceDao.getRecent(5, false);

            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (token != loadToken) {
                    return;
                }

                tvStatTotalSales.setText(NumberUtils.formatCurrency(totalSales));
                tvStatTotalReceived.setText(NumberUtils.formatCurrency(totalPaid));
                tvStatTotalDebts.setText(NumberUtils.formatCurrency(totalDebts));
                tvStatTotalCredit.setText(NumberUtils.formatCurrency(totalCredit));
                tvStatCustomerCount.setText(String.valueOf(customerCount));
                tvStatInvoiceCount.setText(String.valueOf(invoiceCount));
                tvStatPaymentCount.setText(String.valueOf(paymentCount));

                updateBackupReminder(customerCount + invoiceCount + paymentCount > 0);

                renderRecentInvoices(recentInvoices);
            });
        });
    }

    /**
     * Renders the "recent invoices" list. Must be called on the main thread.
     */
    private void renderRecentInvoices(List<Invoice> recentInvoices) {
        llRecentInvoices.removeAllViews();

        if (recentInvoices == null || recentInvoices.isEmpty()) {
            tvNoRecentInvoices.setVisibility(View.VISIBLE);
            llRecentInvoices.setVisibility(View.GONE);
            return;
        }

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
            tvDate.setText(DateUtils.formatForDisplay(inv.getInvoiceDate()));
            tvAmount.setText(NumberUtils.formatCurrency(inv.getTotalAmount()));

            double creditedFromCustomer = inv.getAllocatedFromCredit();
            if (inv.getRemaining() > 0) {
                tvRemaining.setText(getString(R.string.remaining) + ": " +
                        NumberUtils.formatCurrency(inv.getRemaining()));
                tvRemaining.setVisibility(View.VISIBLE);
            } else if (creditedFromCustomer > 0) {
                // Settled from the customer's on-account credit: say so instead of
                // hiding the line (the remaining is now 0, not "no balance").
                tvRemaining.setText(getString(R.string.credit_from_customer_amount,
                        NumberUtils.formatCurrency(creditedFromCustomer)));
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

    /**
     * Shows the weekly backup reminder banner when there is data and either no
     * backup has ever been made or the last one is older than
     * {@link BackupReminder#REMINDER_INTERVAL_MILLIS}. Dismissing hides it until
     * the next app launch.
     */
    private void updateBackupReminder(boolean hasData) {
        if (cardBackupReminder == null) {
            return;
        }
        long lastBackup = BackupManager.getLastBackupTimeMillis(this);
        boolean hasBackup = lastBackup > 0;
        boolean shouldShow = !backupReminderDismissed
                && BackupReminder.shouldRemind(hasData, hasBackup, lastBackup,
                System.currentTimeMillis());
        if (!shouldShow) {
            cardBackupReminder.setVisibility(View.GONE);
            return;
        }
        if (hasBackup) {
            long days = BackupReminder.daysSince(lastBackup, System.currentTimeMillis());
            tvBackupReminder.setText(getString(R.string.backup_reminder_days_ago, (int) days));
        } else {
            tvBackupReminder.setText(R.string.backup_reminder_never);
        }
        cardBackupReminder.setVisibility(View.VISIBLE);
    }
}
