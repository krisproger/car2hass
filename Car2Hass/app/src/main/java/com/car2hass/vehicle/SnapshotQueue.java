package com.car2hass.vehicle;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** In-memory snapshot buffer; drain() atomically returns and clears the batch. */
public final class SnapshotQueue {
    private final ConcurrentLinkedQueue<JSONObject> q = new ConcurrentLinkedQueue<>();

    public void enqueue(JSONObject snap) {
        if (snap != null) q.add(snap);
    }

    public List<JSONObject> drain() {
        List<JSONObject> out = new ArrayList<>();
        JSONObject s;
        while ((s = q.poll()) != null) out.add(s);
        return out;
    }

    public int size() { return q.size(); }
}
