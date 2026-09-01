package com.car2hass.vehicle;

import android.content.Context;
import android.os.IBinder;

import com.car2hass.CANDataItem;
import com.car2hass.LogBuffer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Voyah read-only telemetry channel. On Voyah head units the vendor CAN bus
 * service (PATEO Qinggan) is exported without read permissions, so telemetry
 * is available to any sideloaded app; all write methods require a platform
 * signature and are never called here. The vendor SDK lives on the device
 * bootclasspath, so everything is resolved at runtime via reflection — on
 * BYD devices the classes are absent and every call fails fast into honest
 * dead() results.
 *
 * Registry keys -> VehicleState parameter names (VOYAH_PARAMS in
 * scripts/gen_registry.py); only keys that already exist in the integration
 * are mapped.
 */
public class VoyahChannel implements DataChannel {

    static final String DESCRIPTOR = "com.qinggan.canbus.ICanBusService";
    private static final String IFACE_CLASS = "com.qinggan.canbus.ICanBusService";
    private static final String STATE_CLASS = "com.qinggan.canbus.VehicleState";

    /** registry key -> VehicleState constant name. Sync with gen_registry.py. */
    public static final Map<String, String> VOYAH_PARAMS;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("engine_coolant_temp", "ENG_COOLANT_TEMP");
        m.put("soc", "BMS_SOC_DISPLAY");
        m.put("range", "PDCM_REMAINING_MILEAGE_STANDARD");
        m.put("powertrain_mode", "TravelProgramme");
        m.put("low_beam", "HEAD_LIGHT_STATUS");
        m.put("high_beam", "HIGH_BEAM");
        m.put("drl", "DaytimeRunninglights");
        m.put("front_fog", "FRONT_FOG_LIGHT");
        m.put("rear_fog", "REAR_FOG_LIGHT");
        m.put("brake_pedal", "BRAKE_PEDAL_STATUS");
        m.put("accel_pedal", "ACCEL_PEDAL_POSITION");
        m.put("charging_state", "CHARGE_STATE");
        m.put("charge_gun_state", "CHARGE_GUN_UNLOCK_SET");
        m.put("ac_state", "AC_CLIMATE_SW_REQ");
        m.put("front_wiper_speed", "FRONT_WIPER");
        m.put("rear_left_door", "DOOR_POSITION_STATUS_RL");
        m.put("rear_right_door", "DOOR_POSITION_STATUS_RR");
        m.put("rear_left_door_lock", "DOOR_WORK_STATUS_RL");
        m.put("rear_right_door_lock", "DOOR_WORK_STATUS_RR");
        m.put("driver_seat_heat", "FRONT_SEAT_HEATING_SWITCH_LEFT");
        m.put("passenger_seat_heat", "FRONT_SEAT_HEATING_SWITCH_RIGHT");
        m.put("driver_seat_vent", "FRONT_SEAT_VENTILATION_SWITCH_LEFT");
        m.put("passenger_seat_vent", "FRONT_SEAT_VENTILATION_SWITCH_RIGHT");
        m.put("rear_left_seat_heat", "REAR_SEAT_HEATING_SWITCH_LEFT");
        m.put("rear_right_seat_heat", "REAR_SEAT_HEATING_SWITCH_RIGHT");
        m.put("steering_wheel_heat", "STEERING_WHEEL_HEATING_SWITCH");
        m.put("rear_defrost", "REAR_WINDOWN_HEAT_STATUS");
        m.put("charge_rate", "CHARGE_RATE");
        m.put("mirror_fold", "REAR_MIRROR_FOLD_SET");
        VOYAH_PARAMS = java.util.Collections.unmodifiableMap(m);
    }

    private volatile Object cachedIface;
    private volatile boolean probeFailed;
    private volatile boolean probed;

    // ---- raw-transact fallback (per tsrman/voyah-telemetry-demo: the firmware
    // SDK jars are empty stubs, so reflection may fail even on Voyah heads). ----

    /** Transaction code of getBatteryRemainingCapacity() — float, % SOC. */
    static final int TX_BATTERY_SOC = 71;
    private static final String RAW_PKG = "com.qinggan.canbus.service";
    private static final String RAW_ACTION = "qg.canbus";
    private static final long BIND_TIMEOUT_MS = 3000;

    private volatile android.os.IBinder rawBinder;
    private volatile boolean useRawTransact;
    private android.content.Context rawContext;

    @Override
    public String id() { return "voyah"; }

    @Override
    public String displayName() { return "Voyah CANBus (read-only)"; }

    @Override
    public boolean supportsCommands() { return false; }

    @Override
    public ChannelResult probe(Context ctx) {
        // Primary: reflection into the device SDK classes.
        try {
            Class<?> iface = Class.forName(IFACE_CLASS);
            Object asInterface = stubAsInterface(iface, findBinder());
            if (asInterface != null) {
                cachedIface = asInterface;
                int ok = 0;
                for (String param : VOYAH_PARAMS.values()) {
                    if (queryState(asInterface, iface, param) != null) ok++;
                }
                if (ok > 0) {
                    probed = true;
                    return ChannelResult.rawData("ICanBusService отвечает, параметров прочитано: " + ok);
                }
                LogBuffer.d("VoyahChannel", "reflection found no readable params, trying raw transact");
            }
        } catch (ClassNotFoundException e) {
            LogBuffer.d("VoyahChannel", "SDK classes absent (empty jars?), trying raw transact");
        } catch (Exception e) {
            LogBuffer.d("VoyahChannel", "reflection probe: " + e.getClass().getSimpleName());
        }
        // Fallback: explicit bind + direct IBinder.transact with known getter codes.
        if (bindRaw(ctx)) {
            Float soc = transactFloat(TX_BATTERY_SOC);
            if (soc != null) {
                useRawTransact = true;
                probed = true;
                return ChannelResult.rawData("raw transact ok, SOC=" + soc + "%");
            }
        }
        probeFailed = true;
        probed = true;
        return ChannelResult.dead("сервис qinggan CANBus не найден (не Voyah?)");
    }

    @Override
    public List<CANDataItem> read(Context ctx, List<CANDataItem> knownItems) {
        List<CANDataItem> out = new ArrayList<>();
        if (probeFailed || knownItems == null) return out;
        // Lazy init: the telemetry cycle may read the channel without the
        // research probe having run (e.g. Voyah-only setups).
        if (!probed && !probeFailed) {
            try { probe(ctx); } catch (Exception ignored) {}
        }
        if (probeFailed) return out;
        if (useRawTransact) return readRaw(ctx, knownItems);
        if (cachedIface == null && !ensureIface()) return out;
        try {
            Class<?> iface = Class.forName(IFACE_CLASS);
            for (CANDataItem item : knownItems) {
                if (item == null || item.key == null) continue;
                String param = VOYAH_PARAMS.get(item.key);
                if (param == null) continue;
                Object value = queryState(cachedIface, iface, param);
                if (value != null) {
                    item.value = stringify(value);
                    out.add(item);
                }
            }
        } catch (Exception e) {
            LogBuffer.d("VoyahChannel", "read: " + e.getMessage());
        }
        return out;
    }

    // ---- raw transact path ---------------------------------------------------

    /** Binds to the exported CanBusService; true when a live binder was obtained. */
    private boolean bindRaw(android.content.Context ctx) {
        if (rawBinder != null && rawBinder.pingBinder()) return true;
        try {
            rawContext = ctx.getApplicationContext();
            final java.util.concurrent.CountDownLatch latch =
                    new java.util.concurrent.CountDownLatch(1);
            android.content.ServiceConnection conn = new android.content.ServiceConnection() {
                @Override public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
                    rawBinder = service;
                    latch.countDown();
                }
                @Override public void onServiceDisconnected(android.content.ComponentName name) {
                    rawBinder = null;
                }
            };
            android.content.Intent intent = new android.content.Intent(RAW_ACTION).setPackage(RAW_PKG);
            if (!rawContext.bindService(intent, conn, android.content.Context.BIND_AUTO_CREATE)) {
                LogBuffer.d("VoyahChannel", "bindService(qg.canbus) returned false");
                return false;
            }
            if (!latch.await(BIND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                LogBuffer.d("VoyahChannel", "bind timeout");
                return false;
            }
            return rawBinder != null && rawBinder.pingBinder();
        } catch (Exception e) {
            LogBuffer.d("VoyahChannel", "bindRaw: " + e.getClass().getSimpleName());
            return false;
        }
    }

    /** Registry keys readable via raw transact; others stay unsupported until codes are known. */
    private List<CANDataItem> readRaw(android.content.Context ctx, List<CANDataItem> knownItems) {
        List<CANDataItem> out = new ArrayList<>();
        for (CANDataItem item : knownItems) {
            if (item == null || !"soc".equals(item.key)) continue;
            Float soc = transactFloat(TX_BATTERY_SOC);
            if (soc == null) continue;
            item.value = String.valueOf(Math.round(soc));
            item.lastUpdate = System.currentTimeMillis();
            out.add(item);
        }
        return out;
    }

    private Float transactFloat(int code) {
        android.os.IBinder b = rawBinder;
        if (b == null || !b.pingBinder()) return null;
        android.os.Parcel data = android.os.Parcel.obtain();
        android.os.Parcel reply = android.os.Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            b.transact(code, data, reply, 0);
            reply.readException();
            float v = reply.readFloat();
            // Sentinels from CanBusManager: NaN / Float.MIN_VALUE mean "unknown".
            if (Float.isNaN(v) || v == Float.MIN_VALUE) return null;
            return v;
        } catch (Exception e) {
            LogBuffer.d("VoyahChannel", "transactFloat(" + code + "): " + e.getMessage());
            return null;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /** Single-parameter probe for the research engine; fails fast off-Voyah. */
    public static ProbeResult readSingleParam(String vsParam) {
        try {
            Class<?> iface = Class.forName(IFACE_CLASS);
            Object asInterface = stubAsInterface(iface, findBinder());
            if (asInterface == null) return ProbeResult.unsupported();
            Object v = queryStateStatic(asInterface, iface, vsParam);
            if (v == null) return ProbeResult.error("параметр не читается: " + vsParam);
            return ProbeResult.fromRaw(stringify(v), false);
        } catch (ClassNotFoundException e) {
            return ProbeResult.unsupported();
        } catch (Exception e) {
            return ProbeResult.error(e.getMessage());
        }
    }

    private static Object queryStateStatic(Object ifaceObj, Class<?> iface, String paramName) {
        try {
            Object constant = stateConstant(paramName);
            if (constant == null) return null;
            for (java.lang.reflect.Method m : iface.getMethods()) {
                String name = m.getName();
                if (!name.startsWith("get") && !name.startsWith("query")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 0 || !p[0].isAssignableFrom(constant.getClass())) continue;
                if (p.length == 1) return m.invoke(ifaceObj, constant);
                if (p.length == 2 && p[1] == int.class) return m.invoke(ifaceObj, constant, 0);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- reflection plumbing ------------------------------------------------

    private boolean ensureIface() {
        try {
            Class<?> iface = Class.forName(IFACE_CLASS);
            Object i = stubAsInterface(iface, findBinder());
            if (i == null) return false;
            cachedIface = i;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Finds the system service whose binder descriptor matches ICanBusService. */
    private static IBinder findBinder() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Method getService = sm.getMethod("getService", String.class);
        Object names = sm.getMethod("listServices").invoke(null);
        if (names instanceof String[]) {
            for (String name : (String[]) names) {
                try {
                    IBinder b = (IBinder) getService.invoke(null, name);
                    if (b != null && DESCRIPTOR.equals(b.getInterfaceDescriptor())) return b;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static Object stubAsInterface(Class<?> iface, IBinder binder) throws Exception {
        if (binder == null) return null;
        Class<?> stub = Class.forName(IFACE_CLASS + "$Stub");
        Method asInterface = stub.getMethod("asInterface", IBinder.class);
        return asInterface.invoke(null, binder);
    }

    /**
     * Calls getVehicleState / queryVehicleState with the VehicleState constant,
     * adapting to whichever overload the device interface declares. Read-only:
     * methods starting with set/add/clear/remove are never touched.
     */
    private Object queryState(Object ifaceObj, Class<?> iface, String paramName) {
        try {
            Object constant = stateConstant(paramName);
            if (constant == null) return null;
            for (Method m : iface.getMethods()) {
                String name = m.getName();
                if (!name.startsWith("get") && !name.startsWith("query")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 0 || !p[0].isAssignableFrom(constant.getClass())) continue;
                if (p.length == 1) {
                    return m.invoke(ifaceObj, constant);
                }
                if (p.length == 2 && p[1] == int.class) {
                    return m.invoke(ifaceObj, constant, 0);
                }
            }
            return null;
        } catch (Exception e) {
            LogBuffer.d("VoyahChannel", "query " + paramName + ": " + e.getMessage());
            return null;
        }
    }

    /** Resolves VehicleState.<paramName> enum/int constant by field lookup. */
    private static Object stateConstant(String paramName) {
        try {
            Class<?> state = Class.forName(STATE_CLASS);
            Field f = state.getField(paramName);
            return f.get(null);
        } catch (Exception e) {
            LogBuffer.d("VoyahChannel", "constant " + paramName + ": " + e.getMessage());
            return null;
        }
    }

    private static String stringify(Object v) {
        if (v instanceof Number) return v.toString();
        String s = String.valueOf(v);
        return s.isEmpty() ? null : s;
    }
}
