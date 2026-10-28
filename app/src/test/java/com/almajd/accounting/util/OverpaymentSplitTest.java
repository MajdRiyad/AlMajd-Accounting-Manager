package com.almajd.accounting.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.almajd.accounting.model.PendingPayment;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class OverpaymentSplitTest {

    private static PendingPayment existing(long id, String amount) {
        return new PendingPayment(id, amount, "2026-10-20", false, false);
    }

    private static PendingPayment newRow(String amount) {
        return new PendingPayment(0, amount, "2026-10-20", false, true);
    }

    @Test
    public void noExcessLeavesRowsUntouched() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "300"));
        rows.add(existing(2, "400"));

        OverpaymentSplit split = OverpaymentSplit.calculate(1000, 0, rows);

        assertEquals(700.0, split.directPaid, 1e-9);
        assertEquals(700.0, split.paid, 1e-9);
        assertEquals(300.0, split.remaining, 1e-9);
        assertEquals(0.0, split.overpaidBy, 1e-9);
        assertEquals(0.0, split.excess, 1e-9);
        assertTrue(split.carves.isEmpty());
    }

    @Test
    public void excessSmallerThanLastRowReducesOnlyThatRow() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "300"));
        rows.add(existing(2, "500"));

        OverpaymentSplit split = OverpaymentSplit.calculate(700, 0, rows);

        assertEquals(800.0, split.directPaid, 1e-9);
        assertEquals(100.0, split.excess, 1e-9);
        assertEquals(700.0, split.linkedPaidAfter, 1e-9);
        assertEquals(1, split.carves.size());
        assertEquals(rows.get(1), split.carves.get(0).row);
        assertEquals(400.0, split.carves.get(0).linkedAmount, 1e-9);
        assertEquals(100.0, split.carves.get(0).carvedAmount, 1e-9);
        assertEquals("300", rows.get(0).getAmountText());
    }

    @Test
    public void excessEqualToLastRowRemovesItCompletely() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "300"));
        rows.add(existing(2, "500"));

        OverpaymentSplit split = OverpaymentSplit.calculate(300, 0, rows);

        assertEquals(500.0, split.excess, 1e-9);
        assertEquals(300.0, split.linkedPaidAfter, 1e-9);
        assertEquals(1, split.carves.size());
        assertEquals(0.0, split.carves.get(0).linkedAmount, 1e-9);
        assertEquals("300", rows.get(0).getAmountText());
    }

    @Test
    public void excessSpanningTwoRowsCarvesLastFirst() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "300"));
        rows.add(existing(2, "500"));
        rows.add(existing(3, "200"));

        OverpaymentSplit split = OverpaymentSplit.calculate(600, 0, rows);

        assertEquals(400.0, split.excess, 1e-9);
        assertEquals(600.0, split.linkedPaidAfter, 1e-9);
        assertEquals(2, split.carves.size());

        assertEquals(rows.get(2), split.carves.get(0).row);
        assertEquals(0.0, split.carves.get(0).linkedAmount, 1e-9);
        assertEquals(200.0, split.carves.get(0).carvedAmount, 1e-9);

        assertEquals(rows.get(1), split.carves.get(1).row);
        assertEquals(300.0, split.carves.get(1).linkedAmount, 1e-9);
        assertEquals(200.0, split.carves.get(1).carvedAmount, 1e-9);

        assertEquals("300", rows.get(0).getAmountText());
    }

    @Test
    public void newRowsAreCarvedBeforeEarlierExistingRows() {
        List<PendingPayment> rows = new ArrayList<>();
        PendingPayment existingRow = existing(1, "300");
        PendingPayment newLast = newRow("500");
        rows.add(existingRow);
        rows.add(newLast);

        OverpaymentSplit split = OverpaymentSplit.calculate(700, 0, rows);
        split.applyToRows();

        assertEquals(100.0, split.excess, 1e-9);
        assertEquals(1, split.carves.size());
        assertEquals(newLast, split.carves.get(0).row);
        assertEquals("400.00", newLast.getAmountText());
        assertEquals("300", existingRow.getAmountText());
    }

    @Test
    public void rowReducedToZeroIsMarkedDeleted() {
        List<PendingPayment> rows = new ArrayList<>();
        PendingPayment existingRow = existing(1, "300");
        PendingPayment newLast = newRow("500");
        rows.add(existingRow);
        rows.add(newLast);

        OverpaymentSplit split = OverpaymentSplit.calculate(300, 0, rows);
        split.applyToRows();

        assertEquals(500.0, split.excess, 1e-9);
        assertTrue(newLast.isDeleted());
        assertFalse(existingRow.isDeleted());
        assertEquals("300", existingRow.getAmountText());
    }

    @Test
    public void blankNewRowsAreIgnoredWhenCarving() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "800"));
        rows.add(newRow(""));
        rows.add(newRow("  "));

        OverpaymentSplit split = OverpaymentSplit.calculate(600, 0, rows);

        assertEquals(800.0, split.directPaid, 1e-9);
        assertEquals(200.0, split.excess, 1e-9);
        assertEquals(1, split.carves.size());
        assertEquals(rows.get(0), split.carves.get(0).row);
        assertEquals(600.0, split.carves.get(0).linkedAmount, 1e-9);
    }

    @Test
    public void roundingTenthsDoesNotCreateASpuriousExcess() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "0.1"));
        rows.add(existing(2, "0.2"));

        OverpaymentSplit exact = OverpaymentSplit.calculate(0.3, 0, rows);
        assertEquals(0.3, exact.paid, 1e-9);
        assertEquals(0.0, exact.excess, 1e-9);
        assertTrue(exact.carves.isEmpty());

        OverpaymentSplit over = OverpaymentSplit.calculate(0.2, 0, rows);
        assertEquals(0.1, over.excess, 1e-9);
        assertEquals(1, over.carves.size());
        assertEquals(0.1, over.carves.get(0).linkedAmount, 1e-9);
    }

    @Test
    public void zeroNetTotalCarvesDirectRowsWithoutNegativeRemaining() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "100"));

        OverpaymentSplit split = OverpaymentSplit.calculate(0, 0, rows);

        assertEquals(100.0, split.paid, 1e-9);
        assertEquals(0.0, split.remaining, 1e-9);
        assertEquals(100.0, split.overpaidBy, 1e-9);
        assertEquals(100.0, split.excess, 1e-9);
        assertEquals(0.0, split.linkedPaidAfter, 1e-9);
    }

    @Test
    public void negativeInputsAreClamped() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "-10"));
        rows.add(existing(2, "50"));

        OverpaymentSplit split = OverpaymentSplit.calculate(-100, -20, rows);

        assertEquals(50.0, split.directPaid, 1e-9);
        assertEquals(0.0, split.allocatedFromCredit, 1e-9);
        assertEquals(50.0, split.paid, 1e-9);
        assertEquals(0.0, split.remaining, 1e-9);
        assertEquals(50.0, split.overpaidBy, 1e-9);
        assertEquals(50.0, split.excess, 1e-9);
    }

    @Test
    public void creditCoveringWholeOrderShowsPaidWithoutExtraExcess() {
        OverpaymentSplit split = OverpaymentSplit.calculate(1000, 1000, null);

        assertEquals(1000.0, split.paid, 1e-9);
        assertEquals(0.0, split.remaining, 1e-9);
        assertEquals(0.0, split.overpaidBy, 1e-9);
        assertEquals(0.0, split.excess, 1e-9);
        assertTrue(split.carves.isEmpty());
    }

    @Test
    public void partialCreditIsAddedToDirectPayments() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "400"));

        OverpaymentSplit split = OverpaymentSplit.calculate(1000, 300, rows);

        assertEquals(400.0, split.directPaid, 1e-9);
        assertEquals(300.0, split.allocatedFromCredit, 1e-9);
        assertEquals(700.0, split.paid, 1e-9);
        assertEquals(300.0, split.remaining, 1e-9);
        assertEquals(0.0, split.excess, 1e-9);
    }

    @Test
    public void creditPlusDirectExceedingCarvesOnlyDirectRows() {
        List<PendingPayment> rows = new ArrayList<>();
        rows.add(existing(1, "500"));
        rows.add(existing(2, "400"));

        OverpaymentSplit split = OverpaymentSplit.calculate(1000, 300, rows);

        assertEquals(900.0, split.directPaid, 1e-9);
        assertEquals(1200.0, split.paid, 1e-9);
        assertEquals(200.0, split.overpaidBy, 1e-9);
        assertEquals(200.0, split.excess, 1e-9);
        assertEquals(700.0, split.linkedPaidAfter, 1e-9);
        assertEquals(1, split.carves.size());
        assertEquals(rows.get(1), split.carves.get(0).row);
        assertEquals(200.0, split.carves.get(0).linkedAmount, 1e-9);
        assertEquals("500", rows.get(0).getAmountText());
    }
}
