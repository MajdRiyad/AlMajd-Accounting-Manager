package com.almajd.accounting.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure (Android-free) builder for a customer account statement (كشف حساب).
 * <p>
 * It merges a customer's invoices (debits) and payments (credits) into a single
 * chronological list with a running balance, and reports the opening balance for
 * a date range, the period totals and the closing balance. Nothing is stored and
 * nothing is written back; the statement is a read-time derivation.
 * <p>
 * This is the <b>raw movements</b> view: the display-only on-account credit
 * allocation used elsewhere is deliberately not applied here. Invoices always
 * count for their full total and payments for their full amount, so the closing
 * balance over all time equals {@code SUM(invoices) - SUM(payments)}, which is
 * exactly the customer balance shown on the customer detail and ledger screens.
 * <p>
 * Dates are the database {@code yyyy-MM-dd} strings, so they compare
 * lexicographically; {@code from}/{@code to} are inclusive when present.
 */
public final class StatementBuilder {

    private StatementBuilder() {
    }

    /** A movement is either an invoice (debit) or a payment (credit). */
    public enum Kind {
        INVOICE,
        PAYMENT
    }

    /** Raw invoice figures the statement needs. */
    public static final class InvoiceMovement {
        public final long invoiceId;
        public final String date;
        public final double total;

        public InvoiceMovement(long invoiceId, String date, double total) {
            this.invoiceId = invoiceId;
            this.date = date;
            this.total = total;
        }
    }

    /** Raw payment figures the statement needs. */
    public static final class PaymentMovement {
        public final long paymentId;
        public final String date;
        public final double amount;
        /** Linked invoice id, or {@code null} for an on-account payment. */
        public final Long invoiceId;
        public final String note;

        public PaymentMovement(long paymentId, String date, double amount,
                               Long invoiceId, String note) {
            this.paymentId = paymentId;
            this.date = date;
            this.amount = amount;
            this.invoiceId = invoiceId;
            this.note = note;
        }
    }

    /** One statement line with its running balance. */
    public static final class Row {
        public final Kind kind;
        /** Invoice id or payment id, depending on {@link #kind}. */
        public final long id;
        public final String date;
        /** Invoice total for invoices, 0 for payments. */
        public final double debit;
        /** Payment amount for payments, 0 for invoices. */
        public final double credit;
        /** Linked invoice id for payments; {@code null} otherwise. */
        public final Long linkedInvoiceId;
        /** Payment note; {@code null} for invoices. */
        public final String note;
        public final double runningBalance;

        private Row(Kind kind, long id, String date, double debit, double credit,
                    Long linkedInvoiceId, String note, double runningBalance) {
            this.kind = kind;
            this.id = id;
            this.date = date;
            this.debit = debit;
            this.credit = credit;
            this.linkedInvoiceId = linkedInvoiceId;
            this.note = note;
            this.runningBalance = runningBalance;
        }
    }

    /** The finished statement. */
    public static final class Statement {
        /** Balance of every movement strictly before {@code from} (0 when unset). */
        public final double openingBalance;
        /** True when a {@code from} date was supplied (so the opening row is shown). */
        public final boolean openingRowShown;
        /** Movements inside the period, oldest first (invoices before payments on ties). */
        public final List<Row> rows;
        public final double totalDebit;
        public final double totalCredit;
        /** Opening balance + total debit - total credit. */
        public final double closingBalance;
        /** Normalized lower bound, or {@code null} when open. */
        public final String from;
        /** Normalized upper bound, or {@code null} when open. */
        public final String to;

        private Statement(double openingBalance, boolean openingRowShown, List<Row> rows,
                          double totalDebit, double totalCredit, double closingBalance,
                          String from, String to) {
            this.openingBalance = openingBalance;
            this.openingRowShown = openingRowShown;
            this.rows = rows;
            this.totalDebit = totalDebit;
            this.totalCredit = totalCredit;
            this.closingBalance = closingBalance;
            this.from = from;
            this.to = to;
        }
    }

    /** Merged movement used internally for ordering. */
    private static final class Entry {
        final Kind kind;
        final long id;
        final String date;
        final double amount;
        final Long linkedInvoiceId;
        final String note;

        Entry(Kind kind, long id, String date, double amount, Long linkedInvoiceId, String note) {
            this.kind = kind;
            this.id = id;
            this.date = date;
            this.amount = amount;
            this.linkedInvoiceId = linkedInvoiceId;
            this.note = note;
        }
    }

