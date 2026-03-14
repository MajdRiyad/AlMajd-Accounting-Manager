package com.almajd.accounting.ui.customer;

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
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.ui.BaseActivity;
import com.almajd.accounting.db.CustomerDao;
import com.almajd.accounting.model.Customer;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

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

    private RecyclerView rvCustomers;
    private TextView tvEmpty;
    private CustomerAdapter adapter;
    private CustomerDao customerDao;

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

        // Views
        rvCustomers = findViewById(R.id.rv_customers);
        tvEmpty = findViewById(R.id.tv_empty);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add_customer);

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

        // Load initial data
        loadCustomers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCustomers();
    }

    /**
     * Loads all customers from the database and updates the adapter.
     * Toggles the empty-state view accordingly.
     */
    private void loadCustomers() {
        List<Customer> customers = customerDao.getAll();
        if (customers == null) {
            customers = new ArrayList<>();
        }
        adapter.updateList(customers);
        updateEmptyState(customers.size());
    }

    /**
     * Loads customers whose store name or owner name matches the query.
     *
     * @param query the search text
     */
    private void searchCustomers(String query) {
        if (query == null || query.trim().isEmpty()) {
            loadCustomers();
            return;
        }
        List<Customer> results = customerDao.search(query.trim());
        if (results == null) {
            results = new ArrayList<>();
        }
        adapter.updateList(results);
        updateEmptyState(results.size());
    }

    /**
     * Shows or hides the empty-state text based on the list size.
     */
    private void updateEmptyState(int count) {
        if (count == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvCustomers.setVisibility(View.GONE);
            tvEmpty.setText(R.string.no_customers);
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
                        customerDao.delete(customer.getStoreName());
                        Toast.makeText(CustomerListActivity.this,
                                R.string.deleted_successfully, Toast.LENGTH_SHORT).show();
                        loadCustomers();
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
            loadCustomers();
        }
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
        searchItem.setActionView(searchView);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchCustomers(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchCustomers(newText);
                return true;
            }
        });

        // When the search view is closed, reload full list
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                loadCustomers();
                return true;
            }
        });

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
