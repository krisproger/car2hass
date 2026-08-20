package com.diplustohass;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Translates raw values returned by the diplus API into stable English labels
 * expected by the Home Assistant integration.
 *
 * <p>diplus may return either Chinese text labels (when status=true) or numeric
 * indices (especially for batch reads). This class maps both forms to the same
 * English label set defined in signals.yaml.
 */
public class SignalTranslator {

    // ─── Chinese text → English label (generated from signals.yaml) ───
    private static final HashMap<String, String> VALUE_TRANS = new HashMap<>();
    static {
        // AUTO-GENERATED VALUE TRANS START
        VALUE_TRANS.put("关闭", "off");
        VALUE_TRANS.put("打开", "on");
        VALUE_TRANS.put("已系", "buckled");
        VALUE_TRANS.put("未系", "unbuckled");
        VALUE_TRANS.put("行车", "driving");
        VALUE_TRANS.put("上电", "on");
        VALUE_TRANS.put("断开", "disconnected");
        VALUE_TRANS.put("已连接", "connected");
        VALUE_TRANS.put("解锁", "unlocked");
        VALUE_TRANS.put("未锁", "unlocked");
        VALUE_TRANS.put("开启", "on");
        VALUE_TRANS.put("未显示", "not shown");
        VALUE_TRANS.put("停止", "stopped");
        VALUE_TRANS.put("开启缩时哨兵", "timelapse sentry on");
        VALUE_TRANS.put("开启中", "starting");
        VALUE_TRANS.put("无报警", "no alarm");
        VALUE_TRANS.put("开启熄火录制", "parked recording on");
        VALUE_TRANS.put("开启熄火哨兵", "engine-off sentry on");
        VALUE_TRANS.put("报警中", "alarming");
        VALUE_TRANS.put("无目标", "no target");
        VALUE_TRANS.put("未移动", "not moving");
        VALUE_TRANS.put("晴天", "sunny");
        VALUE_TRANS.put("摄氏度", "°C");
        VALUE_TRANS.put("华氏度", "°F");
        VALUE_TRANS.put("外循环", "external");
        VALUE_TRANS.put("内循环", "recirculation");
        VALUE_TRANS.put("吹面", "face");
        VALUE_TRANS.put("吹脚", "feet");
        VALUE_TRANS.put("未生效", "inactive");
        VALUE_TRANS.put("禁用", "disabled");
        VALUE_TRANS.put("正常", "normal");
        VALUE_TRANS.put("无告警", "no alert");
        VALUE_TRANS.put("无效", "invalid");
        VALUE_TRANS.put("开", "on");
        VALUE_TRANS.put("关", "off");
        VALUE_TRANS.put("已开启", "on");
        VALUE_TRANS.put("已关闭", "off");
        VALUE_TRANS.put("强制EV", "forced EV");
        VALUE_TRANS.put("直流枪", "DC gun");
        VALUE_TRANS.put("激活", "active");
        VALUE_TRANS.put("激活2", "active2");
        VALUE_TRANS.put("吹面吹脚", "face+feet");
        VALUE_TRANS.put("锁定", "locked");
        VALUE_TRANS.put("已锁", "locked");
        VALUE_TRANS.put("锁", "locked");
        VALUE_TRANS.put("充电", "charging");
        VALUE_TRANS.put("放电", "discharging");
        VALUE_TRANS.put("充满", "full");
        VALUE_TRANS.put("空闲", "idle");
        VALUE_TRANS.put("暖机", "warming up");
        VALUE_TRANS.put("运行", "running");
        VALUE_TRANS.put("运行中", "running");
        VALUE_TRANS.put("报警", "alarm");
        VALUE_TRANS.put("在线", "online");
        VALUE_TRANS.put("离线", "offline");
        VALUE_TRANS.put("是", "yes");
        VALUE_TRANS.put("否", "no");
        VALUE_TRANS.put("未定义", "undefined");
        VALUE_TRANS.put("双闪", "hazard");
        VALUE_TRANS.put("左转", "left");
        VALUE_TRANS.put("右转", "right");
        VALUE_TRANS.put("雨天", "rain");
        VALUE_TRANS.put("未连接", "disconnected");
        VALUE_TRANS.put("显示中", "shown");
        VALUE_TRANS.put("待激活", "pending");
        VALUE_TRANS.put("P", "P");
        VALUE_TRANS.put("R", "R");
        VALUE_TRANS.put("N", "N");
        VALUE_TRANS.put("D", "D");
        VALUE_TRANS.put("M", "M");
        VALUE_TRANS.put("S", "S");
        VALUE_TRANS.put("有车接近", "car approaching");
        VALUE_TRANS.put("交流枪", "AC gun");
        VALUE_TRANS.put("转换枪", "adapter gun");
        VALUE_TRANS.put("放电枪", "discharge gun");
        VALUE_TRANS.put("开始", "started");
        VALUE_TRANS.put("完成", "done");
        VALUE_TRANS.put("终止", "aborted");
        VALUE_TRANS.put("激活1", "active1");
        VALUE_TRANS.put("错误", "error");
        VALUE_TRANS.put("状态3", "state3");
        VALUE_TRANS.put("取消或无效", "cancelled");
        VALUE_TRANS.put("状态4", "state4");
        VALUE_TRANS.put("主动起步", "auto-start");
        VALUE_TRANS.put("除霜", "defrost");
        VALUE_TRANS.put("吹脚除霜", "feet+defrost");
        VALUE_TRANS.put("吹面+吹脚+除霜", "face+feet+defrost");
        VALUE_TRANS.put("吹面+除霜", "face+defrost");
        VALUE_TRANS.put("存储异常", "storage error");
        VALUE_TRANS.put("左转2", "left2");
        VALUE_TRANS.put("右转2", "right2");
        VALUE_TRANS.put("紧急", "emergency");
        VALUE_TRANS.put("后闪灯", "rear flash");
        VALUE_TRANS.put("闪灯", "flash");
        // AUTO-GENERATED VALUE TRANS END
    }

