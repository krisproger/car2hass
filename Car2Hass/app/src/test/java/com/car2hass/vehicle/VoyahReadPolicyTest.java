package com.car2hass.vehicle;

import java.util.ArrayList;
import java.util.List;

public class VoyahReadPolicyTest {
    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        check(VoyahReadPolicy.FIRST_BIND_TIMEOUT_MS > VoyahReadPolicy.READ_TIMEOUT_MS,
                "first bind budget must exceed per-read budget");

        check(VoyahReadPolicy.shouldRetry(0), "attempt 0 retries");
        check(VoyahReadPolicy.shouldRetry(VoyahReadPolicy.MAX_READ_ATTEMPTS - 1), "last attempt retries");
        check(!VoyahReadPolicy.shouldRetry(VoyahReadPolicy.MAX_READ_ATTEMPTS), "no retry past the bound");

        check(VoyahReadPolicy.backoffMs(0) == 0, "no backoff before the first attempt");
        check(VoyahReadPolicy.backoffMs(1) > 0, "positive backoff before a retry");

        // Success on the first attempt: no retries, no sleeps.
        final int[] calls = {0};
        List<Long> sleeps = new ArrayList<>();
        int first = VoyahReadPolicy.readWithRetry(() -> { calls[0]++; return 5; }, sleeps::add);
        check(first == 5, "first-attempt count returned");
        check(calls[0] == 1, "no extra attempt after success");
        check(sleeps.isEmpty(), "no backoff after success");

        // Empty then non-empty: one retry succeeds.
        calls[0] = 0;
        sleeps.clear();
        int retried = VoyahReadPolicy.readWithRetry(() -> { calls[0]++; return calls[0] == 1 ? 0 : 3; },
                sleeps::add);
        check(retried == 3, "retry result returned");
        check(calls[0] == 2, "exactly one retry");
        check(sleeps.size() == 1 && sleeps.get(0) == VoyahReadPolicy.backoffMs(1),
                "backoff before retry");

        // Persistent failure is bounded by MAX_READ_ATTEMPTS.
        calls[0] = 0;
        sleeps.clear();
        int empty = VoyahReadPolicy.readWithRetry(() -> { calls[0]++; return 0; }, sleeps::add);
        check(empty == 0, "empty result reported");
        check(calls[0] == VoyahReadPolicy.MAX_READ_ATTEMPTS, "attempts bounded");
        check(sleeps.size() == VoyahReadPolicy.MAX_READ_ATTEMPTS - 1, "backoff between attempts");

        // An exception is a failure, not a crash.
        calls[0] = 0;
        int thrown = VoyahReadPolicy.readWithRetry(() -> { calls[0]++; throw new RuntimeException("boom"); },
                ms -> {});
        check(thrown == 0, "exception yields empty result");
        check(calls[0] == VoyahReadPolicy.MAX_READ_ATTEMPTS, "exception retried up to the bound");

        System.out.println("All VoyahReadPolicy tests passed.");
    }
}
