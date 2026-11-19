package com.car2hass.vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decides which channel workers may run. Availability from the last probe is
 * advisory only: a transient failed probe used to freeze a user-enabled channel
 * until a manual research run, which stopped telemetry updates. Workers have
 * their own retry/backoff, so every enabled channel is kept here.
 */
public final class ChannelGate {
    private ChannelGate() {}

    public static List<String> resolve(List<String> activeChannels,
                                       Map<String, Boolean> availability,
                                       boolean obdEnabled) {
        List<String> out = new ArrayList<>();
        out.add("system");
        if (activeChannels == null) return out;
        for (String id : activeChannels) {
            if (id == null || id.isEmpty() || "system".equals(id)) continue;
            if ("obd".equals(id) && !obdEnabled) continue;
            if (!out.contains(id)) out.add(id);
        }
        return out;
    }
}
