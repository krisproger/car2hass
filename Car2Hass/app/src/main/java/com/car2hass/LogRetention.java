package com.car2hass;

/**
 * Pure retention rules for the log store.
 *
 * <ul>
 *   <li>Records older than {@link #RETENTION_MS} (5 days) are pruned.</li>
 *   <li>When the persisted app version differs from the current one, every
 *       previous log/crash record is purged (new app version = fresh log).</li>
 * </ul>
 */
public final class LogRetention {

    public static final long RETENTION_MS = 5L * 24 * 60 * 60 * 1000;

    private LogRetention() {}

    public static long pruneCutoff(long nowMs) {
        return nowMs - RETENTION_MS;
    }

    public static boolean versionChanged(String previous, String current) {
        return previous != null && !previous.isEmpty() && !previous.equals(current);
    }

    /**
     * Applies retention to the store and returns the version string to persist
     * (the current one).
     */
    public static String apply(LogRecordStore store, long nowMs, String previousVersion,
                               String currentVersion) {
        if (versionChanged(previousVersion, currentVersion)) {
            store.purgeAllLogs();
            store.purgeAllCrashes();
        } else {
            store.deleteLogsBefore(pruneCutoff(nowMs));
        }
        return currentVersion;
    }
}