    /** Chronological: by date, then invoices before payments, then by id. */
    private static final Comparator<Entry> CHRONOLOGICAL = new Comparator<Entry>() {
        @Override
        public int compare(Entry a, Entry b) {
            String dateA = a.date != null ? a.date : "";
            String dateB = b.date != null ? b.date : "";
            int byDate = dateA.compareTo(dateB);
            if (byDate != 0) {
                return byDate;
            }
            if (a.kind != b.kind) {
                return a.kind == Kind.INVOICE ? -1 : 1;
            }
            return Long.compare(a.id, b.id);
        }
    };

    /**
     * Builds the statement.
     *
     * @param invoices the customer's invoices (any order); may be null
     * @param payments the customer's payments (any order); may be null
     * @param from     inclusive lower bound ({@code yyyy-MM-dd}) or null/blank for open
     * @param to       inclusive upper bound ({@code yyyy-MM-dd}) or null/blank for open
     */
    public static Statement build(List<InvoiceMovement> invoices, List<PaymentMovement> payments,
                                  String from, String to) {
        String normalizedFrom = normalize(from);
        String normalizedTo = normalize(to);

        List<Entry> all = new ArrayList<>();
        double openingBalance = 0;

        if (invoices != null) {
            for (InvoiceMovement invoice : invoices) {
                if (invoice == null) {
                    continue;
                }
                all.add(new Entry(Kind.INVOICE, invoice.invoiceId, invoice.date,
                        invoice.total, null, null));
                if (normalizedFrom != null && isBefore(invoice.date, normalizedFrom)) {
                    openingBalance = NumberUtils.round2(openingBalance + invoice.total);
                }
            }
        }
        if (payments != null) {
            for (PaymentMovement payment : payments) {
                if (payment == null) {
                    continue;
                }
                all.add(new Entry(Kind.PAYMENT, payment.paymentId, payment.date,
                        payment.amount, payment.invoiceId, payment.note));
                if (normalizedFrom != null && isBefore(payment.date, normalizedFrom)) {
                    openingBalance = NumberUtils.round2(openingBalance - payment.amount);
                }
            }
        }

        Collections.sort(all, CHRONOLOGICAL);

        List<Row> rows = new ArrayList<>();
        double running = openingBalance;
        double totalDebit = 0;
        double totalCredit = 0;

        for (Entry entry : all) {
            if (!inPeriod(entry.date, normalizedFrom, normalizedTo)) {
                continue;
            }
            if (entry.kind == Kind.INVOICE) {
                running = NumberUtils.round2(running + entry.amount);
                totalDebit = NumberUtils.round2(totalDebit + entry.amount);
                rows.add(new Row(Kind.INVOICE, entry.id, entry.date, entry.amount, 0,
                        null, null, running));
            } else {
                running = NumberUtils.round2(running - entry.amount);
                totalCredit = NumberUtils.round2(totalCredit + entry.amount);
                rows.add(new Row(Kind.PAYMENT, entry.id, entry.date, 0, entry.amount,
                        entry.linkedInvoiceId, entry.note, running));
            }
        }

        double closing = NumberUtils.round2(openingBalance + totalDebit - totalCredit);

        return new Statement(openingBalance, normalizedFrom != null, rows,
                totalDebit, totalCredit, closing, normalizedFrom, normalizedTo);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBefore(String date, String bound) {
        return (date != null ? date : "").compareTo(bound) < 0;
    }

    private static boolean inPeriod(String date, String from, String to) {
        String d = date != null ? date : "";
        if (from != null && d.compareTo(from) < 0) {
            return false;
        }
        return to == null || d.compareTo(to) <= 0;
    }

    /**
     * Makes a string safe to use inside a file name: characters that are invalid
     * on common file systems ({@code \\ / : * ? " < > |}) and control characters
     * become {@code _}, Arabic letters and everything else (including spaces) are
     * kept. Trailing dots/spaces are dropped and the result is capped so the final
     * file name stays comfortably short.
     *
     * @return the sanitized part, never null (empty when the input has no usable
     *         characters)
     */
    public static String sanitizeFileName(String name) {
        if (name == null) {
            return "";
        }
        String invalid = "\\/:*?\"<>|";
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7F || invalid.indexOf(c) >= 0) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String out = sb.toString();
        while (!out.isEmpty() && (out.endsWith(".") || out.endsWith(" "))) {
            out = out.substring(0, out.length() - 1);
        }
        if (out.length() > 80) {
            out = out.substring(0, 80);
        }
        return out;
    }
}
