package com.car2hass;

public final class LogRecord {
    public final long id;
    public final long ts;
    public final String level;
    public final String tag;
    public final String msg;
    public final String source;

    public LogRecord(long id, long ts, String level, String tag, String msg, String source) {
        this.id = id;
        this.ts = ts;
        this.level = level == null ? "" : level;
        this.tag = tag == null ? "" : tag;
        this.msg = msg == null ? "" : msg;
        this.source = source == null ? "" : source;
    }

    public LogRecord(long ts, String level, String tag, String msg, String source) {
        this(0L, ts, level, tag, msg, source);
    }
}
