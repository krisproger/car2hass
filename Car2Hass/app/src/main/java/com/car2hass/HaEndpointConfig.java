package com.car2hass;

import org.json.JSONObject;

public final class HaEndpointConfig {
    public final String id;
    public final String name;
    public final String address;
    public final boolean https;
    public final String carName;
    public final boolean enabled;
    public final boolean commandsEnabled;

    public HaEndpointConfig(String id, String name, String address, boolean https,
                            String carName, boolean enabled, boolean commandsEnabled) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.https = https;
        this.carName = carName == null ? "" : carName;
        this.enabled = enabled;
        this.commandsEnabled = commandsEnabled;
    }

    public HaAddress haAddress() {
        return HaAddress.parse(address, https);
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("address", address);
        o.put("https", https);
        o.put("car_name", carName);
        o.put("enabled", enabled);
        o.put("commands_enabled", commandsEnabled);
        return o;
    }

    public static HaEndpointConfig fromJson(JSONObject o) throws Exception {
        return new HaEndpointConfig(
                o.getString("id"),
                o.optString("name", "Home Assistant"),
                o.getString("address"),
                o.optBoolean("https", false),
                o.optString("car_name", ""),
                o.optBoolean("enabled", true),
                o.optBoolean("commands_enabled", false));
    }
}
