package com.car2hass.vehicle;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class MethodRegistryTest {
    public static void main(String[] args) throws Exception {
        JSONObject sensors = new JSONObject();
        JSONArray methods = new JSONArray();
        methods.put(new JSONObject().put("id", "voyah:icanbus_getter:getEngineSpeed")
                .put("channel", "voyah").put("kind", "icanbus_getter")
                .put("param", "getEngineSpeed")
                .put("canaries", new JSONArray().put("engine_rpm")));
        sensors.put("methods", methods);
        RegistryStore reg = RegistryStore.of(sensors, new JSONObject(), new JSONObject());

        List<MethodRegistry.ChannelMethod> m = MethodRegistry.from(reg);
        if (m.size() != 1) throw new AssertionError("size " + m.size());
        if (!"voyah".equals(m.get(0).channel)) throw new AssertionError("channel");
        if (!"getEngineSpeed".equals(m.get(0).param)) throw new AssertionError("param");
        if (!m.get(0).canaries.equals(java.util.Collections.singletonList("engine_rpm")))
            throw new AssertionError("canaries");

        RegistryStore empty = RegistryStore.of(new JSONObject(), new JSONObject(), new JSONObject());
        if (!MethodRegistry.from(empty).isEmpty()) throw new AssertionError("empty");

        System.out.println("All MethodRegistry tests passed.");
    }
}
