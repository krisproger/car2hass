package com.car2hass.vehicle;

/**
 * Android-free snapshot of a channel worker's state: whether it is running, what
 * its last cycle produced, and its current cadence/backoff. Built by each
 * {@link ChannelWorker#status()} and surfaced by {@link ChannelWorkerRegistry}.
 */
public final class ChannelWorkerStatus {

    public enum Result { OK, EMPTY, ERROR }

    private final String channelId;
    private final boolean running;
    private final Result lastResult;
    private final String lastError;
    private final long cadenceMs;
    private final long retryDelayMs;

    public ChannelWorkerStatus(String channelId, boolean running, Result lastResult,
                               String lastError, long cadenceMs, long retryDelayMs) {
        this.channelId = channelId != null ? channelId : "";
        this.running = running;
        this.lastResult = lastResult != null ? lastResult : Result.OK;
        this.lastError = lastError != null ? lastError : "";
        this.cadenceMs = cadenceMs > 0 ? cadenceMs : 0;
        this.retryDelayMs = retryDelayMs > 0 ? retryDelayMs : 0;
    }

    public String channelId() { return channelId; }
    public boolean running() { return running; }
    public Result lastResult() { return lastResult; }
    public String lastError() { return lastError; }
    public long cadenceMs() { return cadenceMs; }
    public long retryDelayMs() { return retryDelayMs; }

    /** Base cadence in whole seconds (>=1). */
    public long cadenceSeconds() { return toSeconds(cadenceMs); }

    /** Current backoff delay in whole seconds (>=1). */
    public long retryDelaySeconds() { return toSeconds(retryDelayMs); }

    /** True when the worker is waiting out a backoff beyond its base cadence. */
    public boolean inBackoff() {
        return retryDelayMs > 0 && cadenceMs > 0 && retryDelayMs > cadenceMs;
    }

    /** Compact, locale-independent summary (logs and tests). */
    public String describe() {
        if (!running) return "off";
        switch (lastResult) {
            case OK: return "ok:" + cadenceSeconds() + "s";
            case EMPTY: return "empty:retry-" + retryDelaySeconds() + "s";
            default:
                String base = lastError.isEmpty() ? "error" : "error:" + lastError;
                return inBackoff() ? base + ":retry-" + retryDelaySeconds() + "s" : base;
        }
    }

    private static long toSeconds(long ms) {
        if (ms <= 0) return 0;
        long s = ms / 1000;
        return s < 1 ? 1 : s;
    }
}
