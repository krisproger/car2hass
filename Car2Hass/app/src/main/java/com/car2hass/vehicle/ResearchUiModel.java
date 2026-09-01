package com.car2hass.vehicle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure model for the "System research" settings section (testable without Android). */
public final class ResearchUiModel {
    /** Fallback when the registry has no channels_priority block. */
    public static final List<String> CYCLE_PRIORITY = Arrays.asList(
            "diplus", "adb", "dumpsys", "system", "obd", "diplus_push", "byd_cloud");

    public static final class ProfileOption {
        public final String id;
        public final String label;
        public ProfileOption(String id, String label) { this.id = id; this.label = label; }
    }

    public static final class ChannelView {
        public final String name;
        public final boolean available;
        public final boolean checked;
        public ChannelView(String name, boolean available, boolean checked) {
            this.name = name; this.available = available; this.checked = checked;
        }
    }

    public static List<ProfileOption> profiles(RegistryStore reg) throws JSONException {
        List<ProfileOption> out = new ArrayList<>();
        for (String id : reg.profileIds()) {
            out.add(new ProfileOption(id, reg.profileLabel(id)));
        }
        return out;
    }

    /**
     * Channel list in SourceManager priority order. Availability comes from the
     * probe report (all available when there is no report); system is always
     * available and checked.
     */
    public static List<ChannelView> channels(RegistryStore reg, JSONObject report,
                                             List<String> active) throws JSONException {
        List<String> activeList = active != null ? active : new ArrayList<>();
        Map<String, Boolean> avail = new HashMap<>();
        if (report != null) {
            JSONArray arr = report.optJSONArray("channels");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) avail.put(normalizeChannelId(o.optString("id")),
                            o.optBoolean("available", true));
                }
            } else {
                JSONObject jo = report.optJSONObject("channels");
                if (jo != null) {
                    JSONArray names = jo.names();
                    for (int i = 0; names != null && i < names.length(); i++) {
                        String k = names.optString(i);
                        avail.put(k, jo.optBoolean(k, true));
                    }
                }
            }
        }
        List<ChannelView> out = new ArrayList<>();
        List<String> priority = CYCLE_PRIORITY;
        try {
            List<String> ids = reg.channelIds();
            if (!ids.isEmpty()) {
                // Keep the always-on system entry even if the registry omits it.
                java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(ids);
                if (!merged.contains("system")) merged.add("system");
                priority = new ArrayList<>(merged);
            }
        } catch (JSONException ignored) {
        }
        for (String name : priority) {
            boolean isAvailable = avail.isEmpty() || avail.get(name) == Boolean.TRUE;
            boolean isChecked = "system".equals(name) || activeList.contains(normalizeChannelId(name));
            out.add(new ChannelView(name, isAvailable, isChecked));
        }
        return out;
    }

    /** Legacy ids from older probe reports / saved settings. */
    public static String normalizeChannelId(String id) {
        if ("sysprops".equals(id)) return "dumpsys";
        if ("native".equals(id)) return "adb";
        return id;
    }

    /**
     * Auto-probes may only ADD channels: a channel the user enabled stays
     * enabled even when it fails a check (transient failures are normal).
     * Removal happens exclusively by unchecking in the UI.
     */
    public static List<String> unionActive(List<String> previous, List<String> alive) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (previous != null) {
            for (String id : previous) out.add(normalizeChannelId(id));
        }
        if (alive != null) {
            for (String id : alive) out.add(normalizeChannelId(id));
        }
        return new ArrayList<>(out);
    }
}
