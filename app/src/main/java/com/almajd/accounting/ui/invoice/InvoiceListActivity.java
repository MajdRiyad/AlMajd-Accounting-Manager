package com.almajd.accounting.ui.invoice;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.ui.BaseActivity;
import com.almajd.accounting.db.InvoiceDao;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.util.AppExecutors;
import com.almajd.accounting.util.OrderSearch;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * Displays a list of invoices. Supports optional filtering by store name via intent extra.
 * Provides a FAB for creating new invoices and context menus for editing / deleting existing ones.
 */
public class InvoiceListActivity extends BaseActivity
        implements InvoiceAdapter.OnInvoiceClickListener {

    public static final String EXTRA_STORE_NAME = "extra_store_name";
    public static final String EXTRA_INVOICE_ID = "extra_invoice_id";

    private static final int REQUEST_ADD_INVOICE = 100;
    private static final int REQUEST_EDIT_INVOICE = 101;
    private static final int REQUEST_VIEW_INVOICE = 102;

    private static final long SEARCH_DEBOUNCE_MS = 250L;
    private static final String STATE_SEARCH_QUERY = "state_search_query";
    private static final String STATE_STATUS_FILTER = "state_status_filter";

    private RecyclerView rvInvoices;
    private TextView tvEmpty;
    private TextInputEditText etSearch;
    private ChipGroup chipGroupStatus;
    private InvoiceAdapter adapter;
    private InvoiceDao invoiceDao;

    /** Optional store name filter passed via intent. */
    private String filterStoreName;

    /** Current text and status-chip selection, kept across rotation. */
    private String searchQuery = "";
    private OrderSearch.StatusFilter statusFilter = OrderSearch.StatusFilter.ALL;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    /** Incremented on every load so stale background results are discarded. */
    private int loadToken = 0;

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
        etSearch = findViewById(R.id.et_search);
        chipGroupStatus = findViewById(R.id.chip_group_status);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add_invoice);

        // Restore the query/chip before attaching listeners so restoration does
        // not schedule an unnecessary search.
        if (savedInstanceState != null) {
            searchQuery = savedInstanceState.getString(STATE_SEARCH_QUERY, "");
            statusFilter = statusFilterFromName(
                    savedInstanceState.getString(STATE_STATUS_FILTER));
        }
        etSearch.setText(searchQuery);
        if (searchQuery != null && !searchQuery.isEmpty()) {
            etSearch.setSelection(searchQuery.length());
        }
        chipGroupStatus.check(chipIdForStatus(statusFilter));

        // RecyclerView setup
        rvInvoices.setLayoutManager(new LinearLayoutManager(this));
        adapter = new InvoiceAdapter(null, this);
        rvInvoices.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s != null ? s.toString() : "";
                scheduleLoad();
            }
        });

        chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int chipId = checkedIds != null && !checkedIds.isEmpty()
                    ? checkedIds.get(0)
                    : R.id.chip_all;
            statusFilter = statusFilterFromChipId(chipId);
            scheduleLoad();
        });

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

        // Initial data is loaded by onResume (which always follows onCreate).
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning from a detail/form screen must keep an active query and
        // status chip instead of resetting to the full list.
        loadInvoicesNow();
    }

    /**
     * Loads invoices from the database (on the background thread), optionally
     * filtered by store name, then applies the text query and effective status
     * chip in Java. Keeping the search in Java preserves the effective status
     * that the DAO computed after customer-credit allocation and keeps the
     * number of database queries constant. Items are never loaded here: the
     * list does not show them.
     */
    private void loadInvoices() {
        final String store = filterStoreName;
        final boolean filteredByStore = store != null && !store.isEmpty();
        final String query = searchQuery;
        final OrderSearch.StatusFilter status = statusFilter;
        final boolean filtering = OrderSearch.isFiltering(query, status);
        final int token = ++loadToken;
        AppExecutors.runOnDb(() -> {
            final List<Invoice> loaded = filteredByStore
                    ? invoiceDao.getByStoreName(store, false)
                    : invoiceDao.getAll(false);
            final List<Invoice> invoices = OrderSearch.filter(loaded, query, status);
            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (token != loadToken) {
                    return;
                }
                adapter.updateList(invoices);

                if (invoices.isEmpty()) {
                    rvInvoices.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText(filtering
                            ? R.string.no_search_results
                            : R.string.no_invoices);
                } else {
                    rvInvoices.setVisibility(View.VISIBLE);
                    tvEmpty.setVisibility(View.GONE);
                }
            });
        });
    }

    /** Runs an immediate load and cancels any pending debounced search. */
    private void loadInvoicesNow() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
        loadInvoices();
    }

    /**
     * Debounces typing so a burst of keystrokes results in one database read.
     * The token is bumped immediately, so a result that was already in flight
     * for an older query cannot be applied.
     */
    private void scheduleLoad() {
        loadToken++;
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        pendingSearch = this::loadInvoices;
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private static int chipIdForStatus(OrderSearch.StatusFilter status) {
        if (status == OrderSearch.StatusFilter.PAID) {
            return R.id.chip_paid;
        } else if (status == OrderSearch.StatusFilter.PARTIAL) {
            return R.id.chip_partial;
        } else if (status == OrderSearch.StatusFilter.UNPAID) {
            return R.id.chip_unpaid;
        }
        return R.id.chip_all;
    }

    private static OrderSearch.StatusFilter statusFilterFromChipId(int chipId) {
        if (chipId == R.id.chip_paid) {
            return OrderSearch.StatusFilter.PAID;
        } else if (chipId == R.id.chip_partial) {
            return OrderSearch.StatusFilter.PARTIAL;
        } else if (chipId == R.id.chip_unpaid) {
            return OrderSearch.StatusFilter.UNPAID;
        }
        return OrderSearch.StatusFilter.ALL;
    }

    private static OrderSearch.StatusFilter statusFilterFromName(String name) {
        if (name != null) {
            try {
                return OrderSearch.StatusFilter.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // Fall back to the default below.
            }
        }
        return OrderSearch.StatusFilter.ALL;
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
                        AppExecutors.runOnDb(() -> {
                            final boolean deleted = invoiceDao.delete(invoice.getId());
                            AppExecutors.runOnMain(() -> {
                                if (isFinishing() || isDestroyed()) {
                                    return;
                                }
                                if (deleted) {
                                    Toast.makeText(InvoiceListActivity.this,
                                            R.string.deleted_successfully,
                                            Toast.LENGTH_SHORT).show();
                                    loadInvoicesNow();
                                } else {
                                    Toast.makeText(InvoiceListActivity.this,
                                            R.string.delete_failed,
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            loadInvoicesNow();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SEARCH_QUERY, searchQuery);
        outState.putString(STATE_STATUS_FILTER, statusFilter.name());
    }

    @Override
    protected void onDestroy() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
