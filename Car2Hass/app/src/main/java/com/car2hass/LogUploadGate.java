package com.car2hass;

/** Single-slot gate that collapses concurrent crash-upload requests into one. */
public final class LogUploadGate {

    private boolean scheduled;

    public synchronized boolean trySchedule() {
        if (scheduled) return false;
        scheduled = true;
        return true;
    }

    public synchronized void release() {
        scheduled = false;
    }

    public synchronized boolean isScheduled() {
        return scheduled;
    }
}
