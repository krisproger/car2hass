package com.car2hass;

/** Pure mapping of pending/queue bytes to a 0..1 progress value. */
public final class QueueIndicator {

    private QueueIndicator() {}

    public static float progress(long pendingBytes, long referenceBytes) {
        if (pendingBytes <= 0 || referenceBytes <= 0) return 0f;
        float v = (float) pendingBytes / (float) referenceBytes;
        return Math.min(1f, v);
    }
}