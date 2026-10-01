package com.car2hass.vehicle;

import android.content.Context;

import com.car2hass.AppConfig;
import com.car2hass.CANDataItem;
import com.car2hass.PhoneSensors;

import java.util.ArrayList;
import java.util.List;

/**
 * Availability marker for the phone sensor channel. Collection lives in
 * {@link PhoneWorker} (event listeners + 60 s periodic), never in read cycles,
 * so this channel only reports how many phone keys are currently enabled.
 */
public final class PhoneChannel implements DataChannel {

    @Override
    public String id() { return "phone"; }

    @Override
    public String displayName() { return "Phone (device sensors)"; }

    @Override
    public boolean supportsCommands() { return false; }

    @Override
    public ChannelResult probe(Context ctx) {
        int n = PhoneSensors.effectiveKeys(
                AppConfig.isPhoneChannelEnabled(ctx),
                AppConfig.getPhoneEnabledKeys(ctx),
                AppConfig.getDisabledSignals(ctx)).size();
        if (n == 0) return ChannelResult.dead("канал выключен или нет включённых сенсоров");
        return ChannelResult.ok(n);
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        return new ArrayList<>(); // collected by PhoneWorker outside the cycles
    }
}
