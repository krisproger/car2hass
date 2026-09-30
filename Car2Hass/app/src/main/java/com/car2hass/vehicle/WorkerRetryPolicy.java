package com.car2hass.vehicle;

/**
 * Exponential backoff for a channel worker. A healthy cycle returns to the
 * base cadence; each consecutive empty/failed cycle doubles the wait up to the
 * cap, so an unstable channel is retried periodically instead of being polled
 * constantly.
 */
public final class WorkerRetryPolicy {

    /** Consecutive empty cycles tolerated at the base cadence before backing off. */
    public static final int SOFT_FAILURES = 2;

    private final long baseMs;
    private final long maxMs;
    private int consecutiveFailures;

    public WorkerRetryPolicy(long baseMs, long maxMs) {
        if (baseMs <= 0) throw new IllegalArgumentException("baseMs must be > 0");
        this.baseMs = baseMs;
        this.maxMs = Math.max(maxMs, baseMs);
    }

    /** Records a healthy cycle and returns the base cadence. */
    public long onSuccess() {
        consecutiveFailures = 0;
        return baseMs;
    }

    /**
     * Records an empty/failed cycle. An intermittent channel keeps the base
     * cadence for the first {@link #SOFT_FAILURES} cycles; only a persistent
     * failure backs off (doubling up to the cap).
     */
    public long onFailure() {
        consecutiveFailures++;
        if (consecutiveFailures <= SOFT_FAILURES) return baseMs;
        int steps = consecutiveFailures - SOFT_FAILURES;
        long delay = baseMs;
        for (int i = 0; i < steps && delay < maxMs; i++) {
            delay = Math.min(delay * 2, maxMs);
        }
        return delay;
    }

    public long baseMs() { return baseMs; }
    public int consecutiveFailures() { return consecutiveFailures; }
}
