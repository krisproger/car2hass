package com.car2hass;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

final class HaPayloadJson {
    private HaPayloadJson() {}

    static JSONArray addSnapshot(JSONArray batch, HassClient.Snapshot snap,
                                 DeltaSnapshotBuilder.Result res) throws Exception {
        batch.put(wireSnapshot(snap, res).toJson());
        return batch;
    }

    static HassClient.Snapshot wireSnapshot(HassClient.Snapshot snap,
                                             DeltaSnapshotBuilder.Result res) throws Exception {
        String chJson = "";
        if (res.delta && !res.channels.isEmpty()) {
            JSONObject ch = new JSONObject();
            for (Map.Entry<String, List<String>> e : res.channels.entrySet()) {
                JSONArray arr = new JSONArray();
                for (String key : e.getValue()) arr.put(key);
                ch.put(e.getKey(), arr);
            }
            chJson = ch.toString();
        }
        return new HassClient.Snapshot(snap.timestamp, snap.lat, snap.lon,
                snap.accuracy, snap.fixTimeSec,
                new JSONObject(res.signals).toString(), snap.disabledJson,
                res.delta, chJson);
    }
}
