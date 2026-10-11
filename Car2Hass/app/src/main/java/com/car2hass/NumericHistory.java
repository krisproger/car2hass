package com.car2hass;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small in-memory ring buffer of recent numeric values per sensor key (sparkline). */
public final class NumericHistory {

    private static final int MAX = 120;
    private static final Map<String, ArrayDeque<Float>> MAP = new ConcurrentHashMap<>();

    private NumericHistory() {}

    /** Records a numeric value (non-numeric values are ignored). */
    public static void record(String key, String value) {
        if (key == null || value == null || value.isEmpty()) return;
        float v;
        try {
            v = Float.parseFloat(value.replace(",", "."));
        } catch (NumberFormatException e) {
            return;
        }
        ArrayDeque<Float> d = MAP.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (d) {
            d.addLast(v);
            if (d.size() > MAX) d.removeFirst();
        }
    }

    /** Recent values for a key, oldest first (empty when none). */
    public static float[] series(String key) {
        ArrayDeque<Float> d = MAP.get(key);
        if (d == null) return new float[0];
        synchronized (d) {
            float[] out = new float[d.size()];
            int i = 0;
            for (float f : d) out[i++] = f;
            return out;
        }
    }
}
