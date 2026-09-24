package com.car2hass.vehicle;

/**
 * Pure decision logic for the Voyah channel: bounded read retries with a short
 * backoff and the two bind budgets (generous first bind, tighter lazy re-bind).
 * Kept free of Android imports so it is covered by the plain-JVM test harness.
 */
public final class VoyahReadPolicy {

    /** Total read attempts per cycle: the initial one plus quick retries. */
    public static final int MAX_READ_ATTEMPTS = 3;

    /** Backoff unit between retries (attempt N waits N * this). */
    public static final long READ_RETRY_BACKOFF_MS = 40L;

    /** Budget for the lazy re-bind that happens during a read cycle. */
    public static final long READ_TIMEOUT_MS = 1500L;

    /** More generous budget for the initial service discovery/bind. */
    public static final long FIRST_BIND_TIMEOUT_MS = 5000L;

    public interface Attempt {
        /** Returns the number of values read; &le;0 means the attempt failed. */
        int read() throws Exception;
    }

    public interface Sleeper {
        void sleep(long ms) throws Exception;
    }

    private VoyahReadPolicy() {}

    public static boolean shouldRetry(int attempt) {
        return attempt < MAX_READ_ATTEMPTS;
    }

    public static long backoffMs(int attempt) {
        return READ_RETRY_BACKOFF_MS * attempt;
    }

    /**
     * Runs {@code attempt} until it returns a positive count or the bounded
     * attempt budget is exhausted. Exceptions count as an empty read; the
     * method never throws, so a flaky binder cannot crash the worker.
     */
    public static int readWithRetry(Attempt attempt, Sleeper sleeper) {
        int count = 0;
        for (int i = 0; shouldRetry(i); i++) {
            if (i > 0) {
                try {
                    sleeper.sleep(backoffMs(i));
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                    return count;
                }
            }
            try {
                count = attempt.read();
            } catch (Exception e) {
                count = 0;
            }
            if (count > 0) return count;
        }
        return count;
    }
}
