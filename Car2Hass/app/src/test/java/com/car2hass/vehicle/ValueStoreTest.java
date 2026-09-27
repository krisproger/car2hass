package com.car2hass.vehicle;

public class ValueStoreTest {
    public static void main(String[] args) throws Exception {
        ValueStore store = new ValueStore();
        if (store.has("location_lat")) throw new AssertionError("empty store should not have keys");
        store.put("location_lat", "55.1", "system");
        if (!"55.1".equals(store.get("location_lat"))) throw new AssertionError("get mismatch");
        if (!"system".equals(store.sourceOf("location_lat"))) throw new AssertionError("source mismatch");
        if (store.ageMs("location_lat") < 0) throw new AssertionError("age negative");
        if (store.updatedAt("location_lat") <= 0) throw new AssertionError("updatedAt not exposed");
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

        // ── channel priority merge ──────────────────────────────────────────
        ValueStore prio = new ValueStore();
        final int[] notified = {0};
        prio.addListener((k, v, s) -> notified[0]++);

        prio.put("cabin_temp", "20", "diplus");
        if (!"20".equals(prio.get("cabin_temp")) || !"diplus".equals(prio.sourceOf("cabin_temp")))
            throw new AssertionError("first value not stored");
        if (notified[0] != 1) throw new AssertionError("listener should fire on first accepted put");

        prio.put("cabin_temp", "21", "adb");
        if (!"21".equals(prio.get("cabin_temp")) || !"adb".equals(prio.sourceOf("cabin_temp")))
            throw new AssertionError("higher priority must replace");
        if (notified[0] != 2) throw new AssertionError("listener should fire on higher-priority replace");

        prio.put("cabin_temp", "22", "diplus");
        if (!"21".equals(prio.get("cabin_temp")) || !"adb".equals(prio.sourceOf("cabin_temp")))
            throw new AssertionError("lower priority must be ignored");
        if (notified[0] != 2) throw new AssertionError("listener must not fire for ignored put");

        prio.put("cabin_temp", "23", "adb");
        if (!"23".equals(prio.get("cabin_temp"))) throw new AssertionError("same channel must keep newer value");
        if (notified[0] != 3) throw new AssertionError("listener should fire on same-channel replace");

        prio.put("cabin_temp", "24", "weird");
        if (!"23".equals(prio.get("cabin_temp")) || !"adb".equals(prio.sourceOf("cabin_temp")))
            throw new AssertionError("unknown channel must be lowest");
        if (notified[0] != 3) throw new AssertionError("listener must not fire for unknown channel");

        prio.put("cabin_temp", "25", "system");
        if (!"23".equals(prio.get("cabin_temp")) || !"adb".equals(prio.sourceOf("cabin_temp")))
            throw new AssertionError("system must not override the higher-ranked adb");
        if (notified[0] != 3) throw new AssertionError("listener must not fire for lower-ranked system");

        ValueStore rank2 = new ValueStore();
        rank2.put("x", "1", "dumpsys");
        rank2.put("x", "2", "system");
        if (!"2".equals(rank2.get("x")) || !"system".equals(rank2.sourceOf("x")))
            throw new AssertionError("system must override dumpsys");

        ValueStore restoredPrio = new ValueStore();
        restoredPrio.put("doors_state", "1", ValueStore.SOURCE_RESTORED);
        restoredPrio.put("doors_state", "2", "adb");
        restoredPrio.put("doors_state", "3", ValueStore.SOURCE_RESTORED);
        if (!"2".equals(restoredPrio.get("doors_state")) || !"adb".equals(restoredPrio.sourceOf("doors_state")))
            throw new AssertionError("restored must not overwrite a live channel");

        System.out.println("All ValueStore tests passed.");
    }
}
