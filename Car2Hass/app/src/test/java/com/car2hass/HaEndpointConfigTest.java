package com.car2hass;

import org.json.JSONObject;

public final class HaEndpointConfigTest {
    public static void main(String[] args) throws Exception {
        HaEndpointConfig e = new HaEndpointConfig(
                "primary", "Home", "192.168.1.10:8123",
                false, "BYDSongPlusEV", true, false);
        JSONObject json = e.toJson();
        HaEndpointConfig back = HaEndpointConfig.fromJson(json);
        check(e.id.equals(back.id));
        check(e.name.equals(back.name));
        check(e.address.equals(back.address));
        check(e.https == back.https);
        check(e.carName.equals(back.carName));
        check(e.enabled == back.enabled);
        check(e.commandsEnabled == back.commandsEnabled);
        System.out.println("All HaEndpointConfig tests passed.");
    }
    private static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
}
