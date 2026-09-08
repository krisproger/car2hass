package com.car2hass.vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe latest-value store for every signal (system + channels). */
public final class ValueStore {
    private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> updatedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> source = new ConcurrentHashMap<>();

    public void put(String key, String value, String channel) {
        values.put(key, value);
        updatedAt.put(key, System.currentTimeMillis());
        source.put(key, channel);
    }

    public String get(String key) {
        return values.get(key);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public long ageMs(String key) {
        Long t = updatedAt.get(key);
        return t == null ? -1 : System.currentTimeMillis() - t;
    }

    public String sourceOf(String key) {
        return source.get(key);
    }

    public Map<String, String> snapshot() {
        return new HashMap<>(values);
    }
}