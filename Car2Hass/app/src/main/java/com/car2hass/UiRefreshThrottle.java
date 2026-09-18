package com.car2hass;

/**
 * Coalescing frame throttle for UI repaints: the first change schedules a
 * refresh, changes arriving while one is pending are merged into it. This is a
 * trailing-edge throttle so the last value of a burst is always rendered.
 */
public final class UiRefreshThrottle {

    private final long intervalMs;
    private boolean scheduled;
    private long nextAllowedMs;

    public UiRefreshThrottle(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    /**
     * Request a refresh at {@code nowMs}.
     *
     * @return delay in ms until the refresh should run, or -1 when a refresh is
     *         already scheduled (the caller must not post another one).
     */
    public synchronized long schedule(long nowMs) {
        if (scheduled) return -1;
        scheduled = true;
        long delay = nextAllowedMs - nowMs;
        return delay > 0 ? delay : 0;
    }

    /** Must be called when the scheduled refresh has actually run. */
    public synchronized void flushed(long nowMs) {
        scheduled = false;
        nextAllowedMs = nowMs + intervalMs;
    }
}
