package com.car2hass;

import java.util.List;

/** Receives a flushed batch of log records (one DB transaction per call). */
public interface LogSink {
    void write(List<LogRecord> records);
}
