package com.diplustohass.vehicle;

import android.content.Context;
import com.diplustohass.CANDataItem;
import com.diplustohass.CANDataReader;

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
        if (!CANDataReader.isDiplusAlive()) {
            return ChannelResult.dead("DiPlus не отвечает (нет приложения на 127.0.0.1:8988)");
        }
        List<CANDataItem> items = CANDataReader.readHttpSnapshot(ctx);
        if (items == null || items.isEmpty()) {
            return ChannelResult.rawData("DiPlus жив, но сенсоры не распознаны");
        }
        int real = 0;
        for (CANDataItem it : items) {
            if (it != null && it.value != null && !"---".equals(it.value) && !it.value.isEmpty()) real++;
        }
        return ChannelResult.ok(real);
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        return CANDataReader.readHttpSnapshot(ctx);
    }
}