package com.almajd.accounting.ui.customer;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.ui.BaseActivity;
import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.util.AppExecutors;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the full list of customers with search, add, edit, and delete capabilities.
 * <p>
 * Layout: activity_customer_list.xml
 * Contains: Toolbar, RecyclerView (rv_customers), empty state (tv_empty), FAB (fab_add)
 */
public class CustomerListActivity extends BaseActivity
        implements CustomerAdapter.OnCustomerClickListener {

    public static final String EXTRA_STORE_NAME = "extra_store_name";
    private static final int REQUEST_ADD_CUSTOMER = 100;
    private static final int REQUEST_EDIT_CUSTOMER = 101;
    private static final int REQUEST_CUSTOMER_DETAIL = 102;

    private static final long SEARCH_DEBOUNCE_MS = 250L;
    private static final String STATE_SEARCH_QUERY = "state_search_query";

    private RecyclerView rvCustomers;
    private TextView tvEmpty;
    private CustomerAdapter adapter;
    private CustomerDao customerDao;

    /** The text currently in the SearchView, kept across rotation. */
    private String activeQuery = "";
    private boolean restoringSearchView;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    /** Incremented on every load/search so only the latest result is shown. */
    private int loadToken = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_list);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.customers);
        }

        // DAO
        customerDao = new CustomerDao(this);

        // Restore the query before onResume reloads the list.
        if (savedInstanceState != null) {
            activeQuery = savedInstanceState.getString(STATE_SEARCH_QUERY, "");
        }

        // Views
        rvCustomers = findViewById(R.id.rv_customers);
        tvEmpty = findViewById(R.id.tv_empty);
        ExtendedFloatingActionButton fabAdd = findViewById(R.id.fab_add_customer);

        // RecyclerView setup
        rvCustomers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CustomerAdapter(new ArrayList<Customer>(), this);
        rvCustomers.setAdapter(adapter);

        // FAB: add new customer
        fabAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerListActivity.this, CustomerFormActivity.class);
                startActivityForResult(intent, REQUEST_ADD_CUSTOMER);
            }
        });

        // Initial data is loaded by onResume (which always follows onCreate).
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Do not reset an active search to the full list when returning from
        // a customer screen.
        reloadCurrentQuery();
    }

    /**
     * Loads all customers from the database (on the background thread) and
     * updates the adapter. Toggles the empty-state view accordingly.
     */
    private void loadCustomers() {
        final int token = ++loadToken;
        AppExecutors.runOnDb(() -> {
            final List<Customer> customers = customerDao.getAll();
            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (token != loadToken) {
                    return;
                }
                List<Customer> list = customers != null ? customers : new ArrayList<Customer>();
                adapter.updateList(list);
                updateEmptyState(list.size());
            });
        });
    }

    /**
     * Loads customers whose store name, owner name, phone or location matches
     * the query. Runs on the background thread; the token guarantees that only
     * the result of the most recent keystroke is applied.
     *
     * @param query the search text
     */
    private void searchCustomers(String query) {
        if (query == null || query.trim().isEmpty()) {
            loadCustomers();
            return;
        }
        final String trimmed = query.trim();
        final int token = ++loadToken;
        AppExecutors.runOnDb(() -> {
            final List<Customer> results = customerDao.search(trimmed);
            AppExecutors.runOnMain(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (token != loadToken) {
                    return;
                }
                List<Customer> list = results != null ? results : new ArrayList<Customer>();
                adapter.updateList(list);
                updateEmptyState(list.size());
            });
        });
    }

    /** Reloads using the query that is currently active in the SearchView. */
    private void reloadCurrentQuery() {
        cancelPendingSearch();
        if (hasActiveQuery()) {
            searchCustomers(activeQuery);
        } else {
            loadCustomers();
        }
    }

    /**
     * Debounces typing so a burst of keystrokes results in one database read.
     * The token is bumped immediately, so an in-flight result for an older
     * query cannot be applied.
     */
    private void scheduleSearch(String query) {
        activeQuery = query != null ? query : "";
        loadToken++;
        cancelPendingSearch();
        pendingSearch = () -> {
            pendingSearch = null;
            if (hasActiveQuery()) {
                searchCustomers(activeQuery);
            } else {
                loadCustomers();
            }
        };
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    /** Applies a submitted query immediately without waiting for the debounce. */
    private void applySearchNow(String query) {
        activeQuery = query != null ? query : "";
        loadToken++;
        cancelPendingSearch();
        if (hasActiveQuery()) {
            searchCustomers(activeQuery);
        } else {
            loadCustomers();
        }
    }

    private void cancelPendingSearch() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
    }

    private boolean hasActiveQuery() {
        return activeQuery != null && !activeQuery.trim().isEmpty();
    }

    /**
     * Shows or hides the empty-state text based on the list size.
     */
    private void updateEmptyState(int count) {
        if (count == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvCustomers.setVisibility(View.GONE);
            tvEmpty.setText(hasActiveQuery()
                    ? R.string.no_search_results
                    : R.string.no_customers);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvCustomers.setVisibility(View.VISIBLE);
        }
    }

    // ── Adapter callbacks ────────────────────────────────────────────────

    @Override
    public void onCustomerClick(Customer customer) {
        Intent intent = new Intent(this, CustomerDetailActivity.class);
        intent.putExtra(EXTRA_STORE_NAME, customer.getStoreName());
        startActivityForResult(intent, REQUEST_CUSTOMER_DETAIL);
    }

    @Override
    public void onCustomerLongClick(Customer customer, View anchorView) {
        showPopupMenu(customer, anchorView);
    }

    /**
     * Shows Edit / Delete popup menu anchored to the long-pressed item view.
     */
    private void showPopupMenu(final Customer customer, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(Menu.NONE, 1, 1, R.string.edit);
        popup.getMenu().add(Menu.NONE, 2, 2, R.string.delete);

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1: // Edit
                        Intent editIntent = new Intent(
                                CustomerListActivity.this, CustomerFormActivity.class);
                        editIntent.putExtra(EXTRA_STORE_NAME, customer.getStoreName());
                        startActivityForResult(editIntent, REQUEST_EDIT_CUSTOMER);
                        return true;

                    case 2: // Delete
                        confirmDelete(customer);
                        return true;

                    default:
                        return false;
                }
            }
        });
        popup.show();
    }

    /**
     * Shows a confirmation dialog before deleting the customer.
     */
    private void confirmDelete(final Customer customer) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(R.string.confirm_delete_message)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AppExecutors.runOnDb(() -> {
                            final boolean deleted = customerDao.delete(customer.getStoreName());
                            AppExecutors.runOnMain(() -> {
                                if (isFinishing() || isDestroyed()) {
                                    return;
                                }
                                Toast.makeText(CustomerListActivity.this,
                                        deleted ? R.string.deleted_successfully
                                                : R.string.delete_failed,
                                        Toast.LENGTH_SHORT).show();
                                if (deleted) {
                                    reloadCurrentQuery();
                                }
                            });
                        });
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
            reloadCurrentQuery();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SEARCH_QUERY, activeQuery);
    }

    @Override
    protected void onDestroy() {
        cancelPendingSearch();
        super.onDestroy();
    }

    // ── Options menu with SearchView ─────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Programmatic search item (no menu XML needed)
        MenuItem searchItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.search);
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW
                | MenuItem.SHOW_AS_ACTION_IF_ROOM);

        SearchView searchView = new SearchView(this);
        searchView.setQueryHint(getString(R.string.search));
        // The SearchView sits directly on the navy toolbar, so its default
        // dark query/hint colors are hard to read. Keep both white.
        EditText searchText = searchView.findViewById(
                androidx.appcompat.R.id.search_src_text);
        if (searchText != null) {
            searchText.setTextColor(getResources().getColor(R.color.white));
            searchText.setHintTextColor(getResources().getColor(R.color.white));
        }
        searchItem.setActionView(searchView);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applySearchNow(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (!restoringSearchView) {
                    scheduleSearch(newText);
                }
                return true;
            }
        });

        // When the search view is closed, clear the query and reload the full list.
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                activeQuery = "";
                loadToken++;
                cancelPendingSearch();
                loadCustomers();
                return true;
            }
        });

        // Rotation recreates the menu; restore the typed query into it without
        // scheduling a duplicate debounced search.
        if (hasActiveQuery()) {
            restoringSearchView = true;
            searchItem.expandActionView();
            searchView.setQuery(activeQuery, false);
            searchView.clearFocus();
            restoringSearchView = false;
        }

        return true;
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
