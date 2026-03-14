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
 *
 * Layout approach: iText 5 PdfPTable does NOT have a setRunDirection method.
 * RTL is achieved by:
 *  - Setting RUN_DIRECTION_RTL on every PdfPCell that contains Arabic text
 *  - Manually ordering columns so the visual right-to-left reading is correct
 *    (rightmost column = last added in code for LTR table, or use ColumnText)
 *  - Using ALIGN_RIGHT for Arabic text alignment
 */
public class PdfExporter {

    // Cached base fonts
    private static BaseFont cairoRegular;
    private static BaseFont cairoBold;

    // Branding color
    private static final BaseColor BRAND_BLUE = new BaseColor(21, 101, 192);
    private static final BaseColor ALT_ROW_BG = new BaseColor(240, 244, 248);

    private static BaseFont getCairoRegular(Context context) throws Exception {
        if (cairoRegular == null) {
            String fontPath = copyFontToCache(context, "fonts/Cairo-Regular.ttf", "Cairo-Regular.ttf");
            try {
                cairoRegular = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                new File(fontPath).delete();
                throw e;
            }
        }
        return cairoRegular;
    }

    private static BaseFont getCairoBold(Context context) throws Exception {
        if (cairoBold == null) {
            String fontPath = copyFontToCache(context, "fonts/Cairo-Bold.ttf", "Cairo-Bold.ttf");
            try {
                cairoBold = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                new File(fontPath).delete();
                throw e;
            }
        }
        return cairoBold;
    }

    private static String copyFontToCache(Context context, String assetPath, String fileName) throws Exception {
        File fontFile = new File(context.getCacheDir(), fileName);
        InputStream is = context.getAssets().open(assetPath);
        FileOutputStream fos = new FileOutputStream(fontFile);
        byte[] buffer = new byte[4096];
        int len;
        while ((len = is.read(buffer)) != -1) {
            fos.write(buffer, 0, len);
        }
        fos.flush();
        fos.close();
        is.close();

        if (fontFile.length() < 10000) {
            throw new Exception("Font file too small, likely corrupt: " + fontFile.getAbsolutePath());
        }
        return fontFile.getAbsolutePath();
    }

    // ─── Cell helpers ─────────────────────────────────────────────────────

    /** Create a cell with RTL text direction, custom alignment, background, and padding. */
    private static PdfPCell makeCell(Phrase phrase, int hAlign, BaseColor bg,
                                      float padding, boolean rtl, int border) {
        PdfPCell cell = new PdfPCell(phrase);
        if (rtl) cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setHorizontalAlignment(hAlign);
        if (bg != null) cell.setBackgroundColor(bg);
        cell.setPadding(padding);
        cell.setBorder(border);
        return cell;
    }

    private static PdfPCell rtlCell(Phrase phrase, int hAlign, BaseColor bg, float padding) {
        return makeCell(phrase, hAlign, bg, padding, true, Rectangle.BOX);
    }

