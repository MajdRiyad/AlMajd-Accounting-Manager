package com.almajd.accounting.ui.customer;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.ui.BaseActivity;
import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.db.InvoiceDao;
import com.almajd.accounting.db.LedgerDao;
import com.almajd.accounting.db.PaymentDao;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.LedgerEntry;
import com.almajd.accounting.model.Payment;
import com.almajd.accounting.util.DateUtils;
import com.almajd.accounting.util.NumberUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays detailed information for a single customer including a balance
 * summary card, the last 5 invoices, and the last 5 payments.
 * <p>
 * Expects {@link #EXTRA_STORE_NAME} in the launching intent.
 * <p>
 * Layout: activity_customer_detail.xml
 */
public class CustomerDetailActivity extends BaseActivity {

    public static final String EXTRA_STORE_NAME = "extra_store_name";

    private static final int REQUEST_EDIT = 200;
    private static final int MAX_RECENT_ITEMS = 5;

    // DAOs
    private CustomerDao customerDao;
    private InvoiceDao invoiceDao;
    private PaymentDao paymentDao;
    private LedgerDao ledgerDao;

    // Header views
    private TextView tvStoreName;
    private TextView tvOwnerName;
    private TextView tvLocation;
    private TextView tvPhone;

    // Balance summary views
    private TextView tvTotalSales;
    private TextView tvTotalPaid;
    private TextView tvBalance;

    // Recent lists
    private RecyclerView rvInvoices;
    private RecyclerView rvPayments;

    // Navigation buttons
    private Button btnViewAllInvoices;
    private Button btnViewAllPayments;

    private String storeName;

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_detail);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.customer_details);
        }

        // Store name from intent
        storeName = getIntent().getStringExtra(EXTRA_STORE_NAME);
        if (TextUtils.isEmpty(storeName)) {
            finish();
            return;
        }

        // DAOs
        customerDao = new CustomerDao(this);
        invoiceDao = new InvoiceDao(this);
        paymentDao = new PaymentDao(this);
        ledgerDao = new LedgerDao(this);

        // Bind views
        tvStoreName = findViewById(R.id.tv_store_name);
        tvOwnerName = findViewById(R.id.tv_owner_name);
        tvLocation = findViewById(R.id.tv_location);
        tvPhone = findViewById(R.id.tv_phone);
        tvTotalSales = findViewById(R.id.tv_total_sales);
        tvTotalPaid = findViewById(R.id.tv_total_paid);
        tvBalance = findViewById(R.id.tv_balance);
        rvInvoices = findViewById(R.id.rv_invoices);
        rvPayments = findViewById(R.id.rv_payments);
        btnViewAllInvoices = findViewById(R.id.btn_view_all_invoices);
        btnViewAllPayments = findViewById(R.id.btn_view_all_payments);

        // RecyclerView layout managers (no nested scrolling for embedded lists)
        rvInvoices.setLayoutManager(new LinearLayoutManager(this));
        rvInvoices.setNestedScrollingEnabled(false);
        rvPayments.setLayoutManager(new LinearLayoutManager(this));
        rvPayments.setNestedScrollingEnabled(false);

        // Navigation buttons
        btnViewAllInvoices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToInvoiceList();
            }
        });

        btnViewAllPayments.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToPaymentList();
            }
        });

        // Load data
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    // ── Data loading ─────────────────────────────────────────────────────

    /**
     * Loads the customer, ledger entry, recent invoices, and recent payments
     * from the database and populates all UI sections.
     */
    private void loadData() {
        loadCustomerInfo();
        loadBalanceSummary();
        loadRecentInvoices();
        loadRecentPayments();
    }

    private void loadCustomerInfo() {
        Customer customer = customerDao.getByStoreName(storeName);
        if (customer == null) {
            // Customer was deleted externally
            finish();
            return;
        }

        tvStoreName.setText(safeText(customer.getStoreName()));
        tvOwnerName.setText(safeText(customer.getOwnerName()));
        tvLocation.setText(safeText(customer.getLocation()));
        tvPhone.setText(safeText(customer.getPhone()));
    }

    private void loadBalanceSummary() {
        LedgerEntry ledger = ledgerDao.getByStoreName(storeName);

        double totalSales = 0;
        double totalPaid = 0;
        double balance = 0;

        if (ledger != null) {
            totalSales = ledger.getGrandTotalSales();
            totalPaid = ledger.getTotalPaid();
            balance = ledger.getBalance();
        }

        tvTotalSales.setText(NumberUtils.formatCurrency(totalSales));
        tvTotalPaid.setText(NumberUtils.formatCurrency(totalPaid));
        tvBalance.setText(NumberUtils.formatCurrency(balance));

        // Color the balance: red if customer owes money (> 0), green otherwise
        if (balance > 0) {
            tvBalance.setTextColor(ContextCompat.getColor(this, R.color.red));
        } else {
            tvBalance.setTextColor(ContextCompat.getColor(this, R.color.green));
        }
    }

    private void loadRecentInvoices() {
        List<Invoice> allInvoices = invoiceDao.getByStoreName(storeName);
        if (allInvoices == null) {
            allInvoices = new ArrayList<>();
        }

        // Take last N items (most recent first -- the DAO may already sort)
        List<Invoice> recent = allInvoices.size() > MAX_RECENT_ITEMS
                ? allInvoices.subList(0, MAX_RECENT_ITEMS)
                : allInvoices;

        rvInvoices.setAdapter(new MiniInvoiceAdapter(recent));

        // Hide the "view all" button when there are no invoices at all
        btnViewAllInvoices.setVisibility(allInvoices.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void loadRecentPayments() {
        List<Payment> allPayments = paymentDao.getByStoreName(storeName);
        if (allPayments == null) {
            allPayments = new ArrayList<>();
        }

        List<Payment> recent = allPayments.size() > MAX_RECENT_ITEMS
                ? allPayments.subList(0, MAX_RECENT_ITEMS)
                : allPayments;

        rvPayments.setAdapter(new MiniPaymentAdapter(recent));

        btnViewAllPayments.setVisibility(allPayments.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ── Navigation helpers ───────────────────────────────────────────────

    private void navigateToInvoiceList() {
        try {
            // InvoiceListActivity is assumed to exist in a sibling package
            Class<?> cls = Class.forName("com.almajd.accounting.ui.invoice.InvoiceListActivity");
            Intent intent = new Intent(this, cls);
            intent.putExtra(EXTRA_STORE_NAME, storeName);
            startActivity(intent);
        } catch (ClassNotFoundException e) {
            Toast.makeText(this, R.string.no_invoices, Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToPaymentList() {
        try {
            Class<?> cls = Class.forName("com.almajd.accounting.ui.payment.PaymentListActivity");
            Intent intent = new Intent(this, cls);
            intent.putExtra(EXTRA_STORE_NAME, storeName);
            startActivity(intent);
        } catch (ClassNotFoundException e) {
            Toast.makeText(this, R.string.no_payments, Toast.LENGTH_SHORT).show();
        }
    }

    // ── Toolbar menu (Edit / Delete) ─────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, 1, R.string.edit);
        menu.add(Menu.NONE, 2, 2, R.string.delete);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;

            case 1: // Edit
                Intent editIntent = new Intent(this, CustomerFormActivity.class);
                editIntent.putExtra(EXTRA_STORE_NAME, storeName);
                startActivityForResult(editIntent, REQUEST_EDIT);
                return true;

            case 2: // Delete
                confirmDelete();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            loadData();
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(R.string.confirm_delete_message)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        customerDao.delete(storeName);
                        Toast.makeText(CustomerDetailActivity.this,
                                R.string.deleted_successfully, Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── Utility ──────────────────────────────────────────────────────────

    /**
     * Returns the supplied text or a dash if blank / null.
     */
    private String safeText(String text) {
        return TextUtils.isEmpty(text) ? "\u2014" : text;
    }

    // =====================================================================
    //  INNER ADAPTER: Mini Invoice list (last 5 invoices)
    // =====================================================================

    /**
     * Lightweight RecyclerView adapter that displays recent invoices
     * using {@code android.R.layout.simple_list_item_2} as a quick row layout.
     * Each row shows the invoice date on line 1 and the total / remaining on line 2.
     */
    private class MiniInvoiceAdapter extends RecyclerView.Adapter<MiniInvoiceAdapter.VH> {

        private final List<Invoice> invoices;

        MiniInvoiceAdapter(List<Invoice> invoices) {
            this.invoices = invoices != null ? invoices : new ArrayList<Invoice>();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Invoice inv = invoices.get(position);

            // Line 1: date
            String date = inv.getInvoiceDate() != null
                    ? DateUtils.formatForDisplay(inv.getInvoiceDate()) : "\u2014";
            holder.text1.setText(date);

            // Line 2: total / remaining
            String summary = NumberUtils.formatCurrency(inv.getTotalAmount())
                    + "  |  "
                    + getString(R.string.remaining) + ": "
                    + NumberUtils.formatCurrency(inv.getRemaining());
            holder.text2.setText(summary);
        }

        @Override
        public int getItemCount() {
            return invoices.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView text1;
            final TextView text2;

            VH(@NonNull View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }

    // =====================================================================
    //  INNER ADAPTER: Mini Payment list (last 5 payments)
    // =====================================================================

    /**
     * Lightweight RecyclerView adapter that displays recent payments.
     * Each row shows the payment date on line 1 and the amount on line 2.
     */
    private class MiniPaymentAdapter extends RecyclerView.Adapter<MiniPaymentAdapter.VH> {

        private final List<Payment> payments;

        MiniPaymentAdapter(List<Payment> payments) {
            this.payments = payments != null ? payments : new ArrayList<Payment>();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Payment pmt = payments.get(position);

            // Line 1: date
            String date = pmt.getPaymentDate() != null
                    ? DateUtils.formatForDisplay(pmt.getPaymentDate()) : "\u2014";
            holder.text1.setText(date);

            // Line 2: amount
            holder.text2.setText(NumberUtils.formatCurrency(pmt.getAmount()));
        }

        @Override
        public int getItemCount() {
            return payments.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView text1;
            final TextView text2;

            VH(@NonNull View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}
