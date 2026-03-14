package com.almajd.accounting.util;

import android.content.Context;
import java.io.*;
import java.util.List;

public class ExcelExporter {

    /**
     * Generate an Excel-compatible HTML file (.xls) from tabular data.
     * This creates an HTML table that Excel can open natively.
     */
    public static File generateExcel(Context context, String sheetName,
                                      String[] headers, List<List<String>> rows) throws Exception {
        File exportDir = new File(context.getCacheDir(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();
        File file = new File(exportDir, sheetName.replaceAll("\\s+", "_") + "_" + System.currentTimeMillis() + ".xls");

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

        // Write HTML table that Excel can read
        writer.write("<html xmlns:o=\"urn:schemas-microsoft-com:office:office\" ");
        writer.write("xmlns:x=\"urn:schemas-microsoft-com:office:excel\">");
        writer.newLine();
        writer.write("<head><meta charset=\"UTF-8\">");
        writer.write("<style>");
        writer.write("table { border-collapse: collapse; width: 100%; }");
        writer.write("th { background-color: #4472C4; color: white; font-weight: bold; ");
        writer.write("padding: 10px; border: 1px solid #000; text-align: center; }");
        writer.write("td { padding: 8px; border: 1px solid #ccc; text-align: right; }");
        writer.write("tr:nth-child(even) { background-color: #f2f2f2; }");
        writer.write("</style></head>");
        writer.newLine();
        writer.write("<body><table>");
        writer.newLine();

        // Header row
        writer.write("<tr>");
        for (String header : headers) {
            writer.write("<th>" + escapeHtml(header) + "</th>");
        }
        writer.write("</tr>");
        writer.newLine();

        // Data rows
        for (List<String> row : rows) {
            writer.write("<tr>");
            for (String cell : row) {
                writer.write("<td>" + escapeHtml(cell != null ? cell : "") + "</td>");
            }
            writer.write("</tr>");
            writer.newLine();
        }

        writer.write("</table></body></html>");
        writer.flush();
        writer.close();

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

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
