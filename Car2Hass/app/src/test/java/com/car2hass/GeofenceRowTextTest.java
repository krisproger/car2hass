package com.car2hass;

import java.util.Calendar;

/**
 * Plain-Java unit tests for {@link GeofenceRowText} row-formatting helpers.
 * Pure string logic, no Android deps — runs on a standard JDK.
 */
public class GeofenceRowTextTest {

    public static void main(String[] args) throws Exception {
        testNeverVisited();
        testToday();
        testYesterday();
        testOlderAbsolute();
        testRadiusFormatting();
        testBuildRow();

        System.out.println("All GeofenceRowText tests passed.");
    }

    private static void testNeverVisited() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.AUGUST, 20, 12, 0, 0);
        String s = GeofenceRowText.formatLastVisited(0L, "never", "today %s", "yesterday %s", now);
        assertEquals("never", s, "never-visited marker");
    }

    private static void testToday() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.AUGUST, 20, 12, 0, 0);
        Calendar then = Calendar.getInstance();
        then.set(2026, Calendar.AUGUST, 20, 8, 12, 0);
        String s = GeofenceRowText.formatLastVisited(
                then.getTimeInMillis(), "never", "today %s", "yesterday %s", now);
        assertEquals("today 08:12", s, "today marker with HH:mm time");
    }

    private static void testYesterday() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.AUGUST, 20, 12, 0, 0);
        Calendar then = Calendar.getInstance();
        then.set(2026, Calendar.AUGUST, 19, 23, 40, 0);
        String s = GeofenceRowText.formatLastVisited(
                then.getTimeInMillis(), "never", "today %s", "yesterday %s", now);
        assertEquals("yesterday 23:40", s, "yesterday marker");
    }

    private static void testOlderAbsolute() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.AUGUST, 20, 12, 0, 0);
        Calendar then = Calendar.getInstance();
        then.set(2026, Calendar.AUGUST, 1, 10, 5, 0);
        String s = GeofenceRowText.formatLastVisited(
                then.getTimeInMillis(), "never", "today %s", "yesterday %s", now);
        assertEquals("01.08 10:05", s, "older visit uses absolute dd.MM HH:mm");
    }

    private static void testRadiusFormatting() {
        assertEquals("250m", GeofenceRowText.formatRadius(250f), "integer radius");
        assertEquals("99m", GeofenceRowText.formatRadius(99.6f), "truncated radius");
    }

    private static void testBuildRow() {
        assertEquals("home (150m) — Last visited: never",
                GeofenceRowText.buildRow("home", 150f, "Last visited: never"),
                "row assembles name, radius and last-visited");
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}