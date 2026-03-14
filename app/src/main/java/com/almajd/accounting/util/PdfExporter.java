package com.almajd.accounting.util;

import android.content.Context;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.InvoiceItem;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class PdfExporter {

    private static final String PHONE_PLACEHOLDER = "[PHONE]";

    /**
     * Generate a PDF for a single invoice with bilingual header.
     */
    public static File generateInvoicePdf(Context context, Invoice invoice, Customer customer) throws Exception {
        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();
        File pdfFile = new File(exportDir, "invoice_" + invoice.getId() + ".pdf");

        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
        document.open();

        // Use Helvetica as base font (built-in, works everywhere)
        Font titleFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD);
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL);
        Font tableHeaderFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, BaseColor.WHITE);
        Font cellFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL);
        Font totalFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
        Font redFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, new BaseColor(211, 47, 47));

        // === BILINGUAL HEADER ===
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{1, 1});

        // Left cell - English (LTR)
        PdfPCell enCell = new PdfPCell();
        enCell.setBorder(Rectangle.NO_BORDER);
        enCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        enCell.addElement(new Paragraph("AlMajd Communications", titleFont));
        enCell.addElement(new Paragraph("Riyad Barham", headerFont));
        enCell.addElement(new Paragraph(PHONE_PLACEHOLDER, headerFont));
        headerTable.addCell(enCell);

        // Right cell - Arabic (RTL) - using Unicode directly
        PdfPCell arCell = new PdfPCell();
        arCell.setBorder(Rectangle.NO_BORDER);
        arCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        arCell.addElement(createRightAligned("\u0627\u0644\u0645\u062C\u062F \u0644\u0644\u0627\u062A\u0635\u0627\u0644\u0627\u062A", titleFont));
        arCell.addElement(createRightAligned("\u0631\u064A\u0627\u0636 \u0628\u0631\u0647\u0645", headerFont));
        arCell.addElement(createRightAligned(PHONE_PLACEHOLDER, headerFont));
        headerTable.addCell(arCell);

        document.add(headerTable);
        document.add(new Paragraph(" ")); // spacer

        // Divider line
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        PdfPCell divCell = new PdfPCell();
        divCell.setBorder(Rectangle.BOTTOM);
        divCell.setBorderColor(BaseColor.GRAY);
        divCell.setFixedHeight(2f);
        divider.addCell(divCell);
        document.add(divider);
        document.add(new Paragraph(" "));

        // === INVOICE INFO ===
        document.add(new Paragraph("Store: " + invoice.getStoreName(), headerFont));
        document.add(new Paragraph("Date: " + DateUtils.formatForDisplay(invoice.getInvoiceDate()), headerFont));
        document.add(new Paragraph(" "));

        // === ITEMS TABLE ===
        PdfPTable itemsTable = new PdfPTable(4);
        itemsTable.setWidthPercentage(100);
        itemsTable.setWidths(new float[]{4, 1.5f, 2, 2});

        // Header row with blue background
        BaseColor headerBg = new BaseColor(21, 101, 192);
        String[] headers = {"Item", "Qty", "Price", "Subtotal"};
        for (String h : headers) {
            PdfPCell hCell = new PdfPCell(new Phrase(h, tableHeaderFont));
            hCell.setBackgroundColor(headerBg);
            hCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            hCell.setPadding(8);
            itemsTable.addCell(hCell);
        }

        // Data rows
        List<InvoiceItem> items = invoice.getItems();
        if (items != null) {
            boolean alternate = false;
            for (InvoiceItem item : items) {
                BaseColor rowBg = alternate ? new BaseColor(245, 245, 245) : BaseColor.WHITE;

                PdfPCell nameCell = new PdfPCell(new Phrase(item.getItemName(), cellFont));
                nameCell.setBackgroundColor(rowBg);
                nameCell.setPadding(6);
                itemsTable.addCell(nameCell);

                PdfPCell qtyCell = new PdfPCell(new Phrase(String.valueOf(item.getQuantity()), cellFont));
                qtyCell.setBackgroundColor(rowBg);
                qtyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                qtyCell.setPadding(6);
                itemsTable.addCell(qtyCell);

                PdfPCell priceCell = new PdfPCell(new Phrase(NumberUtils.formatCurrency(item.getUnitPrice()), cellFont));
                priceCell.setBackgroundColor(rowBg);
                priceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                priceCell.setPadding(6);
                itemsTable.addCell(priceCell);

                PdfPCell subCell = new PdfPCell(new Phrase(NumberUtils.formatCurrency(item.getSubtotal()), cellFont));
                subCell.setBackgroundColor(rowBg);
                subCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                subCell.setPadding(6);
                itemsTable.addCell(subCell);

                alternate = !alternate;
            }
        }
        document.add(itemsTable);
        document.add(new Paragraph(" "));

        // === FOOTER TOTALS ===
        PdfPTable footerTable = new PdfPTable(2);
        footerTable.setWidthPercentage(50);
        footerTable.setHorizontalAlignment(Element.ALIGN_RIGHT);

        addFooterRow(footerTable, "Total:", NumberUtils.formatCurrency(invoice.getTotalAmount()), totalFont);
        addFooterRow(footerTable, "Paid:", NumberUtils.formatCurrency(invoice.getPaidAmount()), totalFont);

        Font remainFont = invoice.getRemaining() > 0 ? redFont : totalFont;
        addFooterRow(footerTable, "Remaining:", NumberUtils.formatCurrency(invoice.getRemaining()), remainFont);

        document.add(footerTable);

        document.close();
        return pdfFile;
    }

    /**
     * Generate a generic report PDF (for sales, payments, ledger).
     */
    public static File generateReportPdf(Context context, String reportTitle,
                                          String[] headers, List<List<String>> rows,
                                          String[] footerSummary) throws Exception {
        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();
        File pdfFile = new File(exportDir, "report_" + System.currentTimeMillis() + ".pdf");

        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36); // landscape for reports
        PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
        document.open();

        Font titleFontPdf = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
        Font tableHeaderFontPdf = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, BaseColor.WHITE);
        Font cellFontPdf = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL);
        Font footerFontPdf = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);

        // Title
        Paragraph title = new Paragraph(reportTitle, titleFontPdf);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph(" "));

        // Table
        int cols = headers.length;
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100);

        BaseColor headerBg = new BaseColor(21, 101, 192);
        for (String h : headers) {
            PdfPCell hCell = new PdfPCell(new Phrase(h, tableHeaderFontPdf));
            hCell.setBackgroundColor(headerBg);
            hCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            hCell.setPadding(8);
            table.addCell(hCell);
        }

        boolean alt = false;
        for (List<String> row : rows) {
            BaseColor bg = alt ? new BaseColor(245, 245, 245) : BaseColor.WHITE;
            for (String cellVal : row) {
                PdfPCell c = new PdfPCell(new Phrase(cellVal != null ? cellVal : "", cellFontPdf));
                c.setBackgroundColor(bg);
                c.setPadding(6);
                table.addCell(c);
            }
            alt = !alt;
        }
        document.add(table);
        document.add(new Paragraph(" "));

        // Footer summary
        if (footerSummary != null) {
            for (String line : footerSummary) {
                document.add(new Paragraph(line, footerFontPdf));
            }
        }

        document.close();
        return pdfFile;
    }

    private static Paragraph createRightAligned(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static void addFooterRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        labelCell.setPadding(4);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(4);
        table.addCell(valueCell);
    }
}
