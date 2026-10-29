package com.car2hass.vehicle;

import android.content.Context;

import org.json.JSONObject;

/** Collects the Voyah fingerprint and caches the confirmed gate for runtime reads. */
public final class VoyahIdentityProbe {
    private static volatile long lastCheckMs;
    private static volatile boolean lastConfirmed;

    private VoyahIdentityProbe() {}

    public static JSONObject probe(Context ctx) {
        boolean service = VoyahChannel.findBinderPublic() != null;
        ProbeResult odo = VoyahChannel.readOdometerParam();
        String canBox = VoyahDirectReader.canBoxVersion();
        String carType = raw(VoyahChannel.readSignalParam("getCarType"));
        Integer vehicleType = parseInt(raw(VoyahChannel.readSignalParam("getVehicleType")));
        boolean user = userConfirmed(ctx);
        VoyahIdentity.State state = VoyahIdentity.evaluate(
                service, odo != null && odo.isOk(), canBox, carType, vehicleType, user);
        JSONObject o = new JSONObject();
        try {
            o.put("state", state.name());
            o.put("service", service);
            o.put("odometer", odo != null && odo.isOk());
            o.put("can_box_version", canBox);
            o.put("car_type", carType);
            o.put("vehicle_type", vehicleType == null ? JSONObject.NULL : vehicleType);
            o.put("user_confirmed", user);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static boolean isConfirmed(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < 60_000L) return lastConfirmed;
        try {
            lastConfirmed = "CONFIRMED".equals(probe(ctx).optString("state"));
        } catch (Exception e) {
            lastConfirmed = false;
        }
        lastCheckMs = now;
        return lastConfirmed;
    }

    private static boolean userConfirmed(Context ctx) {
        try {
            Class<?> c = Class.forName("com.car2hass.AppConfig");
            Object overridden = c.getMethod("isUserOverridden", Context.class).invoke(null, ctx);
            Object profile = c.getMethod("getSelectedProfile", Context.class).invoke(null, ctx);
            return Boolean.TRUE.equals(overridden) && "voyah_generic".equals(String.valueOf(profile));
        } catch (Exception e) {
            return false;
        }
    }

    private static String raw(ProbeResult r) {
        return r == null || r.rawValue == null ? null : r.rawValue;
    }

    private static Integer parseInt(String s) {
        try {
            return s == null ? null : Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
