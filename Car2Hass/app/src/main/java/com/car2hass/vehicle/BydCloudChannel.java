package com.car2hass.vehicle;

import android.content.Context;

import com.car2hass.CANDataItem;

import java.util.ArrayList;
import java.util.List;

/**
 * BYD cloud API channel. Requires vehicle cloud credentials which the app
 * does not have yet; reports honestly as unavailable (framework placeholder).
 */
public class BydCloudChannel implements DataChannel {

    @Override
    public String id() { return "byd_cloud"; }

    @Override
    public String displayName() { return "BYD Cloud API"; }

    @Override
    public boolean supportsCommands() { return false; }

    @Override
    public ChannelResult probe(Context ctx) {
        return ChannelResult.dead("требуются учётные данные BYD Cloud");
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        return new ArrayList<>();
    }
}
