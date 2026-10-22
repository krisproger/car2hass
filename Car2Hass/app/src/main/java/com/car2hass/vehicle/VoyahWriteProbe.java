package com.car2hass.vehicle;

import android.content.Context;
import android.content.pm.PackageManager;

import org.json.JSONObject;

/**
 * Non-destructive write-capability probe for Voyah. The write API is protected
 * by a signature permission (WRITE_CANBUS); a sideloaded app cannot hold it.
 * This probe never calls any set* method — it only checks the permission and
 * whether the read service is reachable.
 */
public final class VoyahWriteProbe {
    public static final String WRITE_PERMISSION = "com.qinggan.permission.WRITE_CANBUS";

    private VoyahWriteProbe() {}

    public static JSONObject probe(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            boolean granted = ctx.checkSelfPermission(WRITE_PERMISSION)
                    == PackageManager.PERMISSION_GRANTED;
            ProbeResult r = VoyahChannel.readSingleParam("GW_ESC_VEHSPD");
            boolean reachable = r != null
                    && (r.isOk() || r.status == ProbeResult.Status.SENTINEL);
            o.put("permission", WRITE_PERMISSION);
            o.put("permission_granted", granted);
            o.put("service_reachable", reachable);
            o.put("write_capable", granted);
            o.put("detail", granted ? "granted" : "signature_permission_denied");
        } catch (Throwable e) {
            try {
                o.put("write_capable", false);
                o.put("detail", "probe_error: " + e.getMessage());
            } catch (Exception ignored) {
            }
        }
        return o;
    }
}
