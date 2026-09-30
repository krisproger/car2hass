package com.car2hass;

public class CloudSyncDecisionTest {

    public static void main(String[] args) {
        expect(false, 0L, false, 0L, CloudSyncDecision.Action.NONE);
        expect(true, 100L, false, 0L, CloudSyncDecision.Action.UPLOAD);
        expect(false, 0L, true, 100L, CloudSyncDecision.Action.DOWNLOAD);
        expect(true, 200_000L, true, 100L, CloudSyncDecision.Action.UPLOAD);
        expect(true, 100_000L, true, 200L, CloudSyncDecision.Action.DOWNLOAD);
        expect(true, 100_000L, true, 100L, CloudSyncDecision.Action.NONE);
        System.out.println("All CloudSyncDecision tests passed.");
    }

    private static void expect(boolean hasLocal, long localMs, boolean hasRemote, long serverSec,
                               CloudSyncDecision.Action want) {
        CloudSyncDecision.Action got = CloudSyncDecision.decide(hasLocal, localMs, hasRemote, serverSec);
        if (got != want) {
            throw new AssertionError("decide(hasLocal=" + hasLocal + ", local=" + localMs
                    + ", hasRemote=" + hasRemote + ", server=" + serverSec + ") want=" + want + " got=" + got);
        }
    }
}
