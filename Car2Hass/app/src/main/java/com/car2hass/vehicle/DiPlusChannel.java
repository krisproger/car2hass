package com.car2hass.vehicle;

import android.content.Context;
import com.car2hass.CANDataItem;
import com.car2hass.CANDataReader;
import com.car2hass.LogBuffer;

import java.util.List;

/** Канал данных DiPlus (HTTP на 127.0.0.1:8988). */
public class DiPlusChannel implements DataChannel {

    @Override
    public String id() { return "diplus"; }

    @Override
    public String displayName() { return "DiPlus (приложение авто)"; }

    @Override
    public boolean supportsCommands() { return true; }

    private static volatile long lastOkMs = 0;

    @Override
    public ChannelResult probe(Context ctx) {
        // Attempt a real read rather than gating on the ping: the ping can fail
        // while the DiPlus API works. Retry once, and accept a very recent
        // successful read as "alive" so a transient probe timeout does not
        // overwrite a working channel with available=false.
        List<CANDataItem> items = CANDataReader.readHttpSnapshot(ctx);
        if (items == null || items.isEmpty()) {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            items = CANDataReader.readHttpSnapshot(ctx);
        }
        if (com.car2hass.AppConfig.isProbeRawEnabled(ctx)) {
            LogBuffer.i("DiPlusRaw", "probe items=" + (items == null ? -1 : items.size())
                    + " lastOkAgeMs=" + (lastOkMs <= 0 ? -1 : System.currentTimeMillis() - lastOkMs));
        }
        if (items == null || items.isEmpty()) {
            if (lastOkMs > 0 && System.currentTimeMillis() - lastOkMs < 90_000L) {
                return ChannelResult.ok(1);
            }
            return ChannelResult.dead("DiPlus не отвечает (нет данных с 127.0.0.1:8988)");
        }
        int real = 0;
        for (CANDataItem it : items) {
            if (it != null && it.value != null && !"---".equals(it.value) && !it.value.isEmpty()) real++;
        }
        if (real > 0) lastOkMs = System.currentTimeMillis();
        return ChannelResult.ok(real);
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        List<CANDataItem> items = CANDataReader.readHttpSnapshot(ctx, knownItems);
        if (items != null && !items.isEmpty()) lastOkMs = System.currentTimeMillis();
        return items;
    }
}