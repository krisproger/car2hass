package com.diplustohass;

import android.content.Context;

import java.net.URLEncoder;

/**
 * Sends commands to the local DiPlus HTTP API.
 *
 * <p>DiPlus exposes {@code /api/sendCmd?cmd=<chinese command>}. This helper
 * builds the URL, performs the GET request synchronously, and returns a
 * structured result. It is intended to run on a background thread.</p>
 */
public class DiPlusCommandSender {

    private static final String DIPLUS_BASE = "http://127.0.0.1:8988";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 10000;

    /**
     * Min gap between consecutive sendCmd calls. DiPlus ACKs in ~15-170 ms but
     * only enqueues the command into the BYD voice pipeline; bursts fired within
     * ~100 ms (a windows preset applies six commands at once, each from its own
     * thread) lose commands or get them truncated — "…打开百分之20" arrives as a
     * full-open "…打开", and which command is garbled rotates with timing.
     */
    private static final long MIN_SEND_GAP_MS = 500;
    private static final Object SEND_LOCK = new Object();
    private static long lastSendAtMs = 0;

    public static class Result {
        public final boolean success;
        public final String response;
        public final String error;
        public final long elapsedMs;

        public Result(boolean success, String response, String error, long elapsedMs) {
            this.success = success;
            this.response = response;
            this.error = error;
            this.elapsedMs = elapsedMs;
        }
    }

    /**
     * Send a command to DiPlus.
     *
     * @param context application context
     * @param chineseCommand full Chinese command string (e.g. "迪加设置温度24")
     * @return result with response body or error message
     */
    public static Result send(Context context, String chineseCommand) {
        if (chineseCommand == null || chineseCommand.trim().isEmpty()) {
            return new Result(false, null, context.getString(R.string.commands_empty_command), 0);
        }
        // Serialize all senders (UI threads, CommandPoller, RuleEngine) so the
        // voice pipeline receives commands strictly one at a time. The gap is
        // start-to-start; all call sites already run on background threads.
        synchronized (SEND_LOCK) {
            long now = System.currentTimeMillis();
            long waitMs = MIN_SEND_GAP_MS - (now - lastSendAtMs);
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            long start = System.currentTimeMillis();
            lastSendAtMs = start;
            try {
                String encoded = URLEncoder.encode(chineseCommand, "UTF-8");
                String url = DiplusApi.withAuth(DIPLUS_BASE + "/api/sendCmd?cmd=" + encoded, AppConfig.getDiplusAuth(context));
                // Details always go to the in-memory buffer (always detailed);
                // the on-disk log includes them only in detailed mode.
                LogBuffer.d("DiPlusCmd", "Sending: " + chineseCommand);
                // Never log the raw URL: it carries the auth token (review #5).
                LogBuffer.d("DiPlusCmd", "URL: " + DiplusApi.maskAuth(url));
                String response = CANDataReader.sendRequestSync("GET", url, null);
                long elapsed = System.currentTimeMillis() - start;
                boolean ok = response != null && !response.contains("\"success\":false");
                LogBuffer.d("DiPlusCmd", "Result in " + elapsed + "ms: " + response);
                return new Result(ok, response, null, elapsed);
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
                LogBuffer.e("DiPlusCmd", "Failed to send '" + chineseCommand + "': " + msg);
                return new Result(false, null, msg, elapsed);
            }
        }
    }
}
