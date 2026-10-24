package com.almajd.accounting.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a minimal but valid Office Open XML spreadsheet ({@code .xlsx}) using
 * nothing but {@code java.util.zip}. The produced package contains the six
 * mandatory parts:
 * <ul>
 *   <li>{@code [Content_Types].xml}</li>
 *   <li>{@code _rels/.rels}</li>
 *   <li>{@code xl/workbook.xml}</li>
 *   <li>{@code xl/_rels/workbook.xml.rels}</li>
 *   <li>{@code xl/styles.xml}</li>
 *   <li>{@code xl/worksheets/sheet1.xml}</li>
 * </ul>
 * Values that look like amounts are written as real numeric cells (so they can
 * be summed), everything else as inline strings. The sheet view is right-to-left
 * (Arabic friendly) and the header row is bold.
 * <p>
 * This class deliberately has no Android dependency so the XML/zip generation
 * can be verified by plain JVM unit tests.
 */
public final class XlsxWriter {

    private static final String XML_DECL =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    /** Cell style index (cellXfs) used for the bold header row. */
    static final int HEADER_STYLE = 1;

    private static final String CONTENT_TYPES = XML_DECL
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS = XML_DECL
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WORKBOOK_RELS = XML_DECL
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    /** Two cell formats: index 0 = default, index 1 = bold (used by the header). */
    private static final String STYLES = XML_DECL
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"2\">"
            + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "</fonts>"
            + "<fills count=\"2\">"
            + "<fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill>"
            + "</fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"2\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";

    private XlsxWriter() {
    }

    /**
     * Writes the workbook (a single sheet) to {@code out}. The caller owns the
     * stream: it is flushed but never closed by this method.
     */
    public static void write(OutputStream out, String sheetName,
                             String[] headers, List<List<String>> rows) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(new NonClosingOutputStream(out));
        try {
            put(zip, "[Content_Types].xml", CONTENT_TYPES);
            put(zip, "_rels/.rels", ROOT_RELS);
            put(zip, "xl/workbook.xml", workbookXml(sheetName));
            put(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            put(zip, "xl/styles.xml", STYLES);
            put(zip, "xl/worksheets/sheet1.xml", sheetXml(headers, rows));
        } finally {
            // close() writes the central directory and flushes; it must not close
            // the caller's stream (NonClosingOutputStream absorbs that).
            zip.close();
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        zip.write(bytes, 0, bytes.length);
        zip.closeEntry();
    }

    static String workbookXml(String sheetName) {
        return XML_DECL
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + escapeXmlAttr(safeSheetName(sheetName))
                + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    /** Builds {@code xl/worksheets/sheet1.xml}. Pure so it can be unit tested. */
    static String sheetXml(String[] headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append(XML_DECL);
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        // Right-to-left sheet view so Arabic columns read naturally.
        sb.append("<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews>");
        sb.append("<sheetFormatPr defaultRowHeight=\"15\"/>");
        sb.append("<sheetData>");

        int rowNumber = 1;
        if (headers != null && headers.length > 0) {
            appendRow(sb, rowNumber, headers, true);
            rowNumber++;
        }
        if (rows != null) {
            for (List<String> row : rows) {
                if (row == null) {
                    appendRow(sb, rowNumber, new String[0], false);
                } else {
                    appendRow(sb, rowNumber, row.toArray(new String[0]), false);
                }
                rowNumber++;
            }
        }

        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, int rowNumber, String[] cells, boolean header) {
        sb.append("<row r=\"").append(rowNumber).append("\">");
        for (int i = 0; i < cells.length; i++) {
            String cell = cells[i];
            if (cell == null) {
                continue;
            }
            String ref = columnName(i) + rowNumber;
            if (!header) {
                String number = numberOrNull(cell);
                if (number != null) {
                    sb.append("<c r=\"").append(ref).append("\"><v>")
                            .append(number).append("</v></c>");
                    continue;
                }
            }
            sb.append("<c r=\"").append(ref).append('"');
            if (header) {
                sb.append(" s=\"").append(HEADER_STYLE).append('"');
            }
            sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escapeXmlText(cell))
                    .append("</t></is></c>");
        }
        sb.append("</row>");
    }

    /**
     * Translates a zero-based column index into an Excel column name
     * (0 -> A, 25 -> Z, 26 -> AA).
     */
    static String columnName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        }
        return sb.toString();
    }

    /**
     * Returns the plain ASCII numeric literal for a cell that represents a
     * number (tolerating the currency symbol and both Latin/Arabic grouping
     * separators), or {@code null} when the cell is not a number. This lets
     * amounts be written as real numeric cells while dates ("17/10/2026") and
     * text stay strings.
     */
    static String numberOrNull(String cell) {
        if (cell == null) return null;
        String t = NumberUtils.toAsciiDigits(cell)
                .replace("\u20AA", "")   // ₪
                .replace(",", "")
                .replace("\u066C", "")   // ٬
                .trim();
        if (t.isEmpty()) return null;

        boolean digit = false;
        boolean dot = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') {
                digit = true;
            } else if (c == '.' && !dot) {
                dot = true;
            } else if (c != '-' || i != 0) {
                return null;
            }
        }
        return digit ? t : null;
    }

    /** Excel sheet names are limited to 31 chars and may not contain {@code \ / : * ? [ ]}. */
    static String safeSheetName(String name) {
        String s = name == null ? "" : name.trim();
        s = s.replaceAll("[\\\\/:*?\\[\\]]", "_");
        if (s.isEmpty()) {
            s = "Sheet1";
        }
        if (s.length() > 31) {
            s = s.substring(0, 31);
        }
        return s;
    }

    static String escapeXmlText(String text) {
        return escape(text, false);
    }

    static String escapeXmlAttr(String text) {
        return escape(text, true);
    }

    private static String escape(String text, boolean attribute) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append(attribute ? "&quot;" : "\"");
                    break;
                case '\'':
                    sb.append(attribute ? "&apos;" : "'");
                    break;
                default:
                    // Drop characters that are illegal in XML 1.0 (keep tab/newline/CR).
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        break;
                    }
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    /** Wrapper that forwards writes but turns {@code close()} into a {@code flush()}. */
    private static final class NonClosingOutputStream extends FilterOutputStream {

        NonClosingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
