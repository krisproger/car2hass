package com.car2hass;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Pure rolling-2h window of sent telemetry bytes (testable without Android). */
public final class SendHistoryCore {

    public static final long WINDOW_MS = 2 * 3600 * 1000L;
    public static final long FLOOR_BYTES = 256 * 1024L;

    private SendHistoryCore() {}

    public static String append(String historyJson, long tsMs, int bytes) {
        JSONArray arr = new JSONArray();
        if (historyJson != null && !historyJson.isEmpty()) {
            try {
                arr = new JSONArray(historyJson);
            } catch (JSONException ignored) {
            }
        }
        JSONObject o = new JSONObject();
        try {
            o.put("ts", tsMs);
            o.put("bytes", bytes);
        } catch (JSONException ignored) {}
        arr.put(o);
        JSONArray kept = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject e = arr.getJSONObject(i);
                if (tsMs - e.optLong("ts") <= WINDOW_MS) kept.put(e);
            } catch (JSONException ignored) {}
        }
        return kept.toString();
    }

    public static long computeReference(String historyJson, long nowMs) {
        if (historyJson == null || historyJson.isEmpty()) return 0;
        try {
            JSONArray arr = new JSONArray(historyJson);
            long sum = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long ts = o.optLong("ts");
                if (nowMs - ts <= WINDOW_MS) sum += o.optLong("bytes");
            }
            return sum;
        } catch (JSONException e) {
            return 0;
        }
    }
}