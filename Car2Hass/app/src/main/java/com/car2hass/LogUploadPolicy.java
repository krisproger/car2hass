package com.car2hass;

import java.util.ArrayList;
import java.util.List;

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

    /** Cap on the encoded DB-log payload per upload; bounds a single upload burst. */
    public static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;

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

    /** Approximate encoded size of one record (fields + JSON overhead). */
    public static long approxBytes(LogRecord r) {
        if (r == null) return 0L;
        return (long) r.level.length() + r.tag.length() + r.msg.length() + r.source.length() + 64L;
    }

    /**
     * Oldest-first prefix of records that fits the byte budget; always at least one.
     * Records beyond the budget stay unsent and upload on a later run.
     */
    public static List<LogRecord> selectForUpload(List<LogRecord> records, long maxBytes) {
        List<LogRecord> out = new ArrayList<>();
        if (records == null) return out;
        long total = 0L;
        for (LogRecord r : records) {
            long b = approxBytes(r);
            if (!out.isEmpty() && total + b > maxBytes) break;
            out.add(r);
            total += b;
        }
        return out;
    }
}
