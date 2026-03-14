package com.almajd.accounting.ui.invoice;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.util.DateUtils;
import com.almajd.accounting.util.NumberUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying a read-only list of invoices.
 * Each card shows the invoice ID, store name, date, total amount, and remaining balance.
 * The remaining amount is shown in red when outstanding, green when fully paid.
 */
public class InvoiceAdapter extends RecyclerView.Adapter<InvoiceAdapter.InvoiceViewHolder> {

    private List<Invoice> invoices;
    private final OnInvoiceClickListener listener;

    /**
     * Callback interface for invoice item interactions.
     */
    public interface OnInvoiceClickListener {
        /** Called when a user taps an invoice card. */
        void onInvoiceClick(Invoice invoice);

        /** Called when a user long-presses an invoice card. */
        void onInvoiceLongClick(Invoice invoice, View anchorView);
    }

    /**
     * Constructs the adapter with an initial invoice list and click listener.
     *
     * @param invoices initial list of invoices (may be null)
     * @param listener callback for click and long-click events
     */
    public InvoiceAdapter(List<Invoice> invoices, OnInvoiceClickListener listener) {
        this.invoices = invoices != null ? new ArrayList<>(invoices) : new ArrayList<Invoice>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public InvoiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_invoice, parent, false);
        return new InvoiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InvoiceViewHolder holder, int position) {
        Invoice invoice = invoices.get(position);
        holder.bind(invoice);
    }

    @Override
    public int getItemCount() {
        return invoices != null ? invoices.size() : 0;
    }

    /**
     * Replaces the entire dataset and refreshes the RecyclerView.
     *
     * @param newInvoices the new list of invoices
     */
    public void updateList(List<Invoice> newInvoices) {
        this.invoices = newInvoices != null ? new ArrayList<>(newInvoices) : new ArrayList<Invoice>();
        notifyDataSetChanged();
    }

    /**
     * ViewHolder that binds invoice data to the card layout views.
     */
    class InvoiceViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvInvoiceId;
        private final TextView tvStoreName;
        private final TextView tvDate;
        private final TextView tvTotal;
        private final TextView tvRemaining;

        InvoiceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInvoiceId = itemView.findViewById(R.id.tv_invoice_id);
            tvStoreName = itemView.findViewById(R.id.tv_store_name);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvTotal = itemView.findViewById(R.id.tv_total);
            tvRemaining = itemView.findViewById(R.id.tv_remaining);
        }

        void bind(final Invoice invoice) {
            if (invoice == null) return;

            // Invoice ID label (Arabic format)
            tvInvoiceId.setText("\u0641\u0627\u062a\u0648\u0631\u0629 #" + invoice.getId());

            // Store name
            tvStoreName.setText(invoice.getStoreName() != null
                    ? invoice.getStoreName() : "\u2014");

            // Date (formatted for display)
            if (invoice.getInvoiceDate() != null && !invoice.getInvoiceDate().isEmpty()) {
                tvDate.setText(DateUtils.formatForDisplay(invoice.getInvoiceDate()));
            } else {
                tvDate.setText("\u2014");
            }

            // Total amount
            tvTotal.setText(NumberUtils.formatCurrency(invoice.getTotalAmount()));

            // Remaining: red if > 0 (outstanding), green if == 0 (fully paid)
            tvRemaining.setText(NumberUtils.formatCurrency(invoice.getRemaining()));
            if (invoice.getRemaining() > 0) {
                tvRemaining.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.red));
            } else {
                tvRemaining.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.green));
            }

            // Click -> navigate to detail
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            listener.onInvoiceClick(invoices.get(pos));
                        }
                    }
                }
            });

            // Long click -> context menu
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (listener != null) {
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            listener.onInvoiceLongClick(invoices.get(pos), v);
                        }
                    }
                    return true;
                }
            });
        }
    }
}
