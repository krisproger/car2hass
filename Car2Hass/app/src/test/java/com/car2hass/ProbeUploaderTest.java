package com.car2hass;

import org.json.JSONObject;

public class ProbeUploaderTest {
    public static void main(String[] args) throws Exception {
        JSONObject report = new JSONObject("{\"ts\":\"2026-08-21T10:00:00\",\"app_version\":\"2.3.1\"}");
        JSONObject payload = ProbeUploader.buildPayload("abc123", report);
        if (!"abc123".equals(payload.getString("device_anon_id"))) throw new AssertionError("anon id");
        if (!payload.has("report") || !"2.3.1".equals(payload.getJSONObject("report").optString("app_version")))
            throw new AssertionError("report passthrough");
        // null anon id tolerated, null report omitted
        JSONObject p2 = ProbeUploader.buildPayload(null, null);
        if (!"".equals(p2.getString("device_anon_id"))) throw new AssertionError("null id");
        if (p2.has("report")) throw new AssertionError("null report must be omitted");
        System.out.println("All ProbeUploader tests passed.");
    }
}
