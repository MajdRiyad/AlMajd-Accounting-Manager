package com.almajd.accounting.ui.invoice;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
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
import com.almajd.accounting.db.InvoiceDao;
import com.almajd.accounting.model.Invoice;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/**
 * Displays a list of invoices. Supports optional filtering by store name via intent extra.
 * Provides a FAB for creating new invoices and context menus for editing / deleting existing ones.
 */
public class InvoiceListActivity extends AppCompatActivity
        implements InvoiceAdapter.OnInvoiceClickListener {

    public static final String EXTRA_STORE_NAME = "extra_store_name";
    public static final String EXTRA_INVOICE_ID = "extra_invoice_id";

    private static final int REQUEST_ADD_INVOICE = 100;
    private static final int REQUEST_EDIT_INVOICE = 101;
    private static final int REQUEST_VIEW_INVOICE = 102;

    private RecyclerView rvInvoices;
    private TextView tvEmpty;
    private InvoiceAdapter adapter;
    private InvoiceDao invoiceDao;

    /** Optional store name filter passed via intent. */
    private String filterStoreName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice_list);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.invoices);
        }

        // Check for optional store filter
        if (getIntent() != null && getIntent().hasExtra(EXTRA_STORE_NAME)) {
            filterStoreName = getIntent().getStringExtra(EXTRA_STORE_NAME);
            if (getSupportActionBar() != null && filterStoreName != null) {
                getSupportActionBar().setSubtitle(filterStoreName);
            }
        }

        // Initialize DAO
        invoiceDao = new InvoiceDao(this);

        // Views
        rvInvoices = findViewById(R.id.rv_invoices);
        tvEmpty = findViewById(R.id.tv_empty);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add_invoice);

        // RecyclerView setup
        rvInvoices.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InvoiceAdapter(null, this);
        rvInvoices.setAdapter(adapter);

        // FAB -> create new invoice
        fabAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(InvoiceListActivity.this, InvoiceFormActivity.class);
                if (filterStoreName != null && !filterStoreName.isEmpty()) {
                    intent.putExtra(EXTRA_STORE_NAME, filterStoreName);
                }
                startActivityForResult(intent, REQUEST_ADD_INVOICE);
            }
        });

        // Load data
        loadInvoices();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInvoices();
    }

    /**
     * Loads invoices from the database, optionally filtered by store name,
     * and updates the adapter and empty-state visibility.
     */
    private void loadInvoices() {
        List<Invoice> invoices;
        if (filterStoreName != null && !filterStoreName.isEmpty()) {
            invoices = invoiceDao.getByStoreName(filterStoreName);
        } else {
            invoices = invoiceDao.getAll();
        }

        adapter.updateList(invoices);

        if (invoices == null || invoices.isEmpty()) {
            rvInvoices.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(R.string.no_invoices);
        } else {
            rvInvoices.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
        }
    }

    // --- InvoiceAdapter.OnInvoiceClickListener ---

    @Override
    public void onInvoiceClick(Invoice invoice) {
        Intent intent = new Intent(this, InvoiceDetailActivity.class);
        intent.putExtra(EXTRA_INVOICE_ID, invoice.getId());
        startActivityForResult(intent, REQUEST_VIEW_INVOICE);
    }

    @Override
    public void onInvoiceLongClick(final Invoice invoice, View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenu().add(0, 1, 0, R.string.edit);
        popup.getMenu().add(0, 2, 1, R.string.delete);

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case 1: // Edit
                        Intent editIntent = new Intent(
                                InvoiceListActivity.this, InvoiceFormActivity.class);
                        editIntent.putExtra(EXTRA_INVOICE_ID, invoice.getId());
                        startActivityForResult(editIntent, REQUEST_EDIT_INVOICE);
                        return true;

                    case 2: // Delete
                        confirmDelete(invoice);
                        return true;

                    default:
                        return false;
                }
            }
        });

        popup.show();
    }

    /**
     * Shows a confirmation dialog before deleting an invoice.
     */
    private void confirmDelete(final Invoice invoice) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(R.string.confirm_delete_message)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean deleted = invoiceDao.delete(invoice.getId());
                        if (deleted) {
                            Toast.makeText(InvoiceListActivity.this,
                                    R.string.deleted_successfully, Toast.LENGTH_SHORT).show();
                            loadInvoices();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            loadInvoices();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
