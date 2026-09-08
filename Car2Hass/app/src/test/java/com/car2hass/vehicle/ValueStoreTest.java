package com.car2hass.vehicle;

public class ValueStoreTest {
    public static void main(String[] args) throws Exception {
        ValueStore store = new ValueStore();
        if (store.has("location_lat")) throw new AssertionError("empty store should not have keys");
        store.put("location_lat", "55.1", "system");
        if (!"55.1".equals(store.get("location_lat"))) throw new AssertionError("get mismatch");
        if (!"system".equals(store.sourceOf("location_lat"))) throw new AssertionError("source mismatch");
        if (store.ageMs("location_lat") < 0) throw new AssertionError("age negative");
        store.put("speed", "10", "adb");
        if (store.snapshot().size() != 2) throw new AssertionError("snapshot size");
        System.out.println("All ValueStore tests passed.");
    }
}