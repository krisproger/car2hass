package com.car2hass.vehicle;

import android.content.Context;

import com.car2hass.CANDataItem;
import com.car2hass.CANDataReader;
import com.car2hass.LogBuffer;

import java.util.List;

/**
 * Pull-channel worker: reads active channels (diplus/adb/dumpsys/voyah) and
 * writes every produced value into the shared ValueStore. The snapshot
 * aggregator and the telemetry tab read from the store, so neither depends
 * on which channel produced the value.
 */
public final class ChannelWorker {
    private ChannelWorker() {}

    public static void runOnce(Context ctx, ValueStore store) {
        List<CANDataItem> items = CANDataReader.refreshWithSourceManagerForStore(ctx);
        if (items == null) return;
        for (CANDataItem it : items) {
            if (it == null || it.key == null) continue;
            if (it.value == null || "---".equals(it.value)) continue;
            store.put(it.key, it.value,
                    it.sourceChannel != null ? it.sourceChannel : "channel");
        }
    }
}