package com.car2hass;

/**
 * Two-way sync decision for one cloud settings file.
 * A local file wins when its mtime is strictly newer than the server's
 * `updated_at` (seconds); the server wins when strictly newer; otherwise
 * nothing is transferred.
 */
public final class CloudSyncDecision {

    public enum Action { UPLOAD, DOWNLOAD, NONE }

    private CloudSyncDecision() {}

    public static Action decide(boolean hasLocal, long localMtimeMs,
                                boolean hasRemote, long serverUpdatedAtSec) {
        if (!hasLocal && !hasRemote) return Action.NONE;
        if (!hasRemote) return Action.UPLOAD;
        if (!hasLocal) return Action.DOWNLOAD;
        long serverMs = serverUpdatedAtSec * 1000L;
        if (localMtimeMs > serverMs) return Action.UPLOAD;
        if (localMtimeMs < serverMs) return Action.DOWNLOAD;
        return Action.NONE;
    }
}
