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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HaTelemetryWorker {
    private static final int MAX_BUFFER = 1000;
    private static final long MIN_BACKOFF_MS = 12_000L;
    private static final long MAX_BACKOFF_MS = 300_000L;
    private static final long FULL_RESYNC_INTERVAL_MS = 3_600_000L;
    private static final ExecutorService SEND_EXECUTOR = Executors.newCachedThreadPool();

    private final Context ctx;
    private final HaEndpointConfig endpoint;
    private final String endpointId;
    private final int queueMaxMb;
    private final ArrayList<HassClient.Snapshot> buffer = new ArrayList<>();
    private final Object bufferLock = new Object();
    private final AtomicBoolean flushInProgress = new AtomicBoolean(false);
    private final AtomicBoolean integrationInfoQueried = new AtomicBoolean(false);

    private volatile long currentBackoffMs = MIN_BACKOFF_MS;
    private volatile long lastAttemptMs = 0L;
    private volatile Map<String, String> lastSent = new LinkedHashMap<>();
    private volatile boolean forceFullNext = true;
    private volatile long lastFullSendMs = 0L;
    private volatile IntegrationInfo integrationInfo;
    private volatile long lastSuccessMs;
    private volatile String lastError = "";

    private interface BatchCallback {
        void onResult(boolean success, boolean deadLettered, String message);
    }

    public HaTelemetryWorker(Context ctx, HaEndpointConfig endpoint, int queueMaxMb) {
        this.ctx = ctx.getApplicationContext();
        this.endpoint = endpoint;
        this.endpointId = endpoint.id;
        this.queueMaxMb = Math.max(1, queueMaxMb);
    }

    public String endpointId() {
        return endpointId;
    }

    public HaEndpointConfig endpoint() {
        return endpoint;
    }

    public int queueMaxMb() {
        return queueMaxMb;
    }

    public EndpointStatus status() {
        IntegrationInfo info = integrationInfo;
        return new EndpointStatus(endpointId, endpoint.enabled, true,
                lastAttemptMs, lastSuccessMs, lastError, currentBackoffMs,
                getQueueCount(), getBufferSize(), getDeadLetterCount(),
                info == null ? "" : info.integrationVersion,
                info != null && info.loaded);
    }

    private void noteAttempt() {
        lastAttemptMs = System.currentTimeMillis();
    }

    private void noteSuccess() {
        lastSuccessMs = System.currentTimeMillis();
        lastError = "";
    }

    private void noteFailure(String message) {
        lastError = message == null ? "error" : message;
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

    public void collect(HassClient.Snapshot snap) {
        synchronized (bufferLock) {
            buffer.add(snap);
            if (buffer.size() > MAX_BUFFER) buffer.remove(0);
        }
    }

    public void flush(HassClient.FlushCallback callback) {
        if (!endpoint.enabled) {
            result(callback, false, "endpoint disabled");
            return;
        }
        long now = System.currentTimeMillis();
        if (!HaWorkerPolicy.shouldAttempt(lastAttemptMs, currentBackoffMs, now)) {
            result(callback, false, "backed off");
            return;
        }
        lastAttemptMs = now;
        if (!flushInProgress.compareAndSet(false, true)) {
            result(callback, false, "flush in progress");
            return;
        }
        if (!isNetworkAvailable(ctx)) {
            flushInProgress.set(false);
            result(callback, false, "no network");
            return;
        }

        List<HassClient.Snapshot> queueBatch;
        try {
            queueBatch = SnapshotQueue.dequeueChunk(ctx, endpointId,
                    SnapshotQueue.DEQUEUE_CHUNK_SIZE);
        } catch (Exception e) {
            flushInProgress.set(false);
            LogBuffer.e(tag(), "dequeue failed: " + e.getMessage());
            result(callback, false, e.getMessage());
            return;
        }
        int room = HaWorkerPolicy.batchRoom(SnapshotQueue.DEQUEUE_CHUNK_SIZE, queueBatch.size());
        List<HassClient.Snapshot> bufferBatch;
        synchronized (bufferLock) {
            bufferBatch = new ArrayList<>(buffer);
            buffer.clear();
        }
        List<HassClient.Snapshot> batch = new ArrayList<>(queueBatch);
        if (room > 0 && !bufferBatch.isEmpty()) {
            int take = Math.min(room, bufferBatch.size());
            batch.addAll(bufferBatch.subList(0, take));
            if (take < bufferBatch.size()) {
                synchronized (bufferLock) {
                    buffer.addAll(0, bufferBatch.subList(take, bufferBatch.size()));
                }
            }
        }
        if (batch.isEmpty()) {
            flushInProgress.set(false);
            result(callback, true, "nothing to send");
            return;
        }
        Collections.sort(batch, new Comparator<HassClient.Snapshot>() {
            @Override
            public int compare(HassClient.Snapshot a, HassClient.Snapshot b) {
                return Long.compare(a.timestamp, b.timestamp);
            }
        });

        queryIntegrationInfoOnce();
        sendBatch(batch, (success, deadLettered, msg) -> {
            flushInProgress.set(false);
            if (success) {
                currentBackoffMs = MIN_BACKOFF_MS;
                deleteSentQueue(batch);
                int bytes = batch.size() * 200;
                for (HassClient.Snapshot snap : batch) bytes += snap.signalJson.length();
                SendHistory.record(ctx, bytes, batch.size());
            } else if (deadLettered) {
                // The server explicitly refused these snapshots (out-of-order).
                // Move them out of the retry queue; retrying the same timestamps
                // would be an endless loop. Keep them for inspection.
                currentBackoffMs = MIN_BACKOFF_MS;
                deleteSentQueue(batch);
                forceFullNext = true;
            } else {
                currentBackoffMs = HaWorkerPolicy.nextBackoff(currentBackoffMs);
                forceFullNext = true;
                List<HassClient.Snapshot> toPersist = new ArrayList<>();
                for (HassClient.Snapshot snap : batch) {
                    if (snap.queueId < 0) toPersist.add(snap);
                }
                if (!toPersist.isEmpty() && AppConfig.isQueueEnabled(ctx)) {
                    SnapshotQueue.evictByAge(ctx, endpointId, AppConfig.getQueueMaxDays(ctx));
                    SnapshotQueue.evictBySize(ctx, endpointId, queueMaxMb);
                    SnapshotQueue.enqueueAll(ctx, endpointId, toPersist, queueMaxMb);
                }
            }
            result(callback, success, msg);
        });
    }

    private void deleteSentQueue(List<HassClient.Snapshot> batch) {
        long maxQueuedId = -1;
        for (HassClient.Snapshot snap : batch) {
            if (snap.queueId > maxQueuedId) maxQueuedId = snap.queueId;
        }
        if (maxQueuedId >= 0) {
            SnapshotQueue.deleteUpTo(ctx, endpointId, maxQueuedId);
        }
    }

    /** Force the next snapshot to carry the full signal map (HA restart/heartbeat). */
    public void requestFullSync() {
        forceFullNext = true;
    }

    public void requestFlushNow(HassClient.FlushCallback callback) {
        currentBackoffMs = MIN_BACKOFF_MS;
        lastAttemptMs = 0L;
        flush(callback);
    }

    public void onNetworkAvailable() {
        forceFullNext = true;
        currentBackoffMs = MIN_BACKOFF_MS;
        lastAttemptMs = 0L;
        flush(null);
    }

    public int getBufferSize() {
        synchronized (bufferLock) {
            return buffer.size();
        }
    }

    public int getQueueCount() {
        return SnapshotQueue.getCount(ctx, endpointId);
    }

    public int getDeadLetterCount() {
        return SnapshotQueue.getDeadLetterCount(ctx, endpointId);
    }

    private void queryIntegrationInfoOnce() {
        if (!integrationInfoQueried.compareAndSet(false, true)) return;
        String token = new SecureStorage(ctx).getEndpointToken(endpointId);
        integrationInfo = IntegrationInfo.query(ctx, endpoint.haAddress().baseUrl(), token);
        if (integrationInfo != null) {
            LogBuffer.i(tag(), "integration " + integrationInfo.integrationVersion
                    + " api=" + integrationInfo.apiVersion
                    + " loaded=" + integrationInfo.loaded);
        }
    }

    private void sendBatch(List<HassClient.Snapshot> batch, BatchCallback callback) {
        String token = new SecureStorage(ctx).getEndpointToken(endpointId);
        if (endpoint.carName.isEmpty() || token.isEmpty()) {
            noteFailure("config incomplete");
            SendHistory.recordAttempt(ctx, "config incomplete");
            callback.onResult(false, false, "config incomplete");
            return;
        }
        HaAddress address = endpoint.haAddress();
        if (!address.https && !NetSafety.isPrivateHost(address.host)) {
            noteFailure("insecure_http_blocked");
            SendHistory.recordAttempt(ctx, "insecure_http_blocked");
            LogBuffer.w(tag(), "Refusing cleartext HTTP to public host '" + address.host + "'");
            callback.onResult(false, false, "insecure_http_blocked");
            return;
        }
        SEND_EXECUTOR.submit(() -> {
            HttpURLConnection conn = null;
            noteAttempt();
            try {
                JSONObject payload = new JSONObject();
                payload.put("car_name", endpoint.carName);
                payload.put("vvn", CANDataReader.sVin);
                payload.put("firmware", CANDataReader.sFirmware);
                payload.put("app_version", AppInfo.getVersionString(ctx));
                String devJson = PhoneSensors.deviceBlockJson(
                        AppConfig.isPhoneChannelEnabled(ctx) ? "phone" : "car",
                        com.car2hass.vehicle.DeviceAnon.fromContext(ctx),
                        AppConfig.getPhoneName(ctx));
                if (devJson != null) payload.put("dev", new JSONObject(devJson));

                Map<String, String> nextLastSent = lastSent;
                boolean forceFull = forceFullNext;
                long nextLastFullSendMs = lastFullSendMs;
                boolean supportsDelta = integrationInfo != null
                        && integrationInfo.apiVersion >= IntegrationInfo.DELTA_API_VERSION;
                JSONArray batchJson = new JSONArray();
                for (HassClient.Snapshot snap : batch) {
                    boolean full = forceFull || !supportsDelta
                            || (System.currentTimeMillis() - lastFullSendMs) > FULL_RESYNC_INTERVAL_MS;
                    DeltaSnapshotBuilder.Result res = buildDelta(snap, nextLastSent, full);
                    nextLastSent = res.lastSent;
                    if (!res.delta) {
                        nextLastFullSendMs = System.currentTimeMillis();
                        forceFull = false;
                    }
                    HaPayloadJson.addSnapshot(batchJson, snap, res);
                }
                payload.put("batch", batchJson);
                long maxTs = 0;
                for (HassClient.Snapshot snap : batch) {
                    if (snap.timestamp > maxTs) maxTs = snap.timestamp;
                }
                payload.put("ts", maxTs);

                URL url = new URL(address.url("/api/cartelemetry"));
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes("UTF-8"));
                }
                int code = conn.getResponseCode();
                String body = readResponseBody(conn);
                HaResponse response = HaResponse.parse(code, body);
                boolean success = response.ok();
                boolean deadLettered = response.dropped;
                if (success) {
                    lastSent = nextLastSent;
                    lastFullSendMs = nextLastFullSendMs;
                    forceFullNext = false;
                    noteSuccess();
                } else if (deadLettered) {
                    noteFailure("dropped: " + response.reason);
                    for (HassClient.Snapshot snap : batch) {
                        SnapshotQueue.deadLetter(ctx, endpointId, snap, response.reason);
                    }
                } else {
                    noteFailure(response.reason.isEmpty()
                            ? "HTTP " + code : "HTTP " + code + ": " + response.reason);
                }
                LogBuffer.i(tag(), "Flush " + batch.size() + " snapshots -> HTTP " + code
                        + " accepted=" + (success ? "yes" : "no")
                        + (deadLettered ? " dropped=" + response.reason : "")
                        + (success ? " signals=" + response.signals : "")
                        + (body.isEmpty() ? "" : " body=" + ProbeUploader.truncate(body))
                        + " (backoff=" + currentBackoffMs + "ms)");
                SendHistory.recordAttempt(ctx, success ? "HTTP " + code
                        : deadLettered ? "dropped:" + response.reason
                        : "HTTP " + code + " " + response.reason);
                callback.onResult(success, deadLettered,
                        success ? "HTTP " + code : (deadLettered ? "dropped:" + response.reason
                                : "HTTP " + code + " " + response.reason));
            } catch (Exception e) {
                noteFailure(e.getMessage());
                LogBuffer.e(tag(), "Send error: " + e.getMessage()
                        + " (backoff=" + currentBackoffMs + "ms)");
                SendHistory.recordAttempt(ctx, e.getMessage());
                callback.onResult(false, false, e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static String readResponseBody(HttpURLConnection conn) {
        if (conn == null) return "";
        try {
            InputStream is = conn.getErrorStream();
            if (is == null) is = conn.getInputStream();
            if (is == null) return "";
            byte[] b = new byte[4096];
            int n = is.read(b);
            return n > 0 ? new String(b, 0, n, "UTF-8") : "";
        } catch (Exception e) {
            return "";
        }
    }

    private DeltaSnapshotBuilder.Result buildDelta(HassClient.Snapshot snap,
                                                   Map<String, String> previous,
                                                   boolean forceFull) throws Exception {
        JSONObject fullObj = new JSONObject(snap.signalJson);
        Map<String, String> full = new LinkedHashMap<>();
        Map<String, String> keyChannel = new HashMap<>();
        java.util.Iterator<String> it = fullObj.keys();
        com.car2hass.vehicle.ValueStore vs = com.car2hass.vehicle.ValueStore.main();
        while (it.hasNext()) {
            String k = it.next();
            full.put(k, String.valueOf(fullObj.opt(k)));
            if (vs != null) {
                String channel = vs.sourceOf(k);
                if (channel != null) keyChannel.put(k, channel);
            }
        }
        return DeltaSnapshotBuilder.build(full, previous, keyChannel, forceFull);
    }

    private String tag() {
        return "HA[" + endpoint.name + "]";
    }

    private void result(HassClient.FlushCallback callback, boolean success, String message) {
        if (callback != null) callback.onResult(success, message);
    }

    private static boolean isNetworkAvailable(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network nw = cm.getActiveNetwork();
            if (nw == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(nw);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return false;
        }
    }
}
