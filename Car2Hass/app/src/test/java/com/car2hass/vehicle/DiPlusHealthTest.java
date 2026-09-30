package com.car2hass.vehicle;

public class DiPlusHealthTest {
    public static void main(String[] args) {
        long t = 1_000_000L;
        // A recent real read ⇒ alive, even if the ping never succeeded.
        if (!DiPlusHealth.isAlive(t, t - 1_000, 0L, t - 1_000))
            throw new AssertionError("recent read must count as alive");
        // No success within TTL and a recent failure ⇒ not alive.
        if (DiPlusHealth.isAlive(t, 0L, 0L, t - 1_000))
            throw new AssertionError("recent failure must be not-alive");
        // Cold start: shorter negative cache and shorter launch cooldown.
        long start = t - 5_000; // 5 s after service start
        if (DiPlusHealth.negativeTtlMs(t, start) >= DiPlusHealth.FAIL_TTL_MS)
            throw new AssertionError("cold start must shorten the negative cache");
        if (DiPlusHealth.launchCooldownMs(t, start) >= DiPlusHealth.LAUNCH_COOLDOWN_MS)
            throw new AssertionError("cold start must shorten the launch cooldown");
        // Warm: full TTLs.
        long warm = t - DiPlusHealth.COLD_START_MS - 1;
        if (DiPlusHealth.negativeTtlMs(t, warm) != DiPlusHealth.FAIL_TTL_MS)
            throw new AssertionError("warm negative cache must be the full TTL");
        System.out.println("All DiPlusHealth tests passed.");
    }
}
