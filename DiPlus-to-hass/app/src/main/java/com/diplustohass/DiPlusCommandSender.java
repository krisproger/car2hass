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
        boolean detailedLog = AppConfig.isDetailedLogEnabled(context);
        long start = System.currentTimeMillis();
        try {
            String encoded = URLEncoder.encode(chineseCommand, "UTF-8");
            String url = DiplusApi.withAuth(DIPLUS_BASE + "/api/sendCmd?cmd=" + encoded, AppConfig.getDiplusAuth(context));
            if (detailedLog) {
                LogBuffer.i("DiPlusCmd", "Sending: " + chineseCommand);
                LogBuffer.i("DiPlusCmd", "URL: " + url);
            }
            String response = CANDataReader.sendRequestSync("GET", url, null);
            long elapsed = System.currentTimeMillis() - start;
            boolean ok = response != null && !response.contains("\"success\":false");
            if (detailedLog) {
                LogBuffer.i("DiPlusCmd", "Result in " + elapsed + "ms: " + response);
            }
            return new Result(ok, response, null, elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            LogBuffer.e("DiPlusCmd", "Failed to send '" + chineseCommand + "': " + msg);
            return new Result(false, null, msg, elapsed);
        }
    }
}