    // ─── Enum numeric index → English label (generated from signals.yaml) ───
    // Format for each key: "idx:label,idx:label,..."
    private static final HashMap<String, String> ENUM_LABELS = new HashMap<>();
    static {
        // AUTO-GENERATED ENUM LABELS START
        ENUM_LABELS.put("power_state", "0:off,1:on,2:driving");
        ENUM_LABELS.put("gear", "0:—,1:P,2:R,3:N,4:D,5:M,6:S");
        ENUM_LABELS.put("charge_gun_state", "0:—,1:disconnected,2:AC gun,3:DC gun,4:adapter gun,5:discharge gun");
        ENUM_LABELS.put("weather", "0:clear,1:rain");
        ENUM_LABELS.put("driver_seatbelt", "0:unbuckled,1:buckled,2:invalid");
        ENUM_LABELS.put("remote_lock_state", "0:unlocked,1:locked");
        ENUM_LABELS.put("temp_unit", "0:°F,1:°C");
        ENUM_LABELS.put("charging_state", "0:invalid,1:Ready,2:started,3:done,4:aborted");
        ENUM_LABELS.put("left_turn", "0:off,1:on");
        ENUM_LABELS.put("right_turn", "0:off,1:on");
        ENUM_LABELS.put("driver_door_lock", "0:—,1:unlocked,2:locked");
        ENUM_LABELS.put("powertrain_mode", "0:stop,1:EV,2:forced EV,3:HEV");
        ENUM_LABELS.put("drive_mode", "0:NORMAL,1:ECO,2:SPORT");
        ENUM_LABELS.put("passenger_seatbelt_warning", "0:—,1:alarm,2:normal");
        ENUM_LABELS.put("seatbelt_2nd_left", "0:unbuckled,1:buckled,2:invalid");
        ENUM_LABELS.put("seatbelt_2nd_right", "0:unbuckled,1:buckled,2:invalid");
        ENUM_LABELS.put("seatbelt_2nd_center", "0:unbuckled,1:buckled,2:invalid");
        ENUM_LABELS.put("ac_state", "0:off,1:on");
        ENUM_LABELS.put("ac_recirculation", "0:fresh,1:recirc");
        ENUM_LABELS.put("ac_airflow_mode", "0:—,1:face,2:face+feet,3:feet,4:feet+defrost,5:defrost,6:face+feet+defrost,7:face+defrost");
        ENUM_LABELS.put("driver_door", "0:closed,1:open");
        ENUM_LABELS.put("passenger_door", "0:closed,1:open");
        ENUM_LABELS.put("rear_left_door", "0:closed,1:open");
        ENUM_LABELS.put("rear_right_door", "0:closed,1:open");
        ENUM_LABELS.put("bonnet", "0:closed,1:open");
        ENUM_LABELS.put("trunk", "0:closed,1:open");
        ENUM_LABELS.put("fuel_charge_flap", "0:closed,1:open");
        ENUM_LABELS.put("auto_hold", "0:disabled,1:pending,2:active,3:state3");
        ENUM_LABELS.put("acc_cruise_state", "0:disabled,1:cancelled,2:pending,3:active,4:state4,5:auto-start");
        ENUM_LABELS.put("rear_left_approach_warning", "0:no warning,1:car approaching,2:alarm");
        ENUM_LABELS.put("rear_right_approach_warning", "0:no warning,1:car approaching,2:alarm");
        ENUM_LABELS.put("lane_keep_state", "0:off,1:inactive,2:active1,3:active2,4:error");
        ENUM_LABELS.put("rear_left_door_lock", "0:invalid,1:unlocked,2:locked");
        ENUM_LABELS.put("passenger_door_lock", "0:invalid,1:unlocked,2:locked");
        ENUM_LABELS.put("rear_right_door_lock", "0:invalid,1:unlocked,2:locked");
        ENUM_LABELS.put("trunk_lock", "0:invalid,1:unlocked,2:locked");
        ENUM_LABELS.put("rear_left_child_lock", "0:invalid,1:unlocked,2:locked");
        ENUM_LABELS.put("rear_right_child_lock", "0:invalid,1:unlocked,2:locked");
        ENUM_LABELS.put("sidelights", "0:off,1:open");
        ENUM_LABELS.put("low_beam", "0:off,1:open");
        ENUM_LABELS.put("high_beam", "0:off,1:open");
        ENUM_LABELS.put("front_fog", "0:off,1:open");
        ENUM_LABELS.put("rear_fog", "0:off,1:open");
        ENUM_LABELS.put("footwell_light", "0:off,1:open");
        ENUM_LABELS.put("drl", "0:invalid,1:open,2:off,3:未定义");
        ENUM_LABELS.put("hazard", "0:invalid,1:off,2:open");
        ENUM_LABELS.put("passenger_seatbelt", "0:unbuckled,1:buckled,2:invalid");
        ENUM_LABELS.put("turn_signal", "0:off,1:off,2:left,3:left2,4:right,5:right2,6:hazard,7:emergency,8:rear flash,9:flash");
        ENUM_LABELS.put("surround_view_state", "0:hidden,1:shown");
        ENUM_LABELS.put("ui_config_version", "0:UI3,1:UI4");
        ENUM_LABELS.put("wifi_state", "0:disconnected,1:connected");
        ENUM_LABELS.put("bluetooth_state", "0:disconnected,1:connected");
        ENUM_LABELS.put("dashcam_state", "0:stop,1:starting,2:running,3:storage error");
        ENUM_LABELS.put("wireless_adb_switch", "0:off,1:on");
        ENUM_LABELS.put("online", "0:off,1:on");
        // AUTO-GENERATED ENUM LABELS END
    }

