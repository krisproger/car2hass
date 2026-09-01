package com.car2hass.vehicle;

import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe latest-value store for all snapshot keys (location_*, device_*, auto). */
public final class SnapshotStore {
    private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();
    private volatile Loc lastLoc = new Loc();

    public static final class Loc {
        public double lat, lon, alt;
        public float speed, bearing, accuracy;
        public String provider = "";
        public long timeMs;
    }

    public void put(String key, String value) {
        if (key != null) values.put(key, value);
    }

    public String get(String key) { return values.get(key); }

    public String getOrDefault(String key, String def) {
        String v = values.get(key);
        return v != null ? v : def;
    }

    public Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public void setLocation(double lat, double lon, float speed, float bearing,
                            double alt, float accuracy, String provider, long timeMs) {
        Loc l = new Loc();
        l.lat = lat; l.lon = lon; l.speed = speed; l.bearing = bearing;
        l.alt = alt; l.accuracy = accuracy;
        l.provider = provider == null ? "" : provider; l.timeMs = timeMs;
        this.lastLoc = l;
    }

    public Loc getLocation() { return lastLoc; }

    public void clear() { values.clear(); }
}
