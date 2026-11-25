package com.car2hass;

import java.util.concurrent.atomic.AtomicReference;

/** Verifies the user's "send log" note reaches the upload payload/server metadata. */
public class LogUploadMessageTest {

    public static void main(String[] args) {
        testManualNoteCarried();
        testLargeManualBatchIsSingleUpload();
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

    private static void testLargeManualBatchIsSingleUpload() {
        LogStoreTest.InMemoryLogStore store = new LogStoreTest.InMemoryLogStore();
        for (int i = 0; i < 1200; i++) {
            store.insertLog(i + 1L, "I", "t", "line " + i, "app");
        }
        int[] sends = {0};
        AtomicReference<String> note = new AtomicReference<>();
        LogUploadCoordinator coordinator = new LogUploadCoordinator(
                store, payload -> { sends[0]++; note.set(payload.message); return true; },
                100000, "", "big note", 32L * 1024 * 1024);
        LogUploadCoordinator.Result r = coordinator.uploadOnce();
        if (!r.ok || sends[0] != 1) {
            throw new AssertionError("large manual send must be one upload, sends=" + sends[0]);
        }
        if (r.attempted != 1200) {
            throw new AssertionError("all records must be in one payload, attempted=" + r.attempted);
        }
        if (!"big note".equals(note.get())) {
            throw new AssertionError("manual note missing from large payload");
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
