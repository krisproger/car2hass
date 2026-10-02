package com.car2hass;

/** Pure eviction policy for the cloud snapshot queue (mirrors SnapshotQueue). */
public final class CloudQueuePolicy {

    /** Approximate on-disk footprint of one row (signals text + row overhead). */
    public static long sizeBytes(String signals) {
        return (signals == null ? 0 : signals.length()) + 200L;
    }

    /**
     * Oldest-first rows; returns the id at which accumulated sizes reach
     * {@code toFreeBytes} (delete everything with id <= it), or -1 when nothing
     * needs to be freed (or the queue is empty).
     */
    public static long evictionCutoffId(long toFreeBytes, long[] ids, long[] sizes) {
        if (toFreeBytes <= 0 || ids == null || ids.length == 0) return -1;
        long acc = 0;
        long cutoff = -1;
        for (int i = 0; i < ids.length; i++) {
            acc += sizes[i];
            cutoff = ids[i];
            if (acc >= toFreeBytes) break;
        }
        return cutoff;
    }

    private CloudQueuePolicy() {}
}
