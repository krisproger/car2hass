package com.car2hass.vehicle;

import android.content.Context;

import com.car2hass.CANDataReader;
import com.car2hass.NativeSignalMap;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Probes a single sensor on a single channel using the Phase-1 registry
 * descriptors and the existing DiPlus / native readers. Classification is
 * delegated to {@link ProbeResult}.
 */
public final class SignalProber implements VehicleResearch.SensorProbe {

    public SignalProber() {}

    @Override
    public ProbeResult probe(Context ctx, RegistryStore reg, String sensorKey, String channel)
            throws org.json.JSONException {
        JSONObject ch = reg.sensorChannel(sensorKey, channel);
        if (ch == null || ch.length() == 0) {
            return ProbeResult.unsupported();
        }
        try {
            if ("diplus".equals(channel)) {
                String name = ch.optString("name");
                return CANDataReader.getSingleDiplusValue(ctx, name);
            } else if ("adb".equals(channel)) {
                NativeSignalMap.FidEntry fe = toFidEntry(sensorKey, ch);
                return CANDataReader.getSingleNativeValue(ctx, fe);
            } else if ("system".equals(channel)) {
                return SystemChannel.probeField(ctx, ch.optString("field"));
            } else if ("obd".equals(channel)) {
                String pid = ch.optString("pid");
                if (pid.isEmpty()) return ProbeResult.unsupported();
                return ObdChannel.readSinglePid(ctx, pid);
            } else if ("voyah".equals(channel)) {
                String vs = ch.optString("vs");
                if (vs.isEmpty()) return ProbeResult.unsupported();
                return VoyahChannel.readSingleParam(vs);
            }
            // dumpsys / diplus_push / byd_cloud have no per-sensor descriptors
            // yet; channel-level probe covers their availability.
            return ProbeResult.unsupported();
        } catch (Exception e) {
            return ProbeResult.error(e.getMessage());
        }
    }

    @Override
    public Map<String, ProbeResult> obdBatch(Context ctx, RegistryStore reg, List<String> keys) {
        Map<String, String> pidOf = new LinkedHashMap<>();
        for (String key : keys) {
            try {
                JSONObject ch = reg.sensorChannel(key, "obd");
                String pid = ch != null ? ch.optString("pid") : "";
                if (!pid.isEmpty()) pidOf.put(key, pid);
            } catch (org.json.JSONException ignored) {}
        }
        if (pidOf.isEmpty()) return null;
        List<String> pids = new ArrayList<>(pidOf.values());
        Map<String, ProbeResult> byPid = ObdChannel.readPids(ctx, pids);
        Map<String, ProbeResult> byKey = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : pidOf.entrySet()) {
            byKey.put(e.getKey(), byPid.get(e.getValue()));
        }
        return byKey;
    }

    private static NativeSignalMap.FidEntry toFidEntry(String key, JSONObject ch) {
        return new NativeSignalMap.FidEntry(key,
                ch.optInt("device"), ch.optInt("fid"),
                ch.optInt("transact"), ch.optInt("decoder"), ch.optDouble("scale", 1.0));
    }
}
