package com.car2hass.vehicle;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Canonicalizes a translated sensor value so overlapping channels (adb and
 * diplus) agree on the same string for the same logical state. Without this the
 * priority/freshness merge lets the value flip between the two representations,
 * which shows up as HA sensor flicker.
 */
public final class SignalCanonicalizer {

    /** Keys whose adb value is in kPa while the canonical unit is bar. */
    private static final java.util.Set<String> PRESSURE_KEYS =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "tyre_pressure_fl", "tyre_pressure_fr", "tyre_pressure_rl", "tyre_pressure_rr"));

    /** Per-key label canonicalization (channel label -> canonical label). */
    private static final Map<String, Map<String, String>> LABEL_MAP;

    static {
        Map<String, Map<String, String>> m = new HashMap<>();
        Map<String, String> light = new HashMap<>();
        light.put("open", "on");
        m.put("sidelights", light);
        m.put("low_beam", light);
        m.put("high_beam", light);
        Map<String, String> drl = new HashMap<>();
        drl.put("open", "on");
        drl.put("invalid", "off");
        drl.put("未定义", "off");
        m.put("drl", drl);
        Map<String, String> recirc = new HashMap<>();
        recirc.put("external", "fresh");
        m.put("ac_recirculation", recirc);
        LABEL_MAP = Collections.unmodifiableMap(m);
    }

    private SignalCanonicalizer() {}

    /** Returns the canonical value for {@code key} from {@code channel}. */
    public static String canonical(String key, String value, String channel) {
        if (key == null || value == null) return value;
        String v = value.trim();
        if (v.isEmpty()) return v;

        Map<String, String> labels = LABEL_MAP.get(key);
        if (labels != null) {
            String mapped = labels.get(v.toLowerCase());
            if (mapped != null) return mapped;
        }

        if (isNumeric(v)) {
            double d = Double.parseDouble(v);
            boolean adb = "adb".equals(channel) || "native".equals(channel);
            if (adb && PRESSURE_KEYS.contains(key) && d > 20.0) {
                d = d / 100.0; // kPa -> bar
            }
            return trimNumber(d);
        }
        return v;
    }

    private static boolean isNumeric(String v) {
        return v.matches("-?\\d+(\\.\\d+)?");
    }

    private static String trimNumber(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        String s = String.valueOf(d);
        // Strip a trailing ".0" (e.g. 57104.0 -> 57104) and reduce long tails.
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }
}
