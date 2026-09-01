package com.car2hass.vehicle;
import java.util.Map;

public class SnapshotStoreTest {
    public static void main(String[] args) throws Exception {
        SnapshotStore s = new SnapshotStore();
        s.put("speed", "50");
        if (!"50".equals(s.get("speed"))) throw new AssertionError("get");
        if (!"---".equals(s.getOrDefault("missing", "---"))) throw new AssertionError("default");
        Map<String,String> copy = s.snapshot();
        s.put("speed", "60");
        if (!"50".equals(copy.get("speed"))) throw new AssertionError("snapshot is not a copy");
        s.setLocation(55.7, 37.6, 12.5f, 90.0f, 150.0, 5.0f, "gps", 123L);
        if (s.getLocation().lat != 55.7) throw new AssertionError("lat");
        if (s.getLocation().speed != 12.5f) throw new AssertionError("speed");
        System.out.println("All SnapshotStore tests passed.");
    }
}
