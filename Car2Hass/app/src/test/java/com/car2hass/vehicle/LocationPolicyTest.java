package com.car2hass.vehicle;

public class LocationPolicyTest {
    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        long now = 1_000_000_000_000L;

        check(LocationPolicy.isFresh(now - 10_000, now), "fix 10s old is fresh");
        check(LocationPolicy.isFresh(now - LocationPolicy.MAX_FIX_AGE_MS, now),
                "boundary age is fresh");
        check(!LocationPolicy.isFresh(now - 60_000, now), "fix 60s old is stale");
        check(!LocationPolicy.isFresh(0, now), "unknown fix time is stale");

        check(LocationPolicy.isAbsurd(1200f), "1200m is absurd");
        check(!LocationPolicy.isAbsurd(999f), "999m is acceptable");

        check(LocationPolicy.isAccurateGps("gps", 5f), "gps 5m is accurate");
        check(!LocationPolicy.isAccurateGps("network", 5f), "network is not gps");
        check(!LocationPolicy.isAccurateGps("gps", 20f), "gps 20m is not accurate");

        check(LocationPolicy.isRecentAccurateGps("gps", 5f, now - 5_000, now),
                "recent accurate gps");
        check(!LocationPolicy.isRecentAccurateGps("gps", 5f, now - 60_000, now),
                "old accurate gps is not recent");

        // Coarse fixes are suppressed by a recent accurate GPS fix.
        check(LocationPolicy.suppressCoarse("passive", "gps", 5f, now - 5_000, now),
                "passive suppressed by gps");
        check(LocationPolicy.suppressCoarse("network", "gps", 5f, now - 5_000, now),
                "network suppressed by gps");
        check(!LocationPolicy.suppressCoarse("gps", "gps", 5f, now - 5_000, now),
                "gps never suppressed");
        check(!LocationPolicy.suppressCoarse("passive", "gps", 40f, now - 5_000, now),
                "inaccurate gps does not suppress");
        check(!LocationPolicy.suppressCoarse("passive", "network", 5f, now - 5_000, now),
                "non-gps last fix does not suppress");
        check(!LocationPolicy.suppressCoarse("passive", "gps", 5f, now - 60_000, now),
                "old gps does not suppress");

        System.out.println("All LocationPolicy tests passed.");
    }
}
