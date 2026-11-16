package com.car2hass;

import android.content.Context;

import org.json.JSONArray;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EndpointManager {
    private final Context ctx;
    private final Map<String, HaTelemetryWorker> workers = new LinkedHashMap<>();
    private final Map<String, HaCommandPoller> pollers = new LinkedHashMap<>();

    public EndpointManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public synchronized void reconcile() {
        List<HaEndpointConfig> endpoints = EndpointStore.list(ctx);
        int enabledCount = Math.max(1, EndpointRouter.enabledIds(endpoints).size());
        int quota = Math.max(5, AppConfig.getQueueMaxMb(ctx) / enabledCount);
        Set<String> wanted = new HashSet<>();
        for (HaEndpointConfig endpoint : endpoints) {
            if (!endpoint.enabled) continue;
            wanted.add(endpoint.id);
            HaTelemetryWorker existing = workers.get(endpoint.id);
            if (existing == null || !existing.matches(endpoint)
                    || existing.queueMaxMb() != quota) {
                workers.put(endpoint.id, new HaTelemetryWorker(ctx, endpoint, quota));
            }
        }
        workers.keySet().removeIf(id -> !wanted.contains(id));
        reconcilePollers(endpoints);
        LogBuffer.i("EndpointManager", "endpoints active=" + workers.size() + " quota=" + quota + "MB");
    }

    private void reconcilePollers(List<HaEndpointConfig> endpoints) {
        for (HaEndpointConfig endpoint : endpoints) {
            boolean wanted = endpoint.enabled && endpoint.commandsEnabled
                    && AppConfig.isCarControlEnabled(ctx);
            if (!wanted) continue;
            HaCommandPoller existing = pollers.get(endpoint.id);
            if (existing != null && !existing.matches(endpoint)) {
                existing.stop();
                pollers.remove(endpoint.id);
                existing = null;
            }
            if (existing == null) {
                HaCommandPoller poller = new HaCommandPoller(ctx, endpoint);
                pollers.put(endpoint.id, poller);
                poller.start();
            }
        }
        pollers.entrySet().removeIf(entry -> {
            HaEndpointConfig endpoint = EndpointStore.get(ctx, entry.getKey());
            if (endpoint == null || !endpoint.enabled || !endpoint.commandsEnabled
                    || !AppConfig.isCarControlEnabled(ctx)) {
                entry.getValue().stop();
                return true;
            }
            return false;
        });
    }

    public synchronized void stop() {
        for (HaCommandPoller poller : pollers.values()) poller.stop();
        pollers.clear();
        workers.clear();
    }

    public void collectSnapshot(double lat, double lon, float accuracy,
                                long fixTimeSec, String signalJson) {
        String disabledJson = "";
        Set<String> disabled = AppConfig.getDisabledSignals(ctx);
        if (disabled != null && !disabled.isEmpty()) {
            JSONArray arr = new JSONArray();
            for (String key : disabled) arr.put(key);
            disabledJson = arr.toString();
        }
        long tsSec = System.currentTimeMillis() / 1000;
        if (SnapshotDedup.isDuplicate(SnapshotDedup.Channel.HA, lat, lon, signalJson)) {
            LogBuffer.d("EndpointManager", "snapshot skipped (stationary, unchanged)");
            return;
        }
        SnapshotDedup.keep(SnapshotDedup.Channel.HA, lat, lon, tsSec, signalJson);
        HassClient.Snapshot snap = new HassClient.Snapshot(
                tsSec, lat, lon, accuracy, fixTimeSec, signalJson, disabledJson);
        for (HaTelemetryWorker worker : workers.values()) {
            worker.collect(snap);
        }
    }

    public void flushAll() {
        for (HaTelemetryWorker worker : workers.values()) worker.flush(null);
    }

    public void onNetworkAvailable() {
        for (HaTelemetryWorker worker : workers.values()) worker.onNetworkAvailable();
    }

    public void requestFlushNow(HassClient.FlushCallback callback) {
        for (HaTelemetryWorker worker : workers.values()) worker.requestFlushNow(callback);
    }

    public int queueCount() {
        int n = 0;
        for (HaTelemetryWorker worker : workers.values()) n += worker.getQueueCount();
        return n;
    }

    public int bufferCount() {
        int n = 0;
        for (HaTelemetryWorker worker : workers.values()) n += worker.getBufferSize();
        return n;
    }

    public synchronized List<HaTelemetryWorker> workers() {
        return new java.util.ArrayList<>(workers.values());
    }
}
