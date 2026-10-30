package com.car2hass;

/** One-shot first-run handoff: About first, auto-start settings after it closes. */
public final class FirstRunFlow {
    private boolean pendingAutoStart;

    /** True once for a new version; arms the deferred auto-start step. */
    public boolean begin(String current, String seen) {
        if (current == null || current.equals(seen)) return false;
        pendingAutoStart = true;
        return true;
    }

    /** Consumes the deferred auto-start action exactly once. */
    public boolean takePendingAutoStart() {
        boolean pending = pendingAutoStart;
        pendingAutoStart = false;
        return pending;
    }
}
