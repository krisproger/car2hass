package com.car2hass;

import java.util.Locale;

/** Formats the elapsed time since a value last changed as {@code mm:ss}, capped. */
public final class TelemetryAge {

    /** Elapsed times strictly above this show the "stale" symbol. */
    public static final long MAX_MS = 10L * 60L * 1000L;

    public static String format(long elapsedMs) {
        if (elapsedMs < 0) elapsedMs = 0;
        if (elapsedMs > MAX_MS) return "\u221E";
        long seconds = elapsedMs / 1000L;
        return String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private TelemetryAge() {
    }
}
