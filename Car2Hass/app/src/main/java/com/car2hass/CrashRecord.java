package com.car2hass;

public final class CrashRecord {
    public final long id;
    public final long ts;
    public final String stacktrace;
    public final String logSnapshot;

    public CrashRecord(long id, long ts, String stacktrace, String logSnapshot) {
        this.id = id;
        this.ts = ts;
        this.stacktrace = stacktrace == null ? "" : stacktrace;
        this.logSnapshot = logSnapshot == null ? "" : logSnapshot;
    }

    public CrashRecord(long ts, String stacktrace, String logSnapshot) {
        this(0L, ts, stacktrace, logSnapshot);
    }
}
