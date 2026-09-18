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
        if (store.isRestored("speed")) throw new AssertionError("live key reported as restored");

        ValueStore restored = new ValueStore();
        restored.put("doors_state", "2", ValueStore.SOURCE_RESTORED);
        restored.put("speed", "10", "channel");
        if (!restored.isRestored("doors_state")) throw new AssertionError("restored key not detected");
        if (restored.isRestored("speed")) throw new AssertionError("live key after restore reported as restored");
        if (restored.isRestored("missing")) throw new AssertionError("missing key reported as restored");
        restored.put("doors_state", "0", "channel");
        if (restored.isRestored("doors_state")) throw new AssertionError("key stayed restored after live update");

        System.out.println("All ValueStore tests passed.");
    }
}