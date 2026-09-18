package com.car2hass;

public class UiRefreshThrottleTest {

    public static void main(String[] args) {
        UiRefreshThrottle t = new UiRefreshThrottle(200);

        if (t.schedule(1000) != 0) throw new AssertionError("first change -> immediate");
        if (t.schedule(1050) != -1) throw new AssertionError("pending -> merged");
        if (t.schedule(1200) != -1) throw new AssertionError("still pending -> merged");

        t.flushed(1000);
        if (t.schedule(1050) != 150) throw new AssertionError("within interval -> delayed");
        if (t.schedule(1100) != -1) throw new AssertionError("still pending -> merged");

        t.flushed(1200);
        if (t.schedule(1400) != 0) throw new AssertionError("at interval edge -> immediate");

        t.flushed(1400);
        if (t.schedule(1400) != 200) throw new AssertionError("same instant after flush -> full interval");

        UiRefreshThrottle zero = new UiRefreshThrottle(0);
        if (zero.schedule(0) != 0) throw new AssertionError("zero interval -> immediate");

        System.out.println("All UiRefreshThrottle tests passed.");
    }
}
