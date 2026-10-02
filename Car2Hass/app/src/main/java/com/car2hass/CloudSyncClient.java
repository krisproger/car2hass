package com.car2hass;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional cloud synchronization of telemetry to the mytechnic.ru unified
 * account. Login is by email + TOTP code; access/refresh tokens live in
 * SecureStorage. Sending mirrors the HA path: snapshots are staged in an
 * in-memory buffer and persisted to {@link CloudSnapshotQueue} on failure, so
 * data survives offline periods and app restarts; rows are removed only after a
 * confirmed send.
 */
public final class CloudSyncClient {

    private static final String TAG = "CloudSyncClient";

    private static final long FLUSH_INTERVAL_MS = 30000;
    private static final int FLUSH_THRESHOLD = 20;
    private static final int MAX_RESPONSE_BYTES = 4096;
    private static final long EXPIRY_SKEW_MS = 60000;

    private static final long MIN_FLUSH_BACKOFF_MS = 12000;
    private static final long MAX_FLUSH_BACKOFF_MS = 300000;
    private static final long NETWORK_RETRY_DELAY_MS = 12000;

    private static final CloudBatchBuffer buffer = new CloudBatchBuffer();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean flushInProgress = new AtomicBoolean(false);
    private static volatile long lastFlushAttemptMs = 0;
    private static volatile long currentFlushBackoffMs = MIN_FLUSH_BACKOFF_MS;

    private CloudSyncClient() {}

    /**
     * Exchanges email + TOTP code for tokens. Returns "" on success, otherwise
     * an error code ("bad_credentials", "rate_limited", "totp_not_set",
     * "bad_input", "http_<code>" or "network"). Blocking — call off the UI thread.
     */
    public static String login(Context ctx, String email, String code) {
        if (ctx == null || email == null || email.trim().isEmpty()
                || code == null || code.trim().isEmpty()) {
            return "bad_input";
        }
        try {
            JSONObject body = new JSONObject();
            body.put("email", email.trim());
            body.put("code", code.trim());
            HttpResult r = post(ctx, "/api/account/token", null, body);
            if (r.code == 200) {
                JSONObject o = new JSONObject(r.body);
                if (!o.optBoolean("ok", false)) return error(o, "bad_credentials");
                storeTokens(ctx, o);
                AppConfig.setCloudEmail(ctx, email.trim());
                AppConfig.setCloudLastStatus(ctx, "");
                LogBuffer.i(TAG, "Login OK");
                return "";
            }
            if (r.code == 401) return "bad_credentials";
            if (r.code == 429) return "rate_limited";
            try {
                return error(new JSONObject(r.body), "http_" + r.code);
            } catch (Exception e) {
                return "http_" + r.code;
            }
        } catch (Exception e) {
            LogBuffer.e(TAG, "login failed: " + e.getMessage());
            return "network";
        }
    }

    /** True when a valid refresh token exists and can be used. */
    public static boolean refreshToken(Context ctx) {
        String refresh = AppConfig.getCloudRefreshToken(ctx);
        if (refresh == null || refresh.isEmpty()) {
            invalidateTokens(ctx);
            return false;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("grant_type", "refresh_token");
            body.put("refresh_token", refresh);
            HttpResult r = post(ctx, "/api/account/token", null, body);
            if (r.code == 200) {
                JSONObject o = new JSONObject(r.body);
                if (o.optBoolean("ok", false)) {
                    storeTokens(ctx, o);
                    return true;
                }
            }
            LogBuffer.w(TAG, "Token refresh failed: HTTP " + r.code);
            invalidateTokens(ctx);
            return false;
        } catch (Exception e) {
            LogBuffer.e(TAG, "refreshToken: " + e.getMessage());
            return false;
        }
    }

    /** Binds the configured car name once; tolerates 200 and 409. */
    public static boolean ensureCarBound(Context ctx) {
        String carName = AppConfig.getCloudCarName(ctx);
        if (carName == null || carName.isEmpty()) return false;
        if (AppConfig.isCloudCarBound(ctx, carName)) return true;
        try {
            JSONObject body = new JSONObject();
            body.put("car_name", carName);
            HttpResult r = authorizedPost(ctx, "/api/cars", body);
            if (r == null) return false;
            if (r.code == 200 || r.code == 409) {
                AppConfig.setCloudCarBound(ctx, carName);
                LogBuffer.i(TAG, "Car bound (" + r.code + "): " + carName);
                return true;
            }
            if (r.code == 401) {
                invalidateTokens(ctx);
            } else {
                AppConfig.setCloudLastStatus(ctx, "bind_http_" + r.code);
                LogBuffer.w(TAG, "Car bind failed: HTTP " + r.code);
            }
            return false;
        } catch (Exception e) {
            LogBuffer.e(TAG, "ensureCarBound: " + e.getMessage());
            return false;
        }
    }