    private static PdfPCell ltrCell(Phrase phrase, int hAlign, float padding) {
        return makeCell(phrase, hAlign, null, padding, false, Rectangle.NO_BORDER);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  INVOICE PDF
    // ═══════════════════════════════════════════════════════════════════════

    public static File generateInvoicePdf(Context context, Invoice invoice, Customer customer) throws Exception {
        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();
        File pdfFile = new File(exportDir, "invoice_" + invoice.getId() + ".pdf");

        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
        document.open();

        BaseFont regular = getCairoRegular(context);
        BaseFont bold = getCairoBold(context);

        // Font definitions
        Font titleBold   = new Font(bold, 16, Font.BOLD);
        Font subtitleFont = new Font(regular, 11, Font.NORMAL);
        Font tblHdrFont  = new Font(bold, 11, Font.BOLD, BaseColor.WHITE);
        Font cellFont    = new Font(regular, 10, Font.NORMAL);
        Font totalFont   = new Font(bold, 12, Font.BOLD);
        Font redFont     = new Font(bold, 12, Font.BOLD, new BaseColor(211, 47, 47));
        Font labelFont   = new Font(regular, 11, Font.NORMAL, new BaseColor(117, 117, 117));
        Font valueFont   = new Font(bold, 11, Font.BOLD);

        AppSettings settings = new AppSettings(context);
        String phone = settings.getPhone();

        // ─── BILINGUAL HEADER ─────────────────────────────────────────────
        // 3-column LTR table: [English left] | [divider] | [Arabic right]
        // Column order in code: col0=left(EN), col1=divider, col2=right(AR)
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{1.2f, 0.05f, 1.2f});

        // Col 0 — English (far left, LTR)
        PdfPCell enCell = new PdfPCell();
        enCell.setBorder(Rectangle.NO_BORDER);
        enCell.setPaddingBottom(8);
        enCell.setVerticalAlignment(Element.ALIGN_TOP);

        Paragraph enName = new Paragraph(settings.getCompanyNameEn(), titleBold);
        enName.setAlignment(Element.ALIGN_LEFT);
        enCell.addElement(enName);

        Paragraph enOwner = new Paragraph(settings.getOwnerNameEn(), subtitleFont);
        enOwner.setAlignment(Element.ALIGN_LEFT);
        enCell.addElement(enOwner);

        if (phone != null && !phone.isEmpty()) {
            Paragraph enPhone = new Paragraph(phone, subtitleFont);
            enPhone.setAlignment(Element.ALIGN_LEFT);
            enCell.addElement(enPhone);
        }
        headerTable.addCell(enCell);

        // Col 1 — Vertical divider line
        PdfPCell midCell = new PdfPCell();
        midCell.setBorder(Rectangle.LEFT | Rectangle.RIGHT);
        midCell.setBorderColor(BRAND_BLUE);
        midCell.setBorderWidth(1.5f);
        headerTable.addCell(midCell);

        // Col 2 — Arabic (far right, RTL)
        PdfPCell arCell = new PdfPCell();
        arCell.setBorder(Rectangle.NO_BORDER);
        arCell.setPaddingBottom(8);
        arCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        arCell.setVerticalAlignment(Element.ALIGN_TOP);

        Paragraph arName = new Paragraph(settings.getCompanyNameAr(), titleBold);
        arName.setAlignment(Element.ALIGN_RIGHT);
        arCell.addElement(arName);

        Paragraph arOwner = new Paragraph(settings.getOwnerNameAr(), subtitleFont);
        arOwner.setAlignment(Element.ALIGN_RIGHT);
        arCell.addElement(arOwner);

        if (phone != null && !phone.isEmpty()) {
            Paragraph arPhone = new Paragraph(phone, subtitleFont);
            arPhone.setAlignment(Element.ALIGN_RIGHT);
            arCell.addElement(arPhone);
        }
        headerTable.addCell(arCell);

        document.add(headerTable);

        // ─── Divider line ─────────────────────────────────────────────────
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        divider.setSpacingBefore(6);
        divider.setSpacingAfter(10);
        PdfPCell divCell = new PdfPCell();
        divCell.setBorder(Rectangle.BOTTOM);
        divCell.setBorderColor(BRAND_BLUE);
        divCell.setBorderWidth(2f);
        divCell.setFixedHeight(2f);
        divider.addCell(divCell);
        document.add(divider);

        // ─── INVOICE INFO — right-aligned ─────────────────────────────────
        // 2-column table: [value on the left] | [label on the right]
        // This makes labels hug the right margin and values sit to their left.
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 1});
        infoTable.setSpacingAfter(12);

        // Store
        addInfoRow(infoTable, "\u0627\u0633\u0645 \u0627\u0644\u0645\u062D\u0644:", invoice.getStoreName(), labelFont, valueFont);
        // Date
        addInfoRow(infoTable, "\u0627\u0644\u062A\u0627\u0631\u064A\u062E:", DateUtils.formatForDisplay(invoice.getInvoiceDate()), labelFont, valueFont);
        // Invoice #
        addInfoRow(infoTable, "\u0631\u0642\u0645 \u0627\u0644\u0641\u0627\u062A\u0648\u0631\u0629:", String.valueOf(invoice.getId()), labelFont, valueFont);

        document.add(infoTable);

        // ─── ITEMS TABLE — RTL visual order ───────────────────────────────
        // Visual (right to left): البند | الكمية | السعر | المجموع
        // In code (LTR table):   المجموع | السعر | الكمية | البند
        // i.e. we add columns in REVERSE visual order.
        PdfPTable itemsTable = new PdfPTable(4);
        itemsTable.setWidthPercentage(100);
        // Code order widths: Subtotal(2) | Price(2) | Qty(1.5) | Item(4)
        itemsTable.setWidths(new float[]{2, 2, 1.5f, 4});

        // Header row (code order = left to right = visual right to left)
        String[] hdrLabels = {
                "\u0627\u0644\u0645\u062C\u0645\u0648\u0639",  // المجموع
                "\u0627\u0644\u0633\u0639\u0631",              // السعر
                "\u0627\u0644\u0643\u0645\u064A\u0629",        // الكمية
                "\u0627\u0644\u0628\u0646\u062F"               // البند
        };
        for (String h : hdrLabels) {
            itemsTable.addCell(rtlCell(new Phrase(h, tblHdrFont),
                    Element.ALIGN_CENTER, BRAND_BLUE, 8));
        }

        // Data rows — same reversed order
        List<InvoiceItem> items = invoice.getItems();
        if (items != null) {
            boolean alt = false;
            for (InvoiceItem item : items) {
                BaseColor bg = alt ? ALT_ROW_BG : BaseColor.WHITE;

                // Subtotal (leftmost column = visual rightmost... no, leftmost in code = leftmost on page)
                // Wait — we need: visual right side = البند. So البند must be the LAST column in code.
                // Code col 0 = page left = المجموع (visual leftmost for RTL = last thing read)
                // Code col 3 = page right = البند (visual rightmost = first thing read in RTL)
                itemsTable.addCell(rtlCell(new Phrase(NumberUtils.formatCurrency(item.getSubtotal()), cellFont),
                        Element.ALIGN_CENTER, bg, 6));
                itemsTable.addCell(rtlCell(new Phrase(NumberUtils.formatCurrency(item.getUnitPrice()), cellFont),
                        Element.ALIGN_CENTER, bg, 6));
                itemsTable.addCell(rtlCell(new Phrase(String.valueOf(item.getQuantity()), cellFont),
                        Element.ALIGN_CENTER, bg, 6));
                itemsTable.addCell(rtlCell(new Phrase(item.getItemName(), cellFont),
                        Element.ALIGN_RIGHT, bg, 6));

                alt = !alt;
            }
        }
        document.add(itemsTable);
        document.add(new Paragraph(" "));

        // ─── FOOTER TOTALS — right-aligned block ─────────────────────────
        // 2-column table anchored to the right side of the page.
        // Code col 0 = left (value), col 1 = right (label).
        PdfPTable footerTable = new PdfPTable(2);
        footerTable.setWidthPercentage(50);
        footerTable.setHorizontalAlignment(Element.ALIGN_RIGHT);

        addFooterRow(footerTable, "\u0627\u0644\u0625\u062C\u0645\u0627\u0644\u064A:",
                NumberUtils.formatCurrency(invoice.getTotalAmount()), totalFont);
        addFooterRow(footerTable, "\u0627\u0644\u0645\u062F\u0641\u0648\u0639:",
                NumberUtils.formatCurrency(invoice.getPaidAmount()), totalFont);

        Font remainFont = invoice.getRemaining() > 0 ? redFont : totalFont;
        addFooterRow(footerTable, "\u0627\u0644\u0645\u062A\u0628\u0642\u064A:",
                NumberUtils.formatCurrency(invoice.getRemaining()), remainFont);

        document.add(footerTable);

        document.close();
        return pdfFile;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  REPORT PDF
    // ═══════════════════════════════════════════════════════════════════════

    public static File generateReportPdf(Context context, String reportTitle,
                                          String[] headers, List<List<String>> rows,
                                          String[] footerSummary) throws Exception {
        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();
        File pdfFile = new File(exportDir, "report_" + System.currentTimeMillis() + ".pdf");

        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
        document.open();

        BaseFont regular = getCairoRegular(context);
        BaseFont bold = getCairoBold(context);

        Font titleFontPdf     = new Font(bold, 18, Font.BOLD, BRAND_BLUE);
        Font tblHdrFontPdf    = new Font(bold, 11, Font.BOLD, BaseColor.WHITE);
        Font cellFontPdf      = new Font(regular, 10, Font.NORMAL);
        Font footerFontPdf    = new Font(bold, 12, Font.BOLD);

        // Title
        Paragraph title = new Paragraph(reportTitle, titleFontPdf);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(12);
        document.add(title);

        // Reverse headers and row data for RTL visual order
        int cols = headers.length;
        String[] reversedHeaders = new String[cols];
        for (int i = 0; i < cols; i++) {
            reversedHeaders[i] = headers[cols - 1 - i];
        }

        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100);

        // Header row (reversed)
        for (String h : reversedHeaders) {
            table.addCell(rtlCell(new Phrase(h, tblHdrFontPdf),
                    Element.ALIGN_CENTER, BRAND_BLUE, 8));
        }

        // Data rows (reversed)
        boolean alt = false;
        for (List<String> row : rows) {
            BaseColor bg = alt ? ALT_ROW_BG : BaseColor.WHITE;
            for (int i = cols - 1; i >= 0; i--) {
                String cellVal = i < row.size() ? row.get(i) : "";
                table.addCell(rtlCell(new Phrase(cellVal != null ? cellVal : "", cellFontPdf),
                        Element.ALIGN_CENTER, bg, 6));
            }
            alt = !alt;
        }
        document.add(table);
        document.add(new Paragraph(" "));

        // Footer summary — right-aligned RTL lines
        if (footerSummary != null) {
            for (String line : footerSummary) {
                if (line != null && !line.isEmpty()) {
                    PdfPTable footerLine = new PdfPTable(1);
                    footerLine.setWidthPercentage(100);
                    PdfPCell fc = new PdfPCell(new Phrase(line, footerFontPdf));
                    fc.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                    fc.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    fc.setBorder(Rectangle.NO_BORDER);
                    fc.setPadding(4);
                    footerLine.addCell(fc);
                    document.add(footerLine);
                }
            }
        }

        document.close();
        return pdfFile;
    }

    // ─── Private helpers ──────────────────────────────────────────────────

    /**
     * Info row: code col 0 = value (left side), code col 1 = label (right side).
     * The label hugs the right margin; the value sits to its left.
     */
    private static void addInfoRow(PdfPTable table, String label, String value,
                                    Font labelFont, Font valueFont) {
        // Col 0 — value (page left, visually after the label when reading RTL)
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        valueCell.setPadding(4);
        table.addCell(valueCell);

        // Col 1 — label (page right, read first in RTL)
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPadding(4);
        table.addCell(labelCell);
    }

    /**
     * Footer total row: code col 0 = value (left), code col 1 = label (right).
     */
    private static void addFooterRow(PdfPTable table, String label, String value, Font font) {
        // Col 0 — value (left side of the sub-table)
        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        valueCell.setPadding(4);
        table.addCell(valueCell);

        // Col 1 — label (right side of the sub-table)
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPadding(4);
        table.addCell(labelCell);
    }
}
