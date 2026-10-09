package com.car2hass;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the upload queue: sends entries, retries (3 attempts, pause, 3 more),
 * and asks the user via a callback when the retry budget is exhausted.
 */
public final class UploadQueueWorker {

    private static final int ATTEMPTS_BEFORE_ASK = 3;
    private static final int MAX_ATTEMPTS = 6;
    private static final long PAUSE_MS = 5 * 60 * 1000L; // 5 minutes

    public interface UserDecision {
        /** Called when retries are exhausted; returns true to keep trying. */
        boolean askContinue(List<UploadQueue.Entry> entries);
    }

    private UploadQueueWorker() {}

    /** Attempts to flush the queue. Returns entries awaiting a decision. */
    public static List<UploadQueue.Entry> flush(Context ctx, UserDecision decision) {
        List<UploadQueue.Entry> pending = UploadQueue.load(ctx);
        List<UploadQueue.Entry> awaiting = new ArrayList<>();

        for (UploadQueue.Entry e : pending) {
            if (e.awaitingUser) {
                awaiting.add(e);
                continue;
            }
            boolean sent = send(ctx, e);
            if (sent) {
                UploadQueue.remove(ctx, e.id);
                continue;
            }
            e.tries++;
            if (e.tries >= MAX_ATTEMPTS) {
                e.awaitingUser = true;
                UploadQueue.update(ctx, e);
                awaiting.add(e);
                continue;
            }
            if (e.tries == ATTEMPTS_BEFORE_ASK) {
                // pause before the second batch of attempts
                try { Thread.sleep(PAUSE_MS); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            UploadQueue.update(ctx, e);
        }

        // Ask the user about every entry that exhausted its attempts.
        if (decision != null && !awaiting.isEmpty()) {
            boolean keep = decision.askContinue(awaiting);
            for (UploadQueue.Entry e : awaiting) {
                if (keep) {
                    e.tries = 0;
                    e.awaitingUser = false;
                    UploadQueue.update(ctx, e);
                } else {
                    UploadQueue.remove(ctx, e.id);
                }
            }
        }
        return awaiting;
    }

    private static boolean send(Context ctx, UploadQueue.Entry e) {
        try {
            if (UploadQueue.KIND_LOG.equals(e.kind)) {
                // Full app journal (LogBuffer + logcat) with the research report
                // appended when available; delivered in 500 KiB chunks. Bytes are
                // passed straight to the uploader to avoid a second full-size
                // String copy (OOM risk on head units with multi-MB logs).
                byte[] bytes = LogExportHelper.buildLogBytes(ctx, e.path);
                return LogUploader.uploadBytes(ctx, e.message, bytes, e.id);
            }
            ProbeUploader.Result r = ProbeUploader.uploadResult(ctx, e.path);
            if (r.ok) return true;
            if (!ProbeUploadPolicy.isRetryableCode(r.code)) {
                // 429: retrying now will not clear the server window — drop the stale entry.
                LogBuffer.i("UploadQueueWorker", "probe entry dropped (code=" + r.code + ")");
                return true;
            }
            return false;
        } catch (Exception ex) {
            LogBuffer.w("UploadQueueWorker", "send " + e.kind + ": " + ex.getMessage());
            return false;
        }
    }
}