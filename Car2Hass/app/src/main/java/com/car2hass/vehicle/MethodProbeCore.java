package com.car2hass.vehicle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Runs each declared channel method's canaries through an injected invoker and
 * assembles a `method_probe` report. Pure: no Android, no channel classes.
 */
public final class MethodProbeCore {
    private MethodProbeCore() {}

    public interface CanaryInvoker {
        ProbeResult probe(String kind, String param);
    }

    public static JSONObject build(List<MethodRegistry.ChannelMethod> methods,
                                   CanaryInvoker invoker) throws org.json.JSONException {
        JSONObject root = new JSONObject();
        root.put("ts", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
                java.util.Locale.US).format(new java.util.Date()));
        root.put("app_version", "unknown");
        root.put("device_anon", "anon");
        root.put("channels", new JSONArray());
        root.put("profiles", new JSONObject());
        root.put("selected_profile", JSONObject.NULL);
        root.put("sensors", new JSONObject());
        root.put("commands", new JSONObject());
        root.put("report_type", "method_probe");
        root.put("methods", methods(methods, invoker));
        return ReportPrivacyFilter.sanitize(root);
    }

    public static JSONArray methods(List<MethodRegistry.ChannelMethod> methods,
                                    CanaryInvoker invoker) throws org.json.JSONException {
        JSONArray out = new JSONArray();
        for (MethodRegistry.ChannelMethod m : methods) {
            JSONObject mo = new JSONObject();
            mo.put("id", m.id);
            mo.put("channel", m.channel);
            mo.put("kind", m.kind);
            mo.put("param", m.param);
            JSONArray results = new JSONArray();
            for (String canary : m.canaries) {
                JSONObject r = new JSONObject();
                r.put("sensor", canary);
                try {
                    ProbeResult pr = invoker.probe(m.kind, m.param);
                    r.put("status", pr.status.name().toLowerCase());
                    r.put("raw", pr.rawValue == null ? "" : pr.rawValue);
                } catch (Throwable t) {
                    r.put("status", "error");
                    r.put("raw", "");
                    r.put("error", String.valueOf(t.getMessage()));
                }
                results.put(r);
            }
            mo.put("canaries", results);
            out.put(mo);
        }
        return out;
    }
}
