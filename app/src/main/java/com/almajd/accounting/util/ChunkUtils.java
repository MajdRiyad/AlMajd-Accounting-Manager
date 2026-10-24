package com.almajd.accounting.util;

import com.almajd.accounting.model.InvoiceItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, dependency-free helpers used when loading invoice line items in bulk.
 * <p>
 * Kept free of any Android imports so the logic can be unit-tested on the JVM
 * (see {@code ChunkUtilsTest}).
 */
public final class ChunkUtils {

    private ChunkUtils() {
    }

    /**
     * Splits {@code items} into consecutive sub-lists of at most
     * {@code maxChunkSize} elements, preserving order.
     * <p>
     * Used to keep the number of SQL host parameters in an {@code IN (...)}
     * clause within SQLite's limit (SQLITE_MAX_VARIABLE_NUMBER).
     *
     * @return an empty list when {@code items} is null/empty or
     *         {@code maxChunkSize <= 0}
     */
    public static <T> List<List<T>> chunk(List<T> items, int maxChunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        if (items == null || items.isEmpty() || maxChunkSize <= 0) {
            return chunks;
        }
        for (int start = 0; start < items.size(); start += maxChunkSize) {
            int end = Math.min(start + maxChunkSize, items.size());
            chunks.add(new ArrayList<>(items.subList(start, end)));
        }
        return chunks;
    }

    /**
     * Groups line items by their {@code invoice_id}, preserving the encounter
     * order of both the invoice ids and the items inside each group.
     */
    public static Map<Long, List<InvoiceItem>> groupItemsByInvoiceId(List<InvoiceItem> items) {
        Map<Long, List<InvoiceItem>> grouped = new LinkedHashMap<>();
        if (items == null) {
            return grouped;
        }
        for (InvoiceItem item : items) {
            if (item == null) {
                continue;
            }
            long key = item.getInvoiceId();
            List<InvoiceItem> bucket = grouped.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(key, bucket);
            }
            bucket.add(item);
        }
        return grouped;
    }
}
