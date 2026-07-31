package com.diplustohass;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls Home Assistant for vehicle commands, executes them via DiPlus, and
 * reports the result back to HA.
 *
 * <p>The poll loop runs on a background thread. It is independent from the
 * telemetry flush loop so that command delivery keeps working even when the
 * telemetry batch cannot be sent.</p>
 */
public class CommandPoller {

    private static final long POLL_INTERVAL_MS = 10000;
    private static final long POLL_INTERVAL_ERROR_MS = 30000;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final Context appContext;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = this::pollLoop;

    private OnCommandExecutedListener listener;

    public interface OnCommandExecutedListener {
        void onCommandExecuted(String summary);
    }

    public CommandPoller(Context context) {
        this.appContext = context.getApplicationContext();
        LogBuffer.init(appContext);
    }

    public void setListener(OnCommandExecutedListener listener) {
        this.listener = listener;
    }

    public void start() {
        LogBuffer.i("CommandPoller", "start() requested, running=" + running.get());
        // Executor may be terminated after a previous stop(); recreate if needed.
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "CommandPoller");
                t.setUncaughtExceptionHandler((thread, ex) -> {
                    LogBuffer.e("CommandPoller", "Poller thread died: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    running.compareAndSet(true, false);
                });
                return t;
            });
            LogBuffer.d("CommandPoller", "Executor recreated");
        }
        if (running.compareAndSet(false, true)) {
            executor.submit(pollRunnable);
            LogBuffer.i("CommandPoller", "Poll loop started");
        } else if (executor.isTerminated() || executor.isShutdown()) {
            LogBuffer.w("CommandPoller", "running=true but executor is dead, forcing restart");
            running.set(false);
            start();
        } else {
            LogBuffer.w("CommandPoller", "Already running, ignoring start()");
        }
    }

    public void stop() {
        LogBuffer.i("CommandPoller", "stop() requested");
        running.set(false);
        try {
            executor.shutdownNow();
            LogBuffer.i("CommandPoller", "Executor shut down");
        } catch (Exception e) {
            LogBuffer.e("CommandPoller", "stop() failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void pollLoop() {
        LogBuffer.i("CommandPoller", "pollLoop entered");
        try {
            while (running.get()) {
                boolean hadError = false;
                try {
                    if (AppConfig.isHassEnabled(appContext)) {
                        hadError = !pollOnce();
                    } else {
                        LogBuffer.d("CommandPoller", "HA disabled, skipping poll cycle");
                    }
                } catch (Exception e) {
                    LogBuffer.e("CommandPoller", "Poll loop error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    hadError = true;
                }
                try {
                    long sleepMs = hadError ? POLL_INTERVAL_ERROR_MS : POLL_INTERVAL_MS;
                    LogBuffer.d("CommandPoller", "Sleeping " + sleepMs + " ms (hadError=" + hadError + ")");
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    LogBuffer.i("CommandPoller", "Poll loop interrupted, exiting");
                    break;
                }
            }
        } catch (Exception e) {
            LogBuffer.e("CommandPoller", "pollLoop crashed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            running.set(false);
        }
        LogBuffer.i("CommandPoller", "pollLoop exited, running=" + running.get());
    }

    /**
     * Poll HA for pending commands, execute them, and acknowledge results.
     *
     * @return true if the poll/execute/ack cycle completed without transport errors
     */
    private boolean pollOnce() {
        String host = AppConfig.getHassHost(appContext);
        int port = AppConfig.getHassPort(appContext);
        String token = AppConfig.getHassToken(appContext);
        String carName = AppConfig.getCarName(appContext);
        boolean https = AppConfig.isHassHttps(appContext);

        if (host.isEmpty() || token.isEmpty() || carName.isEmpty()) {
            return true; // not an error; just not configured yet
        }

        String scheme = https ? "https" : "http";
        String pollUrl;
        try {
            String encodedCar = URLEncoderWrapper.encode(carName, "UTF-8");
            pollUrl = String.format(Locale.US, "%s://%s:%d/api/byd_diplus/commands?car_name=%s",
                scheme, host, port, encodedCar);
        } catch (Exception e) {
            LogBuffer.e("CommandPoller", "Failed to encode car_name: " + e.getMessage());
            return false;
        }

        String response;
        try {
            response = httpGet(pollUrl, token);
        } catch (Exception e) {
            LogBuffer.d("CommandPoller", "Poll failed: " + e.getMessage());
            return false;
        }

        LogBuffer.d("CommandPoller", "Poll response: " + response);

        JSONArray commands;
        try {
            JSONObject obj = new JSONObject(response);
            commands = obj.optJSONArray("commands");
        } catch (Exception e) {
            LogBuffer.d("CommandPoller", "Invalid poll response: " + e.getMessage());
            return false;
        }

        if (commands == null || commands.length() == 0) {
            return true;
        }

        // Always log command reception at INFO so it is visible even without detailed logs.
        LogBuffer.i("CommandPoller", "Received " + commands.length() + " command(s)");

        boolean allAcked = true;
        for (int i = 0; i < commands.length(); i++) {
            JSONObject cmd = commands.optJSONObject(i);
            if (cmd == null) continue;
            try {
                if (!processSingleCommand(cmd)) {
                    allAcked = false;
                }
            } catch (Exception e) {
                LogBuffer.e("CommandPoller", "Single command failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                allAcked = false;
            }
        }
        return allAcked;
    }

    private boolean processSingleCommand(JSONObject cmd) throws Exception {
        String commandId = cmd.optString("id");
        String commandKey = cmd.optString("command");
        JSONObject params = cmd.optJSONObject("params");
        String value = params != null ? params.optString("value", "") : "";

        CommandExecutor.Result result = CommandExecutor.execute(
            appContext, commandKey, value, CommandExecutor.Source.HA);

        String status = result.success ? "ok" : "error";
        String message = result.success
            ? (result.verified
                ? appContext.getString(R.string.commands_result_ok_short) + " (" + result.verificationMessage + ")"
                : appContext.getString(R.string.commands_result_ok_short) + " [" + result.verificationMessage + "]")
            : (result.error != null ? result.error : appContext.getString(R.string.commands_result_fail));

        String summary = commandId + " " + commandKey + "=" + value + " -> "
            + status + " " + message;
        CommandLog.append(appContext, summary);
        notifyListener(summary);
        LogBuffer.i("CommandPoller", "HA command result: " + summary);

        return acknowledgeCommand(commandId, status, message);
    }

    private boolean acknowledgeCommand(String commandId, String status, String message) {
        String host = AppConfig.getHassHost(appContext);
        int port = AppConfig.getHassPort(appContext);
        String token = AppConfig.getHassToken(appContext);
        boolean https = AppConfig.isHassHttps(appContext);

        if (host.isEmpty() || token.isEmpty()) {
            LogBuffer.w("CommandPoller", "Cannot ack: HA not configured");
            return false;
        }

        String scheme = https ? "https" : "http";
        // The HA VehicleCommandsView is registered on /api/byd_diplus/commands
        // and handles both GET (poll) and POST (ack) on the same URL.
        String ackUrl = String.format(Locale.US, "%s://%s:%d/api/byd_diplus/commands",
            scheme, host, port);

        try {
            JSONObject body = new JSONObject();
            body.put("command_id", commandId);
            body.put("status", status);
            body.put("message", message != null ? message : "");
            String response = httpPost(ackUrl, token, body.toString());
            LogBuffer.i("CommandPoller", "Ack " + commandId + " -> " + response);
            return true;
        } catch (Exception e) {
            LogBuffer.e("CommandPoller", "Ack failed for " + commandId + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    private String httpGet(String urlString, String token) throws Exception {
        return httpRequest("GET", urlString, token, null);
    }

    private String httpPost(String urlString, String token, String body) throws Exception {
        return httpRequest("POST", urlString, token, body);
    }

    private String httpRequest(String method, String urlString, String token, String body) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }
            }

            int code = conn.getResponseCode();
            InputStream is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) {
                throw new Exception("HTTP " + code + ", no response body");
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + ": " + sb);
            }
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void notifyListener(String summary) {
        if (listener == null) return;
        mainHandler.post(() -> listener.onCommandExecuted(summary));
    }

    /**
     * Tiny wrapper because {@link java.net.URLEncoder} is the only encoder we need,
     * kept in a helper to avoid checked-exception noise in the main flow.
     */
    private static class URLEncoderWrapper {
        static String encode(String value, String encoding) throws Exception {
            return java.net.URLEncoder.encode(value, encoding);
        }
    }
}
