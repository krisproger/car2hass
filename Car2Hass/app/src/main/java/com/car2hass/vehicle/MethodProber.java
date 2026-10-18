package com.car2hass.vehicle;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

/** Wires MethodProbeCore to the Voyah channel primitives and stores the report. */
public final class MethodProber {
    private MethodProber() {}

    public static JSONObject run(RegistryStore reg, List<String> availableChannels)
            throws org.json.JSONException {
        List<MethodRegistry.ChannelMethod> all = MethodRegistry.from(reg);
        List<MethodRegistry.ChannelMethod> keep = new java.util.ArrayList<>();
        for (MethodRegistry.ChannelMethod m : all) {
            if (availableChannels == null || availableChannels.contains(m.channel)) keep.add(m);
        }
        return MethodProbeCore.build(keep, MethodProber::invoke);
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

    public static String writeFile(android.content.Context ctx, JSONObject report) {
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) dir = ctx.getFilesDir();
            dir.mkdirs();
            File f = new File(dir, "method_probe_report.json");
            try (FileWriter w = new FileWriter(f)) {
                w.write(ReportPrivacyFilter.sanitize(report).toString(2));
            }
            return f.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }
}
