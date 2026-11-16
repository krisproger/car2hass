package com.car2hass;

public final class HaWorkerPolicyTest {
    public static void main(String[] args) {
        check(HaWorkerPolicy.shouldAttempt(0L, 12_000L, 13_000L));
        check(!HaWorkerPolicy.shouldAttempt(0L, 12_000L, 6_000L));
        check(HaWorkerPolicy.nextBackoff(12_000L) == 24_000L);
        check(HaWorkerPolicy.nextBackoff(300_000L) == 300_000L);
        check(HaWorkerPolicy.batchRoom(100, 80) == 20);
        System.out.println("All HaWorkerPolicy tests passed.");
    }
    private static void check(boolean ok) {
        if (!ok) throw new AssertionError();
    }
}
