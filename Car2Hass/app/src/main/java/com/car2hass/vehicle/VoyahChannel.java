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
 * signature and are never called here. Two read paths are used:
 *
 * 1. Reflection into the vendor SDK classes (ICanBusService/VehicleState).
 *    The classes may live on the bootclasspath or only inside the service APK,
 *    so they are also loaded through the service package classloader.
 * 2. Raw bind + IBinder.transact(code) against the exported service, using the
 *    read-only getter transaction codes verified on firmware f0506h
 *    (tsrman/voyah-telemetry-demo). This path needs no SDK classes at all.
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
        m.put("speed", "GW_ESC_VEHSPD");
        m.put("gear", "TGS_LEVER");
        m.put("driver_seatbelt", "acu_driverSeatBeltSts");
        m.put("passenger_seatbelt", "acu_passengerSeatBeltSts");
        m.put("sunroof", "SUNROOF_OPEN_PERCENT");
        m.put("sunshade", "ROLL_OPEN_PERCENT");
        m.put("window_fl", "DRIVER_WINDOW_CONTROL");
        m.put("window_fr", "PAS_WIDOW_CONTROL");
        m.put("window_rl", "LEFT_BACK_WINDOW_CONTROL");
        m.put("window_rr", "RIGHT_BACK_WINDOW_CONTROL");
        m.put("fuel_charge_flap", "FUEL_PORT_CAP_STS");
        m.put("sidelights", "POSITION_LAMP_SWITCH");
        m.put("left_turn", "LEFT_DIRECTION_LIGHT");
        m.put("right_turn", "RIGHT_DIRECTION_LIGHT");
        m.put("hazard", "WARNING_LIGHT");
        m.put("cruise_switch", "CRUISE_CONTROL");
        m.put("acc_cruise_state", "IACC_OR_ACC_STATUS");
        m.put("lane_keep_state", "LKSStatus");
        m.put("auto_hold", "EPB_PARK_STATUS");
        m.put("total_energy", "ENERGY_CON_SUM_AV");
        m.put("drive_mode", "DRIVING_MODE_SET");
        VOYAH_PARAMS = java.util.Collections.unmodifiableMap(m);
    }

    // ---- raw-transact fallback (per tsrman/voyah-telemetry-demo: the firmware
    // SDK jars are empty stubs, so reflection may fail even on Voyah heads). ----

    private static final String RAW_PKG = "com.qinggan.canbus.service";
    private static final String RAW_ACTION = "qg.canbus";
    private static final long BIND_TIMEOUT_MS = 3000;

    static final int RAW_INT = 0;
    static final int RAW_FLOAT = 1;

    /**
     * registry key -> {transaction code, type}. Codes are the read-only getter
     * transactions of ICanBusService.Stub (firmware f0506h); value sentinels
     * (Integer.MIN_VALUE/-9999, NaN/Float.MIN_VALUE) mean "unknown".
     */
    static final Map<String, int[]> VOYAH_RAW_TX;
    static {
        Map<String, int[]> m = new HashMap<>();
        m.put("soc", new int[]{71, RAW_FLOAT});          // getBatteryRemainingCapacity, %
        m.put("speed", new int[]{26, RAW_INT});          // getVehicleSpeed, km/h
        m.put("ambient_temp", new int[]{7, RAW_INT});    // getAmbientTemperature, °C
        m.put("fuel_rate", new int[]{15, RAW_INT});      // getInstantFuelConsumption
        m.put("powertrain_mode", new int[]{43, RAW_INT}); // getHevSysMode
        VOYAH_RAW_TX = java.util.Collections.unmodifiableMap(m);
    }

    private volatile Object cachedIface;
    private volatile Class<?> cachedIfaceClass;
    private static volatile ClassLoader deviceCl;
    private volatile boolean probeFailed;
    private volatile boolean probed;
    private volatile long lastProbeMs;
    private volatile boolean probing;
    private static final long PROBE_RETRY_MS = 60_000L;

    private volatile IBinder rawBinder;
    private Context rawContext;

    @Override
    public String id() { return "voyah"; }

    @Override
    public String displayName() { return "Voyah CANBus (read-only)"; }

    @Override
    public boolean supportsCommands() { return false; }

    @Override
    public ChannelResult probe(Context ctx) {
        probing = true;
        lastProbeMs = System.currentTimeMillis();
        try {
            return probeInner(ctx);
        } finally {
            probing = false;
        }
    }

    private ChannelResult probeInner(Context ctx) {
        boolean bound = bindRaw(ctx);
        IBinder binder = bound ? rawBinder : findBinder();
        // Primary: reflect the vendor SDK classes.
        int ok = tryReflection(IFACE_CLASS, binder);
        // The firmware SDK jars may be empty stubs; retry the real AIDL Stub and
        // VehicleState from the service package's own APK classloader.
        if (ok <= 1 && deviceCl == null && ctx != null) {
            ClassLoader pcl = serviceClassLoader(ctx);
            if (pcl != null) {
                deviceCl = pcl;
                ok = Math.max(ok, tryReflection(IFACE_CLASS, binder));
            }
        }
        if (ok > 1) {
            probeFailed = false;
            probed = true;
            return ChannelResult.rawData("ICanBusService отвечает, параметров прочитано: " + ok);
        }
        cachedIface = null;
        cachedIfaceClass = null;
        LogBuffer.d("VoyahChannel", "reflection found " + ok + " readable params, using raw transact");
        // Fallback: explicit bind + direct IBinder.transact with known getter codes.
        if (bound) {
            Float soc = transactFloat(71);
            if (soc != null) {
                probeFailed = false;
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
        if (knownItems == null) return out;
        // A failed probe is retried after a cooldown instead of latching forever:
        // the CAN service may not be ready during early boot of the head unit.
        long now = System.currentTimeMillis();
        if ((probeFailed && now - lastProbeMs > PROBE_RETRY_MS) || !probed) {
            if (!probing) probe(ctx);
        }
        if (probeFailed) return out;
        if (cachedIface == null) {
            if (rawBinder == null || !rawBinder.pingBinder()) {
                if (!bindRaw(ctx)) return out;
            }
        }
        for (CANDataItem item : knownItems) {
            if (item == null || item.key == null) continue;
            String value = readKey(item.key);
            if (value == null) continue;
            item.value = value;
            item.lastUpdate = now;
            out.add(item);
        }
        return out;
    }

    /** Registry key -> value via reflection (preferred) then raw transact. */
    private String readKey(String key) {
        Object iface = cachedIface;
        if (iface != null) {
            String param = VOYAH_PARAMS.get(key);
            if (param != null) {
                Object value = queryState(iface, cachedIfaceClass, param);
                if (value != null) return stringify(value);
            }
        }
        int[] tx = VOYAH_RAW_TX.get(key);
        if (tx == null) return null;
        if (tx[1] == RAW_FLOAT) {
            Float f = transactFloat(tx[0]);
            return f == null ? null : String.valueOf(Math.round(f));
        }
        Integer i = transactInt(tx[0]);
        return i == null ? null : String.valueOf(i);
    }

    /** Binds to the exported CanBusService; true when a live binder was obtained. */
    private boolean bindRaw(Context ctx) {
        if (rawBinder != null && rawBinder.pingBinder()) return true;
        try {
            rawContext = ctx.getApplicationContext();
            final java.util.concurrent.CountDownLatch latch =
                    new java.util.concurrent.CountDownLatch(1);
            android.content.ServiceConnection conn = new android.content.ServiceConnection() {
                @Override public void onServiceConnected(android.content.ComponentName name, IBinder service) {
                    rawBinder = service;
                    latch.countDown();
                }
                @Override public void onServiceDisconnected(android.content.ComponentName name) {
                    rawBinder = null;
                }
            };
            android.content.Intent intent = new android.content.Intent(RAW_ACTION).setPackage(RAW_PKG);
            if (!rawContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
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

    private Integer transactInt(int code) {
        android.os.Parcel reply = transactRaw(code, null);
        if (reply == null) return null;
        try {
            reply.readException();
            int v = reply.readInt();
            // Sentinels from CanBusManager: MIN_VALUE / -9999 mean "unknown".
            if (v == Integer.MIN_VALUE || v == -9999) return null;
            return v;
        } catch (Exception e) {
            return null;
        } finally {
            reply.recycle();
        }
    }

    private Float transactFloat(int code) {
        android.os.Parcel reply = transactRaw(code, null);
        if (reply == null) return null;
        try {
            reply.readException();
            float v = reply.readFloat();
            // Sentinels from CanBusManager: NaN / Float.MIN_VALUE mean "unknown".
            if (Float.isNaN(v) || v == Float.MIN_VALUE) return null;
            return v;
        } catch (Exception e) {
            return null;
        } finally {
            reply.recycle();
        }
    }

    /** Runs a read-only transaction; returns the reply Parcel or null. */
    private android.os.Parcel transactRaw(int code, android.os.Parcel data) {
        IBinder b = rawBinder;
        if (b == null || !b.pingBinder()) return null;
        android.os.Parcel req = data;
        if (req == null) req = android.os.Parcel.obtain();
        android.os.Parcel reply = android.os.Parcel.obtain();
        try {
            req.writeInterfaceToken(DESCRIPTOR);
            b.transact(code, req, reply, 0);
            return reply;
        } catch (Exception e) {
            LogBuffer.d("VoyahChannel", "transact(" + code + "): " + e.getMessage());
            reply.recycle();
            return null;
        } finally {
            if (data == null) req.recycle();
        }
    }

    /** Single-parameter probe for the research engine; fails fast off-Voyah. */
    public static ProbeResult readSingleParam(String vsParam) {
        try {
            Class<?> iface = loadDeviceClass(IFACE_CLASS);
            if (iface == null) return ProbeResult.unsupported();
            Object asInterface = stubAsInterface(iface, findBinder());
            if (asInterface == null) return ProbeResult.unsupported();
            Object v = queryStateStatic(asInterface, iface, vsParam);
            if (v == null) return ProbeResult.error("параметр не читается: " + vsParam);
            return ProbeResult.fromRaw(stringify(v), false);
        } catch (Exception e) {
            return ProbeResult.error(e.getMessage());
        }
    }

    private static Object queryStateStatic(Object ifaceObj, Class<?> iface, String paramName) {
        try {
            Object constant = stateConstant(paramName);
            if (constant == null) return null;
            for (Method m : iface.getMethods()) {
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

    // ---- device class loading -----------------------------------------------

    /**
     * Reflects the vendor SDK: loads ICanBusService from the bootclasspath (or
     * already-resolved service classloader), binds it to an IBinder and counts
     * how many VehicleState descriptors answer. Caches the interface on success.
     */
    private int tryReflection(String ifaceName, IBinder binder) {
        try {
            Class<?> iface = loadDeviceClass(ifaceName);
            if (iface == null) {
                LogBuffer.d("VoyahChannel", "SDK classes absent, using raw transact");
                return 0;
            }
            Object asInterface = stubAsInterface(iface, binder);
            if (asInterface == null) return 0;
            int ok = 0;
            for (String param : VOYAH_PARAMS.values()) {
                if (queryState(asInterface, iface, param) != null) ok++;
            }
            if (ok > 0) {
                cachedIface = asInterface;
                cachedIfaceClass = iface;
            }
            return ok;
        } catch (Throwable e) {
            LogBuffer.d("VoyahChannel", "reflection: " + e.getClass().getSimpleName());
            return 0;
        }
    }

    /**
     * Classloader of the CanBus service package, whose APK carries the real
     * AIDL Stub and VehicleState enum even when the firmware jars are empty
     * stubs. Read-only; returns null when the package is absent or restricted.
     */
    private static ClassLoader serviceClassLoader(Context ctx) {
        try {
            Context pkg = ctx.createPackageContext(RAW_PKG, Context.CONTEXT_INCLUDE_CODE);
            return pkg.getClassLoader();
        } catch (Exception e) {
            LogBuffer.d("VoyahChannel", "service classloader: " + e.getClass().getSimpleName());
            return null;
        }
    }

    /** Loads a vendor class from the service classloader first, then the bootclasspath. */
    private static Class<?> loadDeviceClass(String name) {
        ClassLoader cl = deviceCl;
        if (cl != null) {
            try {
                return cl.loadClass(name);
            } catch (ClassNotFoundException ignored) {
                // fall through to the bootclasspath
            }
        }
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    /** Finds the system service whose binder descriptor matches ICanBusService. */
    private static IBinder findBinder() {
        try {
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
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object stubAsInterface(Class<?> iface, IBinder binder) throws Exception {
        if (binder == null) return null;
        ClassLoader cl = iface.getClassLoader();
        Class<?> stub = cl != null
                ? Class.forName(iface.getName() + "$Stub", false, cl)
                : Class.forName(iface.getName() + "$Stub");
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

    /** Resolves VehicleState.<paramName> constant by field lookup. */
    private static Object stateConstant(String paramName) {
        try {
            Class<?> state = Class.forName(STATE_CLASS, false,
                    deviceCl != null ? deviceCl : VoyahChannel.class.getClassLoader());
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
