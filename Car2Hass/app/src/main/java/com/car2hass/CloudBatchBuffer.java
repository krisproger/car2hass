package com.car2hass;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;

/** Bounded FIFO of cloud telemetry snapshots (pure, testable without Android). */
public final class CloudBatchBuffer {

    public static final int MAX_SNAPSHOTS = 100;

    private final ArrayDeque<JSONObject> queue = new ArrayDeque<>();

    public synchronized void add(JSONObject snapshot) {
        if (snapshot == null) return;
        queue.addLast(snapshot);
        while (queue.size() > MAX_SNAPSHOTS) {
            queue.removeFirst();
        }
    }

    public synchronized void addAll(JSONArray snapshots) {
        if (snapshots == null) return;
        for (int i = 0; i < snapshots.length(); i++) {
            add(snapshots.optJSONObject(i));
        }
    }

    public synchronized JSONArray drain() {
        JSONArray arr = new JSONArray();
        while (!queue.isEmpty()) {
            arr.put(queue.removeFirst());
        }
        return arr;
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized void clear() {
        queue.clear();
    }

    public static JSONObject buildSnapshot(long t, JSONObject sensors, Double lat, Double lon) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("t", t);
        if (lat != null && lon != null && !lat.isNaN() && !lon.isNaN()) {
            JSONObject gps = new JSONObject();
            gps.put("lat", lat);
            gps.put("lon", lon);
            obj.put("g", gps);
        }
        obj.put("s", sensors == null ? new JSONObject() : sensors);
        return obj;
    }

    public static JSONObject buildPayload(String carName, JSONArray batch) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("car_name", carName == null ? "" : carName);
        payload.put("batch", batch == null ? new JSONArray() : batch);
        return payload;
    }
}
