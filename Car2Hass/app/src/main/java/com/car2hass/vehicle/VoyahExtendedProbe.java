package com.car2hass.vehicle;

import android.os.IBinder;
import android.os.Parcel;

import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only probe for additional Voyah CAN getters (no runtime mapping yet). */
public final class VoyahExtendedProbe {
    private static final String DESCRIPTOR = "com.qinggan.canbus.ICanBusService";
    private VoyahExtendedProbe() {}

    public static JSONObject probe() {
        JSONObject o = new JSONObject();
        put(o, "tpms", tpms());
        put(o, "wheels", wheels());
        put(o, "seatbelts", seatbelts());
        put(o, "lights", lights());
        put(o, "dvr", ints(3, 20));
        put(o, "rain", ints(68, 1));
        put(o, "aircondition", airCondition());
        put(o, "power_info", method("getPowerInfo"));
        put(o, "vehicle_info", method("getVehicleInfo"));
        put(o, "odometer", method("getOdometer"));
        put(o, "drive_mode_raw", raw(VoyahChannel.readSingleParam("BCM_DRIVEMODE_CHANGE")));
        return o;
    }

    private static void put(JSONObject o, String key, Object value) {
        try { o.put(key, value); } catch (Exception ignored) {}
    }

    private static JSONObject tpms() {
        Parcel r = call(65);
        if (r == null) return null;
        try {
            r.readException();
            if (r.readInt() == 0) return null;
            int sys = r.readInt();
            int flS = r.readInt(), frS = r.readInt(), rlS = r.readInt(), rrS = r.readInt();
            float flT = r.readFloat(), frT = r.readFloat(), rlT = r.readFloat(), rrT = r.readFloat();
            float flP = r.readFloat(), frP = r.readFloat(), rlP = r.readFloat(), rrP = r.readFloat();
            JSONObject o = new JSONObject();
            o.put("system", sys);
            o.put("pressure", new JSONArray(new float[]{flP, frP, rlP, rrP}));
            o.put("temperature", new JSONArray(new float[]{flT, frT, rlT, rrT}));
            o.put("status", new JSONArray(new int[]{flS, frS, rlS, rrS}));
            return o;
        } catch (Exception e) {
            return null;
        } finally { r.recycle(); }
    }

    private static JSONArray wheels() {
        Parcel r = call(8);
        if (r == null) return null;
        try {
            r.readException();
            if (r.readInt() == 0) return null;
            return new JSONArray(new float[]{r.readFloat(), r.readFloat(), r.readFloat(), r.readFloat()});
        } catch (Exception e) {
            return null;
        } finally { r.recycle(); }
    }

    private static JSONArray seatbelts() {
        Parcel r = call(21);
        if (r == null) return null;
        try {
            r.readException();
            if (r.readInt() == 0) return null;
            return new JSONArray(new int[]{r.readInt(), r.readInt()});
        } catch (Exception e) {
            return null;
        } finally { r.recycle(); }
    }

    private static JSONArray lights() {
        return ints(5, 18);
    }

    private static JSONArray ints(int tx, int max) {
        Parcel r = call(tx);
        if (r == null) return null;
        try {
            r.readException();
            if (r.readInt() == 0) return null;
            int n = Math.min(max, r.dataAvail() / 4);
            int[] out = new int[n];
            for (int i = 0; i < n; i++) out[i] = r.readInt();
            return new JSONArray(out);
        } catch (Exception e) {
            return null;
        } finally { r.recycle(); }
    }

    private static JSONObject airCondition() {
        JSONObject o = new JSONObject();
        for (String getter : new String[]{
                "getAirTempInCar", "getAirTempOutCar", "getAirLeftTemperature",
                "getAirRightTemperature", "getAirWindSpeed", "getAirCirculationMode",
                "getAirMode", "getAirACStatus"}) {
            ProbeResult r = VoyahChannel.readAirConditionParam(getter);
            put(o, getter, r == null || r.rawValue == null ? "" : r.rawValue);
        }
        return o;
    }

    private static String method(String getter) {
        ProbeResult r = VoyahChannel.readMethodParam(getter);
        return r == null || r.rawValue == null ? "" : r.rawValue;
    }

    private static String raw(ProbeResult r) {
        return r == null || r.rawValue == null ? "" : r.rawValue;
    }

    private static Parcel call(int tx) {
        IBinder b = VoyahChannel.findBinderPublic();
        if (b == null) return null;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (!b.transact(tx, data, reply, 0)) { reply.recycle(); return null; }
            return reply;
        } catch (Exception e) {
            reply.recycle();
            return null;
        } finally { data.recycle(); }
    }
}
