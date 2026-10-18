package com.car2hass.vehicle;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;

public class ChannelAvailabilityTest {
    public static void main(String[] args) throws Exception {
        JSONObject report = new JSONObject();
        JSONArray ch = new JSONArray();
        ch.put(new JSONObject().put("id", "voyah").put("available", true));
        ch.put(new JSONObject().put("id", "diplus").put("available", false));
        ch.put(new JSONObject().put("id", "system").put("available", true));
        report.put("channels", ch);

        Map<String, Boolean> m = ChannelAvailability.fromReport(report);
        if (!Boolean.TRUE.equals(m.get("voyah"))) throw new AssertionError("voyah");
        if (!Boolean.FALSE.equals(m.get("diplus"))) throw new AssertionError("diplus");
        if (!m.containsKey("system")) throw new AssertionError("system missing");
        if (!ChannelAvailability.fromReport(null).isEmpty()) throw new AssertionError("null report");
        if (!ChannelAvailability.fromReport(new JSONObject()).isEmpty()) throw new AssertionError("no channels");

        System.out.println("All ChannelAvailability tests passed.");
    }
}
