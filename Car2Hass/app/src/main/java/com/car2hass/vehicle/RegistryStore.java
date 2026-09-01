package com.car2hass.vehicle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Loads the Phase-1 telemetry registries (sensors/commands/profiles) from assets
 * and exposes query helpers used by the probe engine. The {@link #of} factory
 * allows tests to inject parsed JSON without an Android Context.
 */
public final class RegistryStore {
    private final JSONObject sensorsRoot;
    private final JSONObject commandsRoot;
    private final JSONObject profilesRoot;

    private RegistryStore(JSONObject sensors, JSONObject commands, JSONObject profiles) {
        this.sensorsRoot = sensors;
        this.commandsRoot = commands;
        this.profilesRoot = profiles;
    }

    public static RegistryStore load(android.content.Context ctx) throws Exception {
        return new RegistryStore(
                readAsset(ctx, "sensors_registry.json"),
                readAsset(ctx, "commands_registry.json"),
                readAsset(ctx, "car_profiles.json"));
    }

    public static RegistryStore of(JSONObject sensors, JSONObject commands, JSONObject profiles) {
        return new RegistryStore(sensors, commands, profiles);
    }

    private static JSONObject readAsset(android.content.Context ctx, String name) throws Exception {
        try (InputStream is = ctx.getAssets().open(name)) {
            return new JSONObject(new Scanner(is, "UTF-8").useDelimiter("\\A").next());
        }
    }

    public int sensorCount() throws org.json.JSONException {
        return sensorsRoot.optJSONArray("sensors").length();
    }

    public List<String> sensorKeys() throws org.json.JSONException {
        List<String> out = new ArrayList<>();
        JSONArray a = sensorsRoot.optJSONArray("sensors");
        for (int i = 0; i < a.length(); i++) {
            out.add(a.getJSONObject(i).getString("key"));
        }
        return out;
    }

    public JSONObject getSensor(String key) throws org.json.JSONException {
        JSONArray a = sensorsRoot.optJSONArray("sensors");
        for (int i = 0; i < a.length(); i++) {
            JSONObject s = a.getJSONObject(i);
            if (key.equals(s.optString("key"))) return s;
        }
        return null;
    }

    public JSONObject sensorChannel(String key, String channel) throws org.json.JSONException {
        JSONObject s = getSensor(key);
        if (s == null) return null;
        JSONObject channels = s.optJSONObject("channels");
        if (channels == null) return null;
        return channels.optJSONObject(channel);
    }

    /** Canonical channel order used when sorting the per-sensor key union. */
    private static final List<String> CANONICAL_CHANNEL_ORDER = java.util.Arrays.asList(
            "diplus", "adb", "dumpsys", "system", "obd", "diplus_push", "byd_cloud", "voyah");

    /**
     * Channel ids in canonical priority order from sensors_registry.json
     * ("channels_priority"). Falls back to the union of per-sensor channel
     * keys (ordered canonically) when the priority block is missing.
     */
    public List<String> channelIds() throws org.json.JSONException {
        List<String> out = new ArrayList<>();
        JSONArray prio = sensorsRoot.optJSONArray("channels_priority");
        if (prio != null) {
            for (int i = 0; i < prio.length(); i++) out.add(prio.getString(i));
            return out;
        }
        java.util.LinkedHashSet<String> union = new java.util.LinkedHashSet<>();
        JSONArray a = sensorsRoot.optJSONArray("sensors");
        for (int i = 0; i < a.length(); i++) {
            JSONObject ch = a.getJSONObject(i).optJSONObject("channels");
            if (ch == null) continue;
            java.util.Iterator<String> it = ch.keys();
            while (it.hasNext()) union.add(it.next());
        }
        // JSONObject.keys() order is unstable — sort by canonical order first.
        out.addAll(union);
        out.sort(java.util.Comparator.comparingInt(id -> {
            int idx = CANONICAL_CHANNEL_ORDER.indexOf(id);
            return idx >= 0 ? idx : CANONICAL_CHANNEL_ORDER.size();
        }));
        return out;
    }

    public List<String> profileIds() throws org.json.JSONException {
        List<String> out = new ArrayList<>();
        JSONArray a = profilesRoot.optJSONArray("profiles");
        for (int i = 0; i < a.length(); i++) {
            out.add(a.getJSONObject(i).getString("id"));
        }
        return out;
    }

    public List<String> profileSensorKeys(String profileId) throws org.json.JSONException {
        List<String> out = new ArrayList<>();
        JSONArray a = profilesRoot.optJSONArray("profiles");
        for (int i = 0; i < a.length(); i++) {
            JSONObject p = a.getJSONObject(i);
            if (profileId.equals(p.optString("id"))) {
                JSONArray arr = p.optJSONArray("expected_sensors");
                for (int j = 0; arr != null && j < arr.length(); j++) {
                    out.add(arr.getString(j));
                }
            }
        }
        return out;
    }

    public List<String> genericSensors() throws org.json.JSONException {
        List<String> out = new ArrayList<>();
        JSONArray a = profilesRoot.optJSONArray("generic_sensors");
        for (int i = 0; a != null && i < a.length(); i++) {
            out.add(a.getString(i));
        }
        return out;
    }

    public boolean isGenericSensor(String key) throws org.json.JSONException {
        return genericSensors().contains(key);
    }

    public String profileKeyChannel(String profileId) throws org.json.JSONException {
        JSONArray a = profilesRoot.optJSONArray("profiles");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject p = a.getJSONObject(i);
            if (profileId.equals(p.optString("id"))) {
                String kc = p.optString("key_channel", null);
                return kc != null && !kc.isEmpty() ? kc : null;
            }
        }
        return null;
    }

    public List<String> profileNonGenericSensors(String profileId) throws org.json.JSONException {
        List<String> out = new ArrayList<>();
        for (String k : profileSensorKeys(profileId)) {
            if (!isGenericSensor(k)) out.add(k);
        }
        return out;
    }

    /** Human-readable profile label from car_profiles.json, falls back to the id. */
    public String profileLabel(String profileId) throws org.json.JSONException {
        JSONArray a = profilesRoot.optJSONArray("profiles");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject p = a.getJSONObject(i);
            if (profileId.equals(p.optString("id"))) {
                String label = p.optString("label", null);
                return label != null ? label : profileId;
            }
        }
        return profileId;
    }

    public List<String> commandIds() throws org.json.JSONException {
        List<String> out = new ArrayList<>();
        JSONArray a = commandsRoot.optJSONArray("commands");
        for (int i = 0; i < a.length(); i++) {
            out.add(a.getJSONObject(i).getString("id"));
        }
        return out;
    }

    public JSONObject commandChannel(String id, String channel) throws org.json.JSONException {
        JSONArray a = commandsRoot.optJSONArray("commands");
        for (int i = 0; i < a.length(); i++) {
            JSONObject c = a.getJSONObject(i);
            if (id.equals(c.optString("id"))) {
                JSONObject channels = c.optJSONObject("channels");
                return channels == null ? null : channels.optJSONObject(channel);
            }
        }
        return null;
    }

    public String commandStateSensor(String id) throws org.json.JSONException {
        JSONArray a = commandsRoot.optJSONArray("commands");
        for (int i = 0; i < a.length(); i++) {
            JSONObject c = a.getJSONObject(i);
            if (id.equals(c.optString("id"))) return c.optString("state_sensor", null);
        }
        return null;
    }
}
