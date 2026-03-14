package com.almajd.accounting.ui.payment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.model.Payment;
import com.almajd.accounting.util.DateUtils;
import com.almajd.accounting.util.NumberUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying a list of payments.
 * Each row shows the store name, payment date, optional notes, and amount.
 */
public class PaymentAdapter extends RecyclerView.Adapter<PaymentAdapter.PaymentViewHolder> {

    /**
     * Callback interface for payment item interactions.
     */
    public interface OnPaymentClickListener {
        void onPaymentClick(Payment payment);
        void onPaymentLongClick(Payment payment, View anchor);
    }

    private List<Payment> payments;
    private final OnPaymentClickListener listener;

    /**
     * Constructs the adapter with an initial payment list and click listener.
     *
     * @param payments initial list of payments (may be null)
     * @param listener callback for click and long-click events
     */
    public PaymentAdapter(List<Payment> payments, OnPaymentClickListener listener) {
        this.payments = payments != null ? new ArrayList<>(payments) : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public PaymentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_payment, parent, false);
        return new PaymentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PaymentViewHolder holder, int position) {
        Payment payment = payments.get(position);
        holder.bind(payment);
    }

    @Override
    public int getItemCount() {
        return payments != null ? payments.size() : 0;
    }

    /**
     * Replaces the entire dataset and refreshes the RecyclerView.
     *
     * @param newPayments the new list of payments
     */
    public void updateList(List<Payment> newPayments) {
        this.payments = newPayments != null ? new ArrayList<>(newPayments) : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * ViewHolder that binds payment data to the item layout views.
     */
    class PaymentViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvStoreName;
        private final TextView tvDate;
        private final TextView tvNotes;
        private final TextView tvAmount;

        PaymentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStoreName = itemView.findViewById(R.id.tv_store_name);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvNotes = itemView.findViewById(R.id.tv_notes);
            tvAmount = itemView.findViewById(R.id.tv_amount);
        }

        void bind(final Payment payment) {
            if (payment == null) {
                return;
            }

            // Store name
            tvStoreName.setText(payment.getStoreName() != null
                    ? payment.getStoreName() : "\u2014");

            // Payment date (formatted for display)
            tvDate.setText(payment.getPaymentDate() != null
                    ? DateUtils.formatForDisplay(payment.getPaymentDate()) : "\u2014");

            // Amount (formatted as currency)
            tvAmount.setText(NumberUtils.formatCurrency(payment.getAmount()));

            // Notes (hide if empty)
            if (payment.getNotes() != null && !payment.getNotes().trim().isEmpty()) {
                tvNotes.setText(payment.getNotes());
                tvNotes.setVisibility(View.VISIBLE);
            } else {
                tvNotes.setVisibility(View.GONE);
            }

            // Click: navigate to detail / edit
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            listener.onPaymentClick(payments.get(pos));
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
                            listener.onPaymentLongClick(payments.get(pos), v);
                        }
                    }
                    return true;
                }
            });
        }
    }
}
