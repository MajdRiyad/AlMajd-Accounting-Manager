package com.almajd.accounting.model;

/**
 * Derived payment status of an invoice. Never persisted -- it is computed
 * by the {@code invoice_view} from the sum of the payments linked to the
 * invoice (invoice_id) compared with the invoice total.
 */
public enum InvoiceStatus {
    UNPAID,
    PARTIAL,
    PAID;

    /** Parses the value produced by the SQL view ("unpaid"/"partial"/"paid"). */
    public static InvoiceStatus fromDb(String value) {
        if (value == null) return UNPAID;
        switch (value.toLowerCase()) {
            case "paid":
                return PAID;
            case "partial":
                return PARTIAL;
            case "unpaid":
            default:
                return UNPAID;
        }
    }
}
