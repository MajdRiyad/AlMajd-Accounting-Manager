package com.almajd.accounting.util;

import com.almajd.accounting.model.InvoiceStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure (Android-free) greedy allocator that spreads a customer's <em>on-account</em>
 * money over that customer's unpaid invoices.
 * <p>
 * On-account money is {@code SUM(payments.amount) WHERE invoice_id IS NULL}: money
 * the customer has with us that is not tied to a specific invoice. The owner wants
 * an invoice that is covered by this credit to stop looking "unpaid", without ever
 * rewriting a stored payment row.
 * <p>
 * Rules (display-only derivation):
 * <ul>
 *   <li>The pool (on-account sum) is spread over the customer's invoices that still
 *       have {@code remaining > 0} after their <em>direct</em> payments.</li>
 *   <li>Invoices are served <b>oldest first</b>: by {@code invoiceDate} ascending,
 *       then by invoice id ascending when the dates tie.</li>
 *   <li>Each invoice receives {@code min(remaining, poolLeft)}.</li>
 *   <li>Nothing is ever written back to the database.</li>
 * </ul>
 * <p>
 * <b>Invariant and its caveat.</b> When no invoice is directly overpaid, the sum of
 * {@code effectiveRemaining} over a customer's invoices equals
 * {@code max(customerBalance, 0)}, where
 * {@code customerBalance = SUM(invoice.total) - SUM(all payments)}.
 * The invariant can break when an invoice is overpaid <em>directly</em>
 * (direct payments exceed its total): that excess stays parked on that invoice and
 * is deliberately not redistributed, so the sum of effective remaining can exceed
 * {@code max(balance, 0)} by the total direct excess. Redistributing it would
 * require rewriting stored payments, which this layer never does.
 */
public final class CreditAllocator {

    private CreditAllocator() {
    }

    /** Immutable raw figures for one invoice, as read from the database. */
    public static final class Debt {
        public final long invoiceId;
        /** DB date string (YYYY-MM-DD); lexicographic order is chronological. */
        public final String invoiceDate;
        public final double total;
        /** Sum of payments linked to this invoice (invoice_id = id). */
        public final double paidDirect;

        public Debt(long invoiceId, String invoiceDate, double total, double paidDirect) {
            this.invoiceId = invoiceId;
            this.invoiceDate = invoiceDate;
            this.total = total;
            this.paidDirect = paidDirect;
        }
    }

    /** Immutable derived figures for one invoice after the credit is allocated. */
    public static final class Allocation {
        public final long invoiceId;
        public final double paidDirect;
        public final double allocatedFromCredit;
        public final double effectivePaid;
        public final double effectiveRemaining;
        public final InvoiceStatus status;

        Allocation(long invoiceId, double paidDirect, double allocatedFromCredit,
                   double effectivePaid, double effectiveRemaining, InvoiceStatus status) {
            this.invoiceId = invoiceId;
            this.paidDirect = paidDirect;
            this.allocatedFromCredit = allocatedFromCredit;
            this.effectivePaid = effectivePaid;
            this.effectiveRemaining = effectiveRemaining;
            this.status = status;
        }
    }

    /** Oldest first: by invoice date, then by id. */
    private static final Comparator<Debt> OLDEST_FIRST = new Comparator<Debt>() {
        @Override
        public int compare(Debt a, Debt b) {
            String dateA = a.invoiceDate != null ? a.invoiceDate : "";
            String dateB = b.invoiceDate != null ? b.invoiceDate : "";
            int byDate = dateA.compareTo(dateB);
            if (byDate != 0) {
                return byDate;
            }
            return Long.compare(a.invoiceId, b.invoiceId);
        }
    };

    /**
     * Allocates {@code pool} over one customer's invoices, oldest first.
     *
     * @param debts the customer's invoices (any order); never mutated
     * @param pool  the customer's on-account total; negative values are clamped to 0
     * @return one {@link Allocation} per input invoice, in the same order as the input
     */
    public static List<Allocation> allocate(List<Debt> debts, double pool) {
        List<Allocation> result = new ArrayList<>();
        if (debts == null || debts.isEmpty()) {
            return result;
        }

        List<Debt> ordered = new ArrayList<>(debts);
        Collections.sort(ordered, OLDEST_FIRST);

        double poolLeft = Math.max(NumberUtils.round2(pool), 0.0);
        Map<Long, Allocation> byId = new LinkedHashMap<>();

        for (Debt debt : ordered) {
            double total = NumberUtils.round2(debt.total);
            double paidDirect = NumberUtils.round2(debt.paidDirect);

            double remaining = NumberUtils.round2(total - paidDirect);
            if (remaining < 0) {
                remaining = 0;
            }

            double allocated = NumberUtils.round2(Math.min(remaining, poolLeft));
            if (allocated < 0) {
                allocated = 0;
            }
            poolLeft = NumberUtils.round2(poolLeft - allocated);
            if (poolLeft < 0) {
                poolLeft = 0;
            }

            double effectivePaid = NumberUtils.round2(paidDirect + allocated);
            double effectiveRemaining = NumberUtils.round2(total - effectivePaid);
            if (effectiveRemaining < 0) {
                effectiveRemaining = 0;
            }

            InvoiceStatus status;
            if (effectiveRemaining <= 0) {
                status = InvoiceStatus.PAID;
            } else if (effectivePaid > 0) {
                status = InvoiceStatus.PARTIAL;
            } else {
                status = InvoiceStatus.UNPAID;
            }

            byId.put(debt.invoiceId, new Allocation(debt.invoiceId, paidDirect, allocated,
                    effectivePaid, effectiveRemaining, status));
        }

        // Preserve the caller's input order so results are easy to map back.
        for (Debt debt : debts) {
            Allocation allocation = byId.get(debt.invoiceId);
            if (allocation != null) {
                result.add(allocation);
            }
        }
        return result;
    }

    /**
     * Convenience batch form: allocates each customer's own pool over that
     * customer's own invoices. Customers never see each other's credit.
     *
     * @param debtsByCustomer invoices grouped by customer key (usually store name)
     * @param poolByCustomer  on-account total per customer key (missing key = 0)
     * @return allocations per customer key, each in the order the debts were given
     */
    public static Map<String, List<Allocation>> allocateByCustomer(
            Map<String, List<Debt>> debtsByCustomer, Map<String, Double> poolByCustomer) {
        Map<String, List<Allocation>> out = new LinkedHashMap<>();
        if (debtsByCustomer == null) {
            return out;
        }
        for (Map.Entry<String, List<Debt>> entry : debtsByCustomer.entrySet()) {
            String customer = entry.getKey();
            double pool = 0.0;
            if (poolByCustomer != null && poolByCustomer.get(customer) != null) {
                pool = poolByCustomer.get(customer);
            }
            out.put(customer, allocate(entry.getValue(), pool));
        }
        return out;
    }
}
