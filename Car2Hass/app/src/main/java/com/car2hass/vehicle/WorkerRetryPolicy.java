package com.car2hass.vehicle;

/**
 * Exponential backoff for a channel worker. A healthy cycle returns to the
 * base cadence; each consecutive empty/failed cycle doubles the wait up to the
 * cap, so an unstable channel is retried periodically instead of being polled
 * constantly.
 */
public final class WorkerRetryPolicy {

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

    /** Records an empty/failed cycle and returns the next backoff delay. */
    public long onFailure() {
        long delay = baseMs;
        for (int i = 0; i < consecutiveFailures && delay < maxMs; i++) {
            delay = Math.min(delay * 2, maxMs);
        }
        consecutiveFailures++;
        return delay;
    }

    public long baseMs() { return baseMs; }
    public int consecutiveFailures() { return consecutiveFailures; }
}
