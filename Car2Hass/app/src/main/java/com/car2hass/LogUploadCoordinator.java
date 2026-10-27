package com.car2hass;

import java.util.ArrayList;
import java.util.List;

/**
 * Claims unsent records, sends one compressed batch, and settles the flags:
 * on success records reach {@code sent=2} and are deleted; on failure they are
 * reset to {@code sent=0} for the next retry. Only {@code sent=0} records are
 * ever claimed, so duplicate/parallel sends cannot re-send in-flight data.
 */
public final class LogUploadCoordinator {

    public interface Sender {
        boolean send(LogUploadPayload payload);
    }

    private final LogRecordStore store;
    private final Sender sender;
    private final int batchLimit;
    private final String preamble;

    public LogUploadCoordinator(LogRecordStore store, Sender sender, int batchLimit) {
        this(store, sender, batchLimit, "");
    }

    public LogUploadCoordinator(LogRecordStore store, Sender sender, int batchLimit, String preamble) {
        this.store = store;
        this.sender = sender;
        this.batchLimit = Math.max(1, batchLimit);
        this.preamble = preamble == null ? "" : preamble;
    }

    /** Outcome of one batch: how many records were attempted and whether the server accepted. */
    public static final class Result {
        public final int attempted;
        public final boolean ok;
        public Result(int attempted, boolean ok) { this.attempted = attempted; this.ok = ok; }
    }

    /** Sends one batch; attempted=0 when nothing is unsent. */
    public Result uploadOnce() {
        List<LogRecord> logs = LogUploadPolicy.selectForUpload(
                store.unsentLogs(batchLimit), LogUploadPolicy.MAX_UPLOAD_BYTES);
        List<CrashRecord> crashes = store.unsentCrashes(batchLimit);
        if (logs.isEmpty() && crashes.isEmpty()) return new Result(0, true);

        String uploadId = "log-" + System.currentTimeMillis();
        store.markSendingLogs(ids(logs), uploadId);
        store.markSendingCrashes(crashIds(crashes), uploadId);

        LogUploadPayload payload = LogPayloadCodec.build(uploadId, logs, crashes, preamble);
        boolean ok;
        try {
            ok = sender.send(payload);
        } catch (Throwable t) {
            ok = false;
        }
        if (ok) {
            store.markSentLogs(ids(logs));
            store.markSentCrashes(crashIds(crashes));
        } else {
            store.resetLogs(ids(logs));
            store.resetCrashes(crashIds(crashes));
        }
        return new Result(logs.size() + crashes.size(), ok);
    }

    private static List<Long> ids(List<LogRecord> logs) {
        List<Long> out = new ArrayList<>();
        if (logs != null) for (LogRecord r : logs) out.add(r.id);
        return out;
    }

    private static List<Long> crashIds(List<CrashRecord> crashes) {
        List<Long> out = new ArrayList<>();
        if (crashes != null) for (CrashRecord c : crashes) out.add(c.id);
        return out;
    }
}
