package com.car2hass.vehicle;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Central dispatcher that decides, per sensor and per cycle, which channel to
 * read — derived from the registry, the user's active channels, the selected
 * profile and (best-effort) the last probe report. Replaces the ad-hoc
 * source branching in CANDataReader.refreshSelected.
 */
public final class SourceManager {
    /** Fallback when the registry has no channels_priority block. */
    private static final List<String> CYCLE_PRIORITY = Arrays.asList(
            "diplus", "adb", "dumpsys", "system", "obd", "diplus_push", "byd_cloud");

    private final List<String> cyclePriority;

    private final RegistryStore reg;
    private final List<String> activeChannels;
    private final String selectedProfile;
    private final JSONObject probeReport;

    public SourceManager(RegistryStore reg, List<String> activeChannels,
            String selectedProfile, JSONObject probeReport) {
        this.reg = reg;
        this.activeChannels = activeChannels != null ? normalizeActive(activeChannels) : new ArrayList<>();
        this.selectedProfile = selectedProfile;
        this.probeReport = probeReport;
        List<String> prio = CYCLE_PRIORITY;
        try {
            List<String> ids = reg.channelIds();
            if (!ids.isEmpty()) prio = ids;
        } catch (JSONException ignored) {
        }
        this.cyclePriority = prio;
    }

    public String getSelectedProfile() { return selectedProfile; }

    /** Channels to try for one sensor, in priority order. system sensors always use system. */
    public List<String> orderedChannels(String sensorKey) throws JSONException {
        JSONObject s = reg.getSensor(sensorKey);
        if (s == null) return new ArrayList<>();
        JSONObject channels = s.optJSONObject("channels");
        if (channels == null) return new ArrayList<>();
        // system sensors are always read from system, outside active_channels
        if (channels.has("system") && channels.optJSONObject("system") != null) {
            return Arrays.asList("system");
        }
        List<String> out = new ArrayList<>();
        for (String ch : cyclePriority) {
            if (!activeChannels.contains(ch)) continue;
            if (channels.optJSONObject(ch) == null) continue;
            if (isUnsupportedInReport(sensorKey, ch)) continue;
            out.add(ch);
        }
        return out;
    }

    /** Source priority for a full read cycle (active channels, in fixed order). */
    public List<String> cycleSourcePriority() {
        List<String> out = new ArrayList<>();
        for (String ch : cyclePriority) {
            if (activeChannels.contains(ch)) out.add(ch);
        }
        return out;
    }

    public List<String> allSensorKeys() throws JSONException {
        return reg.sensorKeys();
    }

    /** Builds CANDataItem list from the registry (diplus name / native fid where present). */
    public List<com.car2hass.CANDataItem> buildSignalItems() throws JSONException {
        List<com.car2hass.CANDataItem> list = new ArrayList<>();
        for (String key : reg.sensorKeys()) {
            JSONObject s = reg.getSensor(key);
            JSONObject ch = s.optJSONObject("channels");
            com.car2hass.CANDataItem item = new com.car2hass.CANDataItem(0, key, "", 0);
            item.key = key;
            item.rawData = s.optString("type", "num");
            if (ch != null && ch.optJSONObject("diplus") != null) {
                item.diplusName = ch.optJSONObject("diplus").optString("name");
            }
            list.add(item);
        }
        return list;
    }

    /** Legacy active-channel lists stored old channel ids. */
    private static String normalizeChannelId(String id) {
        if ("sysprops".equals(id)) return "dumpsys";
        if ("native".equals(id)) return "adb";
        return id;
    }

    private static List<String> normalizeActive(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String id : in) out.add(normalizeChannelId(id));
        return out;
    }

    private boolean isUnsupportedInReport(String sensorKey, String ch) {
        if (probeReport == null) return false;
        try {
            JSONObject sensors = probeReport.optJSONObject("sensors");
            if (sensors == null) return false;
            JSONObject per = sensors.optJSONObject(sensorKey);
            if (per == null) return false;
            String status = per.optString(ch, "");
            return "unsupported".equals(status) || "error".equals(status);
        } catch (Exception e) {
            return false;
        }
    }
}
