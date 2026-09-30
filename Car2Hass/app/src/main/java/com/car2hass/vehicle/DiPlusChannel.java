package com.car2hass.vehicle;

import android.content.Context;
import com.car2hass.CANDataItem;
import com.car2hass.CANDataReader;

import java.util.List;

/** Канал данных DiPlus (HTTP на 127.0.0.1:8988). */
public class DiPlusChannel implements DataChannel {

    @Override
    public String id() { return "diplus"; }

    @Override
    public String displayName() { return "DiPlus (приложение авто)"; }

    @Override
    public boolean supportsCommands() { return true; }

    @Override
    public ChannelResult probe(Context ctx) {
        // Attempt a real read rather than gating on the ping: the ping can fail
        // while the DiPlus API works.
        List<CANDataItem> items = CANDataReader.readHttpSnapshot(ctx);
        if (items == null || items.isEmpty()) {
            return ChannelResult.dead("DiPlus не отвечает (нет данных с 127.0.0.1:8988)");
        }
        int real = 0;
        for (CANDataItem it : items) {
            if (it != null && it.value != null && !"---".equals(it.value) && !it.value.isEmpty()) real++;
        }
        return ChannelResult.ok(real);
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        return CANDataReader.readHttpSnapshot(ctx, knownItems);
    }
}