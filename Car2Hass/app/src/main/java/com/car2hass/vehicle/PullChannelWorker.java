package com.car2hass.vehicle;

import android.content.Context;

import com.car2hass.CANDataItem;
import com.car2hass.LogBuffer;
import com.car2hass.SignalTranslator;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic pull-channel worker: owns one {@link DataChannel}, reads it on its
 * own thread and cadence, and writes every produced value into the shared
 * {@link ValueStore} tagged with the channel id. Raw values are also kept in
 * the store's raw map so the snapshot assembler can translate/forward them
 * without re-reading the channel.
 */
public final class PullChannelWorker implements ChannelWorker {

    private static final long MAX_BACKOFF_MS = 30_000;

    private final Context ctx;
    private final ValueStore store;
    private final DataChannel channel;
    private final List<CANDataItem> itemsTemplate;
    private final WorkerRetryPolicy retry;
    private volatile boolean running;
    private Thread thread;

    public PullChannelWorker(Context ctx, ValueStore store, DataChannel channel,
                             List<CANDataItem> items, long cadenceMs) {
        this.ctx = ctx;
        this.store = store;
        this.channel = channel;
        this.itemsTemplate = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.retry = new WorkerRetryPolicy(cadenceMs, MAX_BACKOFF_MS);
    }

    @Override
    public String channelId() { return channel.id(); }

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "worker-" + channel.id());
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public synchronized void stop() {
        running = false;
        Thread t = thread;
        thread = null;
        if (t != null) t.interrupt();
    }

    @Override
    public boolean isRunning() { return running; }

    private void loop() {
        while (running) {
            long delay;
            try {
                delay = cycle();
            } catch (Throwable t) {
                LogBuffer.d("ChannelWorker", channel.id() + " cycle error: " + t.getMessage());
                delay = retry.onFailure();
            }
            if (!running) break;
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private long cycle() {
        List<CANDataItem> known = new ArrayList<>(itemsTemplate.size());
        for (CANDataItem item : itemsTemplate) {
            if (item == null || item.key == null) continue;
            known.add(copy(item));
        }
        List<CANDataItem> result = channel.read(ctx, known);
        int written = 0;
        if (result != null) {
            for (CANDataItem item : result) {
                if (item == null || item.key == null) continue;
                if (item.value == null || item.value.isEmpty() || "---".equals(item.value)) continue;
                String raw = item.value.replace(',', '.');
                ValueStore.putRaw(item.key, raw);
                String translated = SignalTranslator.translateEnumValue(item.key, raw);
                store.put(item.key, translated, channel.id());
                written++;
            }
        }
        return written > 0 ? retry.onSuccess() : retry.onFailure();
    }

    private static CANDataItem copy(CANDataItem src) {
        CANDataItem c = new CANDataItem(src.canId, src.name, src.unit, src.route, src.diplusName);
        c.key = src.key;
        c.rawData = src.rawData;
        return c;
    }
}
