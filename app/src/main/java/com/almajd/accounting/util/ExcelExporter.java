package com.almajd.accounting.util;

import android.content.Context;
import java.io.*;
import java.util.List;

public class ExcelExporter {

    /**
     * Generate a real Excel workbook (.xlsx, Office Open XML) from tabular data.
     * <p>
     * Amounts are written as real numeric cells (so they can be summed in Excel,
     * Google Sheets and LibreOffice); other cells are written as text. The sheet
     * is right-to-left and the header row is bold. Everything is produced with
     * {@code java.util.zip} -- no external library.
     */
    public static File generateExcel(Context context, String sheetName,
                                      String[] headers, List<List<String>> rows) throws Exception {
        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();
        File file = new File(exportDir,
                sheetName.replaceAll("\\s+", "_") + "_" + System.currentTimeMillis() + ".xlsx");

        FileOutputStream out = new FileOutputStream(file);
        try {
            XlsxWriter.write(out, sheetName, headers, rows);
        } finally {
            out.close();
        }
        return file;
    }

    /**
     * Generate a CSV file from tabular data.
     */
    public static File generateCsv(Context context, String fileName,
                                     String[] headers, List<List<String>> rows) throws Exception {
        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();
        File file = new File(exportDir, fileName + "_" + System.currentTimeMillis() + ".csv");

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

        // Write UTF-8 BOM for Excel compatibility
        writer.write('\ufeff');

        // Header
        writer.write(joinCsv(headers));
        writer.newLine();

        // Data
        for (List<String> row : rows) {
            writer.write(joinCsv(row.toArray(new String[0])));
            writer.newLine();
        }

        writer.flush();
        writer.close();
        return file;
    }

    private static String joinCsv(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(",");
            String v = values[i] != null ? values[i] : "";
            // Escape if contains comma, quote, or newline
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                sb.append("\"").append(v.replace("\"", "\"\"")).append("\"");
            } else {
                sb.append(v);
            }
        }
        return sb.toString();
    }
}
