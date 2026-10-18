package com.car2hass.vehicle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Parses per-channel availability from a probe report's `channels` array. */
public final class ChannelAvailability {
    private ChannelAvailability() {}

    public static Map<String, Boolean> fromReport(JSONObject report) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        if (report == null) return out;
        JSONArray arr = report.optJSONArray("channels");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            String id = c.optString("id", "");
            if (!id.isEmpty()) out.put(id, c.optBoolean("available", false));
        }
        return out;
    }
}