    /** Buffers a snapshot; flushes on a background thread by time or threshold. */
    public static void collectSnapshot(Context ctx, Map<String, Object> sensors, Double lat, Double lon) {
        try {
            if (ctx == null || sensors == null || sensors.isEmpty()) return;
            if (!AppConfig.isCloudSyncEnabled(ctx)) return;
            if (AppConfig.getCloudAccessToken(ctx).isEmpty()) return;

            JSONObject s = new JSONObject();
            for (Map.Entry<String, Object> e : sensors.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                s.put(e.getKey(), e.getValue());
            }
            if (s.length() == 0) return;

            buffer.add(CloudBatchBuffer.buildSnapshot(System.currentTimeMillis() / 1000, s, lat, lon));
            long now = System.currentTimeMillis();
            if (buffer.size() >= FLUSH_THRESHOLD || now - lastFlushAttemptMs >= FLUSH_INTERVAL_MS) {
                flushAsync(ctx.getApplicationContext());
            }
        } catch (Exception e) {
            LogBuffer.e(TAG, "collectSnapshot: " + e.getMessage());
        }
    }

    /** Backwards-compatible alias for {@link #collectSnapshot}. */
    public static void sendBatch(Context ctx, Map<String, Object> sensors, Double lat, Double lon) {
        collectSnapshot(ctx, sensors, lat, lon);
    }

    /** Non-blocking flush of queued + buffered snapshots. */
    public static void flushAsync(final Context ctx) {
        if (!flushInProgress.compareAndSet(false, true)) return;
        executor.submit(() -> {
            JSONArray ram = null;
            try {
                if (!AppConfig.isCloudSyncEnabled(ctx)) return;
                if (AppConfig.getCloudAccessToken(ctx).isEmpty()) return;

                long now = System.currentTimeMillis();
                // Backoff only holds back an idle flush; anything to send goes now.
                if (now - lastFlushAttemptMs < currentFlushBackoffMs
                        && CloudSnapshotQueue.getCount(ctx) == 0 && buffer.isEmpty()) {
                    return;
                }

                List<CloudSnapshotQueue.Snapshot> queued =
                        CloudSnapshotQueue.dequeueChunk(ctx, CloudSnapshotQueue.DEQUEUE_CHUNK_SIZE);
                ram = buffer.drain();
                if (queued.isEmpty() && ram.length() == 0) return;

                lastFlushAttemptMs = System.currentTimeMillis();

                List<JSONObject> batchList = new ArrayList<>();
                long maxQueuedId = -1;
                for (CloudSnapshotQueue.Snapshot q : queued) {
                    batchList.add(CloudBatchBuffer.buildSnapshot(q.ts, new JSONObject(q.signalJson), q.lat, q.lon));
                    if (q.queueId > maxQueuedId) maxQueuedId = q.queueId;
                }
                for (int i = 0; i < ram.length(); i++) {
                    JSONObject o = ram.optJSONObject(i);
                    if (o != null) batchList.add(o);
                }
                batchList.sort(Comparator.comparingLong(o -> o.optLong("t", 0)));
                JSONArray batch = new JSONArray();
                for (JSONObject o : batchList) batch.put(o);
                if (batch.length() == 0) return;

                if (!isNetworkAvailable(ctx)) {
                    persist(ctx, ram);
                    LogBuffer.d(TAG, "No network, " + batch.length() + " snapshots kept");
                    return;
                }

                String carName = AppConfig.getCloudCarName(ctx);
                if (carName.isEmpty()) {
                    persist(ctx, ram);
                    AppConfig.setCloudLastStatus(ctx, "no_car_name");
                    return;
                }
                if (!ensureCarBound(ctx)) {
                    persist(ctx, ram);
                    AppConfig.setCloudLastStatus(ctx, "car_not_bound");
                    return;
                }

                HttpResult r = authorizedPost(ctx, "/api/telemetry",
                        CloudBatchBuffer.buildPayload(carName, batch));
                if (r != null && r.code == 200) {
                    if (maxQueuedId >= 0) CloudSnapshotQueue.deleteUpTo(ctx, maxQueuedId);
                    AppConfig.setCloudLastSyncMs(ctx, System.currentTimeMillis());
                    AppConfig.setCloudLastStatus(ctx, "ok");
                    currentFlushBackoffMs = MIN_FLUSH_BACKOFF_MS;
                    LogBuffer.i(TAG, "Cloud sync OK: " + batch.length() + " snapshots");
                } else {
                    persist(ctx, ram);
                    int code = r == null ? 0 : r.code;
                    AppConfig.setCloudLastStatus(ctx, "http_" + code);
                    currentFlushBackoffMs = Math.min(currentFlushBackoffMs * 2, MAX_FLUSH_BACKOFF_MS);
                    LogBuffer.w(TAG, "Cloud sync failed: HTTP " + code
                            + " — retry in " + currentFlushBackoffMs + " ms");
                }
            } catch (Exception e) {
                // Queue rows were never deleted; persist the RAM batch so it
                // survives a restart (previously the drained batch was lost here).
                LogBuffer.e(TAG, "flush: " + e.getMessage());
                persist(ctx, ram);
                currentFlushBackoffMs = Math.min(currentFlushBackoffMs * 2, MAX_FLUSH_BACKOFF_MS);
            } finally {
                flushInProgress.set(false);
            }
        });
    }

