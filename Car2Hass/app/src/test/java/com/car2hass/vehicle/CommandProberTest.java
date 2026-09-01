package com.car2hass.vehicle;

import org.json.JSONObject;

import java.util.List;

public class CommandProberTest {
    public static void main(String[] args) throws Exception {
        RegistryStore rs = RegistryStore.of(
                new JSONObject("{\"sensors\":[]}"),
                new JSONObject("{\"commands\":["
                        + "{\"id\":\"ac_on\",\"state_sensor\":\"ac_state\","
                        + "\"channels\":{\"diplus\":{\"command\":\"迪加空调\"},\"adb\":{\"dev\":1,\"fid\":2}}}"
                        + "]}"),
                new JSONObject("{\"profiles\":[]}"));

        List<String> cb = CommandProber.callableBy(rs, "ac_on");
        if (!cb.contains("diplus")) throw new AssertionError("ac_on missing diplus: " + cb);
        if (!cb.contains("adb")) throw new AssertionError("ac_on missing native: " + cb);
        if (cb.contains("obd")) throw new AssertionError("ac_on should not have obd: " + cb);

        System.out.println("All CommandProber tests passed.");
    }
}
