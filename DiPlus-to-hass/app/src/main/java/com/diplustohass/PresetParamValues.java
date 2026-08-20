package com.diplustohass;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON (de)serialization of remembered preset parameter values.
 *
 * <p>Shape: {@code {"<presetId>": {"<command>": "<value>", ...}, ...}}.
 * Pure logic, no Android dependencies — unit-testable with plain JDK +
 * org.json.
 */
public final class PresetParamValues {

    private PresetParamValues() {}

    public static String toJson(Map<String, Map<String, String>> all) {
        JSONObject root = new JSONObject();
        for (Map.Entry<String, Map<String, String>> preset : all.entrySet()) {
            JSONObject values = new JSONObject();
            for (Map.Entry<String, String> e : preset.getValue().entrySet()) {
                try {
                    values.put(e.getKey(), e.getValue());
                } catch (Exception ignored) {
                    // org.json put() with null key throws; keys are never null here.
                }
            }
            try {
                root.put(preset.getKey(), values);
            } catch (Exception ignored) {
            }
        }
        return root.toString();
    }

    public static Map<String, Map<String, String>> fromJson(String json) {
        Map<String, Map<String, String>> all = new LinkedHashMap<>();
        if (json == null || json.isEmpty()) {
            return all;
        }
        try {
            JSONObject root = new JSONObject(json);
            Iterator<String> presets = root.keys();
            while (presets.hasNext()) {
                String presetId = presets.next();
                JSONObject values = root.optJSONObject(presetId);
                if (values == null) continue;
                Map<String, String> map = new LinkedHashMap<>();
                Iterator<String> keys = values.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    map.put(key, values.optString(key, ""));
                }
                all.put(presetId, map);
            }
        } catch (Exception ignored) {
            // Malformed stored JSON — behave as if nothing was saved.
        }
        return all;
    }
}
