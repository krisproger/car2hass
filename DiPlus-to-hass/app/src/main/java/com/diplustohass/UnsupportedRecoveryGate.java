package com.diplustohass;

/**
 * Decides when the "known-unsupported trailing group" is re-isolated with
 * allowSplit=true. A mixed group (genuinely-unsupported name + signal that
 * became supported, or that was wrongly cached) fails as a whole with
 * {"success":false}; periodic re-isolation lets cacheSupported recover the
 * good signals while keeping request growth bounded (one split pass every
 * {@code REISOLATE_EVERY_FAILURES} failed probes).
 */
public final class UnsupportedRecoveryGate {
    public static final int REISOLATE_EVERY_FAILURES = 5;

    private UnsupportedRecoveryGate() {}

    public static boolean shouldReisolate(int consecutiveFailures) {
        return consecutiveFailures > 0 && consecutiveFailures % REISOLATE_EVERY_FAILURES == 0;
    }
}