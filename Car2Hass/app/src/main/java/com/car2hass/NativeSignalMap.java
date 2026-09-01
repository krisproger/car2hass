package com.car2hass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static map of native-read signals: stable HA key → autoservice address +
 * decoder. Port of BYDMate {@code FidMap.kt} restricted to the signals that
 * map onto {@link CANDataReader#SIGNAL_REGISTRY} keys.
 *
 * <p>A {@link FidEntry} tells NativeReader which {@code service call autoservice}
 * transact to issue (5 = getInt, 7 = getFloat raw IEEE-754 bits) and which
 * {@link ParamDecoder} decodes the raw value into a signal value.
 *
 * <p>Two signals are not plain one-fid reads and carry extra logic:
 * <ul>
 *   <li>{@code turn_signal} — a bitmask fid (1=off, 2=left, 4=right, 6=hazard)
 *       that produces {@code left_turn} / {@code right_turn} / {@code hazard}
 *       via {@link #deriveTurnValues(int)}.</li>
 *   <li>{@code window_rr} — on DiLink 3.0 the primary fid returns a link error;
 *       {@link #getFallbackFid(String)} supplies the Gen3 fid to retry.</li>
 * </ul>
 *
 * <p>Excluded from native reading (unstable or non-reading fids confirmed in
 * real logs, 2026-08-16/17): {@code passenger_seatbelt} (fid 1042/315621439
 * flickers 227×0/237×1 on an empty seat) and {@code trunk} (fid 1001/1074790416
 * returns a sentinel every tick). Both keep their DiPlus path and are read via
 * HTTP; they are skipped by the native batch so a bad fid no longer pollutes
 * native telemetry.
 */
public final class NativeSignalMap {

    /** Single map entry: how to read + decode one signal. */
    public static final class FidEntry {
        public final String key;
        public final int device;
        public final int fid;
        public final int transact;
        public final int decoder;
        public final double scale;

        public FidEntry(String key, int device, int fid, int transact, int decoder, double scale) {
            this.key = key;
            this.device = device;
            this.fid = fid;
            this.transact = transact;
            this.decoder = decoder;
            this.scale = scale;
        }
    }

    /** Mask values of the turnSignal fid ({@code 950009900}). */
    public static final int TURN_OFF = 1;
    public static final int TURN_LEFT = 2;
    public static final int TURN_RIGHT = 4;
    public static final int TURN_HAZARD = 6;

    /** Derived keys produced from the turnSignal fid. */
    public static final String KEY_LEFT_TURN = "left_turn";
    public static final String KEY_RIGHT_TURN = "right_turn";
    public static final String KEY_HAZARD = "hazard";

    private static final Map<String, FidEntry> ENTRIES = buildEntries();
    private static final Map<String, FidEntry> FALLBACKS = buildFallbacks();

    private static Map<String, FidEntry> buildEntries() {
        Map<String, FidEntry> m = new LinkedHashMap<>();
        add(m, "soc", 1014, 1246777400, 7, ParamDecoder.FLOAT_PERCENT, 1.0);
        add(m, "speed", 1013, -1807745016, 7, ParamDecoder.FLOAT_KW, 1.0);
        add(m, "range", 1014, 1246765072, 5, ParamDecoder.INT_SCALED, 0.1);
        add(m, "gear", 1011, 555745336, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "engine_power", 1012, 339738656, 5, ParamDecoder.INT_RAW, 1.0);
        add(m, "charge_gun_state", 1009, 876609586, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "total_energy", 1014, 1032871984, 7, ParamDecoder.FLOAT_KWH, 1.0);
        add(m, "battery_voltage", 1001, 1128267816, 7, ParamDecoder.FLOAT_VOLT, 1.0);
        add(m, "battery_temp_max", 1014, 1148190752, 5, ParamDecoder.INT_TEMP_C_OFS40, 1.0);
        add(m, "battery_temp_min", 1014, 1148190736, 5, ParamDecoder.INT_TEMP_C_OFS40, 1.0);
        add(m, "cell_voltage_max", 1014, 1147142192, 5, ParamDecoder.INT_SCALED, 0.001);
        add(m, "cell_voltage_min", 1014, 1147142160, 5, ParamDecoder.INT_SCALED, 0.001);
        add(m, "cabin_temp", 1000, 1031798832, 5, ParamDecoder.INT_TEMP_C, 1.0);
        add(m, "outside_temp", 1000, 1077936184, 5, ParamDecoder.INT_TEMP_C, 1.0);
        add(m, "ac_set_temp", 1000, 1077936168, 5, ParamDecoder.INT_TEMP_C, 1.0);
        add(m, "ac_state", 1000, 1077936144, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "fan_speed", 1000, 1077936156, 5, ParamDecoder.INT_RAW, 1.0);
        add(m, "ac_recirculation", 1000, 1077936148, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "driver_seatbelt", 1007, 692060184, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "tyre_pressure_fl", 1016, -1728052956, 5, ParamDecoder.INT_KPA, 1.0);
        add(m, "tyre_pressure_fr", 1016, -1728052952, 5, ParamDecoder.INT_KPA, 1.0);
        add(m, "tyre_pressure_rl", 1016, -1728052948, 5, ParamDecoder.INT_KPA, 1.0);
        add(m, "tyre_pressure_rr", 1016, -1728052944, 5, ParamDecoder.INT_KPA, 1.0);
        add(m, "low_beam", 1004, 950009866, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "drl", 1004, 1231040528, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "sidelights", 1004, 950009864, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "high_beam", 1004, 950009868, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "driver_door", 1001, 692060168, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "passenger_door", 1001, 692060170, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "rear_left_door", 1001, 692060172, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "rear_right_door", 1001, 692060174, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "window_fl", 1001, 947912728, 5, ParamDecoder.INT_PERCENT, 1.0);
        add(m, "window_fr", 1001, 1267728400, 5, ParamDecoder.INT_PERCENT, 1.0);
        add(m, "window_rl", 1001, 947912736, 5, ParamDecoder.INT_PERCENT, 1.0);
        add(m, "window_rr", 1001, 947912752, 5, ParamDecoder.INT_PERCENT, 1.0);
        add(m, "sunroof", 1001, 1101004808, 5, ParamDecoder.INT_PERCENT, 1.0);
        add(m, "bonnet", 1001, 692060188, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "driver_door_lock", 1032, 1081081864, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "drive_mode", 1006, 555745294, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "powertrain_mode", 1006, 874512420, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "power_state", 1023, 315621408, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "charging_state", 1009, 876609560, 5, ParamDecoder.INT_ENUM, 1.0);
        add(m, "turn_signal", 1004, 950009900, 5, ParamDecoder.INT_ENUM, 1.0);
        return m;
    }

    private static Map<String, FidEntry> buildFallbacks() {
        Map<String, FidEntry> m = new LinkedHashMap<>();
        m.put("window_rr", new FidEntry("window_rr", 1001, 1267728408, 5, ParamDecoder.INT_PERCENT, 1.0));
        return m;
    }

    private static void add(Map<String, FidEntry> m, String key, int device, int fid,
                            int transact, int decoder, double scale) {
        m.put(key, new FidEntry(key, device, fid, transact, decoder, scale));
    }

    private NativeSignalMap() {}

    /** All primary entries (keys → addresses). Order is the read batch order. */
    public static List<FidEntry> allEntries() {
        return new ArrayList<>(ENTRIES.values());
    }

    /** Primary entry for a key, or null. */
    public static FidEntry get(String key) {
        return ENTRIES.get(key);
    }

    /**
     * Fallback entry for {@code window_rr} (Gen3 fid) to retry when the primary
     * fid returns a link error, or null for keys without a fallback.
     */
    public static FidEntry getFallbackFid(String key) {
        return FALLBACKS.get(key);
    }

    /**
     * Decodes the turnSignal mask into the three derived signals.
     * Returns an empty map for a sentinel/invalid mask.
     *
     * @return {@code left_turn}/{@code right_turn} as 0 (off) or 1 (on);
     *         {@code hazard} as 1 (off) or 2 (on, matches {@code hazard} enum).
     */
    public static Map<String, Integer> deriveTurnValues(int mask) {
        Map<String, Integer> out = new LinkedHashMap<>();
        Integer cleaned = SentinelDecoder.decodeInt(mask);
        if (cleaned == null) {
            return out;
        }
        out.put(KEY_LEFT_TURN, (cleaned & TURN_LEFT) != 0 ? 1 : 0);
        out.put(KEY_RIGHT_TURN, (cleaned & TURN_RIGHT) != 0 ? 1 : 0);
        out.put(KEY_HAZARD, (cleaned & TURN_HAZARD) == TURN_HAZARD ? 2 : 1);
        return out;
    }
}
