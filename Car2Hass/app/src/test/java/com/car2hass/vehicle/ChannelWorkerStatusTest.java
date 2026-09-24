package com.car2hass.vehicle;

public class ChannelWorkerStatusTest {

    public static void main(String[] args) {
        // Healthy worker: cadence 2000ms -> "2 s", no backoff.
        ChannelWorkerStatus ok = new ChannelWorkerStatus("diplus", true,
                ChannelWorkerStatus.Result.OK, "", 2000, 2000);
        if (ok.cadenceSeconds() != 2) throw new AssertionError("cadence 2000 -> " + ok.cadenceSeconds());
        if (ok.retryDelaySeconds() != 2) throw new AssertionError("delay 2000 -> " + ok.retryDelaySeconds());
        if (ok.inBackoff()) throw new AssertionError("healthy worker must not be in backoff");
        if (!"ok:2s".equals(ok.describe())) throw new AssertionError("describe ok -> " + ok.describe());

        // Empty cycle in backoff: retry 12000ms -> "12 s".
        ChannelWorkerStatus empty = new ChannelWorkerStatus("diplus", true,
                ChannelWorkerStatus.Result.EMPTY, "", 2000, 12000);
        if (empty.retryDelaySeconds() != 12) throw new AssertionError("delay 12000 -> " + empty.retryDelaySeconds());
        if (!empty.inBackoff()) throw new AssertionError("backoff expected");
        if (!"empty:retry-12s".equals(empty.describe())) throw new AssertionError("describe empty -> " + empty.describe());

        // Error with a reason and backoff.
        ChannelWorkerStatus error = new ChannelWorkerStatus("obd", true,
                ChannelWorkerStatus.Result.ERROR, "timeout", 2000, 16000);
        if (!"error:timeout:retry-16s".equals(error.describe()))
            throw new AssertionError("describe error -> " + error.describe());

        // Error without a reason.
        ChannelWorkerStatus bare = new ChannelWorkerStatus("obd", true,
                ChannelWorkerStatus.Result.ERROR, null, 2000, 2000);
        if (!"error".equals(bare.describe())) throw new AssertionError("describe bare error -> " + bare.describe());

        // Stopped worker reports off regardless of last result.
        ChannelWorkerStatus stopped = new ChannelWorkerStatus("voyah", false,
                ChannelWorkerStatus.Result.EMPTY, "", 2000, 8000);
        if (!"off".equals(stopped.describe())) throw new AssertionError("describe stopped -> " + stopped.describe());

        // Sub-second cadence still reports at least 1 s.
        ChannelWorkerStatus fast = new ChannelWorkerStatus("system", true,
                ChannelWorkerStatus.Result.OK, "", 500, 500);
        if (fast.cadenceSeconds() != 1) throw new AssertionError("sub-second cadence -> " + fast.cadenceSeconds());

        // Backoff formatting agrees with the retry policy doubling sequence.
        WorkerRetryPolicy policy = new WorkerRetryPolicy(2000, 30000);
        long[] expected = {2000, 4000, 8000, 16000, 30000};
        for (int i = 0; i < expected.length; i++) {
            long delay = policy.onFailure();
            if (delay != expected[i]) throw new AssertionError("policy step " + i + " -> " + delay);
            ChannelWorkerStatus s = new ChannelWorkerStatus("diplus", true,
                    ChannelWorkerStatus.Result.EMPTY, "", 2000, delay);
            if (s.retryDelaySeconds() != expected[i] / 1000)
                throw new AssertionError("seconds step " + i + " -> " + s.retryDelaySeconds());
        }
        if (policy.onSuccess() != 2000) throw new AssertionError("success reset");

        System.out.println("All ChannelWorkerStatus tests passed.");
    }
}
