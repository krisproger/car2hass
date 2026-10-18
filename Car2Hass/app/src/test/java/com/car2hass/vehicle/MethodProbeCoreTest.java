package com.car2hass.vehicle;

import org.json.JSONObject;
import java.util.List;

public class MethodProbeCoreTest {
    public static void main(String[] args) throws Exception {
        MethodRegistry.ChannelMethod m = new MethodRegistry.ChannelMethod(
                "voyah:icanbus_getter:getEngineSpeed", "voyah", "icanbus_getter",
                "getEngineSpeed", java.util.Collections.singletonList("engine_rpm"));

        MethodProbeCore.CanaryInvoker ok = (kind, param) -> ProbeResult.fromRaw("1200", false);
        JSONObject r = MethodProbeCore.build(java.util.Collections.singletonList(m), ok);
        if (!"method_probe".equals(r.getString("report_type"))) throw new AssertionError("type");
        if (!r.has("ts") || !r.has("app_version") || !r.has("channels")
                || !r.has("profiles") || !r.has("selected_profile")
                || !r.has("sensors") || !r.has("commands")) throw new AssertionError("required keys");
        JSONObject method = r.getJSONArray("methods").getJSONObject(0);
        JSONObject canary = method.getJSONArray("canaries").getJSONObject(0);
        if (!"engine_rpm".equals(canary.getString("sensor"))) throw new AssertionError("sensor");
        if (!"ok".equals(canary.getString("status"))) throw new AssertionError("status");
        if (!"1200".equals(canary.getString("raw"))) throw new AssertionError("raw");

        MethodProbeCore.CanaryInvoker boom = (kind, param) -> { throw new RuntimeException("nope"); };
        JSONObject r2 = MethodProbeCore.build(java.util.Collections.singletonList(m), boom);
        JSONObject c2 = r2.getJSONArray("methods").getJSONObject(0)
                .getJSONArray("canaries").getJSONObject(0);
        if (!"error".equals(c2.getString("status"))) throw new AssertionError("error status");

        System.out.println("All MethodProbeCore tests passed.");
    }
}
