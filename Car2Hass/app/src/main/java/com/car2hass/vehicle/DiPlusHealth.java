package com.car2hass.vehicle;

/** Pure decision helper for DiPlus health and cold-start recovery. */
public final class DiPlusHealth {

    /** A successful real API read keeps the channel alive this long. */
    public static final long SUCCESS_TTL_MS = 30_000L;
    /** Negative cache after a failed read/ping (warm). */
    public static final long FAIL_TTL_MS = 15_000L;
    /** Window after service start where the channel is probed aggressively. */
    public static final long COLD_START_MS = 30_000L;
    /** Negative cache during the cold-start window. */
    public static final long COLD_START_FAIL_TTL_MS = 3_000L;
    /** Launch throttle (warm). */
    public static final long LAUNCH_COOLDOWN_MS = 60_000L;
    /** Launch throttle during cold start. */
    public static final long COLD_START_LAUNCH_COOLDOWN_MS = 5_000L;

    private DiPlusHealth() {}

    public static boolean isColdStart(long now, long serviceStartMs) {
        return serviceStartMs > 0L && now - serviceStartMs < COLD_START_MS;
    }

    /** Alive when a real read or a ping succeeded within the success TTL. */
    public static boolean isAlive(long now, long lastReadSuccessMs, long lastPingSuccessMs, long lastFailureMs) {
        if (lastReadSuccessMs > 0L && now - lastReadSuccessMs < SUCCESS_TTL_MS) return true;
        if (lastPingSuccessMs > 0L && now - lastPingSuccessMs < SUCCESS_TTL_MS) return true;
        if (lastFailureMs > 0L && now - lastFailureMs < FAIL_TTL_MS) return false;
        return false;
    }

    public static long negativeTtlMs(long now, long serviceStartMs) {
        return isColdStart(now, serviceStartMs) ? COLD_START_FAIL_TTL_MS : FAIL_TTL_MS;
    }

    public static long launchCooldownMs(long now, long serviceStartMs) {
        return isColdStart(now, serviceStartMs) ? COLD_START_LAUNCH_COOLDOWN_MS : LAUNCH_COOLDOWN_MS;
    }
}
