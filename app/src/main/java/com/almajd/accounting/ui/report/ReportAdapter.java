package com.almajd.accounting.ui.report;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.almajd.accounting.R;

import java.util.List;

/**
 * Generic reusable RecyclerView adapter for report screens.
 * <p>
 * Each row displays up to three label/value pairs using the item_report_row layout.
 * The third value supports an optional custom text color (e.g. red for outstanding
 * balances, green for settled accounts).
 * <p>
 * Layout: item_report_row.xml
 * IDs: tv_label1, tv_value1, tv_label2, tv_value2, tv_label3, tv_value3
 */
public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {

    /**
     * Data class representing a single report row with three label/value pairs.
     */
    public static class ReportRow {
        public String label1, value1, label2, value2, label3, value3;
        /** Optional color for the third value text. Defaults to black. */
        public int value3Color = Color.BLACK;

        public ReportRow(String l1, String v1, String l2, String v2, String l3, String v3) {
            this.label1 = l1;
            this.value1 = v1;
            this.label2 = l2;
            this.value2 = v2;
            this.label3 = l3;
            this.value3 = v3;
        }
    }

    private List<ReportRow> rows;

    public ReportAdapter(List<ReportRow> rows) {
        this.rows = rows;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ReportRow row = rows.get(position);
        h.tvLabel1.setText(row.label1);
        h.tvValue1.setText(row.value1);
        h.tvLabel2.setText(row.label2);
        h.tvValue2.setText(row.value2);
        h.tvLabel3.setText(row.label3);
        h.tvValue3.setText(row.value3);
        h.tvValue3.setTextColor(row.value3Color);
    }

    @Override
    public int getItemCount() {
        return rows != null ? rows.size() : 0;
    }

    /**
     * Replaces the entire data set and refreshes the RecyclerView.
     *
     * @param newRows the new list of report rows to display
     */
    public void updateList(List<ReportRow> newRows) {
        this.rows = newRows;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvLabel1, tvValue1, tvLabel2, tvValue2, tvLabel3, tvValue3;

        ViewHolder(@NonNull View v) {
            super(v);
            tvLabel1 = v.findViewById(R.id.tv_label1);
            tvValue1 = v.findViewById(R.id.tv_value1);
            tvLabel2 = v.findViewById(R.id.tv_label2);
            tvValue2 = v.findViewById(R.id.tv_value2);
            tvLabel3 = v.findViewById(R.id.tv_label3);
            tvValue3 = v.findViewById(R.id.tv_value3);
        }
    }
}
