package com.car2hass;

import org.json.JSONObject;

/** Pure JSON transforms for cloud settings payloads (no Android dependency). */
public final class CloudSettingsJson {

    private CloudSettingsJson() {}

    /** Removes the device identity (`car_name`); invalid input passes through. */
    public static String stripIdentity(String json) {
        try {
            JSONObject o = new JSONObject(json);
            o.remove("car_name");
            return o.toString();
        } catch (Exception e) {
            return json;
        }
    }

    /** Removes the HA connection secret (`hass_token`); invalid input passes through. */
    public static String stripSecret(String json) {
        try {
            JSONObject o = new JSONObject(json);
            o.remove("hass_token");
            return o.toString();
        } catch (Exception e) {
            return json;
        }
    }

    public static String extractSecret(String json) {
        try {
            return new JSONObject(json).optString("hass_token", "");
        } catch (Exception e) {
            return "";
        }
    }

    public static String metaJson(String label, long updatedAtMs) {
        try {
            return new JSONObject()
                    .put("label", label == null ? "" : label)
                    .put("updated_at", updatedAtMs)
                    .toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}
