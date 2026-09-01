package com.car2hass;

public class QueueIndicatorTest {

    public static void main(String[] args) {
        if (QueueIndicator.progress(0, 1_000_000) != 0f) throw new AssertionError("zero pending -> 0");
        if (QueueIndicator.progress(500_000, 1_000_000) != 0.5f) throw new AssertionError("half");
        if (QueueIndicator.progress(2_000_000, 1_000_000) != 1f) throw new AssertionError("clamp top");
        if (QueueIndicator.progress(100, 0) != 0f) throw new AssertionError("zero reference -> 0");
        if (QueueIndicator.progress(-5, 100) != 0f) throw new AssertionError("negative -> 0");
        System.out.println("All QueueIndicator tests passed.");
    }
}