package com.car2hass;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class HassClient {
    private static final int MAX_BUFFER = 1000;

    private static final ArrayList<Snapshot> buffer = new ArrayList<>();
    // Guards only the in-memory buffer. Disk I/O (SnapshotQueue) and the HTTP
    // send never hold it, so a long flush cannot block collectSnapshot or
    // onNetworkAvailable (previously all of these shared the class monitor).
    private static final Object bufferLock = new Object();
    private static final AtomicBoolean flushInProgress = new AtomicBoolean(false);
    private static final ExecutorService flushExecutor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static volatile boolean initialized = false;

    // Hosts we already warned about for cleartext HTTP to a public address.
    private static final Set<String> warnedCleartextHosts =
            Collections.synchronizedSet(new HashSet<String>());

    // Exponential backoff for HA connection failures to avoid log/CPU spam.
    private static final long MIN_FLUSH_BACKOFF_MS = 12000;
    private static final long MAX_FLUSH_BACKOFF_MS = 300000;
    private static final long NETWORK_RETRY_DELAY_MS = 12000;
    private static volatile long currentFlushBackoffMs = MIN_FLUSH_BACKOFF_MS;
    private static volatile long lastFlushAttemptMs = 0;

    // Delta snapshots (#91): last sent full reference + resync triggers. Guarded by
    // the single-threaded snapshot executor; forceFullNext also set from other threads.
    private static final long FULL_RESYNC_INTERVAL_MS = 3600_000; // periodic safety full
    private static volatile java.util.Map<String, String> lastSentHA = new java.util.LinkedHashMap<>();
    private static volatile boolean forceFullNext = true;
    private static volatile long lastFullSendMs = 0;

    public static class Snapshot {
        public final long timestamp;
        public final double lat;
        public final double lon;
        public final float accuracy;
        /** GPS fix time in epoch seconds (0 when unknown — falls back to timestamp). */
        public final long fixTimeSec;
        public final String signalJson;  // JSON object of all signal key:value pairs
        /** JSON array of disabled signal keys ("" when none). */
        public final String disabledJson;
        /** Database row id when this snapshot came from the queue (-1 for in-memory). */
        public long queueId = -1;
        /** True when signalJson holds only changed signals (delta, #91). */
        public boolean delta = false;
        /** channel → changed keys JSON ("" when not a delta). */
        public String chJson = "";

        public Snapshot(long timestamp, double lat, double lon, float accuracy,
                        long fixTimeSec, String signalJson, String disabledJson) {
            this(timestamp, lat, lon, accuracy, fixTimeSec, signalJson, disabledJson, false, "");
        }

        public Snapshot(long timestamp, double lat, double lon, float accuracy,
                        long fixTimeSec, String signalJson, String disabledJson,
                        boolean delta, String chJson) {
            this.timestamp = timestamp;
            this.lat = lat;
            this.lon = lon;
            this.accuracy = accuracy;
            this.fixTimeSec = fixTimeSec;
            this.signalJson = signalJson;
            this.disabledJson = disabledJson;
            this.delta = delta;
            this.chJson = chJson == null ? "" : chJson;
        }

        public JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("t", timestamp);
            // Only include GPS block when we have a valid fix. Sending 0,0 would
            // place the vehicle in the Atlantic Ocean, so NaN is treated as "unknown".
            if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                JSONObject gps = new JSONObject();
                gps.put("lat", lat);
                gps.put("lon", lon);
                gps.put("a", accuracy);
                // Time of the actual GPS fix; the integration attributes the point
                // to this moment instead of the (later) snapshot collection time.
                if (fixTimeSec > 0) {
                    gps.put("t", fixTimeSec);
                }
                obj.put("g", gps);
            }
            obj.put("s", new JSONObject(signalJson));
            if (disabledJson != null && !disabledJson.isEmpty()) {
                obj.put("d", new JSONArray(disabledJson));
            }
            if (delta) {
                obj.put("delta", true);
                if (chJson != null && !chJson.isEmpty()) {
                    obj.put("ch", new JSONObject(chJson));
                }
            }
            return obj;
        }

        public static Snapshot fromJson(JSONObject obj) throws Exception {
            JSONObject gps = obj.optJSONObject("g");
            double lat = Double.NaN;
            double lon = Double.NaN;
            float accuracy = 0;
            long fixTimeSec = 0;
            if (gps != null) {
                lat = gps.optDouble("lat", Double.NaN);
                lon = gps.optDouble("lon", Double.NaN);
                accuracy = (float) gps.optDouble("a", 0);
                fixTimeSec = gps.optLong("t", 0);
            }
            JSONObject ch = obj.optJSONObject("ch");
            return new Snapshot(
                obj.getLong("t"),
                lat,
                lon,
                accuracy,
                fixTimeSec,
                obj.getJSONObject("s").toString(),
                obj.has("d") ? obj.getJSONArray("d").toString() : "",
                obj.optBoolean("delta", false),
                ch == null ? "" : ch.toString()
            );
        }
    }

    public interface FlushCallback {
        void onResult(boolean success, String message);
    }

    public static synchronized void init(Context ctx) {
        if (initialized) return;
        initialized = true;
        forceFullNext = true; // first snapshot after start is always full (resync)
        LogBuffer.i("HassClient", "Initialized, queued snapshots: " + SnapshotQueue.getCount(ctx));
    }

    public static void collectSnapshot(Context ctx, double lat, double lon,
                                       float accuracy, long fixTimeSec, String signalJson) {
        try {
            java.util.Set<String> disabled = com.car2hass.AppConfig.getDisabledSignals(ctx);
            String disabledJson = "";
            if (disabled != null && !disabled.isEmpty()) {
                org.json.JSONArray arr = new org.json.JSONArray();
                for (String k : disabled) arr.put(k);
                disabledJson = arr.toString();
            }
            // Pre-queue dedup: skip a snapshot that repeats the previous one
            // while the car barely moved (stationary → no redundant load).
            long tsSec = System.currentTimeMillis() / 1000;
            if (SnapshotDedup.isDuplicate(SnapshotDedup.Channel.HA, lat, lon, signalJson)) {
                LogBuffer.d("HassClient", "snapshot skipped (stationary, unchanged)");
                return;
            }
            SnapshotDedup.keep(SnapshotDedup.Channel.HA, lat, lon, tsSec, signalJson);

            // Delta (#91): send only changed signals when the integration supports
            // it (API >= 4). Dedup above still used the FULL signalJson.
            String sendJson = signalJson;
            boolean delta = false;
            String chJson = "";
            try {
                org.json.JSONObject fullObj = new org.json.JSONObject(signalJson);
                java.util.Map<String, String> full = new java.util.LinkedHashMap<>();
                java.util.Iterator<String> it = fullObj.keys();
                com.car2hass.vehicle.ValueStore vs = com.car2hass.vehicle.ValueStore.main();
                java.util.Map<String, String> keyChannel = new java.util.HashMap<>();
                while (it.hasNext()) {
                    String k = it.next();
                    full.put(k, String.valueOf(fullObj.opt(k)));
                    if (vs != null) {
                        String c = vs.sourceOf(k);
                        if (c != null) keyChannel.put(k, c);
                    }
                }
                long nowMs = System.currentTimeMillis();
                boolean forceFull = forceFullNext
                        || !IntegrationInfo.supportsDelta()
                        || (nowMs - lastFullSendMs) > FULL_RESYNC_INTERVAL_MS;
                DeltaSnapshotBuilder.Result res =
                        DeltaSnapshotBuilder.build(full, lastSentHA, keyChannel, forceFull);
                lastSentHA = res.lastSent;
                forceFullNext = false;
                if (!res.delta) {
                    lastFullSendMs = nowMs;
                }
                delta = res.delta;
                sendJson = new org.json.JSONObject(res.signals).toString();
                if (res.delta && !res.channels.isEmpty()) {
                    org.json.JSONObject ch = new org.json.JSONObject();
                    for (java.util.Map.Entry<String, java.util.List<String>> e : res.channels.entrySet()) {
                        org.json.JSONArray arr = new org.json.JSONArray();
                        for (String k : e.getValue()) {
                            arr.put(k);
                        }
                        ch.put(e.getKey(), arr);
                    }
                    chJson = ch.toString();
                }
            } catch (Exception e) {
                // Any delta failure falls back to the full snapshot (safe).
                delta = false;
                sendJson = signalJson;
                chJson = "";
            }

            Snapshot snap = new Snapshot(tsSec, lat, lon,
                    accuracy, fixTimeSec, sendJson, disabledJson, delta, chJson);
            synchronized (bufferLock) {
                buffer.add(snap);
                if (buffer.size() > MAX_BUFFER) {
                    buffer.remove(0);
                }
            }
        } catch (Exception e) {
            LogBuffer.e("HassClient", "collectSnapshot failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static void flush(Context ctx, FlushCallback callback) {
        try {
            if (!AppConfig.isHassEnabled(ctx)) {
                LogBuffer.d("HassClient", "HA disabled — not flushing");
                if (callback != null) callback.onResult(false, "HA disabled");
                return;
            }

            long now = System.currentTimeMillis();
            // Back off after failures regardless of queue depth. A non-empty queue
            // used to disable backoff, which turned an unreachable HA into a 4s
            // re-dequeue/encode/log loop that never drained (main Voyah lag source).
            if (now - lastFlushAttemptMs < currentFlushBackoffMs) {
                LogBuffer.d("HassClient", "flush backed off (" + currentFlushBackoffMs + " ms)");
                if (callback != null) callback.onResult(false, "backed off");
                return;
            }
            lastFlushAttemptMs = now;

            if (!flushInProgress.compareAndSet(false, true)) {
                LogBuffer.d("HassClient", "flush already in progress, skipping");
                if (callback != null) callback.onResult(false, "flush in progress");
                return;
            }

            if (isBufferEmpty() && SnapshotQueue.getCount(ctx) == 0) {
                flushInProgress.set(false);
                if (callback != null) callback.onResult(true, "nothing to send");
                return;
            }

            // Build a chronologically ordered batch, never exceeding the HA batch
            // limit. Queued rows are read WITHOUT deletion — they are removed only
            // after a confirmed send (deleteUpTo), so a crash mid-flush loses nothing.
            List<Snapshot> queueBatch = SnapshotQueue.dequeueChunk(ctx, SnapshotQueue.DEQUEUE_CHUNK_SIZE);
            int room = SnapshotQueue.DEQUEUE_CHUNK_SIZE - queueBatch.size();

            // Copy the in-memory buffer out from under bufferLock only. collectSnapshot
            // keeps appending while we talk to the SQLite queue — that is fine, the
            // next flush picks up whatever lands here.
            List<Snapshot> bufferBatch;
            synchronized (bufferLock) {
                bufferBatch = new ArrayList<>(buffer);
                buffer.clear();
            }
            List<Snapshot> batch = new ArrayList<>(queueBatch);
            if (room > 0 && !bufferBatch.isEmpty()) {
                int take = Math.min(room, bufferBatch.size());
                batch.addAll(bufferBatch.subList(0, take));
                // Keep the unwritten remainder of the in-memory buffer for next flush.
                if (take < bufferBatch.size()) {
                    synchronized (bufferLock) {
                        buffer.addAll(0, bufferBatch.subList(take, bufferBatch.size()));
                    }
                }
            }

            if (batch.isEmpty()) {
                flushInProgress.set(false);
                if (callback != null) callback.onResult(true, "nothing to send");
                return;
            }

            Collections.sort(batch, new Comparator<Snapshot>() {
                @Override
                public int compare(Snapshot a, Snapshot b) {
                    return Long.compare(a.timestamp, b.timestamp);
                }
            });

            long minTs = batch.get(0).timestamp;
            long maxTs = batch.get(batch.size() - 1).timestamp;
            LogBuffer.i("HassClient", "Flushing " + batch.size() + " snapshots, t=" + minTs + ".." + maxTs);

            sendBatch(ctx, batch, (success, msg) -> {
                try {
                    flushInProgress.set(false);
                    if (success) {
                        currentFlushBackoffMs = MIN_FLUSH_BACKOFF_MS;
                        // Remove only rows confirmed sent. In-memory snapshots have
                        // queueId < 0 and are never touched.
                        long maxQueuedId = -1;
                        for (Snapshot s : batch) {
                            if (s.queueId > maxQueuedId) maxQueuedId = s.queueId;
                        }
                        if (maxQueuedId >= 0) {
                            SnapshotQueue.deleteUpTo(ctx, maxQueuedId);
                        }
                        // Track sent volume (same metric as SnapshotQueue.getApproximateSize).
                        int bytes = batch.size() * 200;
                        for (Snapshot s : batch) {
                            bytes += s.signalJson.length();
                        }
                        SendHistory.record(ctx, bytes, batch.size());
                    } else {
                        // Grow the backoff so an unreachable/misconfigured HA is
                        // retried with increasing spacing instead of every cycle.
                        currentFlushBackoffMs = Math.min(currentFlushBackoffMs * 2, MAX_FLUSH_BACKOFF_MS);
                        // Next snapshot after recovery must be full (delta resync).
                        forceFullNext = true;
                        // Re-enqueue only in-memory snapshots; queued rows were never
                        // deleted, so re-inserting them would duplicate telemetry.
                        List<Snapshot> toRequeue = new ArrayList<>();
                        for (Snapshot s : batch) {
                            if (s.queueId < 0) toRequeue.add(s);
                        }
                        SnapshotQueue.enqueueAll(ctx, toRequeue);
                    }
                    if (callback != null) callback.onResult(success, msg);
                } catch (Exception e) {
                    LogBuffer.e("HassClient", "flush callback failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    flushInProgress.set(false);
                    if (callback != null) callback.onResult(false, e.getMessage());
                }
            });
        } catch (Exception e) {
            LogBuffer.e("HassClient", "flush failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            flushInProgress.set(false);
            if (callback != null) callback.onResult(false, e.getMessage());
        }
    }

    private static boolean isBufferEmpty() {
        synchronized (bufferLock) {
            return buffer.isEmpty();
        }
    }

    public static int getBufferSize() {
        synchronized (bufferLock) {
            return buffer.size();
        }
    }

    // ─── Network restore ───

    public static void onNetworkAvailable(final Context ctx) {
        forceFullNext = true; // network recovered → full resync
        // A network change is a fresh start: drop any failure backoff so the
        // queued telemetry is retried immediately.
        currentFlushBackoffMs = MIN_FLUSH_BACKOFF_MS;
        lastFlushAttemptMs = 0L;
        if (flushInProgress.get()) {
            LogBuffer.d("HassClient", "Network restored, but flush already in progress — retry in " + NETWORK_RETRY_DELAY_MS + " ms");
            mainHandler.removeCallbacksAndMessages(null);
            mainHandler.postDelayed(() -> flush(ctx, null), NETWORK_RETRY_DELAY_MS);
            return;
        }
        LogBuffer.i("HassClient", "Network restored, flushing buffer");
        flush(ctx, null);
    }

    // ─── HTTP send ───

    private static void sendBatch(Context ctx, List<Snapshot> batch, FlushCallback callback) {
        if (!isNetworkAvailable(ctx)) {
            SendHistory.recordAttempt(ctx, "no network");
            LogBuffer.w("HassClient", "No network, deferring flush");
            if (callback != null) callback.onResult(false, "no network");
            return;
        }

        String host = AppConfig.getHassHost(ctx);
        int port = AppConfig.getHassPort(ctx);
        String token = AppConfig.getHassToken(ctx);
        String carName = AppConfig.getCarName(ctx);
        boolean https = AppConfig.isHassHttps(ctx);

        if (host.isEmpty() || token.isEmpty() || carName.isEmpty()) {
            SendHistory.recordAttempt(ctx, "config incomplete");
            if (callback != null) callback.onResult(false, "config incomplete");
            return;
        }

        final String scheme = https ? "https" : "http";
        // Refuse to send the HA long-lived token in cleartext to a public host.
        // LAN/loopback HTTP stays allowed (HA on http://192.168.x.x:8123), but a
        // public hostname/IP must use HTTPS — otherwise the token is exposed.
        if ("http".equals(scheme) && !NetSafety.isPrivateHost(host)) {
            SendHistory.recordAttempt(ctx, "insecure_http_blocked");
            LogBuffer.w("HassClient", "Refusing cleartext HTTP to public host '" + host
                    + "' — the HA token would be readable on the network. Enable HTTPS.");
            if (callback != null) callback.onResult(false, "insecure_http_blocked");
            return;
        }
        flushExecutor.submit(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject payload = new JSONObject();
                payload.put("car_name", carName);
                payload.put("vvn", CANDataReader.sVin);
                payload.put("firmware", CANDataReader.sFirmware);
                payload.put("app_version", AppInfo.getVersionString(ctx));

                // Device descriptor: constant per install. Omitted for the car
                // device (absent block = car) so existing telemetry is unchanged.
                // Built at the top level (not per snapshot) so queued snapshots,
                // which are persisted without it, still carry the descriptor.
                String devJson = PhoneSensors.deviceBlockJson(
                        AppConfig.isPhoneChannelEnabled(ctx) ? "phone" : "car",
                        com.car2hass.vehicle.DeviceAnon.fromContext(ctx),
                        AppConfig.getPhoneName(ctx));
                if (devJson != null) {
                    payload.put("dev", new JSONObject(devJson));
                }

                JSONArray batchJson = new JSONArray();
                for (Snapshot snap : batch) {
                    batchJson.put(snap.toJson());
                }
                payload.put("batch", batchJson);

                // Timestamp of the newest snapshot — lets HA track data freshness
                long maxTs = 0;
                for (Snapshot snap : batch) {
                    if (snap.timestamp > maxTs) maxTs = snap.timestamp;
                }
                payload.put("ts", maxTs);

                byte[] body = payload.toString().getBytes("UTF-8");

                URL url = new URL(String.format(Locale.US, "%s://%s:%d/api/cartelemetry", scheme, host, port));
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }

                int code = conn.getResponseCode();

                boolean success = (code == 200 || code == 201 || code == 202);
                String errBody = "";
                if (success) {
                    currentFlushBackoffMs = MIN_FLUSH_BACKOFF_MS;
                } else {
                    currentFlushBackoffMs = Math.min(currentFlushBackoffMs * 2, MAX_FLUSH_BACKOFF_MS);
                    // The integration returns {"status":"error","message":"<Exc>: <msg>"}
                    // on 500 — log the body so the exact exception is visible in the device log.
                    try (InputStream es = conn.getErrorStream()) {
                        if (es != null) {
                            byte[] b = new byte[2048];
                            int n = es.read(b);
                            if (n > 0) errBody = new String(b, 0, n, "UTF-8");
                        }
                    } catch (Exception ignored) {}
                }
                LogBuffer.i("HassClient", "Flush " + batch.size() + " snapshots → HTTP " + code
                        + (errBody.isEmpty() ? "" : " body=" + errBody)
                        + " (backoff=" + currentFlushBackoffMs + "ms)");
                SendHistory.recordAttempt(ctx, "HTTP " + code);
                if (callback != null) callback.onResult(success, "HTTP " + code);
            } catch (Exception e) {
                currentFlushBackoffMs = Math.min(currentFlushBackoffMs * 2, MAX_FLUSH_BACKOFF_MS);
                LogBuffer.e("HassClient", "Send error: " + e.getMessage()
                        + " (backoff=" + currentFlushBackoffMs + "ms)");
                SendHistory.recordAttempt(ctx, e.getMessage());
                if (callback != null) callback.onResult(false, e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
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
            // If we cannot determine connectivity, assume offline to avoid leaking
            // buffered snapshots into a failed send attempt.
            return false;
        }
    }
}
