package com.car2hass;

/** Retry policy for log-upload HTTP chunks. */
public final class UploadRetryPolicy {
    public static final int MAX_ATTEMPTS = 3;

    private UploadRetryPolicy() {}

    /** Retry transport errors and server failures; do not retry 4xx client errors. */
    public static boolean shouldRetry(int code) {
        return code == 0 || code >= 500;
    }

    /** Backoff after the first and second failed attempt. */
    public static long backoffMs(int attempt) {
        if (attempt <= 1) return 1000L;
        if (attempt == 2) return 3000L;
        return 8000L;
    }
}
