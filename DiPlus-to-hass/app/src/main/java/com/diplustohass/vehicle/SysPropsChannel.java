package com.diplustohass.vehicle;

import android.content.Context;
import com.diplustohass.CANDataItem;
import com.diplustohass.CANDataReader;

import java.util.List;

/** Канал чтения системных свойств (getprop): VVIN, FW, тип авто и др. */
public class SysPropsChannel implements DataChannel {

    @Override
    public String id() { return "sysprops"; }

    @Override
    public String displayName() { return "Системные свойства"; }

    @Override
    public boolean supportsCommands() { return false; }

    @Override
    public ChannelResult probe(Context ctx) {
        List<CANDataItem> items = CANDataReader.readSysProps(ctx, null);
        if (items == null || items.isEmpty()) {
            return ChannelResult.dead("getprop вернул пустой список");
        }
        int real = 0;
        for (CANDataItem it : items) {
            if (it != null && it.value != null && !"---".equals(it.value) && !it.value.isEmpty()) real++;
        }
        if (real == 0) return ChannelResult.rawData("getprop отдал только пустые значения");
        return ChannelResult.ok(real);
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        return CANDataReader.readSysProps(ctx, knownItems);
    }
}