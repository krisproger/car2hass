package com.car2hass.vehicle;

import android.os.IBinder;
import android.os.Parcel;

import java.util.Locale;

/** Read-only direct Binder access to the Voyah ICanBusService snapshots. */
public final class VoyahDirectReader {
    private static final String DESCRIPTOR = "com.qinggan.canbus.ICanBusService";
    private static final int TX_ODOMETER = 1, TX_DOORS = 2, TX_CANBOX = 4,
            TX_FUEL = 9, TX_BATTERY = 19;
    private static final long CACHE_MS = 1000L;

    private static volatile boolean enabled;
    private static volatile Snapshot cached;
    private static volatile long cachedAt;

    private VoyahDirectReader() {}

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) { cached = null; cachedAt = 0L; }
    }

    public static final class Snapshot {
        final int[] doors;
        final float fuelPercent;
        final float batteryVolts;
        final float odometerTotal, range, evRange;
        final boolean fuelValid, batteryValid, odometerValid;
        Snapshot(int[] doors, float fuelPercent, float batteryVolts,
                 float odometerTotal, float range, float evRange,
                 boolean fuelValid, boolean batteryValid, boolean odometerValid) {
            this.doors = doors;
            this.fuelPercent = fuelPercent;
            this.batteryVolts = batteryVolts;
            this.odometerTotal = odometerTotal;
            this.range = range;
            this.evRange = evRange;
            this.fuelValid = fuelValid;
            this.batteryValid = batteryValid;
            this.odometerValid = odometerValid;
        }
    }

    public static String valueFor(String key) {
        if (!enabled) return null;
        Snapshot s = snapshot();
        if (s == null) return null;
        if ("fuel_level".equals(key)) {
            return s.fuelValid ? String.format(Locale.US, "%.1f", s.fuelPercent) : null;
        }
        if ("battery_12v_voltage".equals(key)) {
            return s.batteryValid ? String.format(Locale.US, "%.1f", s.batteryVolts) : null;
        }
        if ("odometer".equals(key)) {
            return s.odometerValid ? String.valueOf(Math.round(s.odometerTotal)) : null;
        }
        if ("range".equals(key)) {
            return s.odometerValid ? String.valueOf(Math.round(s.range)) : null;
        }
        VoyahDirectCodec.Doors d = VoyahDirectCodec.doors(s.doors);
        if (!d.valid) return null;
        switch (key) {
            case "driver_door": return d.frontLeft ? "open" : "closed";
            case "passenger_door": return d.frontRight ? "open" : "closed";
            case "rear_left_door": return d.rearLeft ? "open" : "closed";
            case "rear_right_door": return d.rearRight ? "open" : "closed";
            case "trunk": return d.trunkValid ? (d.trunk ? "open" : "closed") : null;
            default: return null;
        }
    }

    private static Snapshot snapshot() {
        long now = System.currentTimeMillis();
        Snapshot s = cached;
        if (s != null && now - cachedAt < CACHE_MS) return s;
        s = read();
        cached = s;
        cachedAt = now;
        return s;
    }

    private static Snapshot read() {
        int[] doors = readInts(TX_DOORS);
        float[] fuel = readFuel();
        int[] battery = readInts(TX_BATTERY);
        float[] odo = readFloats(TX_ODOMETER);
        VoyahDirectCodec.Fuel f = fuel == null ? new VoyahDirectCodec.Fuel(false, 0f)
                : VoyahDirectCodec.fuel((int) fuel[0], (int) fuel[1], fuel[2], (int) fuel[3]);
        VoyahDirectCodec.Battery b = battery == null ? new VoyahDirectCodec.Battery(false, 0f)
                : VoyahDirectCodec.battery(battery[0], battery[1]);
        VoyahDirectCodec.Odometer o = odo == null
                ? new VoyahDirectCodec.Odometer(false, 0f, 0f, 0f)
                : VoyahDirectCodec.odometer(odo);
        return new Snapshot(doors, f.percent, b.volts,
                o.total, o.canTravel, o.evRange, f.valid, b.valid, o.valid);
    }

    public static String canBoxVersion() {
        Parcel reply = call(TX_CANBOX);
        if (reply == null) return null;
        try {
            reply.readException();
            return reply.readString();
        } catch (Exception e) {
            return null;
        } finally {
            reply.recycle();
        }
    }

    public static org.json.JSONObject probe() {
        org.json.JSONObject o = new org.json.JSONObject();
        try {
            int[] doors = readInts(TX_DOORS);
            float[] fuel = readFuel();
            int[] battery = readInts(TX_BATTERY);
            float[] odo = readFloats(TX_ODOMETER);
            if (doors != null) o.put("door_raw", new org.json.JSONArray(doors));
            if (fuel != null) {
                org.json.JSONArray a = new org.json.JSONArray();
                for (float v : fuel) a.put(v);
                o.put("fuel_raw", a);
            }
            if (battery != null) o.put("battery_raw", new org.json.JSONArray(battery));
            ProbeResult bcm = VoyahChannel.readSingleParam("INPUT_VOLT_FOR_BCM");
            if (bcm != null && bcm.rawValue != null) {
                o.put("input_volt_for_bcm", bcm.rawValue);
            }
            if (odo != null) {
                org.json.JSONArray a = new org.json.JSONArray();
                for (float v : odo) a.put(v);
                o.put("odometer_raw", a);
            }
            o.put("can_box_version", canBoxVersion());
        } catch (Exception e) {
            try { o.put("error", e.getMessage()); } catch (Exception ignored) {}
        }
        return o;
    }

    private static int[] readInts(int tx) {
        Parcel reply = call(tx);
        if (reply == null) return null;
        try {
            reply.readException();
            if (reply.readInt() == 0) return null;
            int count = Math.min(10, reply.dataAvail() / 4);
            int[] out = new int[count];
            for (int i = 0; i < count; i++) out[i] = reply.readInt();
            return out;
        } catch (Exception e) {
            return null;
        } finally {
            reply.recycle();
        }
    }

    private static float[] readFuel() {
        Parcel reply = call(TX_FUEL);
        if (reply == null) return null;
        try {
            reply.readException();
            if (reply.readInt() == 0 || reply.dataAvail() < 12) return null;
            int capacity = reply.readInt();
            int remain = reply.readInt();
            float pct = reply.readFloat();
            int shortage = reply.dataAvail() >= 4 ? reply.readInt() : 0;
            return new float[]{capacity, remain, pct, shortage};
        } catch (Exception e) {
            return null;
        } finally {
            reply.recycle();
        }
    }

    private static float[] readFloats(int tx) {
        Parcel reply = call(tx);
        if (reply == null) return null;
        try {
            reply.readException();
            if (reply.readInt() == 0) return null;
            int count = Math.min(6, reply.dataAvail() / 4);
            float[] out = new float[count];
            for (int i = 0; i < count; i++) out[i] = reply.readFloat();
            return out.length < 6 ? null : out;
        } catch (Exception e) {
            return null;
        } finally {
            reply.recycle();
        }
    }

    private static Parcel call(int tx) {
        IBinder binder = VoyahChannel.findBinderPublic();
        if (binder == null) return null;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (!binder.transact(tx, data, reply, 0)) {
                reply.recycle();
                return null;
            }
            return reply;
        } catch (Exception e) {
            reply.recycle();
            return null;
        } finally {
            data.recycle();
        }
    }
}
