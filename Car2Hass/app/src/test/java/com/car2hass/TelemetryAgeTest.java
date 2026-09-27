package com.car2hass;

public class TelemetryAgeTest {
    private static void check(long elapsedMs, String expected) {
        String actual = TelemetryAge.format(elapsedMs);
        if (!expected.equals(actual))
            throw new AssertionError("format(" + elapsedMs + ") = " + actual + ", expected " + expected);
    }

    public static void main(String[] args) {
        check(0L, "00:00");
        check(95_000L, "01:35");
        check(600_000L, "10:00");
        check(601_000L, "\u221E");
        check(-1L, "00:00");
        System.out.println("All TelemetryAge tests passed.");
    }
}
