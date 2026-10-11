package com.car2hass;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Owns "send log to server": enqueues the log, drives the send button as an
 * inline progress indicator, and restores it when done. Extracted from
 * MainActivity to keep that class smaller.
 */
public final class LogSendController {

    public interface Host {
        Button sendButton();
        ProgressBar sendProgress();
        /** Path of the current research report, or null. */
        String takeResearchPath();
        /** Fallback identifier when the user leaves the comment empty. */
        String buildIdentifier();
        /** Retry/flush the upload queue (may prompt the user). */
        void flushQueue();
        void post(Runnable r);
        void toast(int res);
        String str(int res);
    }

    private final Activity activity;
    private final Host host;
    private boolean uploading;

    public LogSendController(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
    }

    /** Uploads the current log (with the optional note) to the developer server. */
    public void upload(String message) {
        if (uploading) return;
        final String logPath = host.takeResearchPath();
        final String msg = message != null && !message.isEmpty()
                ? message : host.buildIdentifier();
        final String logTitle = host.str(R.string.upload_log_title);
        LogBuffer.i("Main", "uploadLogToServer: path=" + logPath + " msg=" + msg);

        // The send button becomes an inline progress bar and cannot be pressed
        // again until the current log finishes uploading.
        uploading = true;
        final Button send = host.sendButton();
        final ProgressBar progress = host.sendProgress();
        if (send != null) send.setVisibility(View.GONE);
        if (progress != null) { progress.setIndeterminate(true); progress.setVisibility(View.VISIBLE); }
        TaskNotifier.progress(activity, logTitle, host.str(R.string.upload_in_progress), 0, 0);

        // Also push the DB log (the real, complete log) right away — the file
        // queue above is the legacy path and may be empty/stale.
        try {
            LogManager lm = LogManager.get();
            if (lm != null) lm.uploadNow();
        } catch (Exception e) {
            LogBuffer.e("Main", "uploadLogToServer: db upload trigger: " + e.getMessage());
        }

        new Thread(() -> {
            try {
                String anonId = com.car2hass.vehicle.DeviceAnon.fromContext(activity);
                final String entryId = UploadQueue.enqueue(activity, UploadQueue.KIND_LOG,
                        AppInfo.getVersionString(activity), anonId, msg, logPath);
                LogBuffer.i("Main", "uploadLogToServer: enqueued " + entryId);
                host.flushQueue();
                LogBuffer.i("Main", "uploadLogToServer: flush done");
                host.post(() -> {
                    boolean stillPending = false;
                    for (UploadQueue.Entry e : UploadQueue.load(activity)) {
                        if (entryId.equals(e.id)) { stillPending = true; break; }
                    }
                    LogBuffer.i("Main", "uploadLogToServer: stillPending=" + stillPending);
                    TaskNotifier.done(activity, logTitle, host.str(stillPending
                            ? R.string.upload_failed : R.string.log_server_ok));
                    restore();
                    host.toast(stillPending ? R.string.log_server_pending : R.string.log_server_ok);
                });
            } catch (Exception e) {
                LogBuffer.e("Main", "uploadLogToServer failed: " + e.getMessage());
                host.post(() -> {
                    TaskNotifier.done(activity, logTitle, host.str(R.string.upload_failed));
                    restore();
                    host.toast(R.string.log_server_error);
                });
            }
        }, "log-upload").start();
    }

    /** Returns the send-log button to its idle state after an upload. */
    private void restore() {
        uploading = false;
        ProgressBar progress = host.sendProgress();
        Button send = host.sendButton();
        if (progress != null) progress.setVisibility(View.GONE);
        if (send != null) send.setVisibility(View.VISIBLE);
    }
}
