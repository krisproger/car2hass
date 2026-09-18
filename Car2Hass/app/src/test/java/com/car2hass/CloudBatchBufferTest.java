package com.car2hass;

import org.json.JSONArray;
import org.json.JSONObject;

public class CloudBatchBufferTest {

    public static void main(String[] args) throws Exception {
        snapshotWithGps();
        snapshotWithoutGps();
        snapshotSkipsNanGps();
        boundAndDrain();
        addAllHonoursBound();
        payloadShape();
        System.out.println("All CloudBatchBuffer tests passed.");
    }

    private static void snapshotWithGps() throws Exception {
        JSONObject s = new JSONObject();
        s.put("soc", 72);
        JSONObject snap = CloudBatchBuffer.buildSnapshot(1700000000L, s, 55.75, 37.62);
        if (snap.getLong("t") != 1700000000L) throw new AssertionError("t mismatch");
        JSONObject g = snap.getJSONObject("g");
        if (Math.abs(g.getDouble("lat") - 55.75) > 1e-9) throw new AssertionError("lat mismatch");
        if (Math.abs(g.getDouble("lon") - 37.62) > 1e-9) throw new AssertionError("lon mismatch");
        if (snap.getJSONObject("s").getInt("soc") != 72) throw new AssertionError("sensors mismatch");
    }

    private static void snapshotWithoutGps() throws Exception {
        JSONObject snap = CloudBatchBuffer.buildSnapshot(1L, new JSONObject(), null, null);
        if (snap.has("g")) throw new AssertionError("g must be absent without a fix");
    }

    private static void snapshotSkipsNanGps() throws Exception {
        JSONObject snap = CloudBatchBuffer.buildSnapshot(1L, new JSONObject(), Double.NaN, Double.NaN);
        if (snap.has("g")) throw new AssertionError("g must be absent for NaN fix");
    }

    private static void boundAndDrain() throws Exception {
        CloudBatchBuffer b = new CloudBatchBuffer();
        for (int i = 0; i < CloudBatchBuffer.MAX_SNAPSHOTS + 50; i++) {
            b.add(CloudBatchBuffer.buildSnapshot(i, new JSONObject(), null, null));
        }
        if (b.size() != CloudBatchBuffer.MAX_SNAPSHOTS) {
            throw new AssertionError("size must be capped at " + CloudBatchBuffer.MAX_SNAPSHOTS + ", got " + b.size());
        }
        JSONArray arr = b.drain();
        if (arr.length() != CloudBatchBuffer.MAX_SNAPSHOTS) throw new AssertionError("drain length mismatch");
        if (arr.getJSONObject(0).getLong("t") != 50) throw new AssertionError("oldest entries must be dropped");
        if (!b.isEmpty()) throw new AssertionError("buffer must be empty after drain");
    }

    private static void addAllHonoursBound() throws Exception {
        CloudBatchBuffer b = new CloudBatchBuffer();
        JSONArray arr = new JSONArray();
        for (int i = 0; i < CloudBatchBuffer.MAX_SNAPSHOTS + 10; i++) {
            arr.put(CloudBatchBuffer.buildSnapshot(i, new JSONObject(), null, null));
        }
        b.addAll(arr);
        if (b.size() != CloudBatchBuffer.MAX_SNAPSHOTS) throw new AssertionError("addAll must enforce the bound");
    }

    private static void payloadShape() throws Exception {
        JSONArray batch = new JSONArray();
        batch.put(CloudBatchBuffer.buildSnapshot(1, new JSONObject(), null, null));
        JSONObject p = CloudBatchBuffer.buildPayload("BYD Song Pro", batch);
        if (!"BYD Song Pro".equals(p.getString("car_name"))) throw new AssertionError("car_name mismatch");
        if (p.getJSONArray("batch").length() != 1) throw new AssertionError("batch mismatch");
    }
}
