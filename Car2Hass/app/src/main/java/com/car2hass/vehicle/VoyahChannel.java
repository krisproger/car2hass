package com.car2hass.vehicle;

import android.content.Context;
import android.os.IBinder;

import com.car2hass.CANDataItem;
import com.car2hass.LogBuffer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        m.put("window_fl", "BCM_FLWindowHorizontalSts");
        m.put("window_fr", "BCM_FRWindowHorizontalSts");
        m.put("window_rl", "BCM_RLWindowHorizontalSts");
        m.put("window_rr", "BCM_RRWindowHorizontalSts");
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
        m.put("drive_mode", "BCM_DRIVEMODE_CHANGE");
        m.put("battery_remaining_charge_time", "BMS_REMAIN_CHARGE_TIME");
        m.put("avg_speed", "AVG_SPEED");
        m.put("avg_power", "AVG_POWER");
        m.put("energy_recovery_gear", "ENERGY_RECOVERY_GEAR");
        m.put("energy_flow", "ENERGY_FLOW");
        m.put("charge_voltage", "OBC_CHARGE_VOLTAGE");
        m.put("charge_current", "OBC_CHARGE_CURRENT");
        m.put("ac_charge_state", "BMS_AC_CHARGE_STAT");
        m.put("dc_charge_state", "BMS_DC_CHARGE_STAT");
        m.put("ac_charge_connected", "AC_CHARG_CONNECT_STS");
        m.put("dc_charge_connected", "DC_CHARG_CONNECT_STS");
        m.put("charge_port_flap", "CHRG_PORT_CAP_STS");
        m.put("wireless_charge_state", "WCM_CHARGE_STATUS");
        m.put("trip_distance", "ODO_THISTIME");
        m.put("pm25", "PM25_STS");
        m.put("clean_air_level", "CLEAN_AIR_LEVEL");
        m.put("trans_oil_temp", "TRANS_OIL_TEMP");
        m.put("ambient_light_color", "VEHICLE_AMBIENT_LIGHT_COLOR");
        m.put("ambient_light_brightness", "VEHICLE_AMBIENT_LIGHT_BRIGHTNESS_LEVEL");
        m.put("ready_lamp", "READY_LAMP");
        m.put("window_fl_position", "BCM_FLWindowHorizontalSts");
        m.put("window_fr_position", "BCM_FRWindowHorizontalSts");
        m.put("window_rl_position", "BCM_RLWindowHorizontalSts");
        m.put("window_rr_position", "BCM_RRWindowHorizontalSts");
        m.put("vehicle_locked", "VEHICLE_LOCKED_FED");
        m.put("driver_seat_massage", "FRONT_SEAT_MASS_SWITCH_LEFT");
        m.put("passenger_seat_massage", "FRONT_SEAT_MASS_SWITCH_RIGHT");
        m.put("rear_left_seat_massage", "REAR_SEAT_MASS_SWITCH_LEFT");
        m.put("rear_right_seat_massage", "REAR_SEAT_MASS_SWITCH_RIGHT");
        VOYAH_PARAMS = java.util.Collections.unmodifiableMap(m);
    }

    /**
     * registry key -> no-argument ICanBusService getter. These are ordinary
     * interface methods, not VehicleState constants, so they are invoked by
     * name via reflection (source: VOYAH_FIRMWARE_ANALYSIS.md section 8.2 and
     * the tsrman/voyah-telemetry-demo). Sync with gen_registry.py.
     */
    public static final Map<String, String> VOYAH_METHODS;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("outside_temp", "getAmbientTemperature");  // int, °C
        m.put("ambient_temp", "getAmbientTemperature");  // int, °C
        VOYAH_METHODS = java.util.Collections.unmodifiableMap(m);
    }

    /**
     * registry key -> AirCondition getter. The A/C model lives in a separate
     * class ({@code com.qinggan.canbus.AirCondition}, 182 methods) and no
     * ICanBusService getter returns it directly, so the value is obtained from
     * the object returned by {@code getAirConditionState()} when that call
     * yields one. Kept best-effort: a null result only skips the key.
     * Sync with gen_registry.py.
     */
    public static final Map<String, String> VOYAH_AC_METHODS;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("cabin_temp", "getAirTempInCar");
        VOYAH_AC_METHODS = java.util.Collections.unmodifiableMap(m);
    }

    // ---- raw-transact fallback (per tsrman/voyah-telemetry-demo: the firmware
    // SDK jars are empty stubs, so reflection may fail even on Voyah heads). ----

    private static final String RAW_PKG = "com.qinggan.canbus.service";
    private static final String RAW_ACTION = "qg.canbus";
    private static final String PATH_SM = "ServiceManager";
    private static final String PATH_BIND = "bindService";

    /** Direct ServiceManager names to probe when listServices() is restricted. */
    private static final String[] SERVICE_CANDIDATES = {
            "canbus", "qg.canbus", "com.qinggan.canbus.ICanBusService"
    };

    /** (package, action) bind combinations tried in order; the first is the legacy route. */
    private static final String[][] RAW_COMBOS = {
            {RAW_PKG, RAW_ACTION},
            {"com.qinggan.canbus", "com.qinggan.canbus.ICanBusService"},
            {"com.qinggan.canbus.service", "com.qinggan.canbus.service.ICanBusService"},
    };

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
        m.put("outside_temp", new int[]{7, RAW_INT});    // getAmbientTemperature, °C
        m.put("fuel_rate", new int[]{15, RAW_INT});      // getInstantFuelConsumption
        m.put("powertrain_mode", new int[]{43, RAW_INT}); // getHevSysMode
        VOYAH_RAW_TX = java.util.Collections.unmodifiableMap(m);
    }

    /**
     * Keys whose raw getter transaction is tried before the VehicleState
     * constant. The demo documents {@code getVehicleSpeed} (tx 26) in km/h while
     * the 2026-10-07 raw dump showed {@code GW_ESC_VEHSPD} unavailable (-1), so
     * tx 26 is authoritative for speed and the constant is only a fallback.
     */
    private static final Set<String> TX_FIRST_KEYS = Collections.singleton("speed");

    private volatile Object cachedIface;
    private volatile Class<?> cachedIfaceClass;
    private static volatile ClassLoader deviceCl;
    private volatile boolean probeFailed;
    private volatile boolean probed;
    /** Whether the "channel down" line has already been logged this down-period. */
    private volatile boolean downLogged;
    private volatile long lastProbeMs;
    private volatile boolean probing;
    private static final long PROBE_RETRY_MS = 60_000L;

    /** Binder discovered from ServiceManager or bindService; reused across read cycles. */
    private volatile IBinder cachedBinder;
    private volatile String binderPath = PATH_SM;
    private volatile String lastBindError = "unavailable";
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
        ensureDeviceClass(ctx);

        // Fast path: a binder discovered earlier is reused instead of walking
        // ServiceManager.listServices() again on every probe/read cycle.
        IBinder cached = liveCachedBinder();
        if (cached != null) {
            ChannelResult r = tryBinder(cached, binderPath);
            if (r != null) return r;
            invalidateBinder();
        }

        // Primary path: exactly what the research engine uses — the binder is
        // taken straight from ServiceManager (ICanBusService descriptor), and the
        // vendor SDK class is loaded from the service APK's classloader. It does
        // not rely on bindService(qg.canbus), which some head units reject.
        IBinder smBinder = findBinder();
        if (smBinder == null) {
            LogBuffer.d("VoyahChannel", "ServiceManager path: ICanBusService not registered");
        } else {
            cachedBinder = smBinder;
            binderPath = PATH_SM;
            ChannelResult r = tryBinder(smBinder, PATH_SM);
            if (r != null) return r;
            LogBuffer.d("VoyahChannel", "ServiceManager path: no readable params");
        }

        // Fallback: the legacy bindService(qg.canbus) route, then reflection and
        // finally the known raw getter transaction codes.
        if (bindRaw(ctx, VoyahReadPolicy.FIRST_BIND_TIMEOUT_MS)) {
            binderPath = PATH_BIND;
            ChannelResult r = tryBinder(cachedBinder, PATH_BIND);
            if (r != null) return r;
            lastBindError = "no-readable-params";
            LogBuffer.d("VoyahChannel", "bindService path: no readable params");
        } else {
            LogBuffer.d("VoyahChannel", "bindService path: " + lastBindError);
        }

        invalidateBinder();
        probeFailed = true;
        probed = true;
        String why = "ServiceManager=" + (smBinder == null ? "not-registered" : "no-readable-params")
                + ", bindService=" + lastBindError;
        if (!downLogged) {
            downLogged = true;
            LogBuffer.i("VoyahChannel", "channel down: " + why);
        }
        return ChannelResult.dead("сервис qinggan CANBus не найден (не Voyah?)");
    }

    /** Reflection first, then raw transact; null when the binder yields nothing. */
    private ChannelResult tryBinder(IBinder binder, String path) {
        int ok = tryReflection(IFACE_CLASS, binder);
        if (ok > 1) {
            probeFailed = false;
            probed = true;
            downLogged = false;
            LogBuffer.i("VoyahChannel", "values via " + path + "+reflection (" + ok + " params)");
            return ChannelResult.rawData("ICanBusService отвечает, параметров прочитано: " + ok);
        }
        // A single readable param is not enough to trust reflection; drop the
        // partial interface so the raw-transact fallback can take over.
        if (ok == 1) {
            cachedIface = null;
            cachedIfaceClass = null;
        }
        Float soc = transactFloat(71);
        if (soc != null) {
            probeFailed = false;
            probed = true;
            downLogged = false;
            LogBuffer.i("VoyahChannel", "values via " + path + "+transact (SOC=" + soc + "%)");
            return ChannelResult.rawData("raw transact ok, SOC=" + soc + "%");
        }
        return null;
    }

    private IBinder liveCachedBinder() {
        IBinder b = cachedBinder;
        return (b != null && b.pingBinder()) ? b : null;
    }

    private void invalidateBinder() {
        cachedIface = null;
        cachedIfaceClass = null;
        cachedBinder = null;
    }

    /** Discovers a binder from ServiceManager first, then the legacy bindService. */
    private IBinder acquireBinder(Context ctx, long bindTimeoutMs) {
        IBinder sm = findBinder();
        if (sm != null) {
            cachedBinder = sm;
            binderPath = PATH_SM;
            return sm;
        }
        if (bindRaw(ctx, bindTimeoutMs)) {
            binderPath = PATH_BIND;
            return cachedBinder;
        }
        return null;
    }

    /** Ensures a live binder + interface are cached, re-discovering only when stale. */
    private void ensureReadable(Context ctx) {
        IBinder b = liveCachedBinder();
        if (b == null) {
            invalidateBinder();
            b = acquireBinder(ctx, VoyahReadPolicy.READ_TIMEOUT_MS);
            if (b == null) return;
        }
        if (cachedIface == null && tryReflection(IFACE_CLASS, b) > 0) {
            LogBuffer.i("VoyahChannel", "values via " + binderPath + "+reflection (lazy)");
        }
    }

    /** Loads the vendor SDK classes from the service APK once, if possible. */
    private static void ensureDeviceClass(Context ctx) {
        if (deviceCl != null || ctx == null) return;
        ClassLoader pcl = serviceClassLoader(ctx);
        if (pcl != null) {
            deviceCl = pcl;
            LogBuffer.d("VoyahChannel", "loaded vendor classes from service package");
        }
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
        // Reuse the cached binder; re-discover (and retry with a short backoff)
        // only when a cycle comes back empty or the binder went away.
        int count = VoyahReadPolicy.readWithRetry(() -> {
            out.clear();
            ensureReadable(ctx);
            return collect(knownItems, out);
        }, Thread::sleep);
        if (count <= 0) {
            LogBuffer.i("VoyahChannel", "read: 0 values after " + VoyahReadPolicy.MAX_READ_ATTEMPTS
                    + " attempts (path=" + binderPath + "); binder cache reset");
            invalidateBinder();
        }
        return out;
    }

    /**
     * Keys whose raw (pre-decode) value is logged each read cycle. The door
     * position encoding could not be pinned from the firmware sources (the
     * owner saw closed doors as open), so the next device log carries the raw
     * RL/RR values and settles the mapping. Cheap: one line per 2 s cycle.
     */
    private static final String[] DIAG_KEYS = {
            "rear_left_door", "rear_right_door",
            "fuel_charge_flap", "charge_port_flap",
            "outside_temp", "cabin_temp"};

    private static volatile boolean acProbed;

    /** Full raw dump of every mapped Voyah param/getter, throttled to one per interval. */
    private static final long RAW_DUMP_INTERVAL_MS = 15_000L;
    private volatile long lastRawDumpMs;

    /** Reads every known key once; returns how many produced a value. */
    private int collect(List<CANDataItem> knownItems, List<CANDataItem> out) {
        int count = 0;
        long now = System.currentTimeMillis();
        StringBuilder diag = new StringBuilder();
        for (CANDataItem item : knownItems) {
            if (item == null || item.key == null) continue;
            String raw = readRaw(item.key);
            if (raw == null) continue;
            String value = VoyahValueDecoder.decode(item.key, raw);
            item.value = value;
            item.lastUpdate = now;
            out.add(item);
            count++;
            if (isDiagKey(item.key)) {
                diag.append(item.key).append('=').append(raw).append(' ');
            }
        }
        if (diag.length() > 0) LogBuffer.d("VoyahChannel", "raw " + diag.toString().trim());
        if (cachedIface != null) logAirConditionProbe();
        maybeRawDump();
        return count;
    }

    /**
     * Dumps the RAW (pre-decode) value of every mapped Voyah param/getter to the
     * log (tag {@code VoyahDump}), throttled. Used to calibrate scales/sentinels
     * and enum encodings against the dashboard (a broken value on the site/app
     * still needs the raw reading to be fixed).
     */
    private void maybeRawDump() {
        long now = System.currentTimeMillis();
        if (now - lastRawDumpMs < RAW_DUMP_INTERVAL_MS) return;
        lastRawDumpMs = now;
        Object iface = cachedIface;
        LogBuffer.i("VoyahDump", "begin path=" + binderPath + " iface=" + (iface != null));
        for (Map.Entry<String, String> e : VOYAH_PARAMS.entrySet()) {
            String raw = null;
            if (iface != null) {
                Object v = queryState(iface, cachedIfaceClass, e.getValue());
                raw = v == null ? null : stringify(v);
            }
            LogBuffer.i("VoyahDump", e.getKey() + " " + e.getValue() + " = "
                    + (raw == null ? "null" : raw));
        }
        for (Map.Entry<String, String> e : VOYAH_METHODS.entrySet()) {
            String raw = iface == null ? null : invokeGetter(cachedIfaceClass, iface, e.getValue());
            LogBuffer.i("VoyahDump", e.getKey() + " " + e.getValue() + "() = "
                    + (raw == null ? "null" : raw));
        }
        for (Map.Entry<String, String> e : VOYAH_AC_METHODS.entrySet()) {
            String raw = iface == null ? null : readAirConditionValue(cachedIfaceClass, iface, e.getValue());
            LogBuffer.i("VoyahDump", e.getKey() + " ac:" + e.getValue() + "() = "
                    + (raw == null ? "null" : raw));
        }
        for (Map.Entry<String, int[]> e : VOYAH_RAW_TX.entrySet()) {
            int[] tx = e.getValue();
            String raw;
            if (tx[1] == RAW_FLOAT) {
                Float f = transactFloat(tx[0]);
                raw = f == null ? null : String.valueOf(f);
            } else {
                Integer i = transactInt(tx[0]);
                raw = i == null ? null : String.valueOf(i);
            }
            LogBuffer.i("VoyahDump", e.getKey() + " tx" + tx[0] + " = " + (raw == null ? "null" : raw));
        }
        LogBuffer.i("VoyahDump", "end");
    }

    private static boolean isDiagKey(String key) {
        for (String k : DIAG_KEYS) if (k.equals(key)) return true;
        return false;
    }

    /**
     * Registry key -> raw value, in order of reliability: VehicleState constant,
     * no-argument ICanBusService getter, AirCondition getter, raw transaction.
     * Decoding happens in {@link VoyahValueDecoder}, not here.
     */
    private String readRaw(String key) {
        String value = null;
        Object iface = cachedIface;
        if (TX_FIRST_KEYS.contains(key)) {
            value = readRawTx(key);
            if (value != null) return value;
        }
        if (iface != null) {
            String param = VOYAH_PARAMS.get(key);
            if (param != null) {
                Object raw = queryState(iface, cachedIfaceClass, param);
                if (raw != null) value = accept(key, stringify(raw));
            }
            if (value == null) {
                String method = VOYAH_METHODS.get(key);
                if (method != null) value = accept(key, invokeGetter(cachedIfaceClass, iface, method));
            }
            if (value == null) {
                String ac = VOYAH_AC_METHODS.get(key);
                if (ac != null) value = accept(key, readAirConditionValue(cachedIfaceClass, iface, ac));
            }
        }
        if (value == null) value = readRawTx(key);
        return value;
    }

    /** A raw getter transaction value, or null when the key has none / it is a sentinel. */
    private String readRawTx(String key) {
        int[] tx = VOYAH_RAW_TX.get(key);
        if (tx == null) return null;
        String v;
        if (tx[1] == RAW_FLOAT) {
            Float f = transactFloat(tx[0]);
            v = f == null ? null : String.valueOf(Math.round(f));
        } else {
            Integer i = transactInt(tx[0]);
            v = i == null ? null : String.valueOf(i);
        }
        return accept(key, v);
    }

    /** Drops the Voyah "unknown" sentinel (-1 / MIN_VALUE / -9999 / huge / 2550) for a key. */
    private static String accept(String key, String raw) {
        if (raw == null) return null;
        return VoyahValueDecoder.isUnavailable(key, raw) ? null : raw;
    }

    /**
     * Reads an A/C getter from the object returned by {@code getAirConditionState()}.
     * Returns null when that call yields no object (the extracted interface
     * declares an int), so the key is simply skipped instead of faked.
     */
    private static String readAirConditionValue(Class<?> ifaceClass, Object iface, String getter) {
        Object ac = invokeGetterObject(ifaceClass, iface, "getAirConditionState");
        if (ac == null || ac instanceof Number || ac instanceof CharSequence || ac instanceof Boolean) {
            return null;
        }
        return invokeGetter(ac.getClass(), ac, getter);
    }

    /** One-shot field diagnostic: is the separate AirCondition model reachable? */
    private static void logAirConditionProbe() {
        if (acProbed) return;
        acProbed = true;
        Class<?> ac = loadDeviceClass("com.qinggan.canbus.AirCondition");
        if (ac == null) {
            LogBuffer.i("VoyahChannel", "AirCondition class absent -> cabin_temp/A-C unreachable");
            return;
        }
        LogBuffer.i("VoyahChannel", "AirCondition present (getAirTempInCar="
                + hasMethod(ac, "getAirTempInCar") + ", getAirTempOutCar="
                + hasMethod(ac, "getAirTempOutCar") + "), needs getAirConditionState() object");
    }

    private static boolean hasMethod(Class<?> type, String name) {
        try {
            type.getMethod(name);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** Invokes a public no-argument method; returns its stringified result or null. */
    private static String invokeGetter(Class<?> type, Object target, String name) {
        Object v = invokeGetterObject(type, target, name);
        return v == null ? null : stringify(v);
    }

    private static Object invokeGetterObject(Class<?> type, Object target, String name) {
        try {
            Method m = type.getMethod(name);
            return m.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    /** Binds to the exported CanBusService; true when a live binder was obtained. */
    private boolean bindRaw(Context ctx, long timeoutMs) {
        if (cachedBinder != null && cachedBinder.pingBinder()) return true;
        // Try the known (package, action) combinations; bound each attempt so a
        // rejected route cannot stall the probe cycle.
        for (String[] combo : RAW_COMBOS) {
            if (tryBind(ctx, combo[0], combo[1], Math.min(timeoutMs, 3000L))) return true;
        }
        return false;
    }

    private boolean tryBind(Context ctx, String pkg, String action, long timeoutMs) {
        try {
            rawContext = ctx.getApplicationContext();
            final java.util.concurrent.CountDownLatch latch =
                    new java.util.concurrent.CountDownLatch(1);
            android.content.ServiceConnection conn = new android.content.ServiceConnection() {
                @Override public void onServiceConnected(android.content.ComponentName name, IBinder service) {
                    cachedBinder = service;
                    latch.countDown();
                }
                @Override public void onServiceDisconnected(android.content.ComponentName name) {
                    cachedBinder = null;
                }
            };
            android.content.Intent intent = new android.content.Intent(action).setPackage(pkg);
            if (!rawContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
                lastBindError = "bindService-false(" + pkg + "/" + action + ")";
                return false;
            }
            if (!latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                lastBindError = "timeout(" + pkg + "/" + action + ")";
                try { rawContext.unbindService(conn); } catch (Throwable ignored) {}
                return false;
            }
            boolean ok = cachedBinder != null && cachedBinder.pingBinder();
            if (!ok) lastBindError = "no-live-binder(" + pkg + "/" + action + ")";
            return ok;
        } catch (Exception e) {
            lastBindError = e.getClass().getSimpleName() + "(" + pkg + "/" + action + ")";
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
        IBinder b = cachedBinder;
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

    /**
     * Probes a no-argument ICanBusService getter (e.g. getAmbientTemperature)
     * for the research engine. Read-only.
     */
    public static ProbeResult readMethodParam(String method) {
        try {
            Class<?> iface = loadDeviceClass(IFACE_CLASS);
            if (iface == null) return ProbeResult.unsupported();
            Object asInterface = stubAsInterface(iface, findBinder());
            if (asInterface == null) return ProbeResult.unsupported();
            String v = invokeGetter(iface, asInterface, method);
            if (v == null) return ProbeResult.unsupported();
            return ProbeResult.fromRaw(v, false);
        } catch (Exception e) {
            return ProbeResult.error(e.getMessage());
        }
    }

    /**
     * Probes an AirCondition getter (e.g. getAirTempInCar) through the object
     * returned by getAirConditionState(). Unsupported when the interface only
     * exposes the int state (as in the firmware AIDL dump).
     */
    public static ProbeResult readAirConditionParam(String getter) {
        try {
            Class<?> iface = loadDeviceClass(IFACE_CLASS);
            if (iface == null) return ProbeResult.unsupported();
            Object asInterface = stubAsInterface(iface, findBinder());
            if (asInterface == null) return ProbeResult.unsupported();
            String v = readAirConditionValue(iface, asInterface, getter);
            if (v == null) return ProbeResult.unsupported();
            return ProbeResult.fromRaw(v, false);
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
            // listServices() can be restricted on some heads; probe known names directly.
            for (String name : SERVICE_CANDIDATES) {
                try {
                    IBinder b = (IBinder) getService.invoke(null, name);
                    if (b != null) return b;
                } catch (Throwable ignored) {}
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
        if (v instanceof Number) {
            if (com.car2hass.SentinelDecoder.isSentinelNumber((Number) v)) return null;
            return v.toString();
        }
        String s = String.valueOf(v);
        return s.isEmpty() ? null : s;
    }
}
