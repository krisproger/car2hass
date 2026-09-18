package com.car2hass;

import com.car2hass.vehicle.ValueStore;

import java.util.Locale;

public final class DerivedAggregates {

    public static final String[] DOOR_LOCK_KEYS = {
            "driver_door_lock", "passenger_door_lock",
            "rear_left_door_lock", "rear_right_door_lock"
    };

    private DerivedAggregates() {
    }

    /**
     * True when no door lock is explicitly open/unlocked. Missing, unknown and
     * restored values are ignored and count as locked, so only an explicit
     * unlocked state of any door turns the aggregate false.
     */
    public static boolean doorsAllLocked(ValueStore store) {
        if (store == null) return true;
        for (String key : DOOR_LOCK_KEYS) {
            if (store.isRestored(key)) continue;
            if (isUnlocked(store.get(key))) return false;
        }
        return true;
    }

    private static boolean isUnlocked(String value) {
        if (value == null) return false;
        String s = value.trim().toLowerCase(Locale.ROOT);
        return "unlocked".equals(s) || "1".equals(s);
    }
}
