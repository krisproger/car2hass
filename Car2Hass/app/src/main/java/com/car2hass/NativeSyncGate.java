package com.car2hass;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Synchronous-result helper for {@link AdbShellExecutor#executeSync}.
 *
 * <p>The async ADB path reports through a callback that can only be observed
 * from another thread; {@link #await} blocks that thread until the callback
 * fires or the timeout elapses. Pure Java — unit-testable without the Android
 * runtime (unlike AdbShellExecutor itself, which needs adblib + android.jar).
 */
public final class NativeSyncGate {

    private NativeSyncGate() {}

    /**
     * Blocks until {@code latch} reaches zero (the callback fired) or the
     * timeout elapses, whichever comes first.
     *
     * @return the value stored in {@code result} (may be null when the callee
     *         reported an error/failure), or null on timeout/interrupt
     */
    public static String await(CountDownLatch latch, AtomicReference<String> result, long timeoutMs) {
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return result.get();
    }
}
