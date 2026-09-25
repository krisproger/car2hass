package com.car2hass;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe in-memory buffer that accumulates log records. A background
 * flusher calls {@link #flush()} to drain the buffer to a {@link LogSink} in
 * blocks of up to {@code maxBlockSize} messages (the final block may be smaller;
 * a single-message block is acceptable). Buffering never touches the sink, so a
 * log call from the main thread never performs DB work.
 */
public final class LogBatchBuffer {

    private final LogSink sink;
    private final int maxBlockSize;
    private final List<LogRecord> pending = new ArrayList<>();

    public LogBatchBuffer(LogSink sink, int maxBlockSize) {
        this.sink = sink;
        this.maxBlockSize = Math.max(1, maxBlockSize);
    }

    public synchronized void add(long ts, String level, String tag, String msg, String source) {
        pending.add(new LogRecord(ts, level, tag, msg, source));
    }

    public synchronized void flush() {
        if (pending.isEmpty() || sink == null) return;
        for (int i = 0; i < pending.size(); i += maxBlockSize) {
            int end = Math.min(pending.size(), i + maxBlockSize);
            sink.write(new ArrayList<>(pending.subList(i, end)));
        }
        pending.clear();
    }

    public synchronized int pendingCount() {
        return pending.size();
    }
}
