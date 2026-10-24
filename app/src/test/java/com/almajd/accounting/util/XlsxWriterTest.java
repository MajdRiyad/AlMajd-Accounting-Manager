package com.almajd.accounting.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Pure-JVM coverage of the .xlsx generation (no Android APIs involved).
 */
public class XlsxWriterTest {

    private static Map<String, String> writeAndRead(String[] headers, List<List<String>> rows)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XlsxWriter.write(baos, "Sales", headers, rows);

        Map<String, String> parts = new HashMap<>();
        ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(baos.toByteArray()));
        byte[] buffer = new byte[4096];
        ZipEntry entry;
        while ((entry = zin.getNextEntry()) != null) {
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            int read;
            while ((read = zin.read(buffer)) != -1) {
                content.write(buffer, 0, read);
            }
            parts.put(entry.getName(), new String(content.toByteArray(), StandardCharsets.UTF_8));
            zin.closeEntry();
        }
        zin.close();
        return parts;
    }

    private static List<List<String>> rows(String[]... rows) {
        List<List<String>> list = new ArrayList<>();
        for (String[] r : rows) {
            list.add(new ArrayList<>(Arrays.asList(r)));
        }
        return list;
    }

    @Test
    public void containsAllMandatoryPackageParts() throws IOException {
        Map<String, String> parts = writeAndRead(
                new String[]{"a"}, rows(new String[]{"1"}));
        assertTrue(parts.containsKey("[Content_Types].xml"));
        assertTrue(parts.containsKey("_rels/.rels"));
        assertTrue(parts.containsKey("xl/workbook.xml"));
        assertTrue(parts.containsKey("xl/_rels/workbook.xml.rels"));
        assertTrue(parts.containsKey("xl/styles.xml"));
        assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"));
    }

    @Test
    public void sheetIsRightToLeftAndHeaderIsBold() throws IOException {
        String sheet = writeAndRead(new String[]{"store"}, rows(new String[]{"x"}))
                .get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("rightToLeft=\"1\""));
        // Header cell (A1) uses the bold style index 1.
        assertTrue(sheet.contains("<c r=\"A1\" s=\"1\" t=\"inlineStr\">"));
    }

    @Test
    public void amountCellsAreWrittenAsRealNumbers() throws IOException {
        String sheet = writeAndRead(
                new String[]{"store", "date", "total", "remaining"},
                rows(new String[]{"Store A", "17/10/2026", "1,000.00 \u20AA", "200.00 \u20AA"}))
                .get("xl/worksheets/sheet1.xml");

        // Amounts become numeric cells (summable)...
        assertTrue(sheet.contains("<c r=\"C2\"><v>1000.00</v></c>"));
        assertTrue(sheet.contains("<c r=\"D2\"><v>200.00</v></c>"));
        // ...the store name and the date stay inline strings.
        assertTrue(sheet.contains("<c r=\"A2\" t=\"inlineStr\">"));
        assertTrue(sheet.contains("<c r=\"B2\" t=\"inlineStr\"><is><t xml:space=\"preserve\">17/10/2026</t></is></c>"));
    }

    @Test
    public void arabicTextRoundTripsAndIsEscaped() throws IOException {
        String sheet = writeAndRead(
                new String[]{"store"},
                rows(new String[]{"\u0645\u062D\u0644 & \u0634\u0631\u0643\u0627\u0621 <A> \"B\""}))
                .get("xl/worksheets/sheet1.xml");

        assertTrue(sheet.contains("&amp;"));
        assertTrue(sheet.contains("&lt;A&gt;"));
        assertTrue(sheet.contains("\u0645\u062D\u0644"));
    }

    @Test
    public void sheetNameIsEscapedInWorkbookPart() throws IOException {
        String workbook = writeAndRead(new String[]{"a"}, rows(new String[]{"1"}))
                .get("xl/workbook.xml");
        assertTrue(workbook.contains("name=\"Sales\""));
        assertEquals("A&amp;B", XlsxWriter.escapeXmlAttr("A&B"));
    }

    @Test
    public void everyDataRowGetsARowElement() throws IOException {
        String sheet = writeAndRead(
                new String[]{"a"},
                rows(new String[]{"1"}, new String[]{"2"}, new String[]{"3"}))
                .get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("<row r=\"1\">"));
        assertTrue(sheet.contains("<row r=\"2\">"));
        assertTrue(sheet.contains("<row r=\"3\">"));
        assertTrue(sheet.contains("<row r=\"4\">"));
    }

    @Test
    public void columnNamesAdvancePastZ() {
        assertEquals("A", XlsxWriter.columnName(0));
        assertEquals("Z", XlsxWriter.columnName(25));
        assertEquals("AA", XlsxWriter.columnName(26));
        assertEquals("AB", XlsxWriter.columnName(27));
        assertEquals("AZ", XlsxWriter.columnName(51));
        assertEquals("BA", XlsxWriter.columnName(52));
    }

    @Test
    public void numberDetectionAcceptsAmountsAndRejectsText() {
        assertEquals("1000.00", XlsxWriter.numberOrNull("1,000.00 \u20AA"));
        assertEquals("1500", XlsxWriter.numberOrNull("1500"));
        assertEquals("-5", XlsxWriter.numberOrNull("-5"));
        assertEquals("123.5", XlsxWriter.numberOrNull("\u0661\u0662\u0663\u066B\u0665"));
        assertNull(XlsxWriter.numberOrNull("17/10/2026"));
        assertNull(XlsxWriter.numberOrNull("Store A"));
        assertNull(XlsxWriter.numberOrNull(""));
        assertNull(XlsxWriter.numberOrNull("-"));
        assertNull(XlsxWriter.numberOrNull("1.2.3"));
    }

    @Test
    public void sheetNameIsSanitised() {
        assertEquals("Sales", XlsxWriter.safeSheetName("Sales"));
        assertEquals("a_b", XlsxWriter.safeSheetName("a/b"));
        assertEquals("Sheet1", XlsxWriter.safeSheetName("   "));
        assertEquals(31, XlsxWriter.safeSheetName(
                "0123456789012345678901234567890123456789").length());
    }
}
