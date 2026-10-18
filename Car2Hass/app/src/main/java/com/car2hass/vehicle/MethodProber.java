package com.car2hass.vehicle;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Runs declared channel-method canaries against the Voyah primitives. */
public final class MethodProber {
    private MethodProber() {}

    /**
     * Canary results for the given probe availability. Fail-open: a channel with
     * no availability entry (or true) keeps its methods; only an explicit false
     * drops them.
     */
    public static JSONArray methodsFor(RegistryStore reg, Map<String, Boolean> availability)
            throws org.json.JSONException {
        List<MethodRegistry.ChannelMethod> keep = new ArrayList<>();
        for (MethodRegistry.ChannelMethod m : MethodRegistry.from(reg)) {
            Boolean avail = availability == null ? null : availability.get(m.channel);
            if (avail != null && !avail) continue;
            keep.add(m);
        }
        return MethodProbeCore.methods(keep, MethodProber::invoke);
    }

    private static ProbeResult invoke(String kind, String param) {
        switch (kind) {
            case "voyah_state": return VoyahChannel.readSingleParam(param);
            case "icanbus_getter": return VoyahChannel.readMethodParam(param);
            case "aircondition_getter": return VoyahChannel.readAirConditionParam(param);
            case "icarsignal_getter": return VoyahChannel.readSignalParam(param);
            case "raw_tx": return VoyahChannel.readRawTxParam(param);
            default: return ProbeResult.unsupported();
        }
    }
}
