package com.almajd.accounting.util;

import android.content.Context;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.InvoiceItem;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Generates PDF documents with full Arabic + English bilingual support.
 * Uses the Cairo font (bundled in assets/fonts/) which supports both
 * Latin and Arabic glyphs with proper RTL shaping.
 */
public class PdfExporter {

    // Cached base fonts (loaded once per app session)
    private static BaseFont cairoRegular;
    private static BaseFont cairoBold;

    /**
     * Loads the Cairo font from assets. Caches for reuse.
     */
    private static BaseFont getCairoRegular(Context context) throws Exception {
        if (cairoRegular == null) {
            String fontPath = copyFontToCache(context, "fonts/Cairo-Regular.ttf", "Cairo-Regular.ttf");
            cairoRegular = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        }
        return cairoRegular;
    }

    private static BaseFont getCairoBold(Context context) throws Exception {
        if (cairoBold == null) {
            String fontPath = copyFontToCache(context, "fonts/Cairo-Bold.ttf", "Cairo-Bold.ttf");
            cairoBold = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        }
        return cairoBold;
    }

    /**
     * iText can't read from Android assets directly, so we copy the font
     * to the cache directory and return its absolute path.
     */
    private static String copyFontToCache(Context context, String assetPath, String fileName) throws Exception {
        File fontFile = new File(context.getCacheDir(), fileName);
        if (!fontFile.exists()) {
            InputStream is = context.getAssets().open(assetPath);
            FileOutputStream fos = new FileOutputStream(fontFile);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            is.close();
        }
        return fontFile.getAbsolutePath();
    }

    /**
     * Generate a PDF for a single invoice with bilingual header.
     * Arabic text is rendered with the Cairo font and proper RTL direction.
     */
    public static File generateInvoicePdf(Context context, Invoice invoice, Customer customer) throws Exception {
        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();
        File pdfFile = new File(exportDir, "invoice_" + invoice.getId() + ".pdf");

        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
        document.open();

        // Load Cairo fonts for Arabic+English support
        BaseFont regular = getCairoRegular(context);
        BaseFont bold = getCairoBold(context);

        // Font definitions
        Font titleFontBold = new Font(bold, 16, Font.BOLD);
        Font titleFontBoldAr = new Font(bold, 16, Font.BOLD);
        Font headerFont = new Font(regular, 11, Font.NORMAL);
        Font headerFontAr = new Font(regular, 11, Font.NORMAL);
        Font tableHeaderFont = new Font(bold, 11, Font.BOLD, BaseColor.WHITE);
        Font cellFont = new Font(regular, 10, Font.NORMAL);
        Font totalFont = new Font(bold, 12, Font.BOLD);
        Font redFont = new Font(bold, 12, Font.BOLD, new BaseColor(211, 47, 47));
        Font labelFont = new Font(regular, 11, Font.NORMAL, new BaseColor(117, 117, 117));
        Font valueFont = new Font(bold, 11, Font.BOLD);

        // Read business info from settings
        AppSettings settings = new AppSettings(context);

        // === BILINGUAL HEADER ===
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{1.2f, 0.1f, 1.2f});

        // Left cell - English (LTR)
        PdfPCell enCell = new PdfPCell();
        enCell.setBorder(Rectangle.NO_BORDER);
        enCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        enCell.setPaddingBottom(8);
        enCell.addElement(new Paragraph(settings.getCompanyNameEn(), titleFontBold));
        enCell.addElement(new Paragraph(settings.getOwnerNameEn(), headerFont));
        String phone = settings.getPhone();
        if (phone != null && !phone.isEmpty()) {
            enCell.addElement(new Paragraph(phone, headerFont));
        }
        headerTable.addCell(enCell);

        // Middle divider cell
        PdfPCell midCell = new PdfPCell();
        midCell.setBorder(Rectangle.LEFT | Rectangle.RIGHT);
        midCell.setBorderColor(new BaseColor(21, 101, 192));
        midCell.setBorderWidth(1.5f);
        headerTable.addCell(midCell);

        // Right cell - Arabic (RTL)
        PdfPCell arCell = new PdfPCell();
        arCell.setBorder(Rectangle.NO_BORDER);
        arCell.setPaddingBottom(8);
        arCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        Paragraph arTitle = new Paragraph(settings.getCompanyNameAr(), titleFontBoldAr);
        arTitle.setAlignment(Element.ALIGN_RIGHT);
        arCell.addElement(arTitle);

        Paragraph arOwner = new Paragraph(settings.getOwnerNameAr(), headerFontAr);
        arOwner.setAlignment(Element.ALIGN_RIGHT);
        arCell.addElement(arOwner);

        if (phone != null && !phone.isEmpty()) {
            Paragraph arPhone = new Paragraph(phone, headerFontAr);
            arPhone.setAlignment(Element.ALIGN_RIGHT);
            arCell.addElement(arPhone);
        }

        headerTable.addCell(arCell);
        document.add(headerTable);

        // Divider line
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        divider.setSpacingBefore(6);
        divider.setSpacingAfter(10);
        PdfPCell divCell = new PdfPCell();
        divCell.setBorder(Rectangle.BOTTOM);
        divCell.setBorderColor(new BaseColor(21, 101, 192));
        divCell.setBorderWidth(2f);
        divCell.setFixedHeight(2f);
        divider.addCell(divCell);
        document.add(divider);