    // Keys whose values are free-form identifiers / firmware strings and should
    // not produce "untranslated" warnings.
    private static final Set<String> FREE_TEXT_KEYS = new HashSet<>(Arrays.asList(
            "vvin", "firmware",
            "ro_vehicle_type", "ro_vehicle_type_value", "ro_car_protocol",
            "sys_tcp_client_ver",
            "persist_sys_byd_default_name", "persist_sys_byd_bluetooth_name",
            "persist_sys_byd_theme", "persist_sys_vehicle_40d_code",
            "persist_sys_vehicle_sales_record", "persist_sys_vehicle_rudder_info",
            "vehicle_config_map", "sys_byd_cdr_recording", "sys_byd_pano"
    ));

    // Numeric sensors may return values that are not Chinese labels and should
    // not produce "untranslated" warnings.
    //   ∞  = infinity (e.g. energy_per_100km while the car is parked)
    //   NaN / - / — / N/A = numeric "no value" markers
    private static final Set<String> KNOWN_NUMERIC_VALUES = new HashSet<>(Arrays.asList(
            "∞", "+∞", "-∞", "NaN", "-", "—", "N/A"
    ));

    /** Translate a Chinese value string to English, or return the original value. */
    public static String translateValue(String value) {
        if (value == null) return "---";
        String t = VALUE_TRANS.get(value);
        if (t != null) return t;
        return value;
    }

