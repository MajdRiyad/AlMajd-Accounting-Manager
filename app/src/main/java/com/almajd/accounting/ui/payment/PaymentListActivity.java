package com.almajd.accounting.ui.payment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.db.PaymentDao;
import com.almajd.accounting.model.Payment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays a list of payments with add, edit, and delete capabilities.
 * <p>
 * Accepts an optional {@link #EXTRA_STORE_NAME} intent extra to filter
 * payments for a specific store (e.g. when launched from CustomerDetailActivity).
 * <p>
 * Layout: activity_payment_list.xml
 * Contains: Toolbar, RecyclerView (rv_payments), empty state (tv_empty), FAB (fab_add)
 */
public class PaymentListActivity extends AppCompatActivity
        implements PaymentAdapter.OnPaymentClickListener {

    public static final String EXTRA_STORE_NAME = "extra_store_name";
    public static final String EXTRA_PAYMENT_ID = "extra_payment_id";

    private static final int REQUEST_ADD_PAYMENT = 200;
    private static final int REQUEST_EDIT_PAYMENT = 201;

    private RecyclerView rvPayments;
    private TextView tvEmpty;
    private PaymentAdapter adapter;
    private PaymentDao paymentDao;

    /** Optional store-name filter; null means show all payments. */
    private String filterStoreName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_list);

        // Check for optional store filter
        if (getIntent() != null && getIntent().hasExtra(EXTRA_STORE_NAME)) {
            filterStoreName = getIntent().getStringExtra(EXTRA_STORE_NAME);
        }

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (filterStoreName != null && !filterStoreName.isEmpty()) {
                getSupportActionBar().setTitle(
                        getString(R.string.payments) + " - " + filterStoreName);
            } else {
                getSupportActionBar().setTitle(R.string.payments);
            }
        }

        // DAO
        paymentDao = new PaymentDao(this);

        // Views
        rvPayments = findViewById(R.id.rv_payments);
        tvEmpty = findViewById(R.id.tv_empty);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add_payment);

        // RecyclerView setup
        rvPayments.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PaymentAdapter(new ArrayList<Payment>(), this);
        rvPayments.setAdapter(adapter);

        // FAB: add new payment
        fabAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PaymentListActivity.this, PaymentFormActivity.class);
                if (filterStoreName != null && !filterStoreName.isEmpty()) {
                    intent.putExtra(EXTRA_STORE_NAME, filterStoreName);
                }
                startActivityForResult(intent, REQUEST_ADD_PAYMENT);
            }
        });

        // Load initial data
        loadPayments();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPayments();
    }

    /**
     * Loads payments from the database and updates the adapter.
     * If a store filter is set, only that store's payments are loaded.
     * Toggles the empty-state view accordingly.
     */
    private void loadPayments() {
        List<Payment> payments;
        if (filterStoreName != null && !filterStoreName.isEmpty()) {
            payments = paymentDao.getByStoreName(filterStoreName);
        } else {
            payments = paymentDao.getAll();
        }
        if (payments == null) {
            payments = new ArrayList<>();
        }
        adapter.updateList(payments);
        updateEmptyState(payments.size());
    }

    /**
     * Shows or hides the empty-state text based on the list size.
     */
    private void updateEmptyState(int count) {
        if (count == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvPayments.setVisibility(View.GONE);
            tvEmpty.setText(R.string.no_payments);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvPayments.setVisibility(View.VISIBLE);
        }
    }

    // ── Adapter callbacks ────────────────────────────────────────────────

    @Override
    public void onPaymentClick(Payment payment) {
        Intent intent = new Intent(this, PaymentFormActivity.class);
        intent.putExtra(EXTRA_PAYMENT_ID, payment.getId());
        startActivityForResult(intent, REQUEST_EDIT_PAYMENT);
    }

    @Override
    public void onPaymentLongClick(Payment payment, View anchorView) {
        showPopupMenu(payment, anchorView);
    }

    /**
     * Shows Edit / Delete popup menu anchored to the long-pressed item view.
     */
    private void showPopupMenu(final Payment payment, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(Menu.NONE, 1, 1, R.string.edit);
        popup.getMenu().add(Menu.NONE, 2, 2, R.string.delete);

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1: // Edit
                        Intent editIntent = new Intent(
                                PaymentListActivity.this, PaymentFormActivity.class);
                        editIntent.putExtra(EXTRA_PAYMENT_ID, payment.getId());
                        startActivityForResult(editIntent, REQUEST_EDIT_PAYMENT);
                        return true;

                    case 2: // Delete
                        confirmDelete(payment);
                        return true;

                    default:
                        return false;
                }
            }
        });
        popup.show();
    }

    /**
     * Shows a confirmation dialog before deleting the payment.
     */
    private void confirmDelete(final Payment payment) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(R.string.confirm_delete_message)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        paymentDao.delete(payment.getId());
                        Toast.makeText(PaymentListActivity.this,
                                R.string.deleted_successfully, Toast.LENGTH_SHORT).show();
                        loadPayments();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── Activity result ──────────────────────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            loadPayments();
        }
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
