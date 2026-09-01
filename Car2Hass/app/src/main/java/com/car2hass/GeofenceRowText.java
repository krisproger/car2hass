package com.car2hass;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Pure row-formatting helpers for the geofence list (no Android deps).
 * The last-visited logic is extracted from the removed GeofenceListActivity
 * so it can be unit-tested on a standard JDK.
 */
public final class GeofenceRowText {

    private GeofenceRowText() {
    }

    /**
     * Format a last-visited timestamp relative to today, e.g. "today 08:12",
     * "yesterday 23:40", or an absolute "dd.MM HH:mm" for older visits.
     * Returns the never marker when ms is &lt;= 0.
     *
     * @param never              localized "never" marker
     * @param todayTemplate      localized template with one %s time placeholder
     * @param yesterdayTemplate  localized template with one %s time placeholder
     * @param now                current time (injected for testability)
     */
    public static String formatLastVisited(long ms, String never,
                                           String todayTemplate, String yesterdayTemplate, Calendar now) {
        if (ms <= 0) {
            return never;
        }
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ms);
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = timeFmt.format(then.getTime());

        if (isSameDay(now, then)) {
            return String.format(todayTemplate, time);
        }
        Calendar day = (Calendar) now.clone();
        day.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(day, then)) {
            return String.format(yesterdayTemplate, time);
        }
        return new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(then.getTime());
    }

    /** Render the radius as an integer meter value, e.g. "150m". */
    public static String formatRadius(float radius) {
        return ((int) radius) + "m";
    }

    /** Assemble one list row: "name (150m) — last visited text". */
    public static String buildRow(String name, float radius, String lastVisited) {
        return name + " (" + formatRadius(radius) + ") — " + lastVisited;
    }

    private static boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}