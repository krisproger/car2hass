package com.car2hass.vehicle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the structured {@code probe_report.json} described in spec Section 2.
 * Pure construction is testable without Android; {@link #writeFile} needs a Context.
 */
public final class ProbeReport {

    private ProbeReport() {}

    public static JSONObject build(RegistryStore reg, Map<String, Boolean> channelAvailability,
            Map<String, Map<String, ProbeResult>> sensorResults,
            Map<String, List<String>> commandCallable,
            Map<String, Integer> scores, String selected,
            String appVersion, String deviceAnon) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("ts", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
        root.put("app_version", appVersion == null ? "unknown" : appVersion);
        root.put("device_anon", deviceAnon == null ? "anon" : deviceAnon);

        JSONArray chArr = new JSONArray();
        if (channelAvailability != null) {
            for (Map.Entry<String, Boolean> e : channelAvailability.entrySet()) {
                JSONObject c = new JSONObject();
                c.put("id", e.getKey());
                c.put("available", e.getValue());
                chArr.put(c);
            }
        }
        root.put("channels", chArr);

        JSONObject prof = new JSONObject();
        for (String pid : reg.profileIds()) {
            JSONObject p = new JSONObject();
            Integer s = scores.get(pid);
            p.put("score", s == null ? 0 : s);
            prof.put(pid, p);
        }
        root.put("profiles", prof);
        root.put("selected_profile", selected);

        JSONObject sens = new JSONObject();
        if (sensorResults != null) {
            for (Map.Entry<String, Map<String, ProbeResult>> e : sensorResults.entrySet()) {
                JSONObject s = new JSONObject();
                for (Map.Entry<String, ProbeResult> per : e.getValue().entrySet()) {
                    s.put(per.getKey(), per.getValue().status.name().toLowerCase());
                }
                sens.put(e.getKey(), s);
            }
        }
        root.put("sensors", sens);

        JSONObject cmds = new JSONObject();
        if (commandCallable != null) {
            for (Map.Entry<String, List<String>> e : commandCallable.entrySet()) {
                JSONObject c = new JSONObject();
                c.put("callable_by", new JSONArray(e.getValue()));
                cmds.put(e.getKey(), c);
            }
        }
        root.put("commands", cmds);

        return root;
    }

    public static String writeFile(android.content.Context ctx, JSONObject report) {
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) dir = ctx.getFilesDir();
            dir.mkdirs();
            File f = new File(dir, "probe_report.json");
            try (FileWriter w = new FileWriter(f)) {
                w.write(report.toString(2));
            }
            return f.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }
}
