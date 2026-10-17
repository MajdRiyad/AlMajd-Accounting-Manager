package com.almajd.accounting.util;

import android.app.DatePickerDialog;
import android.content.Context;
import android.widget.EditText;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateUtils {

    public static final String DB_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DISPLAY_DATE_FORMAT = "dd/MM/yyyy";
    /** Human-readable timestamp used for "last backup" and the restore dialog. */
    public static final String HUMAN_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm";

    /**
     * Sortable sentinel bounds for open-ended date-range filters. Because dates
     * are stored as fixed-width {@code yyyy-MM-dd} strings, a one-sided filter
     * can reuse the same {@code >= ? AND <= ?} query by substituting these.
     */
    public static final String DB_DATE_MIN = "0000-00-00";
    public static final String DB_DATE_MAX = "9999-99-99";

    /** @return {@code from}, or {@link #DB_DATE_MIN} when it is null/blank. */
    public static String rangeLower(String from) {
        return (from == null || from.trim().isEmpty()) ? DB_DATE_MIN : from.trim();
    }

    /** @return {@code to}, or {@link #DB_DATE_MAX} when it is null/blank. */
    public static String rangeUpper(String to) {
        return (to == null || to.trim().isEmpty()) ? DB_DATE_MAX : to.trim();
    }

    public static String todayAsDbFormat() {
        return new SimpleDateFormat(DB_DATE_FORMAT, Locale.US).format(new Date());
    }

    public static String humanTimestamp() {
        return humanTimestamp(System.currentTimeMillis());
    }

    public static String humanTimestamp(long millis) {
        return new SimpleDateFormat(HUMAN_TIMESTAMP_FORMAT, Locale.US).format(new Date(millis));
    }

    public static String formatForDisplay(String dbDate) {
        try {
            Date date = new SimpleDateFormat(DB_DATE_FORMAT, Locale.US).parse(dbDate);
            return new SimpleDateFormat(DISPLAY_DATE_FORMAT, Locale.US).format(date);
        } catch (Exception e) {
            return dbDate;
        }
    }

    public static String formatForDb(int year, int month, int day) {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
    }

    public static void showDatePicker(Context context, EditText target) {
        showDatePickerForView(context, target);
    }

    public static void showDatePicker(Context context, TextView target) {
        showDatePickerForView(context, target);
    }

    private static void showDatePickerForView(Context context, TextView target) {
        Calendar cal = Calendar.getInstance();
        String current = target.getText().toString();
        if (!current.isEmpty()) {
            try {
                Date d = new SimpleDateFormat(DB_DATE_FORMAT, Locale.US).parse(current);
                cal.setTime(d);
            } catch (Exception ignored) {
            }
        }
        new DatePickerDialog(context, (view, year, month, day) -> {
            target.setText(formatForDb(year, month, day));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }
}
