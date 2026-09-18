package com.car2hass.vehicle;

/** Pure acceptance rules for Android location fixes (no android.* dependency). */
public final class LocationPolicy {
    /** A fix older than this is history, not a current position. */
    public static final long MAX_FIX_AGE_MS = 45_000L;
    /** Accuracy (m) at or below which a GPS fix counts as reliable. */
    public static final float ACCURATE_GPS_ACCURACY_M = 15f;
    /** Fixes coarser than this are dropped as absurd. */
    public static final float MAX_ACCURACY_M = 1000f;
    /** Window in which a recent accurate GPS fix suppresses coarse fixes. */
    public static final long RECENT_FIX_WINDOW_MS = 30_000L;

    private LocationPolicy() {
    }

    public static boolean isFresh(long fixTimeMs, long nowMs) {
        return fixTimeMs > 0 && nowMs - fixTimeMs <= MAX_FIX_AGE_MS;
    }

    public static boolean isAbsurd(float accuracyM) {
        return accuracyM > MAX_ACCURACY_M;
    }

    public static boolean isAccurateGps(String provider, float accuracyM) {
        return "gps".equals(provider) && accuracyM > 0 && accuracyM <= ACCURATE_GPS_ACCURACY_M;
    }

    /** True when this provider/accuracy/time triple is a recent accurate GPS fix. */
    public static boolean isRecentAccurateGps(String provider, float accuracyM,
                                              long fixTimeMs, long nowMs) {
        return isAccurateGps(provider, accuracyM)
                && fixTimeMs > 0
                && nowMs - fixTimeMs < RECENT_FIX_WINDOW_MS;
    }

    /**
     * A coarse (network/passive) fix must not override a recent accurate GPS
     * fix; GPS fixes are never suppressed.
     */
    public static boolean suppressCoarse(String incomingProvider, String lastProvider,
                                         float lastAccuracyM, long lastFixTimeMs,
                                         long nowMs) {
        if (incomingProvider == null || "gps".equals(incomingProvider)) {
            return false;
        }
        return isRecentAccurateGps(lastProvider, lastAccuracyM, lastFixTimeMs, nowMs);
    }
}
