package com.car2hass.vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decides which channel workers may run, given the last probe's per-channel
 * availability. A channel with no entry (never probed) is kept (fail-open);
 * only a channel marked dead is dropped. "system" is always present.
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
            Boolean avail = availability == null ? null : availability.get(id);
            if (avail != null && !avail) continue;
            if (!out.contains(id)) out.add(id);
        }
        return out;
    }
}