    /** Re-flushes promptly when connectivity returns. */
    public static void onNetworkAvailable(final Context ctx) {
        try {
            if (!AppConfig.isCloudSyncEnabled(ctx)) return;
            if (flushInProgress.get()) {
                executor.submit(() -> {
                    try { Thread.sleep(NETWORK_RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
                    flushAsync(ctx.getApplicationContext());
                });
            } else {
                flushAsync(ctx.getApplicationContext());
            }
        } catch (Exception e) {
            LogBuffer.e(TAG, "onNetworkAvailable: " + e.getMessage());
        }
    }

    /** Persists drained RAM snapshots to the durable queue (restart-safe). */
    private static void persist(Context ctx, JSONArray ram) {
        if (ram == null || ram.length() == 0) return;
        try {
            List<CloudSnapshotQueue.Snapshot> snaps = new ArrayList<>();
            for (int i = 0; i < ram.length(); i++) {
                JSONObject o = ram.optJSONObject(i);
                if (o == null) continue;
                double lat = Double.NaN, lon = Double.NaN;
                JSONObject g = o.optJSONObject("g");
                if (g != null) {
                    lat = g.optDouble("lat", Double.NaN);
                    lon = g.optDouble("lon", Double.NaN);
                }
                JSONObject s = o.optJSONObject("s");
                snaps.add(new CloudSnapshotQueue.Snapshot(
                        o.optLong("t", 0), lat, lon, s == null ? "{}" : s.toString()));
            }
            CloudSnapshotQueue.enqueueAll(ctx, snaps);
        } catch (Exception e) {
            LogBuffer.e(TAG, "persist: " + e.getMessage());
        }
    }

    private static HttpResult authorizedPost(Context ctx, String path, JSONObject body) throws Exception {
        String token = AppConfig.getCloudAccessToken(ctx);
        if (token.isEmpty()) return null;
        if (System.currentTimeMillis() >= AppConfig.getCloudTokenExpiryMs(ctx) - EXPIRY_SKEW_MS) {
            if (!refreshToken(ctx)) return null;
            token = AppConfig.getCloudAccessToken(ctx);
            if (token.isEmpty()) return null;
        }
        HttpResult r = post(ctx, path, token, body);
        if (r.code == 401) {
            if (!refreshToken(ctx)) return r;
            String retryToken = AppConfig.getCloudAccessToken(ctx);
            if (retryToken.isEmpty()) return r;
            r = post(ctx, path, retryToken, body);
        }
        return r;
    }

    private static void storeTokens(Context ctx, JSONObject o) {
        String access = o.optString("access_token", "");
        String refresh = o.optString("refresh_token", "");
        long expiresIn = o.optLong("expires_in", 3600);
        AppConfig.saveCloudAccessToken(ctx, access);
        if (!refresh.isEmpty()) {
            AppConfig.saveCloudRefreshToken(ctx, refresh);
        }
        AppConfig.setCloudTokenExpiryMs(ctx, System.currentTimeMillis() + expiresIn * 1000L);
    }

    private static void invalidateTokens(Context ctx) {
        AppConfig.clearCloudTokens(ctx);
        AppConfig.setCloudSyncEnabled(ctx, false);
        AppConfig.setCloudLastStatus(ctx, "logged_out");
        LogBuffer.w(TAG, "Cloud tokens cleared, cloud sync disabled");
    }

    private static String error(JSONObject o, String fallback) {
        String e = o == null ? "" : o.optString("error", "");
        return e.isEmpty() ? fallback : e;
    }

    private static HttpResult post(Context ctx, String path, String bearer, JSONObject body) throws Exception {
        String base = AppConfig.getCloudBaseUrl(ctx);
        URL url = new URL(base + path);
        if ("http".equalsIgnoreCase(url.getProtocol()) && !NetSafety.isPrivateHost(url.getHost())) {
            LogBuffer.w(TAG, "Cleartext HTTP to a public host '" + url.getHost()
                    + "'. The account token can be read on the network.");
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (bearer != null && !bearer.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + bearer);
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            boolean success = code >= 200 && code < 300;
            return new HttpResult(code, readBody(conn, success));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readBody(HttpURLConnection conn, boolean success) {
        try (InputStream is = success ? conn.getInputStream() : conn.getErrorStream()) {
            if (is == null) return "";
            byte[] b = new byte[MAX_RESPONSE_BYTES];
            int n = is.read(b);
            return n > 0 ? new String(b, 0, n, StandardCharsets.UTF_8) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isNetworkAvailable(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network nw = cm.getActiveNetwork();
            if (nw == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return false;
        }
    }

    private static final class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }
}
