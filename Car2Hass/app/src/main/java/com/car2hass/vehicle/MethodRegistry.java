package com.car2hass.vehicle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Parses the declarative `methods` block of sensors_registry.json. */
public final class MethodRegistry {
    private MethodRegistry() {}

    public static final class ChannelMethod {
        public final String id, channel, kind, param;
        public final List<String> canaries;
        ChannelMethod(String id, String channel, String kind, String param, List<String> canaries) {
            this.id = id; this.channel = channel; this.kind = kind;
            this.param = param; this.canaries = canaries;
        }
    }

    public static List<ChannelMethod> from(RegistryStore reg) throws org.json.JSONException {
        List<ChannelMethod> out = new ArrayList<>();
        JSONArray arr = reg.methods();
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONObject m = arr.optJSONObject(i);
            if (m == null) continue;
            List<String> canaries = new ArrayList<>();
            JSONArray c = m.optJSONArray("canaries");
            for (int j = 0; c != null && j < c.length(); j++) canaries.add(c.optString(j));
            out.add(new ChannelMethod(m.optString("id"), m.optString("channel"),
                    m.optString("kind"), m.optString("param"), canaries));
        }
        return out;
    }
}