        // === INVOICE INFO ===
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 1});
        infoTable.setSpacingAfter(12);

        // Store row
        PdfPCell storeLabel = new PdfPCell(new Phrase("\u0627\u0633\u0645 \u0627\u0644\u0645\u062D\u0644 / Store:", labelFont));
        storeLabel.setBorder(Rectangle.NO_BORDER);
        storeLabel.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        infoTable.addCell(storeLabel);

        PdfPCell storeValue = new PdfPCell(new Phrase(invoice.getStoreName(), valueFont));
        storeValue.setBorder(Rectangle.NO_BORDER);
        storeValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        infoTable.addCell(storeValue);

        // Date row
        PdfPCell dateLabel = new PdfPCell(new Phrase("\u0627\u0644\u062A\u0627\u0631\u064A\u062E / Date:", labelFont));
        dateLabel.setBorder(Rectangle.NO_BORDER);
        dateLabel.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        infoTable.addCell(dateLabel);

        PdfPCell dateValue = new PdfPCell(new Phrase(DateUtils.formatForDisplay(invoice.getInvoiceDate()), valueFont));
        dateValue.setBorder(Rectangle.NO_BORDER);
        dateValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        infoTable.addCell(dateValue);

        // Invoice number row
        PdfPCell invLabel = new PdfPCell(new Phrase("\u0631\u0642\u0645 \u0627\u0644\u0641\u0627\u062A\u0648\u0631\u0629 / Invoice #:", labelFont));
        invLabel.setBorder(Rectangle.NO_BORDER);
        invLabel.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        infoTable.addCell(invLabel);

        PdfPCell invValue = new PdfPCell(new Phrase(String.valueOf(invoice.getId()), valueFont));
        invValue.setBorder(Rectangle.NO_BORDER);
        invValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        infoTable.addCell(invValue);

        document.add(infoTable);

        // === ITEMS TABLE ===
        PdfPTable itemsTable = new PdfPTable(4);
        itemsTable.setWidthPercentage(100);
        itemsTable.setWidths(new float[]{4, 1.5f, 2, 2});

        // Header row with blue background
        BaseColor headerBg = new BaseColor(21, 101, 192);
        String[] headers = {
                "\u0627\u0644\u0628\u0646\u062F / Item",
                "\u0627\u0644\u0643\u0645\u064A\u0629 / Qty",
                "\u0627\u0644\u0633\u0639\u0631 / Price",
                "\u0627\u0644\u0645\u062C\u0645\u0648\u0639 / Subtotal"
        };
        for (String h : headers) {
            PdfPCell hCell = new PdfPCell(new Phrase(h, tableHeaderFont));
            hCell.setBackgroundColor(headerBg);
            hCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            hCell.setPadding(8);
            hCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            itemsTable.addCell(hCell);
        }

        // Data rows
        List<InvoiceItem> items = invoice.getItems();
        if (items != null) {
            boolean alternate = false;
            for (InvoiceItem item : items) {
                BaseColor rowBg = alternate ? new BaseColor(240, 244, 248) : BaseColor.WHITE;

                PdfPCell nameCell = new PdfPCell(new Phrase(item.getItemName(), cellFont));
                nameCell.setBackgroundColor(rowBg);
                nameCell.setPadding(6);
                nameCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
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

        addFooterRow(footerTable, "\u0627\u0644\u0625\u062C\u0645\u0627\u0644\u064A / Total:", NumberUtils.formatCurrency(invoice.getTotalAmount()), totalFont);
        addFooterRow(footerTable, "\u0627\u0644\u0645\u062F\u0641\u0648\u0639 / Paid:", NumberUtils.formatCurrency(invoice.getPaidAmount()), totalFont);

        Font remainFont = invoice.getRemaining() > 0 ? redFont : totalFont;
        addFooterRow(footerTable, "\u0627\u0644\u0645\u062A\u0628\u0642\u064A / Remaining:", NumberUtils.formatCurrency(invoice.getRemaining()), remainFont);

        document.add(footerTable);

        document.close();
        return pdfFile;
    }

    /**
     * Generate a generic report PDF (for sales, payments, ledger).
     * Supports Arabic text in all cells using Cairo font.
     */
    public static File generateReportPdf(Context context, String reportTitle,
                                          String[] headers, List<List<String>> rows,
                                          String[] footerSummary) throws Exception {
        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();
        File pdfFile = new File(exportDir, "report_" + System.currentTimeMillis() + ".pdf");

        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
        document.open();

        // Load fonts
        BaseFont regular = getCairoRegular(context);
        BaseFont bold = getCairoBold(context);

        Font titleFontPdf = new Font(bold, 18, Font.BOLD, new BaseColor(21, 101, 192));
        Font tableHeaderFontPdf = new Font(bold, 11, Font.BOLD, BaseColor.WHITE);
        Font cellFontPdf = new Font(regular, 10, Font.NORMAL);
        Font footerFontPdf = new Font(bold, 12, Font.BOLD);

        // Title
        Paragraph title = new Paragraph(reportTitle, titleFontPdf);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(12);
        document.add(title);

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
            hCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            table.addCell(hCell);
        }

        boolean alt = false;
        for (List<String> row : rows) {
            BaseColor bg = alt ? new BaseColor(240, 244, 248) : BaseColor.WHITE;
            for (String cellVal : row) {
                PdfPCell c = new PdfPCell(new Phrase(cellVal != null ? cellVal : "", cellFontPdf));
                c.setBackgroundColor(bg);
                c.setPadding(6);
                c.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                table.addCell(c);
            }
            alt = !alt;
        }
        document.add(table);
        document.add(new Paragraph(" "));

        // Footer summary
        if (footerSummary != null) {
            for (String line : footerSummary) {
                if (line != null && !line.isEmpty()) {
                    Paragraph p = new Paragraph(line, footerFontPdf);
                    p.setAlignment(Element.ALIGN_RIGHT);
                    document.add(p);
                }
            }
        }

        document.close();
        return pdfFile;
    }

    private static void addFooterRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        labelCell.setPadding(4);
        labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(4);
        table.addCell(valueCell);
    }
}
