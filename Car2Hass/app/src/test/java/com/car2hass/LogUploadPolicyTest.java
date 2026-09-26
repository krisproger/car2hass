package com.car2hass;

import com.car2hass.LogUploadPolicy.Trigger;

/** Plain-Java tests for the log upload trigger policy and crash-upload dedup. */
public class LogUploadPolicyTest {

    private static final long MIN = 60_000L;
    private static final long HOUR = 60L * MIN;
    private static final long DAY = 24L * HOUR;

    public static void main(String[] args) {
        testAutoUploadIsNotPeriodic();
        testAutoUploadOncePerDay();
        testAutoUploadGuardSurvivesRestart();
        testNothingNewMeansNoUpload();
        testUserActionAlwaysAllowed();
        testCrashUploadDeduplicated();
        testCrashUploadBackoff();
        System.out.println("All LogUploadPolicy tests passed.");
    }

    /** The automatic safety net must not fire on the old ~2-minute cadence. */
    private static void testAutoUploadIsNotPeriodic() {
        long now = 1_700_000_000_000L;
        assertFalse(LogUploadPolicy.shouldUpload(Trigger.AUTO, true, now, now - 2 * MIN),
                "auto upload must not repeat after 2 minutes");
        assertFalse(LogUploadPolicy.shouldUpload(Trigger.AUTO, true, now, now - HOUR),
                "auto upload must not repeat after an hour");
        if (LogUploadPolicy.AUTO_INTERVAL_MS != DAY) {
            throw new AssertionError("auto upload interval must be 24h, got "
                    + LogUploadPolicy.AUTO_INTERVAL_MS);
        }
    }

    private static void testAutoUploadOncePerDay() {
        long now = 1_700_000_000_000L;
        assertTrue(LogUploadPolicy.shouldUpload(Trigger.AUTO, true, now, 0L),
                "first automatic upload is allowed");
        assertFalse(LogUploadPolicy.shouldUpload(Trigger.AUTO, true, now, now - DAY + 1),
                "must not auto upload again before 24h");
        assertTrue(LogUploadPolicy.shouldUpload(Trigger.AUTO, true, now, now - DAY),
                "auto upload is allowed once 24h elapsed");
    }

    /** A restarted process reads the persisted timestamp, so the guard holds. */
    private static void testAutoUploadGuardSurvivesRestart() {
        long now = 1_700_000_000_000L;
        long persisted = now;
        assertFalse(LogUploadPolicy.shouldUpload(Trigger.AUTO, true, now + HOUR, persisted),
                "persisted guard must block a restart within 24h");
        assertTrue(LogUploadPolicy.shouldUpload(Trigger.AUTO, true, now + DAY + 1, persisted),
                "persisted guard expires after 24h");
    }

    private static void testNothingNewMeansNoUpload() {
        long now = 1_700_000_000_000L;
        assertFalse(LogUploadPolicy.shouldUpload(Trigger.AUTO, false, now, 0L),
                "no unsent records -> no automatic upload");
        assertFalse(LogUploadPolicy.shouldUpload(Trigger.CRASH, false, now, 0L),
                "no unsent crashes -> no crash upload");
    }

    private static void testUserActionAlwaysAllowed() {
        long now = 1_700_000_000_000L;
        assertTrue(LogUploadPolicy.shouldUpload(Trigger.USER, true, now, now),
                "user send is always allowed");
        assertTrue(LogUploadPolicy.shouldUpload(Trigger.USER, false, now, now),
                "user send is allowed even without new records");
    }

    /** Several crash lines schedule exactly one upload until it is released. */
    private static void testCrashUploadDeduplicated() {
        LogUploadGate gate = new LogUploadGate();
        int scheduled = 0;
        for (int i = 0; i < 5; i++) {
            if (gate.trySchedule()) scheduled++;
        }
        assertEquals(1, scheduled, "only one crash upload may be scheduled");
        assertTrue(gate.isScheduled(), "the scheduled flag stays set");
        gate.release();
        assertTrue(gate.trySchedule(), "a released gate accepts a new upload");
    }

    private static void testCrashUploadBackoff() {
        long now = 1_700_000_000_000L;
        long backoff = LogUploadPolicy.CRASH_BACKOFF_MS;
        assertTrue(LogUploadPolicy.shouldUpload(Trigger.CRASH, true, now, 0L),
                "first crash upload is allowed");
        assertFalse(LogUploadPolicy.shouldUpload(Trigger.CRASH, true, now, now - backoff + 1),
                "crash upload respects the retry backoff");
        assertTrue(LogUploadPolicy.shouldUpload(Trigger.CRASH, true, now, now - backoff),
                "crash upload is allowed after the backoff");
    }

    private static void assertTrue(boolean value, String what) {
        if (!value) throw new AssertionError(what);
    }

    private static void assertFalse(boolean value, String what) {
        if (value) throw new AssertionError(what);
    }

    private static void assertEquals(long expected, long actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
