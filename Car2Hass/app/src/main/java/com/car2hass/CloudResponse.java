package com.car2hass;

import org.json.JSONObject;

/** Parsed response from the mytechnic.ru cloud telemetry endpoint. */
public final class CloudResponse {
    public final boolean httpOk;
    public final boolean ok;
    public final int accepted;
    public final String reason;
    public final String body;

    private CloudResponse(boolean httpOk, boolean ok, int accepted,
                          String reason, String body) {
        this.httpOk = httpOk;
        this.ok = ok;
        this.accepted = accepted;
        this.reason = reason == null ? "" : reason;
        this.body = body == null ? "" : body;
    }

    public static CloudResponse parse(int code, String body) {
        boolean httpOk = code >= 200 && code < 300;
        String raw = body == null ? "" : body.trim();
        if (!httpOk) {
            return new CloudResponse(false, false, -1, "HTTP " + code, raw);
        }
        if (raw.isEmpty()) {
            return new CloudResponse(true, false, -1, "empty response", raw);
        }
        try {
            JSONObject o = new JSONObject(raw);
            boolean ok = o.optBoolean("ok", false);
            int accepted = o.optInt("accepted", -1);
            return new CloudResponse(true, ok, accepted,
                    ok ? "" : o.optString("error", "not accepted"), raw);
        } catch (Exception e) {
            return new CloudResponse(true, false, -1, "invalid response", raw);
        }
    }
}
