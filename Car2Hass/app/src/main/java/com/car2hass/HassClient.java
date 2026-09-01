package com.car2hass;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
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

    public static class Snapshot {
        public final long timestamp;
        public final double lat;
        public final double lon;
        public final float accuracy;
        /** GPS fix time in epoch seconds (0 when unknown — falls back to timestamp). */
        public final long fixTimeSec;
        public final String signalJson;  // JSON object of all signal key:value pairs
        /** Database row id when this snapshot came from the queue (-1 for in-memory). */
        public long queueId = -1;

        public Snapshot(long timestamp, double lat, double lon, float accuracy,
                        long fixTimeSec, String signalJson) {
            this.timestamp = timestamp;
            this.lat = lat;
            this.lon = lon;
            this.accuracy = accuracy;
            this.fixTimeSec = fixTimeSec;
            this.signalJson = signalJson;
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
            return new Snapshot(
                obj.getLong("t"),
                lat,
                lon,
                accuracy,
                fixTimeSec,
                obj.getJSONObject("s").toString()
            );
        }
    }

    public interface FlushCallback {
        void onResult(boolean success, String message);
    }

    public static synchronized void init(Context ctx) {
        if (initialized) return;
        initialized = true;
        LogBuffer.i("HassClient", "Initialized, queued snapshots: " + SnapshotQueue.getCount(ctx));
    }

    public static void collectSnapshot(Context ctx, double lat, double lon,
                                       float accuracy, long fixTimeSec, String signalJson) {
        try {
            Snapshot snap = new Snapshot(System.currentTimeMillis() / 1000, lat, lon,
                    accuracy, fixTimeSec, signalJson);
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
            if (now - lastFlushAttemptMs < currentFlushBackoffMs && SnapshotQueue.getCount(ctx) == 0 && isBufferEmpty()) {
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
        if ("http".equals(scheme) && !NetSafety.isPrivateHost(host) && warnedCleartextHosts.add(host)) {
            LogBuffer.w("HassClient", "Cleartext HTTP to a public host '" + host
                    + "'. Anyone on the network can read your token. Use HTTPS for remote servers.");
        }
        flushExecutor.submit(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject payload = new JSONObject();
                payload.put("car_name", carName);
                payload.put("vvn", CANDataReader.sVin);
                payload.put("firmware", CANDataReader.sFirmware);
                payload.put("app_version", AppInfo.getVersionString(ctx));

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
                if (success) {
                    currentFlushBackoffMs = MIN_FLUSH_BACKOFF_MS;
                } else {
                    currentFlushBackoffMs = Math.min(currentFlushBackoffMs * 2, MAX_FLUSH_BACKOFF_MS);
                }
                LogBuffer.i("HassClient", "Flush " + batch.size() + " snapshots → HTTP " + code
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