    /**
     * Translate a Chinese value string to English for a specific signal key.
     * Some binary sensors (doors, bonnet, trunk) should use closed/open instead
     * of the generic off/on mapping used for lights.
     */
    public static String translateValueForKey(String key, String value) {
        if (value == null) return "---";
        if (isOpenableSensor(key)) {
            if ("关闭".equals(value)) return "closed";
            if ("打开".equals(value)) return "open";
        }
        return translateValue(value);
    }

    private static boolean isOpenableSensor(String key) {
        if (key == null) return false;
        return key.equals("driver_door") || key.equals("passenger_door")
                || key.equals("rear_left_door") || key.equals("rear_right_door")
                || key.equals("bonnet") || key.equals("trunk")
                || key.equals("fuel_charge_flap");
    }

    /**
     * Translate a raw enum value for a specific signal key.
     *
     * <p>Tries Chinese text translation first, then numeric index mapping using
     * the registry labels. Falls back to the raw value if no mapping exists.
     */
    public static String translateEnumValue(String key, String rawValue) {
        if (rawValue == null) return "---";

        // Numeric values are handled by enum labels or returned as-is for
        // continuous sensors; do not pass them through the Chinese text map.
        String trimmed = rawValue.trim();
        boolean isNumeric = trimmed.matches("-?\\d+(\\.\\d+)?");

        String labels = ENUM_LABELS.get(key);
        if (labels != null && !labels.isEmpty()) {
            boolean parsedAsNumber = false;
            try {
                int idx = Integer.parseInt(trimmed);
                parsedAsNumber = true;
                for (String part : labels.split(",")) {
                    String[] kv = part.split(":", 2);
                    if (kv.length != 2) continue;
                    if (Integer.parseInt(kv[0].trim()) == idx) {
                        return kv[1].trim();
                    }
                }
            } catch (NumberFormatException ignored) {
            }

            // diplus sometimes returns the English label itself instead of the
            // numeric index (e.g. charging_state -> "Ready"). Accept it if it
            // matches a known label for this key.
            if (!parsedAsNumber) {
                for (String part : labels.split(",")) {
                    String[] kv = part.split(":", 2);
                    if (kv.length != 2) continue;
                    String label = kv[1].trim();
                    if (label.equalsIgnoreCase(trimmed)) {
                        return label;
                    }
                }
            }
        }

        // Try key-aware Chinese text translation (e.g. doors -> closed/open).
        String text = translateValueForKey(key, rawValue);
        if (!text.equals(rawValue)) {
            return text;
        }

        // Free-form identifiers / firmware strings are expected to stay as-is.
        if (FREE_TEXT_KEYS.contains(key)) {
            return text;
        }

        // Numeric sensors may report known non-Chinese values such as infinity
        // (valid numeric result, e.g. consumption while parked) or NaN / —.
        // Keep the raw value and do not log a warning.
        if (KNOWN_NUMERIC_VALUES.contains(trimmed)) {
            return text;
        }

        // Virtual geo sensors report English zone states ('inside'/'outside')
        // directly; keep the raw value and do not log a warning.
        if (key != null && key.startsWith("geo_")) {
            return text;
        }

        // Only warn for genuinely unexpected non-numeric strings.
        if (!isNumeric) {
            LogBuffer.w("SignalTranslator", "Untranslated value from diplus for " + key + ": '" + rawValue + "'");
        }
        return text;
    }

    /** Return true if a translated value represents an "off"/inactive state. */
    public static boolean isOffState(String translatedValue) {
        if (translatedValue == null) return false;
        String lower = translatedValue.toLowerCase(Locale.US);
        return lower.equals("off") || lower.equals("offline") || lower.equals("inactive")
                || lower.equals("disabled") || lower.equals("stopped");
    }

    /**
     * Return true when {@code "invalid"} is a defined enum state for the sensor.
     *
     * <p>Sensors that report {@code invalid} when the feature is simply not
     * applicable (child locks, hazard, DRL, door locks) should not make command
     * verification fail — the sensor is alive but the state is not applicable.</p>
     */
    public static boolean hasInvalidState(String sensorKey) {
        String labels = ENUM_LABELS.get(sensorKey);
        if (labels == null || labels.isEmpty()) return false;
        for (String part : labels.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2 && "invalid".equals(kv[1].trim())) {
                return true;
            }
        }
        return false;
    }

    // Package-private helper used by the generator.
    static Map<String, String> getEnumLabels() {
        return ENUM_LABELS;
    }
}
