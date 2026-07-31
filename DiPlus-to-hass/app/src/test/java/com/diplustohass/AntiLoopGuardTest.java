package com.diplustohass;

import com.diplustohass.rules.AntiLoopGuard;

public class AntiLoopGuardTest {

    private static final long WINDOW = 5000L;
    private static final long T = 1_000_000_000L;

    public static void main(String[] args) {
        testFirstActionAllowed();
        testSameActionWithinWindowBlocked();
        testSameActionAfterWindowAllowed();
        testOppositeValueWithinWindowBlocked();
        testDifferentCommandIdUnaffected();
        testClearResets();

        System.out.println("All AntiLoopGuard tests passed.");
    }

    private static void testFirstActionAllowed() {
        AntiLoopGuard g = new AntiLoopGuard();
        assertTrue(g.allow("cmd1", "on", T, WINDOW), "first action allowed");
    }

    private static void testSameActionWithinWindowBlocked() {
        AntiLoopGuard g = new AntiLoopGuard();
        g.record("cmd1", "on", T);
        assertFalse(g.allow("cmd1", "on", T + 1000, WINDOW), "same action within window blocked");
    }

    private static void testSameActionAfterWindowAllowed() {
        AntiLoopGuard g = new AntiLoopGuard();
        g.record("cmd1", "on", T);
        assertTrue(g.allow("cmd1", "on", T + WINDOW + 1, WINDOW), "same action after window allowed");
    }

    private static void testOppositeValueWithinWindowBlocked() {
        AntiLoopGuard g = new AntiLoopGuard();
        g.record("cmd1", "on", T);
        assertFalse(g.allow("cmd1", "off", T + 1000, WINDOW), "opposite value within window blocked");
    }

    private static void testDifferentCommandIdUnaffected() {
        AntiLoopGuard g = new AntiLoopGuard();
        g.record("cmd1", "on", T);
        assertTrue(g.allow("cmd2", "on", T + 1000, WINDOW), "different commandId unaffected");
    }

    private static void testClearResets() {
        AntiLoopGuard g = new AntiLoopGuard();
        g.record("cmd1", "on", T);
        g.clear();
        assertTrue(g.allow("cmd1", "on", T + 1000, WINDOW), "clear resets guard state");
    }

    private static void assertTrue(boolean v, String msg) {
        if (!v) throw new AssertionError(msg + " expected=true actual=false");
    }

    private static void assertFalse(boolean v, String msg) {
        if (v) throw new AssertionError(msg + " expected=false actual=true");
    }
}
