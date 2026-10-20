package com.almajd.accounting.ui;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.almajd.accounting.R;
import com.almajd.accounting.ui.customer.CustomerListActivity;
import com.almajd.accounting.ui.invoice.InvoiceFormActivity;
import com.almajd.accounting.ui.report.ReportMenuActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Wires the shared bottom navigation bar.
 * <p>
 * Main screens are kept in a shallow stack: Dashboard is the root, and switching
 * between main tabs starts the target then finishes the previous non-Dashboard
 * screen. That makes Back from Customers/Reports return to Dashboard and Back
 * from Dashboard exit the app. The new-order item launches the form without
 * changing the selected tab and without finishing the current screen.
 */
public final class BottomNavHelper {

    /** Request code used when the bottom bar launches the new-order form. */
    public static final int REQUEST_NEW_INVOICE = 4101;

    private BottomNavHelper() {
    }

    public static void setup(final AppCompatActivity activity, int selectedItemId) {
        final BottomNavigationView nav = activity.findViewById(R.id.bottom_navigation);
        if (nav == null) {
            return;
        }
        if (selectedItemId != 0) {
            nav.setSelectedItemId(selectedItemId);
        }

        nav.setOnItemSelectedListener(item -> {
            final int id = item.getItemId();

            if (id == R.id.nav_new_invoice) {
                Intent form = new Intent(activity, InvoiceFormActivity.class);
                activity.startActivityForResult(form, REQUEST_NEW_INVOICE);
                // Keep the currently selected tab visually unchanged.
                return false;
            }

            if (id == selectedItemId) {
                return true;
            }

            Intent target;
            if (id == R.id.nav_home) {
                target = new Intent(activity, DashboardActivity.class);
            } else if (id == R.id.nav_customers) {
                target = new Intent(activity, CustomerListActivity.class);
            } else if (id == R.id.nav_reports) {
                target = new Intent(activity, ReportMenuActivity.class);
            } else {
                return false;
            }

            // Reuse Dashboard if it is already in the task; new tabs are pushed
            // directly above it because the previous main screen is finished.
            target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(target);

            if (!(activity instanceof DashboardActivity)) {
                activity.finish();
            }
            return true;
        });
    }
}
