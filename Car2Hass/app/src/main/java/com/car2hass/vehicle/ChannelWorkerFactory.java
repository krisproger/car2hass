package com.car2hass.vehicle;

import android.content.Context;

import com.car2hass.CANDataItem;

import java.util.List;

/**
 * Maps a channel id to its worker type. {@code system} and {@code obd} have
 * dedicated worker types; every other registry channel becomes a generic
 * {@link PullChannelWorker}. The per-channel cadence is chosen so a slow
 * channel never blocks the others (each worker runs on its own thread).
 */
public final class ChannelWorkerFactory implements WorkerFactory {

    private final Context ctx;
    private final ValueStore store;
    private final List<CANDataItem> items;

    public ChannelWorkerFactory(Context ctx, ValueStore store, List<CANDataItem> items) {
        this.ctx = ctx;
        this.store = store;
        this.items = items;
    }

    @Override
    public ChannelWorker create(String channelId) {
        String id = normalize(channelId);
        if ("system".equals(id)) return new SystemWorker(ctx, store);
        if ("obd".equals(id)) return new ObdWorker(ctx, store);
        DataChannel channel = ChannelCatalog.create(id);
        if (channel == null) return null;
        return new PullChannelWorker(ctx, store, channel, items, cadenceFor(id));
    }

    /** Legacy channel ids stored by older builds. */
    public static String normalize(String id) {
        if ("native".equals(id)) return "adb";
        if ("sysprops".equals(id)) return "dumpsys";
        return id;
    }

    static long cadenceFor(String id) {
        if ("system".equals(id)) return 1000;
        if ("dumpsys".equals(id)) return 5000;
        if ("diplus_push".equals(id) || "byd_cloud".equals(id)) return 30_000;
        return 2000;
    }
}
