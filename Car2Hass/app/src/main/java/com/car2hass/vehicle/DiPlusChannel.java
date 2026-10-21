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
        // DiPlus batch reads need the registry items (diplus names); an empty list
        // makes diplusGetDiPars return null, so the channel would look dead even
        // when the API works. Build the same item list the worker path uses.
        List<CANDataItem> known = new java.util.ArrayList<>();
        try {
            RegistryStore reg = RegistryStore.load(ctx);
            SourceManager sm = new SourceManager(reg,
                    java.util.Collections.singletonList("diplus"),
                    com.car2hass.AppConfig.getSelectedProfile(ctx), null);
            known = sm.buildSignalItems();
        } catch (Exception e) {
            LogBuffer.w("DiPlusChannel", "probe items: " + e.getMessage());
        }
        List<CANDataItem> items = CANDataReader.readHttpSnapshot(ctx, known);
        if (items == null || items.isEmpty()) {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            items = CANDataReader.readHttpSnapshot(ctx, known);
        }
        if (com.car2hass.AppConfig.isProbeRawEnabled(ctx)) {
            LogBuffer.i("DiPlusRaw", "probe known=" + known.size()
                    + " items=" + (items == null ? -1 : items.size())
                    + " lastOkAgeMs=" + (lastOkMs <= 0 ? -1 : System.currentTimeMillis() - lastOkMs));
        }
        if (items == null || items.isEmpty()) {
            if (lastOkMs > 0 && System.currentTimeMillis() - lastOkMs < 90_000L) {
                return ChannelResult.ok(1);
            }
            String detail = com.car2hass.CANDataReader.lastHttpError();
            return ChannelResult.dead("DiPlus: " + (detail == null || detail.isEmpty()
                    ? "нет данных с 127.0.0.1:8988" : detail));
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