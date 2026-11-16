package com.car2hass;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HaCommandPoller {
    private static final long POLL_INTERVAL_MS = 10_000L;
    private static final long POLL_ERROR_INTERVAL_MS = 30_000L;
    private static final long NOT_FOUND_LOG_INTERVAL_MS = 10 * 60 * 1000L;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private final Context appContext;
    private final HaEndpointConfig endpoint;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = this::pollLoop;
    private volatile boolean endpointMissing;
    private volatile long last404LoggedMs;
    private volatile long nextRetryDelayMs;

    private OnCommandExecutedListener listener;

    public interface OnCommandExecutedListener {
        void onCommandExecuted(String summary);
    }

    public HaCommandPoller(Context context, HaEndpointConfig endpoint) {
        this.appContext = context.getApplicationContext();
        this.endpoint = endpoint;
        LogBuffer.init(appContext);
    }

    public void setListener(OnCommandExecutedListener listener) {
        this.listener = listener;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean matches(HaEndpointConfig other) {
        return other != null && endpoint.id.equals(other.id)
                && endpoint.name.equals(other.name)
                && endpoint.address.equals(other.address)
                && endpoint.https == other.https
                && endpoint.carName.equals(other.carName)
                && endpoint.enabled == other.enabled
                && endpoint.commandsEnabled == other.commandsEnabled;
    }

    public void start() {
        LogBuffer.i(tag(), "start() requested, running=" + running.get());
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "HaCommandPoller-" + endpoint.id);
                t.setUncaughtExceptionHandler((thread, ex) -> {
                    LogBuffer.e(tag(), "poller thread died: " + ex.getMessage());
                    running.compareAndSet(true, false);
                });
                return t;
            });
        }
        if (running.compareAndSet(false, true)) {
            executor.submit(pollRunnable);
            LogBuffer.i(tag(), "poll loop started");
        }
    }

    public void stop() {
        running.set(false);
        if (executor != null) executor.shutdownNow();
        LogBuffer.i(tag(), "poll loop stopped");
    }

    private void pollLoop() {
        while (running.get()) {
            boolean hadError;
            try {
                hadError = !pollOnce();
            } catch (Exception e) {
                LogBuffer.e(tag(), "poll error: " + e.getMessage());
                hadError = true;
            }
            long sleep = nextRetryDelayMs > 0
                    ? nextRetryDelayMs
                    : (hadError ? POLL_ERROR_INTERVAL_MS : POLL_INTERVAL_MS);
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                break;
            }
        }
        running.set(false);
    }

    private boolean pollOnce() {
        String token = new SecureStorage(appContext).getEndpointToken(endpoint.id);
        if (token.isEmpty() || endpoint.carName.isEmpty()) {
            return true;
        }
        String pollUrl = HaCommandUrls.pollUrl(endpoint);
        String response;
        try {
            response = httpGet(pollUrl, token);
            endpointMissing = false;
            nextRetryDelayMs = 0L;
        } catch (HttpStatusException e) {
            CommandPollClassifier.Kind kind =
                    CommandPollClassifier.classify(e.code, e.body);
            nextRetryDelayMs = CommandPollClassifier.retryDelayMs(kind);
            endpointMissing = kind == CommandPollClassifier.Kind.ENDPOINT_MISSING;
            switch (kind) {
                case INTEGRATION_NOT_LOADED:
                    logThrottled("integration not loaded; retrying");
                    break;
                case UNKNOWN_CAR_NAME:
                    logThrottled("unknown car_name '" + endpoint.carName
                            + "'; check the integration car name");
                    break;
                case ENDPOINT_MISSING:
                    logIntegrationMismatch(token);
                    break;
                case AUTH_ERROR:
                    logThrottled("token rejected (HTTP " + e.code + "); check the long-lived token");
                    break;
                default:
                    LogBuffer.d(tag(), "poll failed: HTTP " + e.code + " " + e.body);
                    break;
            }
            return false;
        } catch (Exception e) {
            endpointMissing = false;
            nextRetryDelayMs = CommandPollClassifier.retryDelayMs(
                    CommandPollClassifier.Kind.TRANSIENT);
            LogBuffer.d(tag(), "poll failed: " + e.getMessage());
            return false;
        }

        try {
            JSONObject obj = new JSONObject(response);
            JSONArray commands = obj.optJSONArray("commands");
            if (commands == null || commands.length() == 0) return true;
            LogBuffer.i(tag(), "received " + commands.length() + " command(s)");
            for (int i = 0; i < commands.length(); i++) {
                JSONObject cmd = commands.optJSONObject(i);
                if (cmd != null) enqueueSingleCommand(cmd);
            }
            return true;
        } catch (Exception e) {
            LogBuffer.d(tag(), "invalid poll response: " + e.getMessage());
            return false;
        }
    }

    private void enqueueSingleCommand(JSONObject cmd) {
        String commandId = cmd.optString("id");
        String commandKey = cmd.optString("command");
        JSONObject params = cmd.optJSONObject("params");
        String value = params != null ? params.optString("value", "") : "";

        CommandQueue.enqueue(appContext, commandKey, value, CommandExecutor.Source.HA, result -> {
            String status = result.success ? "ok" : "error";
            String message = result.success
                    ? (result.verified
                        ? appContext.getString(R.string.commands_result_ok_short)
                            + " (" + result.verificationMessage + ")"
                        : appContext.getString(R.string.commands_result_ok_short)
                            + " [" + result.verificationMessage + "]")
                    : (result.error != null ? result.error
                        : appContext.getString(R.string.commands_result_fail));
            String summary = commandId + " " + commandKey + "=" + value + " -> "
                    + status + " " + message;
            CommandLog.append(appContext, summary);
            notifyListener(summary);
            LogBuffer.i(tag(), "command result: " + summary);
            acknowledgeCommand(commandId, status, message);
        });
    }

    private boolean acknowledgeCommand(String commandId, String status, String message) {
        String token = new SecureStorage(appContext).getEndpointToken(endpoint.id);
        if (token.isEmpty()) return false;
        try {
            JSONObject body = new JSONObject();
            body.put("command_id", commandId);
            body.put("status", status);
            body.put("message", message == null ? "" : message);
            String response = httpPost(HaCommandUrls.ackUrl(endpoint), token, body.toString());
            LogBuffer.i(tag(), "ack " + commandId + " -> " + response);
            return true;
        } catch (Exception e) {
            LogBuffer.e(tag(), "ack failed for " + commandId + ": " + e.getMessage());
            return false;
        }
    }

    private void logIntegrationMismatch(String token) {
        long now = System.currentTimeMillis();
        if (now - last404LoggedMs < NOT_FOUND_LOG_INTERVAL_MS) return;
        last404LoggedMs = now;
        IntegrationInfo info = IntegrationInfo.query(endpoint.haAddress().baseUrl(), token);
        if (info == null) {
            LogBuffer.w(tag(), "commands endpoint missing or integration unreachable");
        } else if (!info.loaded) {
            LogBuffer.w(tag(), "integration not loaded; command poll will retry");
        } else if (!info.isCompatible()) {
            LogBuffer.w(tag(), "integration " + info.integrationVersion + " (api "
                    + info.apiVersion + ") too old, need api >= "
                    + IntegrationInfo.MIN_API_VERSION);
        } else {
            LogBuffer.w(tag(), "commands endpoint missing for compatible integration "
                    + info.integrationVersion + " (api " + info.apiVersion + ")");
        }
    }

    private void logThrottled(String message) {
        long now = System.currentTimeMillis();
        if (now - last404LoggedMs < NOT_FOUND_LOG_INTERVAL_MS) return;
        last404LoggedMs = now;
        LogBuffer.w(tag(), message);
    }

    private String httpGet(String urlString, String token) throws Exception {
        return httpRequest("GET", urlString, token, null);
    }

    private String httpPost(String urlString, String token, String body) throws Exception {
        return httpRequest("POST", urlString, token, body);
    }

    private String httpRequest(String method, String urlString, String token, String body)
            throws Exception {
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
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
            }
            if (code < 200 || code >= 300) {
                throw new HttpStatusException(code, sb.toString());
            }
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void notifyListener(String summary) {
        if (listener != null) {
            mainHandler.post(() -> listener.onCommandExecuted(summary));
        }
    }

    private String tag() {
        return "HA[" + endpoint.name + "]";
    }

    private static final class HttpStatusException extends Exception {
        final int code;
        final String body;

        HttpStatusException(int code, String body) {
            super("HTTP " + code + ": " + body);
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }
}
