package com.almajd.accounting.ui.customer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.model.Customer;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying a list of customers.
 * Each row shows the store name, owner name, and location.
 */
public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder> {

    private List<Customer> customers;
    private final OnCustomerClickListener listener;

    /**
     * Callback interface for customer item interactions.
     */
    public interface OnCustomerClickListener {
        void onCustomerClick(Customer customer);
        void onCustomerLongClick(Customer customer, View anchorView);
    }

    /**
     * Constructs the adapter with an initial customer list and click listener.
     *
     * @param customers initial list of customers (may be null)
     * @param listener  callback for click and long-click events
     */
    public CustomerAdapter(List<Customer> customers, OnCustomerClickListener listener) {
        this.customers = customers != null ? new ArrayList<>(customers) : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public CustomerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer, parent, false);
        return new CustomerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CustomerViewHolder holder, int position) {
        Customer customer = customers.get(position);
        holder.bind(customer);
    }

    @Override
    public int getItemCount() {
        return customers != null ? customers.size() : 0;
    }

    /**
     * Replaces the entire dataset and refreshes the RecyclerView.
     *
     * @param newCustomers the new list of customers
     */
    public void updateList(List<Customer> newCustomers) {
        this.customers = newCustomers != null ? new ArrayList<>(newCustomers) : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * ViewHolder that binds customer data to the item layout views.
     */
    class CustomerViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvStoreName;
        private final TextView tvOwnerName;
        private final TextView tvLocation;

        CustomerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStoreName = itemView.findViewById(R.id.tv_store_name);
            tvOwnerName = itemView.findViewById(R.id.tv_owner_name);
            tvLocation = itemView.findViewById(R.id.tv_location);
        }

        void bind(final Customer customer) {
            if (customer == null) {
                return;
            }

            // Store name (primary identifier, shown bold via XML style)
            tvStoreName.setText(customer.getStoreName() != null
                    ? customer.getStoreName() : "\u2014");

            // Owner name
            tvOwnerName.setText(customer.getOwnerName() != null
                    && !customer.getOwnerName().trim().isEmpty()
                    ? customer.getOwnerName() : "\u2014");

            // Location (show dash if empty)
            tvLocation.setText(customer.getLocation() != null
                    && !customer.getLocation().trim().isEmpty()
                    ? customer.getLocation() : "\u2014");

            // Click: navigate to detail
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            listener.onCustomerClick(customers.get(pos));
                        }
                    }
                }
            });

            // Long click: show context menu
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (listener != null) {
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            listener.onCustomerLongClick(customers.get(pos), v);
                        }
                    }
                    return true;
                }
            });
        }
    }
}
