package com.diplustohass;

import org.json.JSONObject;

import java.util.UUID;

public class GeofenceZone {
    public String id;
    public String name;
    public double latitude;
    public double longitude;
    public float radius;
    /** Epoch millis of the last outside→inside transition; 0 = never visited. */
    public long lastVisitedAtMs = 0;

    public GeofenceZone() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
    }

    public GeofenceZone(String name, double latitude, double longitude, float radius) {
        this();
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }

    public static GeofenceZone fromJson(JSONObject o) {
        GeofenceZone z = new GeofenceZone();
        z.id = o.optString("id", z.id);
        z.name = o.optString("name", "");
        z.latitude = o.optDouble("latitude", 0);
        z.longitude = o.optDouble("longitude", 0);
        z.radius = (float) o.optDouble("radius", 100);
        z.lastVisitedAtMs = o.optLong("lastVisitedAtMs", 0);
        return z;
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("latitude", latitude);
            o.put("longitude", longitude);
            o.put("radius", radius);
            o.put("lastVisitedAtMs", lastVisitedAtMs);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
