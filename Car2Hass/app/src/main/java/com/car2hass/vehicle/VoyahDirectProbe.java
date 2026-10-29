package com.car2hass.vehicle;

import android.content.Context;

import org.json.JSONObject;

/** Combined raw probe for the direct Voyah binder and its identity gate. */
public final class VoyahDirectProbe {
    private VoyahDirectProbe() {}

    public static JSONObject probe(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            o.put("identity", VoyahIdentityProbe.probe(ctx));
            o.put("direct", VoyahDirectReader.probe());
        } catch (Exception e) {
            try {
                o.put("error", e.getMessage());
            } catch (Exception ignored) {
            }
        }
        return o;
    }
}
