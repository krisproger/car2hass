package com.car2hass;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DeltaSnapshotBuilderTest {

    public static void main(String[] args) {
        // 1) First send (no reference) must be FULL.
        Map<String, String> full1 = new LinkedHashMap<>();
        full1.put("speed", "10");
        full1.put("soc", "50");
        DeltaSnapshotBuilder.Result r1 = DeltaSnapshotBuilder.build(full1, null, null, false);
        if (r1.delta || !r1.signals.equals(full1) || !r1.channels.isEmpty()) {
            throw new AssertionError("first send must be full: " + r1.delta);
        }

        // 2) One changed key → delta with that key + channel.
        Map<String, String> full2 = new LinkedHashMap<>();
        full2.put("speed", "20");   // changed
        full2.put("soc", "50");     // same
        Map<String, String> chans = new HashMap<>();
        chans.put("speed", "dumpsys");
        DeltaSnapshotBuilder.Result r2 = DeltaSnapshotBuilder.build(full2, r1.lastSent, chans, false);
        if (!r2.delta) throw new AssertionError("second send should be delta");
        if (!r2.signals.equals(java.util.Collections.singletonMap("speed", "20"))) {
            throw new AssertionError("delta signals wrong: " + r2.signals);
        }
        if (!r2.channels.containsKey("dumpsys") || !r2.channels.get("dumpsys").contains("speed")) {
            throw new AssertionError("channel map wrong: " + r2.channels);
        }

        // 3) No changes → empty delta.
        DeltaSnapshotBuilder.Result r3 = DeltaSnapshotBuilder.build(full2, r2.lastSent, chans, false);
        if (!r3.delta || !r3.signals.isEmpty()) {
            throw new AssertionError("no-change delta should be empty: " + r3.signals);
        }

        // 4) forceFull overrides reference.
        DeltaSnapshotBuilder.Result r4 = DeltaSnapshotBuilder.build(full2, r2.lastSent, chans, true);
        if (r4.delta || !r4.signals.equals(full2)) {
            throw new AssertionError("forceFull must send full");
        }

        // 5) key removed from full drops out of the new reference.
        Map<String, String> full3 = new LinkedHashMap<>();
        full3.put("speed", "20");
        DeltaSnapshotBuilder.Result r5 = DeltaSnapshotBuilder.build(full3, r2.lastSent, chans, false);
        if (r5.lastSent.containsKey("soc")) {
            throw new AssertionError("removed key must leave the reference");
        }

        // 6) Missing channel → "unknown".
        Map<String, String> full4 = new LinkedHashMap<>();
        full4.put("newkey", "1");
        DeltaSnapshotBuilder.Result r6 = DeltaSnapshotBuilder.build(full4, full3, new HashMap<>(), false);
        if (!r6.channels.containsKey("unknown")) {
            throw new AssertionError("missing channel should map to unknown: " + r6.channels);
        }

        System.out.println("All DeltaSnapshotBuilder tests passed.");
    }
}
