package com.almajd.accounting.ui.invoice;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.model.InvoiceItem;
import com.almajd.accounting.util.NumberUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Editable RecyclerView adapter for invoice line items in the invoice form.
 * Each row allows the user to enter item name, quantity, unit price, and shows computed subtotal.
 * Handles TextWatcher lifecycle carefully to avoid infinite loops and memory leaks during
 * view recycling.
 */
public class InvoiceItemAdapter extends RecyclerView.Adapter<InvoiceItemAdapter.ViewHolder> {

    /** Tag keys for storing TextWatcher references on EditText views. */
    private static final int TAG_WATCHER_NAME = R.id.et_item_name;
    private static final int TAG_WATCHER_QTY = R.id.et_quantity;
    private static final int TAG_WATCHER_PRICE = R.id.et_unit_price;

    private final List<InvoiceItem> items;
    private final OnTotalsChangedListener listener;

    /**
     * Callback fired whenever any line item changes, providing the recalculated grand total.
     */
    public interface OnTotalsChangedListener {
        void onTotalsChanged(double newTotal);
    }

    /**
     * Constructs the adapter.
     *
     * @param items    initial list of invoice items (may be null)
     * @param listener callback for total recalculation events
     */
    public InvoiceItemAdapter(List<InvoiceItem> items, OnTotalsChangedListener listener) {
        this.items = items != null ? items : new ArrayList<InvoiceItem>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_invoice_line, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        final InvoiceItem item = items.get(position);

        // --- Remove any existing watchers before setting text to prevent infinite loops ---
        removeWatchers(holder);

        // Use a binding flag so that programmatic setText calls do not trigger recalculation
        holder.binding = true;

        // Populate fields from the model
        if (item.getItemName() != null && !item.getItemName().isEmpty()) {
            holder.etItemName.setText(item.getItemName());
        } else {
            holder.etItemName.setText("");
        }

        if (item.getQuantity() > 0) {
            holder.etQuantity.setText(String.valueOf(item.getQuantity()));
        } else {
            holder.etQuantity.setText("");
        }

        if (item.getUnitPrice() > 0) {
            holder.etUnitPrice.setText(String.valueOf(item.getUnitPrice()));
        } else {
            holder.etUnitPrice.setText("");
        }

        holder.tvSubtotal.setText(NumberUtils.formatCurrency(item.getSubtotal()));

        holder.binding = false;

        // --- Attach new TextWatchers ---

        TextWatcher nameWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (holder.binding) return;
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= items.size()) return;
                items.get(pos).setItemName(s.toString().trim());
            }
        };

        TextWatcher qtyWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (holder.binding) return;
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= items.size()) return;
                updateSubtotal(holder, pos);
            }
        };

        TextWatcher priceWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (holder.binding) return;
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= items.size()) return;
                updateSubtotal(holder, pos);
            }
        };

        holder.etItemName.addTextChangedListener(nameWatcher);
        holder.etQuantity.addTextChangedListener(qtyWatcher);
        holder.etUnitPrice.addTextChangedListener(priceWatcher);

        // Store watchers as tags so we can remove them later
        holder.etItemName.setTag(TAG_WATCHER_NAME, nameWatcher);
        holder.etQuantity.setTag(TAG_WATCHER_QTY, qtyWatcher);
        holder.etUnitPrice.setTag(TAG_WATCHER_PRICE, priceWatcher);

        // --- Delete button ---
        holder.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < items.size()) {
                    items.remove(pos);
                    notifyItemRemoved(pos);
                    // Update positions for items that shifted
                    notifyItemRangeChanged(pos, items.size() - pos);
                    recalculateTotal();
                }
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        // Remove watchers to prevent memory leaks and ghost callbacks on recycled views
        removeWatchers(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Removes TextWatchers from the holder's EditText fields using the stored tag references.
     */
    private void removeWatchers(ViewHolder holder) {
        TextWatcher nameW = (TextWatcher) holder.etItemName.getTag(TAG_WATCHER_NAME);
        if (nameW != null) {
            holder.etItemName.removeTextChangedListener(nameW);
            holder.etItemName.setTag(TAG_WATCHER_NAME, null);
        }

        TextWatcher qtyW = (TextWatcher) holder.etQuantity.getTag(TAG_WATCHER_QTY);
        if (qtyW != null) {
            holder.etQuantity.removeTextChangedListener(qtyW);
            holder.etQuantity.setTag(TAG_WATCHER_QTY, null);
        }

        TextWatcher priceW = (TextWatcher) holder.etUnitPrice.getTag(TAG_WATCHER_PRICE);
        if (priceW != null) {
            holder.etUnitPrice.removeTextChangedListener(priceW);
            holder.etUnitPrice.setTag(TAG_WATCHER_PRICE, null);
        }
    }

    /**
     * Parses quantity and unit price from the holder's fields, computes the subtotal,
     * updates the model and the subtotal TextView, then triggers grand total recalculation.
     */
    private void updateSubtotal(ViewHolder holder, int position) {
        int qty = 0;
        double price = 0.0;

        try {
            String qtyStr = holder.etQuantity.getText().toString().trim();
            if (!qtyStr.isEmpty()) {
                qty = Integer.parseInt(qtyStr);
            }
        } catch (NumberFormatException ignored) { }

        try {
            String priceStr = holder.etUnitPrice.getText().toString().trim();
            if (!priceStr.isEmpty()) {
                price = Double.parseDouble(priceStr);
            }
        } catch (NumberFormatException ignored) { }

        double subtotal = qty * price;

        InvoiceItem item = items.get(position);
        item.setQuantity(qty);
        item.setUnitPrice(price);
        item.setSubtotal(subtotal);

        holder.tvSubtotal.setText(NumberUtils.formatCurrency(subtotal));

        recalculateTotal();
    }

    /**
     * Sums all item subtotals and notifies the listener of the new grand total.
     */
    private void recalculateTotal() {
        double total = 0.0;
        for (InvoiceItem item : items) {
            total += item.getSubtotal();
        }
        if (listener != null) {
            listener.onTotalsChanged(total);
        }
    }

    /**
     * Appends a new empty InvoiceItem row to the end of the list.
     */
    public void addItem() {
        InvoiceItem newItem = new InvoiceItem();
        items.add(newItem);
        notifyItemInserted(items.size() - 1);
    }

    /**
     * Returns a defensive copy of the current items list with up-to-date values.
     *
     * @return new ArrayList containing all current InvoiceItem objects
     */
    public List<InvoiceItem> getAllItems() {
        return new ArrayList<>(items);
    }

    /**
     * ViewHolder for an editable invoice line item row.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {

        final EditText etItemName;
        final EditText etQuantity;
        final EditText etUnitPrice;
        final TextView tvSubtotal;
        final ImageButton btnDelete;

        /** Flag to suppress TextWatcher callbacks during programmatic setText calls. */
        boolean binding = false;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            etItemName = itemView.findViewById(R.id.et_item_name);
            etQuantity = itemView.findViewById(R.id.et_quantity);
            etUnitPrice = itemView.findViewById(R.id.et_unit_price);
            tvSubtotal = itemView.findViewById(R.id.tv_subtotal);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
