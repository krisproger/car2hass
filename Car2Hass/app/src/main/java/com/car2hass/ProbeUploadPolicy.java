package com.car2hass;

/**
 * Android-free decision logic for probe-report uploads.
 *
 * <p>The server accepts at most one report per device per 30 minutes (and 10/day);
 * a 429 means "too soon", so the client must defer instead of retrying immediately.
 */
public final class ProbeUploadPolicy {

    /** Mirrors the server window in {@code _reports_lib.php:rate_limit_ok} (1800 s). */
    public static final long MIN_INTERVAL_MS = 30L * 60 * 1000L;

    private ProbeUploadPolicy() {}

    /** Whether a failed upload should be queued for a later retry. */
    public static boolean isRetryableCode(int code) {
        if (code == 0) return true;    // network/transport error
        if (code == 429) return false; // server says: too soon — wait for the next window
        return code >= 500;
    }

    /** Whether enough time has passed since the last successful upload. */
    public static boolean shouldUpload(long nowMs, long lastUploadMs) {
        return lastUploadMs <= 0L || nowMs - lastUploadMs >= MIN_INTERVAL_MS;
    }
}
