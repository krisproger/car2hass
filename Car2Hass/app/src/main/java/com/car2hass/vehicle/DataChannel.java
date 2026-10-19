package com.car2hass.vehicle;

import android.content.Context;
import com.car2hass.CANDataItem;

import java.util.List;

/** Канал получения данных автомобиля. */
public interface DataChannel {
    String id();
    String displayName();
    boolean supportsCommands();
    ChannelResult probe(Context ctx);
    List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems);

    /** Called when the channel is added/enabled. Default no-op. */
    default void start(Context ctx, ValueStore store) {}

    /** Called when the channel is removed/disabled. Default no-op. */
    default void stop() {}
}