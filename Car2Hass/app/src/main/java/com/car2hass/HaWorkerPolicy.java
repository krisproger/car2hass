package com.car2hass;

public final class HaWorkerPolicy {
    private HaWorkerPolicy() {}

    public static boolean shouldAttempt(long lastAttemptMs, long backoffMs, long nowMs) {
        return nowMs - lastAttemptMs >= backoffMs;
    }

    public static long nextBackoff(long current) {
        return Math.min(current * 2, 300_000L);
    }

    public static int batchRoom(int limit, int queued) {
        return Math.max(0, limit - queued);
    }
}
