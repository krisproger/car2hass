package com.car2hass;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure retention rules for the log store.
 *
 * <ul>
 *   <li>Records older than {@link #RETENTION_MS} (5 days) are pruned.</li>
 *   <li>Only records from the current and the immediately previous app version
 *       are kept. Older versions (and legacy rows without a version) are
 *       deleted, so an upgrade does not keep obsolete-version logs forever.</li>
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
     * (the current one). Keeps {@code currentVersion} and {@code previousVersion}
     * (current - 1); all other versions are removed.
     */
    public static String apply(LogRecordStore store, long nowMs, String previousVersion,
                               String currentVersion) {
        String previous = previousVersion == null ? "" : previousVersion.trim();
        String current = currentVersion == null ? "" : currentVersion.trim();

        Set<String> keep = new HashSet<>();
        if (!current.isEmpty()) keep.add(current);
        if (!previous.isEmpty() && !previous.equals(current)) keep.add(previous);

        // Legacy (v1) rows have no version. Attribute them to the previous
        // version before pruning so current-1 survives the migration.
        if (!previous.isEmpty()) {
            store.assignVersionToNull(previous);
        }
        store.deleteLogsNotInVersions(keep);
        store.deleteCrashesNotInVersions(keep);

        long cutoff = pruneCutoff(nowMs);
        store.deleteLogsBefore(cutoff);
        store.deleteCrashesBefore(cutoff);
        return current;
    }
}
