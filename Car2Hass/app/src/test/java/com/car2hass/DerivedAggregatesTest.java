package com.car2hass;

import com.car2hass.vehicle.ValueStore;

public class DerivedAggregatesTest {

    public static void main(String[] args) {
        testAllLocked();
        testOneUnlocked();
        testOneUnlockedRawNumeric();
        testMissingUnknown();
        testEmptyInput();
        testRestoredIgnored();

        System.out.println("All DerivedAggregates tests passed.");
    }

    private static void testAllLocked() {
        ValueStore store = new ValueStore();
        store.put("driver_door_lock", "locked", "channel");
        store.put("passenger_door_lock", "locked", "channel");
        store.put("rear_left_door_lock", "locked", "channel");
        store.put("rear_right_door_lock", "locked", "channel");
        assertTrue(DerivedAggregates.doorsAllLocked(store), "all locked");
    }

    private static void testOneUnlocked() {
        ValueStore store = new ValueStore();
        store.put("driver_door_lock", "locked", "channel");
        store.put("passenger_door_lock", "unlocked", "channel");
        store.put("rear_left_door_lock", "locked", "channel");
        store.put("rear_right_door_lock", "locked", "channel");
        assertFalse(DerivedAggregates.doorsAllLocked(store), "one unlocked");
    }

    private static void testOneUnlockedRawNumeric() {
        ValueStore store = new ValueStore();
        store.put("driver_door_lock", "locked", "channel");
        store.put("passenger_door_lock", "1", "channel");
        assertFalse(DerivedAggregates.doorsAllLocked(store), "one unlocked (raw 1)");
    }

    private static void testMissingUnknown() {
        ValueStore store = new ValueStore();
        store.put("driver_door_lock", "locked", "channel");
        store.put("passenger_door_lock", "invalid", "channel");
        store.put("rear_left_door_lock", "—", "channel");
        assertTrue(DerivedAggregates.doorsAllLocked(store), "missing/unknown treated as locked");
    }

    private static void testEmptyInput() {
        assertTrue(DerivedAggregates.doorsAllLocked(new ValueStore()), "empty store");
        assertTrue(DerivedAggregates.doorsAllLocked(null), "null store");
    }

    private static void testRestoredIgnored() {
        ValueStore store = new ValueStore();
        store.put("driver_door_lock", "unlocked", ValueStore.SOURCE_RESTORED);
        store.put("passenger_door_lock", "locked", "channel");
        assertTrue(DerivedAggregates.doorsAllLocked(store), "restored unlocked ignored");
    }

    private static void assertTrue(boolean value, String what) {
        if (!value) throw new AssertionError(what + ": expected true");
    }

    private static void assertFalse(boolean value, String what) {
        if (value) throw new AssertionError(what + ": expected false");
    }
}
