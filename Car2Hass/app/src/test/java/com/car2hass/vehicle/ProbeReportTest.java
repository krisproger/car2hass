package com.car2hass.vehicle;

import org.json.JSONObject;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProbeReportTest {
    public static void main(String[] args) throws Exception {
        RegistryStore rs = RegistryStore.of(
                new JSONObject("{\"sensors\":[{\"key\":\"speed\",\"channels\":{\"diplus\":{\"name\":\"车速\"}}}]}"),
                new JSONObject("{\"commands\":[{\"id\":\"ac_on\",\"state_sensor\":\"ac_state\",\"channels\":{\"diplus\":{\"command\":\"x\"}}}]}"),
                new JSONObject("{\"profiles\":[{\"id\":\"byd_generic\",\"expected_sensors\":[\"speed\"]}]}"));

        Map<String, Map<String, ProbeResult>> sr = new HashMap<>();
        Map<String, ProbeResult> speed = new HashMap<>();
        speed.put("diplus", ProbeResult.fromRaw("42", false));
        sr.put("speed", speed);

        Map<String, Boolean> channels = new HashMap<>();
        channels.put("diplus", true);
        channels.put("adb", false);

        Map<String, List<String>> cmd = new HashMap<>();
        cmd.put("ac_on", Arrays.asList("diplus"));

        Map<String, Integer> scores = new HashMap<>();
        scores.put("byd_generic", 1);

        JSONObject j = ProbeReport.build(rs, channels, sr, cmd, scores, "byd_generic", "2.3.1", "anon");
        if (!"byd_generic".equals(j.optString("selected_profile")))
            throw new AssertionError("selected_profile missing");
        if (!"2.3.1".equals(j.optString("app_version"))) throw new AssertionError("app_version");
        if (!j.getJSONObject("sensors").has("speed")) throw new AssertionError("sensors.speed missing");
        if (!"1".equals(j.getJSONObject("profiles").optJSONObject("byd_generic").optString("score")))
            throw new AssertionError("profile score missing");
        if (j.getJSONArray("channels").length() != 2) throw new AssertionError("channels count");

        System.out.println("All ProbeReport tests passed.");
    }
}
