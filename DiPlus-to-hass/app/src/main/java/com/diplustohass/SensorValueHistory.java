package com.diplustohass;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Dynamic dictionary of recently observed sensor values.
 *
 * <p>Used by the rule editor to suggest values that a sensor has actually
 * reported. Values are recorded in translated form (the same form rules
 * compare against) as they arrive in TelemetryService.</p>
 *
 * <p>The in-memory map is the runtime source of truth. Persistence to
 * SharedPreferences is performed by callers (TelemetryService flush loop,
 * service shutdown) via {@link #toJson()} / {@link #ensureLoaded(String)}
 * to avoid SharedPreferences write amplification on hot paths.</p>
 *
 * <p>The pure in-memory core ({@link #recordValue}, {@link #getValues},
 * {@link #clearAll}) has no org.json or Android dependencies at call time
 * and is covered by plain-JVM unit tests.</p>
 */
public class SensorValueHistory {

    /** Max distinct values kept per sensor key. */
    public static final int MAX_VALUES_PER_KEY = 30;

    /** Min interval between persisted writes, used by needsFlush(). */
    private static final long FLUSH_INTERVAL_MS = 60_000;

    // key -> values, iteration order oldest..newest (LinkedHashSet)
    private static final Map<String, LinkedHashSet<String>> history = new HashMap<>();
    private static boolean loaded = false;
    private static boolean dirty = false;
    private static long lastFlushMs = 0;

    // ---- pure core ----

    /**
     * Record a value for a sensor key. Dedups, caps at MAX_VALUES_PER_KEY
     * (oldest evicted), and moves re-seen values to the most-recent slot.
     *
     * @return true if the stored set changed.
     */
    public static synchronized boolean recordValue(String key, String value) {
        if (key == null || key.isEmpty()) return false;
        if (value == null) return false;
        String v = value.trim();
        if (v.isEmpty() || "---".equals(v)) return false;

        LinkedHashSet<String> set = history.get(key);
        if (set == null) {
            set = new LinkedHashSet<>();
            history.put(key, set);
        }
        boolean changed;
        if (set.contains(v)) {
            // Re-seen: refresh recency only when it is not already newest.
            if (isNewest(set, v)) {
                changed = false;
            } else {
                set.remove(v);
                set.add(v);
                changed = true;
            }
        } else {
            set.add(v);
            changed = true;
            while (set.size() > MAX_VALUES_PER_KEY) {
                Iterator<String> it = set.iterator();
                it.next();
                it.remove();
            }
        }
        if (changed) dirty = true;
        return changed;
    }

    private static boolean isNewest(LinkedHashSet<String> set, String v) {
        String last = null;
        for (String s : set) last = s;
        return v.equals(last);
    }

    /**
     * Observed values for a key, most recent first.
     */
    public static synchronized List<String> getValues(String key) {
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> set = history.get(key);
        if (set == null) return out;
        out.addAll(set);
        Collections.reverse(out);

        boolean allNumeric = true;
        for (String s : out) {
            try {
                Double.parseDouble(s.replace(",", "."));
            } catch (NumberFormatException e) {
                allNumeric = false;
                break;
            }
        }
        if (allNumeric && out.size() > 1) {
            Collections.sort(out, (a, b) -> {
                double da = Double.parseDouble(a.replace(",", "."));
                double db = Double.parseDouble(b.replace(",", "."));
                return Double.compare(da, db);
            });
        }

        return out;
    }

    /** Reset everything (tests, or explicit user action). */
    public static synchronized void clearAll() {
        history.clear();
        loaded = false;
        dirty = false;
        lastFlushMs = 0;
    }

    /** Number of sensor keys currently tracked. */
    public static synchronized int keyCount() {
        return history.size();
    }

    // ---- persistence (org.json — Android runtime only) ----

    /**
     * Load persisted history once. Subsequent calls are no-ops.
     * Safe to call with null/empty json (starts empty).
     */
    public static synchronized void ensureLoaded(String json) {
        if (loaded) return;
        loaded = true;
        if (json == null || json.isEmpty()) return;
        try {
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray arr = obj.optJSONArray(key);
                if (arr == null) continue;
                LinkedHashSet<String> set = new LinkedHashSet<>();
                // Keep only the newest MAX_VALUES_PER_KEY entries.
                int start = Math.max(0, arr.length() - MAX_VALUES_PER_KEY);
                for (int i = start; i < arr.length(); i++) {
                    String v = arr.optString(i, null);
                    if (v != null && !v.isEmpty() && !"---".equals(v)) set.add(v);
                }
                if (!set.isEmpty()) history.put(key, set);
            }
        } catch (Exception e) {
            LogBuffer.w("SensorValueHistory", "load error: " + e.getMessage());
        }
    }

    public static synchronized boolean isLoaded() {
        return loaded;
    }

    /** Serialize to JSON (object of key -> array of values, oldest first). */
    public static synchronized String toJson() {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, LinkedHashSet<String>> e : history.entrySet()) {
                JSONArray arr = new JSONArray();
                for (String v : e.getValue()) arr.put(v);
                obj.put(e.getKey(), arr);
            }
            return obj.toString();
        } catch (Exception e) {
            LogBuffer.w("SensorValueHistory", "persist error: " + e.getMessage());
            return "{}";
        }
    }

    /** True when there are unpersisted changes older than the flush interval. */
    public static synchronized boolean needsFlush(long nowMs) {
        return dirty && (nowMs - lastFlushMs >= FLUSH_INTERVAL_MS);
    }

    /** True when there are unpersisted changes (immediate flush on shutdown). */
    public static synchronized boolean isDirty() {
        return dirty;
    }

    /** Call after a successful persist. */
    public static synchronized void markFlushed() {
        dirty = false;
        lastFlushMs = System.currentTimeMillis();
    }

    private SensorValueHistory() {
    }
}
