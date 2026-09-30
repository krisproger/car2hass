package com.car2hass;

public class ProbeUploadDecisionTest {
    public static void main(String[] args) {
        if (ProbeUploadPolicy.isRetryableCode(429)) throw new AssertionError("429 must not be retried immediately");
        if (!ProbeUploadPolicy.isRetryableCode(500)) throw new AssertionError("500 should be retryable");
        if (!ProbeUploadPolicy.isRetryableCode(0)) throw new AssertionError("network error (0) should be retryable");
        if (ProbeUploadPolicy.isRetryableCode(200)) throw new AssertionError("200 is not a failure");

        if (!ProbeUploadPolicy.shouldUpload(1000L, 0L)) throw new AssertionError("first upload must be allowed");
        if (ProbeUploadPolicy.shouldUpload(1000L + ProbeUploadPolicy.MIN_INTERVAL_MS - 1, 1000L)) {
            throw new AssertionError("upload inside the server window must be blocked");
        }
        if (!ProbeUploadPolicy.shouldUpload(1000L + ProbeUploadPolicy.MIN_INTERVAL_MS, 1000L)) {
            throw new AssertionError("upload at the window boundary must be allowed");
        }
        System.out.println("All ProbeUploadDecision tests passed.");
    }
}
