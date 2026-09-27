package com.car2hass;

/**
 * Timestamp unit convention for the log store and its upload payload.
 *
 * <p>The on-device SQLite store keeps {@code ts} in <b>epoch milliseconds</b>
 * (Java {@code System.currentTimeMillis()}), used for retention and ordering.
 * The upload wire carries <b>epoch seconds</b>: values beyond
 * {@link #MILLIS_THRESHOLD} are milliseconds and get divided down. The server
 * applies the same conversion defensively (intake PHP {@code log_ts_seconds()}),
 * so an older client that still sends milliseconds is formatted correctly too.</p>
 */
public final class LogTimestamp {

    /** Anything above this is milliseconds (year 5138 in seconds). */
    public static final long MILLIS_THRESHOLD = 100_000_000_000L;

    private LogTimestamp() {}

    /** Normalizes a millisecond or second timestamp to whole epoch seconds. */
    public static long toSeconds(long ts) {
        return ts > MILLIS_THRESHOLD ? ts / 1000L : ts;
    }
}
