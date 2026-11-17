package com.car2hass;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;

public final class HaPayloadJsonTest {
    public static void main(String[] args) throws Exception {
        HassClient.Snapshot snap = new HassClient.Snapshot(
                1L, 1.0, 2.0, 3f, 0L, "{\"speed\":\"10\"}", "");
        DeltaSnapshotBuilder.Result res = DeltaSnapshotBuilder.build(
                Collections.singletonMap("speed", "10"),
                Collections.emptyMap(), Collections.emptyMap(), true);
        JSONArray batch = new JSONArray();
        HaPayloadJson.addSnapshot(batch, snap, res);
        check(batch.get(0) instanceof JSONObject);
        check("10".equals(((JSONObject) batch.get(0))
                .getJSONObject("s").getString("speed")));
        System.out.println("All HaPayloadJson tests passed.");
    }
    private static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
}
