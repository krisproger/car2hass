package com.car2hass;

import org.json.JSONArray;
import org.json.JSONObject;

public final class CloudSettingsPayloadMultiTest {
    public static void main(String[] args) throws Exception {
        JSONObject cfg = new JSONObject();
        JSONArray list = new JSONArray();
        list.put(new JSONObject()
                .put("id", "primary")
                .put("name", "Home")
                .put("address", "192.168.1.10:8123")
                .put("https", false)
                .put("car_name", "Car")
                .put("enabled", true)
                .put("commands_enabled", false));
        cfg.put("hass_endpoints", list);
        check("primary".equals(cfg.getJSONArray("hass_endpoints")
                .getJSONObject(0).getString("id")));
        System.out.println("All CloudSettingsPayloadMulti tests passed.");
    }
    private static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
}
