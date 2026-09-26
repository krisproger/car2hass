package com.car2hass;

/**
 * Android-free decision logic for when the on-device log may be uploaded.
 *
 * <p>Uploads are user-triggered, crash-triggered, or a once-per-day safety net.
 * There is no short periodic upload: the timer only drains the in-memory buffer
 * to the database.
 */
public final class LogUploadPolicy {

    public static final long AUTO_INTERVAL_MS = 24L * 60 * 60 * 1000L;
    public static final long CRASH_BACKOFF_MS = 10L * 60 * 1000L;

    public enum Trigger { USER, CRASH, AUTO }

    private LogUploadPolicy() {}

    /**
     * @param trigger       what requested the upload
     * @param hasUnsent     true when the store holds at least one unsent record
     * @param nowMs         current wall-clock time
     * @param lastAttemptMs persisted time of the previous attempt of this trigger
     */
    public static boolean shouldUpload(Trigger trigger, boolean hasUnsent,
                                       long nowMs, long lastAttemptMs) {
        if (trigger == Trigger.USER) return true;
        if (!hasUnsent) return false;
        long interval = trigger == Trigger.CRASH ? CRASH_BACKOFF_MS : AUTO_INTERVAL_MS;
        return lastAttemptMs <= 0L || nowMs - lastAttemptMs >= interval;
    }
}
