package com.diplustohass.rules;

import java.util.HashMap;
import java.util.Map;

public class AntiLoopGuard {

    private static final class Entry {
        final String value;
        final long timestamp;

        Entry(String value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    private final Map<String, Entry> lastActions = new HashMap<>();

    public synchronized boolean allow(String commandId, String commandValue, long nowMs, long windowMs) {
        Entry entry = lastActions.get(commandId);
        if (entry == null) return true;
        return nowMs - entry.timestamp >= windowMs;
    }

    public synchronized void record(String commandId, String commandValue, long nowMs) {
        lastActions.put(commandId, new Entry(commandValue, nowMs));
    }

    public synchronized void clear() {
        lastActions.clear();
    }
}
