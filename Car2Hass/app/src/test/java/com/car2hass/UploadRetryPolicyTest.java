package com.car2hass;

public final class UploadRetryPolicyTest {
    public static void main(String[] args) {
        check(UploadRetryPolicy.MAX_ATTEMPTS == 3, "attempts");
        check(UploadRetryPolicy.shouldRetry(0), "transport");
        check(UploadRetryPolicy.shouldRetry(500), "5xx");
        check(!UploadRetryPolicy.shouldRetry(400), "4xx");
        check(!UploadRetryPolicy.shouldRetry(429), "429");
        check(UploadRetryPolicy.backoffMs(1) == 1000L, "backoff1");
        check(UploadRetryPolicy.backoffMs(2) == 3000L, "backoff2");
        System.out.println("All UploadRetryPolicy tests passed.");
    }
    private static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); }
}
