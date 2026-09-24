package com.car2hass.vehicle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns one worker instance per enabled channel. {@link #sync(List)} reconciles
 * the running set with the user's enabled channels: newly enabled channels get
 * a worker started, disabled channels get theirs stopped and removed. The
 * registry itself is Android-free so the enable/disable contract is testable.
 */
public final class ChannelWorkerRegistry {

    private final WorkerFactory factory;
    private final Map<String, ChannelWorker> workers = new LinkedHashMap<>();

    public ChannelWorkerRegistry(WorkerFactory factory) {
        this.factory = factory;
    }

    /** Starts workers for enabled ids, stops workers for ids that dropped out. */
    public synchronized void sync(List<String> enabledChannelIds) {
        Set<String> desired = new LinkedHashSet<>();
        if (enabledChannelIds != null) {
            for (String id : enabledChannelIds) {
                if (id != null && !id.isEmpty()) desired.add(id);
            }
        }

        Iterator<Map.Entry<String, ChannelWorker>> it = workers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ChannelWorker> e = it.next();
            if (!desired.contains(e.getKey())) {
                safeStop(e.getValue());
                it.remove();
            }
        }

        for (String id : desired) {
            if (workers.containsKey(id)) continue;
            ChannelWorker worker;
            try {
                worker = factory.create(id);
            } catch (Exception e) {
                continue;
            }
            if (worker == null) continue;
            workers.put(id, worker);
            try {
                worker.start();
            } catch (Exception ignored) {
            }
        }
    }

    public synchronized void stopAll() {
        for (ChannelWorker worker : workers.values()) safeStop(worker);
        workers.clear();
    }

    public synchronized boolean isRunning(String channelId) {
        return workers.containsKey(channelId);
    }

    public synchronized Set<String> runningChannels() {
        return new LinkedHashSet<>(workers.keySet());
    }

    /** Status snapshot of a single running worker, or null when the channel has none. */
    public synchronized ChannelWorkerStatus status(String channelId) {
        ChannelWorker worker = workers.get(channelId);
        return worker == null ? null : safeStatus(worker);
    }

    /** Status snapshots of every running worker, in insertion order. */
    public synchronized List<ChannelWorkerStatus> statuses() {
        List<ChannelWorkerStatus> out = new ArrayList<>(workers.size());
        for (ChannelWorker worker : workers.values()) {
            ChannelWorkerStatus s = safeStatus(worker);
            if (s != null) out.add(s);
        }
        return out;
    }

    private static ChannelWorkerStatus safeStatus(ChannelWorker worker) {
        try {
            return worker.status();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void safeStop(ChannelWorker worker) {
        try {
            worker.stop();
        } catch (Exception ignored) {
        }
    }
}
