package com.car2hass.vehicle;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class MethodProberTest {
    public static void main(String[] args) throws Exception {
        JSONObject sensors = new JSONObject();
        JSONArray methods = new JSONArray();
        methods.put(new JSONObject().put("id", "voyah:icanbus_getter:getEngineSpeed")
                .put("channel", "voyah").put("kind", "icanbus_getter")
                .put("param", "getEngineSpeed")
                .put("canaries", new JSONArray().put("engine_rpm")));
        sensors.put("methods", methods);
        RegistryStore reg = RegistryStore.of(sensors, new JSONObject(), new JSONObject());

        // fail-open: empty map keeps methods.
        JSONArray a = MethodProber.methodsFor(reg, new HashMap<>());
        if (a.length() != 1) throw new AssertionError("fail-open length " + a.length());

        // explicitly dead channel drops its methods.
        Map<String, Boolean> dead = new HashMap<>();
        dead.put("voyah", false);
        if (MethodProber.methodsFor(reg, dead).length() != 0) throw new AssertionError("dead not dropped");

        // alive channel keeps them.
        Map<String, Boolean> alive = new HashMap<>();
        alive.put("voyah", true);
        if (MethodProber.methodsFor(reg, alive).length() != 1) throw new AssertionError("alive dropped");

        System.out.println("All MethodProber tests passed.");
    }
}
