package com.car2hass;

import org.json.JSONObject;

/** Parsed response from the Home Assistant telemetry endpoint. */
public final class HaResponse {
    public final boolean httpOk;
    public final boolean accepted;
    public final boolean dropped;
    public final String reason;
    public final String body;
    public final int signals;
    public final int snapshots;

    private HaResponse(boolean httpOk, boolean accepted, boolean dropped,
                       String reason, String body, int signals, int snapshots) {
        this.httpOk = httpOk;
        this.accepted = accepted;
        this.dropped = dropped;
        this.reason = reason == null ? "" : reason;
        this.body = body == null ? "" : body;
        this.signals = signals;
        this.snapshots = snapshots;
    }

    public static HaResponse parse(int code, String body) {
        boolean httpOk = code >= 200 && code < 300;
        String raw = body == null ? "" : body.trim();
        if (!httpOk) {
            return new HaResponse(false, false, false, "HTTP " + code, raw, -1, -1);
        }
        if (raw.isEmpty()) {
            return new HaResponse(true, false, false, "empty response", raw, -1, -1);
        }
        try {
            JSONObject o = new JSONObject(raw);
            String status = o.optString("status", "");
            String dropped = o.optString("dropped", "");
            boolean ok = "ok".equals(status) && dropped.isEmpty();
            String reason = dropped.isEmpty() ? status : dropped;
            return new HaResponse(true, ok, !dropped.isEmpty(), reason, raw,
                    o.optInt("signals", -1), o.optInt("snapshots", -1));
        } catch (Exception e) {
            return new HaResponse(true, false, false, "invalid response", raw, -1, -1);
        }
    }

    /** True when the server accepted the batch normally. */
    public boolean ok() {
        return httpOk && accepted;
    }
}
