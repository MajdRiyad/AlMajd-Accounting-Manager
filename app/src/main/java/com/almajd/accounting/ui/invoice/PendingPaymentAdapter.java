package com.almajd.accounting.ui.invoice;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;
import com.almajd.accounting.model.PendingPayment;
import com.almajd.accounting.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the in-memory payment rows shown while editing an order.
 * Amount/date edits only update the pending model; the activity writes them on
 * Save. Rows marked deleted are filtered out before reaching this adapter.
 */
public class PendingPaymentAdapter
        extends RecyclerView.Adapter<PendingPaymentAdapter.ViewHolder> {

    private static final int TAG_WATCHER_AMOUNT = R.id.et_payment_amount;
    private static final int TAG_WATCHER_DATE = R.id.et_payment_date;

    private final List<PendingPayment> rows = new ArrayList<>();
    private final Listener listener;

    public interface Listener {
        void onPaymentsChanged();

        void onDeleteRequested(PendingPayment row);
    }

    public PendingPaymentAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setRows(List<PendingPayment> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_invoice_payment_edit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        final PendingPayment row = rows.get(position);

        removeWatchers(holder);
        holder.binding = true;
        holder.etAmount.setText(row.getAmountText() != null ? row.getAmountText() : "");
        holder.etDate.setText(row.getPaymentDate() != null ? row.getPaymentDate() : "");
        holder.binding = false;

        TextWatcher amountWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (holder.binding) {
                    return;
                }
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= rows.size()) {
                    return;
                }
                rows.get(pos).setAmountText(s != null ? s.toString() : "");
                holder.tilAmount.setError(null);
                listener.onPaymentsChanged();
            }
        };

        TextWatcher dateWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (holder.binding) {
                    return;
                }
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= rows.size()) {
                    return;
                }
                rows.get(pos).setPaymentDate(s != null ? s.toString().trim() : "");
                listener.onPaymentsChanged();
            }
        };

        holder.etAmount.addTextChangedListener(amountWatcher);
        holder.etDate.addTextChangedListener(dateWatcher);
        holder.etAmount.setTag(TAG_WATCHER_AMOUNT, amountWatcher);
        holder.etDate.setTag(TAG_WATCHER_DATE, dateWatcher);

        View.OnClickListener dateClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DateUtils.showDatePicker(v.getContext(), holder.etDate);
            }
        };
        holder.etDate.setOnClickListener(dateClickListener);
        holder.tilDate.setOnClickListener(dateClickListener);

        holder.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < rows.size()) {
                    listener.onDeleteRequested(rows.get(pos));
                }
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        removeWatchers(holder);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private void removeWatchers(ViewHolder holder) {
        TextWatcher amount = (TextWatcher) holder.etAmount.getTag(TAG_WATCHER_AMOUNT);
        if (amount != null) {
            holder.etAmount.removeTextChangedListener(amount);
            holder.etAmount.setTag(TAG_WATCHER_AMOUNT, null);
        }
        TextWatcher date = (TextWatcher) holder.etDate.getTag(TAG_WATCHER_DATE);
        if (date != null) {
            holder.etDate.removeTextChangedListener(date);
            holder.etDate.setTag(TAG_WATCHER_DATE, null);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final com.google.android.material.textfield.TextInputLayout tilAmount;
        final com.google.android.material.textfield.TextInputLayout tilDate;
        final EditText etAmount;
        final EditText etDate;
        final ImageButton btnDelete;

        boolean binding = false;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tilAmount = itemView.findViewById(R.id.til_payment_amount);
            tilDate = itemView.findViewById(R.id.til_payment_date);
            etAmount = itemView.findViewById(R.id.et_payment_amount);
            etDate = itemView.findViewById(R.id.et_payment_date);
            btnDelete = itemView.findViewById(R.id.btn_delete_payment);
        }
    }
}
