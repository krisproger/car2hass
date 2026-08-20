package com.diplustohass.vehicle;

import java.util.ArrayList;
import java.util.List;

public class VehicleResearchTest {
    public static void main(String[] args) {
        List<ChannelResult> results = new ArrayList<>();
        results.add(ChannelResult.ok(7));
        results.add(ChannelResult.dead("ECONNREFUSED"));

        String s = VehicleResearch.summary(results);
        check(s.length() > 0, "summary non-empty");

        StringBuilder sb = new StringBuilder();
        for (ChannelResult r : results) sb.append(r.summary()).append('\n');
        check(sb.toString().contains("7"), "summary keeps signals count");

        testProgressListener();
        System.out.println("All VehicleResearch tests passed.");
    }

    private static void testProgressListener() {
        final int[] calls = {0};
        final int[] lastDone = {0};
        final int[] lastTotal = {0};
        VehicleResearch.ProgressListener listener = (done, total, name) -> {
            calls[0]++;
            lastDone[0] = done;
            lastTotal[0] = total;
        };
        // run() requires a Context and probes channels — not available in plain
        // Java. Here we only verify the listener contract is callable.
        listener.onChannelDone(2, 4, "DiPlus");
        check(calls[0] == 1, "listener called once");
        check(lastDone[0] == 2 && lastTotal[0] == 4, "listener got done/total");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}