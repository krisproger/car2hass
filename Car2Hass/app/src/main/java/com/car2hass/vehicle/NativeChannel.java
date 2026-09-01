package com.car2hass.vehicle;

import android.content.Context;
import com.car2hass.CANDataItem;
import com.car2hass.CANDataReader;

import java.util.List;

/** Канал native-чтения через ADB (autoservice, BYD-специфичные транзакции). */
public class NativeChannel implements DataChannel {

    @Override
    public String id() { return "adb"; }

    @Override
    public String displayName() { return "ADB (service call)"; }

    @Override
    public boolean supportsCommands() { return true; }

    @Override
    public ChannelResult probe(Context ctx) {
        List<CANDataItem> items = CANDataReader.readNativeSnapshot(ctx);
        if (items == null || items.isEmpty()) {
            return ChannelResult.dead("ADB не отвечает (127.0.0.1:5555) или данных нет");
        }
        int real = 0;
        for (CANDataItem it : items) {
            if (it != null && it.value != null && !"---".equals(it.value) && !it.value.isEmpty()) real++;
        }
        if (real == 0) return ChannelResult.rawData("ADB жив, сенсоры не расшифрованы");
        return ChannelResult.ok(real);
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        return CANDataReader.readNativeSnapshot(ctx);
    }
}