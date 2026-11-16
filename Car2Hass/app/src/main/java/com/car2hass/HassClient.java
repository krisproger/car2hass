package com.car2hass;

import org.json.JSONArray;
import org.json.JSONObject;

/** Shared Home Assistant snapshot DTO and flush callback. */
public final class HassClient {
    private HassClient() {}

    public interface FlushCallback {
        void onResult(boolean success, String message);
    }

    public static class Snapshot {
        public final long timestamp;
        public final double lat;
        public final double lon;
        public final float accuracy;
        /** GPS fix time in epoch seconds (0 when unknown — falls back to timestamp). */
        public final long fixTimeSec;
        /** JSON object of all signal key:value pairs. */
        public final String signalJson;
        /** JSON array of disabled signal keys ("" when none). */
        public final String disabledJson;
        /** Database row id when this snapshot came from the queue (-1 for in-memory). */
        public long queueId = -1;
        /** True when signalJson holds only changed signals (delta, #91). */
        public boolean delta = false;
        /** channel → changed keys JSON ("" when not a delta). */
        public String chJson = "";

        public Snapshot(long timestamp, double lat, double lon, float accuracy,
                        long fixTimeSec, String signalJson, String disabledJson) {
            this(timestamp, lat, lon, accuracy, fixTimeSec, signalJson, disabledJson, false, "");
        }

        public Snapshot(long timestamp, double lat, double lon, float accuracy,
                        long fixTimeSec, String signalJson, String disabledJson,
                        boolean delta, String chJson) {
            this.timestamp = timestamp;
            this.lat = lat;
            this.lon = lon;
            this.accuracy = accuracy;
            this.fixTimeSec = fixTimeSec;
            this.signalJson = signalJson;
            this.disabledJson = disabledJson;
            this.delta = delta;
            this.chJson = chJson == null ? "" : chJson;
        }

        public JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("t", timestamp);
            if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                JSONObject gps = new JSONObject();
                gps.put("lat", lat);
                gps.put("lon", lon);
                gps.put("a", accuracy);
                if (fixTimeSec > 0) gps.put("t", fixTimeSec);
                obj.put("g", gps);
            }
            obj.put("s", new JSONObject(signalJson));
            if (disabledJson != null && !disabledJson.isEmpty()) {
                obj.put("d", new JSONArray(disabledJson));
            }
            if (delta) {
                obj.put("delta", true);
                if (chJson != null && !chJson.isEmpty()) {
                    obj.put("ch", new JSONObject(chJson));
                }
            }
            return obj;
        }

        public static Snapshot fromJson(JSONObject obj) throws Exception {
            JSONObject gps = obj.optJSONObject("g");
            double lat = Double.NaN;
            double lon = Double.NaN;
            float accuracy = 0;
            long fixTimeSec = 0;
            if (gps != null) {
                lat = gps.optDouble("lat", Double.NaN);
                lon = gps.optDouble("lon", Double.NaN);
                accuracy = (float) gps.optDouble("a", 0);
                fixTimeSec = gps.optLong("t", 0);
            }
            JSONObject ch = obj.optJSONObject("ch");
            return new Snapshot(
                    obj.getLong("t"), lat, lon, accuracy, fixTimeSec,
                    obj.getJSONObject("s").toString(),
                    obj.has("d") ? obj.getJSONArray("d").toString() : "",
                    obj.optBoolean("delta", false),
                    ch == null ? "" : ch.toString());
        }
    }
}
