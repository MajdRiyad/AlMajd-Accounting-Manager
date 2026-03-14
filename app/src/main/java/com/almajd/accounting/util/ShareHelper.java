package com.almajd.accounting.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

public class ShareHelper {

    /**
     * Share a file using the native Android share sheet.
     */
    public static void shareFile(Context context, File file, String mimeType) {
        try {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(Intent.createChooser(shareIntent,
                    file.getName()));
        } catch (Exception e) {
            Toast.makeText(context, "Error sharing: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Share a PDF file.
     */
    public static void sharePdf(Context context, File pdfFile) {
        shareFile(context, pdfFile, "application/pdf");
    }

    /**
     * Share an Excel file.
     */
    public static void shareExcel(Context context, File excelFile) {
        shareFile(context, excelFile, "application/vnd.ms-excel");
    }

    /**
     * Share a CSV file.
     */
    public static void shareCsv(Context context, File csvFile) {
        shareFile(context, csvFile, "text/csv");
    }
}
