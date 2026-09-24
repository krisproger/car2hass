package com.car2hass.vehicle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Strips privacy deny-listed keys (VIN, serial numbers, firmware version) from
 * probe reports before they leave the device. Pure JSON logic, no Android deps;
 * mirrors the server intake rule in html/cartelemetry/api/_reports_lib.php.
 */
public final class ReportPrivacyFilter {

    private ReportPrivacyFilter() {}

    private static final Pattern DENY =
            Pattern.compile("(^|_)vin($|_)|serial|fw_version", Pattern.CASE_INSENSITIVE);

    /** True when a key must never appear in an emitted report. */
    public static boolean isDenied(String key) {
        return key != null && DENY.matcher(key).find();
    }

    /** Deep copy of {@code in} with every deny-listed key removed at any depth. */
    public static JSONObject sanitize(JSONObject in) {
        if (in == null) return null;
        JSONObject out = new JSONObject();
        Iterator<String> keys = in.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (isDenied(k)) continue;
            put(out, k, sanitizeValue(in.opt(k)));
        }
        return out;
    }

    /** Deep copy of {@code in} with every deny-listed key removed at any depth. */
    public static JSONArray sanitize(JSONArray in) {
        if (in == null) return null;
        JSONArray out = new JSONArray();
        for (int i = 0; i < in.length(); i++) {
            out.put(sanitizeValue(in.opt(i)));
        }
        return out;
    }

    private static Object sanitizeValue(Object v) {
        if (v instanceof JSONObject) return sanitize((JSONObject) v);
        if (v instanceof JSONArray) return sanitize((JSONArray) v);
        return v;
    }

    private static void put(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (Exception ignored) {
        }
    }
}
