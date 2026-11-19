package com.car2hass;

import java.util.concurrent.atomic.AtomicReference;

/** Verifies the user's "send log" note reaches the upload payload/server metadata. */
public class LogUploadMessageTest {

    public static void main(String[] args) {
        testManualNoteCarried();
        testCoordinatorWithoutNoteStaysEmpty();

        System.out.println("All LogUploadMessage tests passed.");
    }

    private static void testManualNoteCarried() {
        LogStoreTest.InMemoryLogStore store = new LogStoreTest.InMemoryLogStore();
        store.insertLog(1L, "I", "Main", "uploadLogToServer", "app");
        AtomicReference<String> sent = new AtomicReference<>();
        LogUploadCoordinator coordinator = new LogUploadCoordinator(
                store, payload -> { sent.set(payload.message); return true; },
                10, "=== Car2Hass log ===", "  не работает вентиляция  ");
        LogUploadCoordinator.Result r = coordinator.uploadOnce();
        if (!r.ok) throw new AssertionError("upload must succeed");
        if (!"  не работает вентиляция  ".equals(sent.get())) {
            throw new AssertionError("manual note must be carried as-is, got: '" + sent.get() + "'");
        }
    }

    private static void testCoordinatorWithoutNoteStaysEmpty() {
        LogStoreTest.InMemoryLogStore store = new LogStoreTest.InMemoryLogStore();
        store.insertLog(2L, "I", "t", "auto", "app");
        AtomicReference<String> sent = new AtomicReference<>();
        new LogUploadCoordinator(store, payload -> { sent.set(payload.message); return true; }, 10)
                .uploadOnce();
        if (!"".equals(sent.get())) {
            throw new AssertionError("auto upload must not carry a note, got: '" + sent.get() + "'");
        }
    }
}
