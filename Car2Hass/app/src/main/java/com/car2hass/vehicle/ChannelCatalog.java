package com.car2hass.vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * Single place that maps registry channel ids to DataChannel instances.
 * Replaces the duplicated manual instantiation in MainActivity and
 * TelemetryService. The experimental channel is not part of the registry,
 * so it is always appended last.
 */
public final class ChannelCatalog {

    private ChannelCatalog() {}

    /** Creates the channel instance for a registry id, or null if unknown. */
    public static DataChannel create(String id) {
        if ("diplus".equals(id)) return new DiPlusChannel();
        if ("adb".equals(id) || "native".equals(id)) return new NativeChannel(); // "native": legacy id
        if ("dumpsys".equals(id)) return new SysPropsChannel();
        if ("system".equals(id)) return new SystemChannel();
        if ("obd".equals(id)) return new ObdChannel();
        if ("diplus_push".equals(id)) return new DiPlusPushChannel();
        if ("byd_cloud".equals(id)) return new BydCloudChannel();
        if ("voyah".equals(id)) return new VoyahChannel();
        return null;
    }

    /** All probeable channels in registry priority order + experimental last. */
    public static List<DataChannel> createAll(RegistryStore reg) {
        List<DataChannel> out = new ArrayList<>();
        try {
            for (String id : reg.channelIds()) {
                DataChannel ch = create(id);
                if (ch != null) out.add(ch);
            }
        } catch (org.json.JSONException e) {
            // Registry broken — fall back to the core channels.
            out.add(new DiPlusChannel());
            out.add(new NativeChannel());
        }
        out.add(new ExperimentalChannel());
        return out;
    }
}
