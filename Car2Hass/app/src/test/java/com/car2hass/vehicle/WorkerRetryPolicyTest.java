package com.car2hass.vehicle;

public class WorkerRetryPolicyTest {

    public static void main(String[] args) {
        WorkerRetryPolicy p = new WorkerRetryPolicy(2000, 30000);

        if (p.onFailure() != 2000) throw new AssertionError("first failure -> base");
        if (p.onFailure() != 4000) throw new AssertionError("second failure -> 2x");
        if (p.onFailure() != 8000) throw new AssertionError("third failure -> 4x");
        if (p.onFailure() != 16000) throw new AssertionError("fourth failure -> 8x");
        if (p.onFailure() != 30000) throw new AssertionError("fifth failure -> capped");
        if (p.onFailure() != 30000) throw new AssertionError("stays capped");
        if (p.consecutiveFailures() != 6) throw new AssertionError("failure count");

        if (p.onSuccess() != 2000) throw new AssertionError("success resets to base");
        if (p.consecutiveFailures() != 0) throw new AssertionError("failures reset");
        if (p.onSuccess() != 2000) throw new AssertionError("healthy stays at base");

        // A cap below the base must not invert the backoff.
        WorkerRetryPolicy clamped = new WorkerRetryPolicy(5000, 1000);
        if (clamped.onFailure() != 5000) throw new AssertionError("max clamped to base");
        if (clamped.onFailure() != 5000) throw new AssertionError("max clamped stays");

        try {
            new WorkerRetryPolicy(0, 1000);
            throw new AssertionError("zero base must be rejected");
        } catch (IllegalArgumentException expected) {
        }

        System.out.println("All WorkerRetryPolicy tests passed.");
    }
}
