package com.almajd.accounting.util;

import android.content.Context;
import com.almajd.accounting.R;
import com.almajd.accounting.model.Customer;
import com.almajd.accounting.model.Invoice;
import com.almajd.accounting.model.InvoiceItem;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
        addBilingualHeader(document, settings, titleBold, subtitleFont);

        // ─── INVOICE INFO — right-aligned block ─────────────────────────────
        // Compact table hugging the right margin.
        // 2-column table: [value on the left] | [label on the right]
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(42);
        infoTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        infoTable.setWidths(new float[]{1.4f, 1f});
        infoTable.setSpacingAfter(12);

        // Store
        addInfoRow(infoTable, "\u0627\u0633\u0645 \u0627\u0644\u0645\u062D\u0644:", invoice.getStoreName(), labelFont, valueFont);
        // Date
        addInfoRow(infoTable, "\u0627\u0644\u062A\u0627\u0631\u064A\u062E:", DateUtils.formatForDisplay(invoice.getInvoiceDate()), labelFont, valueFont);
        // Invoice #
        addInfoRow(infoTable, "\u0631\u0642\u0645 \u0627\u0644\u0641\u0627\u062A\u0648\u0631\u0629:", String.valueOf(invoice.getId()), labelFont, valueFont);

        document.add(infoTable);

        // ─── ITEMS TABLE — RTL visual order ───────────────────────────────
        // Visual (right to left): الصنف | الكمية | سعر الواحدة | المجموع
        // In code (LTR table):   المجموع | سعر الواحدة | الكمية | الصنف
        // i.e. we add columns in REVERSE visual order.
        PdfPTable itemsTable = new PdfPTable(4);
        itemsTable.setWidthPercentage(100);
        // Code order widths: Subtotal(2) | Price(2) | Qty(1.5) | Item(4)
        itemsTable.setWidths(new float[]{2, 2, 1.5f, 4});

        // Header row (code order = left to right = visual right to left)
        String[] hdrLabels = {
                "\u0627\u0644\u0645\u062C\u0645\u0648\u0639",  // المجموع
                "\u0633\u0639\u0631 \u0627\u0644\u0648\u0627\u062D\u062F\u0629", // سعر الواحدة
                "\u0627\u0644\u0643\u0645\u064A\u0629",        // الكمية
                "\u0627\u0644\u0635\u0646\u0641"               // الصنف
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
                // Wait — we need: visual right side = الصنف. So الصنف must be the LAST column in code.
                // Code col 0 = page left = المجموع (visual leftmost for RTL = last thing read)
                // Code col 3 = page right = الصنف (visual rightmost = first thing read in RTL)
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

        // The invoice was (partly) settled from the customer's on-account credit:
        // show how much, so the printed paid/remaining figures are explainable.
        if (invoice.getAllocatedFromCredit() > 0) {
            addFooterRow(footerTable,
                    "\u0645\u0646 \u0631\u0635\u064A\u062F \u0627\u0644\u0632\u0628\u0648\u0646:",
                    NumberUtils.formatCurrency(invoice.getAllocatedFromCredit()), totalFont);
        }

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

    // ═══════════════════════════════════════════════════════════════════════
    //  CUSTOMER ACCOUNT STATEMENT PDF
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Generates a customer account statement (كشف حساب): raw invoices and
     * payments in chronological order with a running balance, an opening balance
     * row for a date range and a closing balance.
     * <p>
     * The data is derived read-time by {@link StatementBuilder}; nothing is stored.
     * The table header repeats on every page and a "صفحة N من M" footer is drawn on
     * each page. Call from a background thread (it reads assets and writes a file).
     *
     * @param storeName  the customer's store name (used for the header and file name)
     * @param customer   extra customer details (owner/phone/location); may be null
     * @param statement  the built statement
     */
    public static File generateCustomerStatementPdf(Context context, String storeName,
                                                    Customer customer,
                                                    StatementBuilder.Statement statement) throws Exception {
        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();

        String namePart = StatementBuilder.sanitizeFileName(storeName);
        if (namePart.isEmpty()) {
            namePart = "customer";
        }
        File pdfFile = new File(exportDir,
                "statement-" + namePart + "-" + DateUtils.todayAsDbFormat() + ".pdf");

        // Landscape makes room for the 6-column movements table; the taller bottom
        // margin leaves space for the page-number footer.
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 46);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(pdfFile));

        BaseFont regular = getCairoRegular(context);
        BaseFont bold = getCairoBold(context);

        Font titleBold    = new Font(bold, 16, Font.BOLD, BRAND_BLUE);
        Font subtitleFont = new Font(regular, 11, Font.NORMAL);
        Font titleFont    = new Font(bold, 18, Font.BOLD, BRAND_BLUE);
        Font tblHdrFont   = new Font(bold, 11, Font.BOLD, BaseColor.WHITE);
        Font cellFont     = new Font(regular, 10, Font.NORMAL);
        Font totalFont    = new Font(bold, 12, Font.BOLD);
        Font labelFont    = new Font(regular, 11, Font.NORMAL, new BaseColor(117, 117, 117));
        Font valueFont    = new Font(bold, 11, Font.BOLD);
        Font emptyFont    = new Font(regular, 12, Font.NORMAL, new BaseColor(117, 117, 117));

        writer.setPageEvent(new PageNumberEvent(regular,
                context.getString(R.string.statement_page_label),
                context.getString(R.string.statement_page_of)));

        document.open();

        AppSettings settings = new AppSettings(context);
        addBilingualHeader(document, settings, titleBold, subtitleFont);

        // ─── Title ────────────────────────────────────────────────────────
        Paragraph title = new Paragraph(context.getString(R.string.account_statement), titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10);
        document.add(title);

        // ─── Customer / period info — right-aligned block ─────────────────
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(58);
        infoTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        infoTable.setWidths(new float[]{1.6f, 1f});
        infoTable.setSpacingAfter(12);

        addInfoRow(infoTable, context.getString(R.string.store_name) + ":",
                safe(storeName), labelFont, valueFont);
        if (customer != null) {
            addInfoRow(infoTable, context.getString(R.string.owner_name) + ":",
                    safe(customer.getOwnerName()), labelFont, valueFont);
            addInfoRow(infoTable, context.getString(R.string.phone) + ":",
                    safe(customer.getPhone()), labelFont, valueFont);
            addInfoRow(infoTable, context.getString(R.string.location) + ":",
                    safe(customer.getLocation()), labelFont, valueFont);
        }
        addInfoRow(infoTable, context.getString(R.string.statement_period_label) + ":",
                formatPeriod(context, statement.from, statement.to), labelFont, valueFont);
        addInfoRow(infoTable, context.getString(R.string.statement_print_date) + ":",
                DateUtils.formatForDisplay(DateUtils.todayAsDbFormat()), labelFont, valueFont);
        document.add(infoTable);

        // ─── Movements (or the empty hint) ────────────────────────────────
        if (statement.rows.isEmpty()) {
            Paragraph empty = new Paragraph(context.getString(R.string.statement_no_movements), emptyFont);
            empty.setAlignment(Element.ALIGN_CENTER);
            empty.setSpacingBefore(24);
            empty.setSpacingAfter(24);
            document.add(empty);
        } else {
            document.add(buildStatementTable(context, statement, tblHdrFont, cellFont));
            document.add(new Paragraph(" "));
        }

        // ─── Totals / closing balance — right-aligned block ───────────────
        PdfPTable footerTable = new PdfPTable(2);
        footerTable.setWidthPercentage(58);
        footerTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        addFooterRow(footerTable, context.getString(R.string.statement_total_debit),
                NumberUtils.formatCurrency(statement.totalDebit), totalFont);
        addFooterRow(footerTable, context.getString(R.string.statement_total_credit),
                NumberUtils.formatCurrency(statement.totalCredit), totalFont);
        addFooterRow(footerTable, context.getString(R.string.statement_closing_balance),
                BalanceUtils.format(context, statement.closingBalance), totalFont);
        document.add(footerTable);

        document.close();
        return pdfFile;
    }

    /**
     * Builds the 6-column movements table in RTL visual order (date rightmost),
     * with a repeating header row. Logical columns:
     * date | type | reference | debit | credit | running balance.
     */
    private static PdfPTable buildStatementTable(Context context, StatementBuilder.Statement statement,
                                                 Font tblHdrFont, Font cellFont) throws DocumentException {
        String[] headers = {
                context.getString(R.string.date),
                context.getString(R.string.statement_col_type),
                context.getString(R.string.statement_col_reference),
                context.getString(R.string.statement_col_debit),
                context.getString(R.string.statement_col_credit),
                context.getString(R.string.statement_col_balance)
        };

        List<List<String>> rows = new ArrayList<>();
        if (statement.openingRowShown) {
            rows.add(Arrays.asList("", "",
                    context.getString(R.string.statement_opening_balance), "", "",
                    BalanceUtils.format(context, statement.openingBalance)));
        }
        for (StatementBuilder.Row row : statement.rows) {
            String date = DateUtils.formatForDisplay(row.date);
            String type = context.getString(row.kind == StatementBuilder.Kind.INVOICE
                    ? R.string.statement_type_invoice : R.string.statement_type_payment);

            String reference;
            if (row.kind == StatementBuilder.Kind.INVOICE) {
                reference = context.getString(R.string.statement_invoice_ref, row.id);
            } else {
                String link = row.linkedInvoiceId != null
                        ? context.getString(R.string.statement_payment_on_invoice, row.linkedInvoiceId)
                        : context.getString(R.string.statement_payment_on_account);
                String note = row.note != null ? row.note.trim() : "";
                reference = note.isEmpty() ? link
                        : context.getString(R.string.statement_reference_with_note, note, link);
            }

            String debit = row.kind == StatementBuilder.Kind.INVOICE
                    ? NumberUtils.formatCurrency(row.debit) : "";
            String credit = row.kind == StatementBuilder.Kind.PAYMENT
                    ? NumberUtils.formatCurrency(row.credit) : "";

            rows.add(Arrays.asList(date, type, reference, debit, credit,
                    BalanceUtils.format(context, row.runningBalance)));
        }

        int cols = headers.length;
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2f, 1.4f, 4.5f, 2f, 2f, 3.4f});
        // Repeat the header row at the top of every page.
        table.setHeaderRows(1);

        for (int i = cols - 1; i >= 0; i--) {
            table.addCell(rtlCell(new Phrase(headers[i], tblHdrFont),
                    Element.ALIGN_CENTER, BRAND_BLUE, 8));
        }

        boolean alt = false;
        for (List<String> row : rows) {
            BaseColor bg = alt ? ALT_ROW_BG : BaseColor.WHITE;
            for (int i = cols - 1; i >= 0; i--) {
                table.addCell(rtlCell(new Phrase(row.get(i), cellFont),
                        Element.ALIGN_CENTER, bg, 6));
            }
            alt = !alt;
        }
        return table;
    }

    /** "كل الفترات" / "من X إلى Y" / one-sided variants, using display dates. */
    private static String formatPeriod(Context context, String from, String to) {
        if (from != null && to != null) {
            return context.getString(R.string.statement_period_from_to,
                    DateUtils.formatForDisplay(from), DateUtils.formatForDisplay(to));
        }
        if (from != null) {
            return context.getString(R.string.statement_period_from, DateUtils.formatForDisplay(from));
        }
        if (to != null) {
            return context.getString(R.string.statement_period_until, DateUtils.formatForDisplay(to));
        }
        return context.getString(R.string.statement_period_all);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    /**
     * Draws a centred "صفحة N من M" footer on every page. The total page count is
     * only known once the document closes, so it lives in a {@link PdfTemplate}
     * filled in {@link #onCloseDocument}.
     * <p>
     * The four cells are added left-to-right as [total][من][N][صفحة] so that the
     * Arabic reads right-to-left as "صفحة N من M".
     */
    private static final class PageNumberEvent extends PdfPageEventHelper {

        private static final float TABLE_WIDTH = 260f;

        private final BaseFont baseFont;
        private final String labelPage;
        private final String labelOf;
        private Font font;
        private PdfTemplate totalTemplate;

        PageNumberEvent(BaseFont baseFont, String labelPage, String labelOf) {
            this.baseFont = baseFont;
            this.labelPage = labelPage;
            this.labelOf = labelOf;
            this.font = new Font(baseFont, 9, Font.NORMAL, new BaseColor(117, 117, 117));
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalTemplate = writer.getDirectContent().createTemplate(40, 16);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfPTable footer = new PdfPTable(4);
            footer.setTotalWidth(TABLE_WIDTH);
            footer.setLockedWidth(true);

            // Left to right: [total template] [من] [N] [صفحة]
            PdfPCell totalCell;
            try {
                footer.setWidths(new float[]{2f, 1.2f, 1f, 2f});
                totalCell = new PdfPCell(Image.getInstance(totalTemplate));
            } catch (DocumentException e) {
                return;
            }
            totalCell.setBorder(Rectangle.NO_BORDER);
            totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            footer.addCell(totalCell);

            footer.addCell(makeCell(new Phrase(labelOf, font),
                    Element.ALIGN_CENTER, null, 2, true, Rectangle.NO_BORDER));
            footer.addCell(makeCell(new Phrase(String.valueOf(writer.getPageNumber()), font),
                    Element.ALIGN_CENTER, null, 2, false, Rectangle.NO_BORDER));
            footer.addCell(makeCell(new Phrase(labelPage, font),
                    Element.ALIGN_CENTER, null, 2, true, Rectangle.NO_BORDER));

            Rectangle page = document.getPageSize();
            float x = (page.getWidth() - TABLE_WIDTH) / 2f;
            footer.writeSelectedRows(0, -1, x, 28, writer.getDirectContent());
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalTemplate.beginText();
            totalTemplate.setFontAndSize(baseFont, font.getSize());
            totalTemplate.setTextMatrix(0, 0);
            totalTemplate.showText(String.valueOf(writer.getPageNumber()));
            totalTemplate.endText();
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────

    /**
     * Adds the shared bilingual company header (English | blue line | Arabic)
     * followed by the blue horizontal divider. Shared by the invoice and the
     * customer statement PDFs so both look identical at the top.
     */
    private static void addBilingualHeader(Document document, AppSettings settings,
                                           Font titleBold, Font subtitleFont) throws DocumentException {
        String phone = settings.getPhone();

        // ─── HEADER ───────────────────────────────────────────────────────
        // Simple 3-column table: English | blue line | Arabic
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{1f, 0.02f, 1f});
        headerTable.setSpacingAfter(4);

        // ── Left column: English ──
        PdfPCell enCell = new PdfPCell();
        enCell.setBorder(Rectangle.NO_BORDER);
        enCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        enCell.setPadding(6);

        Paragraph pEnName = new Paragraph(settings.getCompanyNameEn(), titleBold);
        pEnName.setAlignment(Element.ALIGN_LEFT);
        enCell.addElement(pEnName);

        Paragraph pEnOwner = new Paragraph(settings.getOwnerNameEn(), subtitleFont);
        pEnOwner.setAlignment(Element.ALIGN_LEFT);
        enCell.addElement(pEnOwner);

        if (phone != null && !phone.isEmpty()) {
            Paragraph pEnPhone = new Paragraph(phone, subtitleFont);
            pEnPhone.setAlignment(Element.ALIGN_LEFT);
            enCell.addElement(pEnPhone);
        }
        headerTable.addCell(enCell);

        // ── Middle column: blue vertical line ──
        PdfPCell lineCell = new PdfPCell();
        lineCell.setBorder(Rectangle.NO_BORDER);
        lineCell.setCellEvent(new PdfPCellEvent() {
            @Override
            public void cellLayout(PdfPCell cell, Rectangle rect, PdfContentByte[] canvases) {
                PdfContentByte cb = canvases[PdfPTable.LINECANVAS];
                cb.setColorStroke(BRAND_BLUE);
                cb.setLineWidth(1.5f);
                float x = (rect.getLeft() + rect.getRight()) / 2f;
                cb.moveTo(x, rect.getBottom() + 4);
                cb.lineTo(x, rect.getTop() - 4);
                cb.stroke();
            }
        });
        headerTable.addCell(lineCell);

        // ── Right column: Arabic ──
        PdfPCell arCell = new PdfPCell();
        arCell.setBorder(Rectangle.NO_BORDER);
        arCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        arCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        arCell.setPadding(6);

        Paragraph pArName = new Paragraph(settings.getCompanyNameAr(), titleBold);
        pArName.setAlignment(Element.ALIGN_RIGHT);
        arCell.addElement(pArName);

        Paragraph pArOwner = new Paragraph(settings.getOwnerNameAr(), subtitleFont);
        pArOwner.setAlignment(Element.ALIGN_RIGHT);
        arCell.addElement(pArOwner);

        if (phone != null && !phone.isEmpty()) {
            Paragraph pArPhone = new Paragraph(phone, subtitleFont);
            pArPhone.setAlignment(Element.ALIGN_RIGHT);
            arCell.addElement(pArPhone);
        }
        headerTable.addCell(arCell);

        document.add(headerTable);

        // ─── Horizontal divider ──────────────────────────────────────────
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        divider.setSpacingBefore(4);
        divider.setSpacingAfter(10);
        PdfPCell divCell = new PdfPCell();
        divCell.setBorder(Rectangle.BOTTOM);
        divCell.setBorderColor(BRAND_BLUE);
        divCell.setBorderWidth(2f);
        divCell.setFixedHeight(2f);
        divider.addCell(divCell);
        document.add(divider);
    }

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
