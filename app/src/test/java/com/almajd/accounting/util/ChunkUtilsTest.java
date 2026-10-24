package com.almajd.accounting.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.almajd.accounting.model.InvoiceItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Plain JVM tests for the pure helpers used to batch invoice-item loading.
 */
public class ChunkUtilsTest {

    // ── chunk() ─────────────────────────────────────────────────────────

    @Test
    public void chunk_nullOrEmpty_returnsEmpty() {
        assertTrue(ChunkUtils.chunk(null, 500).isEmpty());
        assertTrue(ChunkUtils.chunk(new ArrayList<Long>(), 500).isEmpty());
    }

    @Test
    public void chunk_nonPositiveSize_returnsEmpty() {
        assertTrue(ChunkUtils.chunk(Arrays.asList(1L, 2L, 3L), 0).isEmpty());
        assertTrue(ChunkUtils.chunk(Arrays.asList(1L, 2L, 3L), -5).isEmpty());
    }

    @Test
    public void chunk_splitsIntoSizedBatchesPreservingOrder() {
        List<Long> ids = ids(10);
        List<List<Long>> chunks = ChunkUtils.chunk(ids, 3);

        assertEquals(4, chunks.size());
        assertEquals(3, chunks.get(0).size());
        assertEquals(3, chunks.get(1).size());
        assertEquals(3, chunks.get(2).size());
        assertEquals(1, chunks.get(3).size());

        // Flattened order must match the input order exactly.
        List<Long> flat = new ArrayList<>();
        for (List<Long> chunk : chunks) {
            flat.addAll(chunk);
        }
        assertEquals(ids, flat);
    }

    @Test
    public void chunk_exactMultiple_hasNoEmptyTrailingChunk() {
        List<List<Long>> chunks = ChunkUtils.chunk(ids(6), 3);
        assertEquals(2, chunks.size());
        assertEquals(3, chunks.get(0).size());
        assertEquals(3, chunks.get(1).size());
    }

    @Test
    public void chunk_largeListAcrossManyBatches_coversEverythingOnce() {
        List<Long> ids = ids(1200);
        List<List<Long>> chunks = ChunkUtils.chunk(ids, 500);

        assertEquals(3, chunks.size());
        assertEquals(500, chunks.get(0).size());
        assertEquals(500, chunks.get(1).size());
        assertEquals(200, chunks.get(2).size());

        // Every id appears exactly once, in order.
        List<Long> flat = new ArrayList<>();
        for (List<Long> chunk : chunks) {
            flat.addAll(chunk);
        }
        assertEquals(ids, flat);
    }

    // ── groupItemsByInvoiceId() ─────────────────────────────────────────

    @Test
    public void group_null_returnsEmptyMap() {
        Map<Long, List<InvoiceItem>> grouped = ChunkUtils.groupItemsByInvoiceId(null);
        assertTrue(grouped.isEmpty());
    }

    @Test
    public void group_groupsItemsByInvoiceIdPreservingOrder() {
        List<InvoiceItem> items = new ArrayList<>();
        items.add(item(1, 10));
        items.add(item(2, 10));
        items.add(item(3, 20));
        items.add(item(4, 10));
        items.add(item(5, 20));

        Map<Long, List<InvoiceItem>> grouped = ChunkUtils.groupItemsByInvoiceId(items);

        assertEquals(2, grouped.size());
        assertEquals(3, grouped.get(10L).size());
        assertEquals(2, grouped.get(20L).size());

        // Item order inside each group is the encounter order.
        assertEquals(1, grouped.get(10L).get(0).getId());
        assertEquals(2, grouped.get(10L).get(1).getId());
        assertEquals(4, grouped.get(10L).get(2).getId());
        assertEquals(3, grouped.get(20L).get(0).getId());
        assertEquals(5, grouped.get(20L).get(1).getId());
    }

    @Test
    public void group_skipsNullEntries() {
        List<InvoiceItem> items = new ArrayList<>();
        items.add(null);
        items.add(item(7, 99));
        items.add(null);

        Map<Long, List<InvoiceItem>> grouped = ChunkUtils.groupItemsByInvoiceId(items);

        assertEquals(1, grouped.size());
        assertEquals(1, grouped.get(99L).size());
        assertEquals(7, grouped.get(99L).get(0).getId());
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static List<Long> ids(int count) {
        List<Long> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add((long) i);
        }
        return list;
    }

    private static InvoiceItem item(long itemId, long invoiceId) {
        InvoiceItem item = new InvoiceItem();
        item.setId(itemId);
        item.setInvoiceId(invoiceId);
        return item;
    }
}
