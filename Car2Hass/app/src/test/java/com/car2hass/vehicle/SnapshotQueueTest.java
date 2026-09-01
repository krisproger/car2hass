package com.car2hass.vehicle;
import org.json.JSONObject;
import java.util.List;

public class SnapshotQueueTest {
    public static void main(String[] args) throws Exception {
        SnapshotQueue q = new SnapshotQueue();
        q.enqueue(new JSONObject("{\"a\":1}"));
        q.enqueue(new JSONObject("{\"b\":2}"));
        if (q.size() != 2) throw new AssertionError("size=" + q.size());
        List<JSONObject> batch = q.drain();
        if (batch.size() != 2) throw new AssertionError("drain=" + batch.size());
        if (q.size() != 0) throw new AssertionError("not cleared");
        System.out.println("All SnapshotQueue tests passed.");
    }
}
