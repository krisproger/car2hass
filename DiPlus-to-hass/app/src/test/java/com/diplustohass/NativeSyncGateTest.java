package com.diplustohass;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plain-Java unit tests for the synchronous ADB result gate.
 *
 * <p>AdbShellExecutor itself needs adblib + android.jar, so the sync-wait
 * helper (NativeSyncGate) is tested here in isolation.
 */
public class NativeSyncGateTest {

    public static void main(String[] args) {
        testSuccessReturnsValue();
        testTimeoutReturnsNull();
        testInterruptReturnsNull();
        testNullResultValue();
        testZeroWaitTimesOut();

        System.out.println("All NativeSyncGate tests passed.");
    }

    private static void testSuccessReturnsValue() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>("Result: Parcel(00000000 3f9df3b6)");
        new Thread(() -> {
            sleep(50);
            result.set("Result: Parcel(00000000 00000001)");
            latch.countDown();
        }).start();
        String out = NativeSyncGate.await(latch, result, 2000);
        assertEquals("Result: Parcel(00000000 00000001)", out, "value stored before countDown");
    }

    private static void testTimeoutReturnsNull() {
        CountDownLatch latch = new CountDownLatch(1);
        String out = NativeSyncGate.await(latch, new AtomicReference<>("never-read"), 100);
        assertEquals(null, out, "timeout must return null");
    }

    private static void testInterruptReturnsNull() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        Thread t = new Thread(() -> {
            String out = NativeSyncGate.await(latch, result, 5000);
            if (out != null) {
                throw new AssertionError("interrupted await must return null");
            }
            if (!Thread.currentThread().isInterrupted()) {
                throw new AssertionError("interrupt flag must be restored");
            }
        });
        t.start();
        sleep(50);
        t.interrupt();
        join(t);
    }

    private static void testNullResultValue() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>(null);
        new Thread(() -> {
            sleep(30);
            latch.countDown();
        }).start();
        String out = NativeSyncGate.await(latch, result, 2000);
        assertEquals(null, out, "countDown without a stored value gives null");
    }

    private static void testZeroWaitTimesOut() {
        CountDownLatch latch = new CountDownLatch(1);
        String out = NativeSyncGate.await(latch, new AtomicReference<>(), 0);
        assertEquals(null, out, "zero wait must time out immediately");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void join(Thread t) {
        try {
            t.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (t.isAlive()) {
            throw new AssertionError("worker thread did not finish");
        }
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
