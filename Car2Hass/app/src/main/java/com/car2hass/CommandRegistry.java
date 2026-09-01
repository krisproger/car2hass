package com.car2hass;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Registry of DiPlus commands that can be triggered from Home Assistant.
 *
 * <p>Each command has a stable English {@code id} used in HA, a user-facing
 * display name and category resolved from string resources, and a Chinese
 * template that is sent to the DiPlus HTTP API ({@code /api/sendCmd?cmd=...}).
 * Value placeholders use {@code {value}}.</p>
 */
public class CommandRegistry {

    public enum ValueType {
        NONE,    // command takes no value
        NUMBER,  // free numeric input
        RANGE,   // percentage 0-100
        ENUM,    // one of predefined values
        STRING   // free text
    }

    public static class CommandEntry {
        public final String id;
        public final int displayNameResId;
        public final int categoryResId;
        public final String commandTemplate;
        public final String offTemplate;
        public final ValueType valueType;
        public final double minValue;
        public final double maxValue;
        public final Map<String, String> enumValues; // value id -> Chinese suffix
        public final String valueUnit;
        public final int valueHintResId;

        public CommandEntry(String id, int displayNameResId, int categoryResId,
                            String template, ValueType valueType,
                            double minValue, double maxValue,
                            Map<String, String> enumValues,
                            String valueUnit, int valueHintResId) {
            this(id, displayNameResId, categoryResId, template, null, valueType,
                minValue, maxValue, enumValues, valueUnit, valueHintResId);
        }

        public CommandEntry(String id, int displayNameResId, int categoryResId,
                            String template, String offTemplate, ValueType valueType,
                            double minValue, double maxValue,
                            Map<String, String> enumValues,
                            String valueUnit, int valueHintResId) {
            this.id = id;
            this.displayNameResId = displayNameResId;
            this.categoryResId = categoryResId;
            this.commandTemplate = template;
            this.offTemplate = offTemplate;
            this.valueType = valueType;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.enumValues = enumValues != null ? enumValues : Collections.emptyMap();
            this.valueUnit = valueUnit;
            this.valueHintResId = valueHintResId;
        }

        public String getDisplayName(Context ctx) {
            return ctx.getString(displayNameResId);
        }

        public String getCategory(Context ctx) {
            return ctx.getString(categoryResId);
        }

        public String getValueHint(Context ctx) {
            if (valueHintResId != 0) {
                return ctx.getString(valueHintResId);
            }
            return "";
        }

        public boolean needsValue() {
            return valueType != ValueType.NONE;
        }

        @Override
        public String toString() {
            // The default toString is used by ArrayAdapter when no custom
            // getView is set. We cannot resolve resources without a Context here,
            // so callers should use getDisplayName(Context) for UI.
            return id;
        }
    }

    private static final List<CommandEntry> COMMANDS = new ArrayList<>();

    private static final Pattern CHARGE_SCHEDULE = Pattern.compile("\\d{1,2}:\\d{1,2}-\\d{1,2}");

    static {
        // ─── Climate ───
        add("ac_on", R.string.cmd_ac_on, R.string.cmd_category_climate, "迪加空调自动", ValueType.NONE);
        add("ac_off", R.string.cmd_ac_off, R.string.cmd_category_climate, "迪加关空调", ValueType.NONE);
        add("ac_auto", R.string.cmd_ac_auto, R.string.cmd_category_climate, "迪加空调自动", ValueType.NONE);
        add("ac_temp", R.string.cmd_ac_temp, R.string.cmd_category_climate, "迪加设置温度{value}",
            ValueType.NUMBER, 17, 30, null, R.string.unit_celsius, R.string.hint_celsius);
        add("ac_temp_up", R.string.cmd_ac_temp_up, R.string.cmd_category_climate, "迪加空调升温", ValueType.NONE);
        add("ac_temp_down", R.string.cmd_ac_temp_down, R.string.cmd_category_climate, "迪加空调降温", ValueType.NONE);
        add("ac_fan", R.string.cmd_ac_fan, R.string.cmd_category_climate, "迪加设置风速{value}",
            ValueType.NUMBER, 1, 7, null, R.string.unit_percent, R.string.hint_fan);
        add("ac_fan_up", R.string.cmd_ac_fan_up, R.string.cmd_category_climate, "迪加升风速", ValueType.NONE);
        add("ac_fan_down", R.string.cmd_ac_fan_down, R.string.cmd_category_climate, "迪加降风速", ValueType.NONE);
        addEnum("ac_airflow", R.string.cmd_ac_airflow, R.string.cmd_category_climate, "迪加空调{value}",
            map("face", R.string.enum_face, "吹面",
                "face_feet", R.string.enum_face_feet, "吹面吹脚",
                "feet", R.string.enum_feet, "吹脚",
                "feet_defrost", R.string.enum_feet_defrost, "吹脚除霜",
                "defrost", R.string.enum_defrost, "除霜",
                "face_feet_defrost", R.string.enum_face_feet_defrost, "吹面吹脚除霜",
                "face_defrost", R.string.enum_face_defrost, "吹面除霜"));
        addEnum("ac_recirc", R.string.cmd_ac_recirc, R.string.cmd_category_climate, "迪加{value}",
            map("recirc", R.string.enum_recirc, "内循环",
                "fresh", R.string.enum_fresh, "外循环"));
        addEnumWithOff("driver_seat_heat", R.string.cmd_driver_seat_heat, R.string.cmd_category_climate,
            "迪加主驾座椅加热{value}", "迪加关闭主驾座椅加热",
            map("off", R.string.enum_off, "关闭",
                "low", R.string.enum_low, "1档",
                "high", R.string.enum_high, "2档"));
        addEnumWithOff("passenger_seat_heat", R.string.cmd_passenger_seat_heat, R.string.cmd_category_climate,
            "迪加副驾座椅加热{value}", "迪加关闭副驾座椅加热",
            map("off", R.string.enum_off, "关闭",
                "low", R.string.enum_low, "1档",
                "high", R.string.enum_high, "2档"));
        addEnumWithOff("driver_seat_vent", R.string.cmd_driver_seat_vent, R.string.cmd_category_climate,
            "迪加主驾座椅通风{value}", "迪加关闭主驾座椅通风",
            map("off", R.string.enum_off, "关闭",
                "low", R.string.enum_low, "1档",
                "high", R.string.enum_high, "2档"));
        addEnumWithOff("passenger_seat_vent", R.string.cmd_passenger_seat_vent, R.string.cmd_category_climate,
            "迪加副驾座椅通风{value}", "迪加关闭副驾座椅通风",
            map("off", R.string.enum_off, "关闭",
                "low", R.string.enum_low, "1档",
                "high", R.string.enum_high, "2档"));
        add("steering_heat_on", R.string.cmd_steering_heat_on, R.string.cmd_category_climate, "迪加开启方向盘加热", ValueType.NONE);
        add("steering_heat_off", R.string.cmd_steering_heat_off, R.string.cmd_category_climate, "迪加关闭方向盘加热", ValueType.NONE);
        add("mirror_heat_on", R.string.cmd_mirror_heat_on, R.string.cmd_category_climate, "迪加后视镜加热", ValueType.NONE);
        add("mirror_heat_off", R.string.cmd_mirror_heat_off, R.string.cmd_category_climate, "迪加关闭后视镜加热", ValueType.NONE);
        add("front_defrost_on", R.string.cmd_front_defrost_on, R.string.cmd_category_climate, "迪加吹前挡", ValueType.NONE);
        add("front_defrost_off", R.string.cmd_front_defrost_off, R.string.cmd_category_climate, "迪加关闭吹前挡", ValueType.NONE);

        // ─── Windows ───
        addRange("window_driver", R.string.cmd_window_driver, R.string.cmd_category_windows, "迪加主驾打开百分之{value}");
        addRange("window_passenger", R.string.cmd_window_passenger, R.string.cmd_category_windows, "迪加副驾打开百分之{value}");
        addRange("window_rear_left", R.string.cmd_window_rear_left, R.string.cmd_category_windows, "迪加左后打开百分之{value}");
        addRange("window_rear_right", R.string.cmd_window_rear_right, R.string.cmd_category_windows, "迪加右后打开百分之{value}");
        addRange("sunroof", R.string.cmd_sunroof, R.string.cmd_category_windows, "迪加天窗打开百分之{value}");
        addRange("sunshade", R.string.cmd_sunshade, R.string.cmd_category_windows, "迪加遮阳帘打开百分之{value}");
        add("windows_close_all", R.string.cmd_windows_close_all, R.string.cmd_category_windows, "迪加一键关窗", ValueType.NONE);
        add("windows_vent", R.string.cmd_windows_vent, R.string.cmd_category_windows, "迪加一键通风", ValueType.NONE);

        // ─── Doors / locks ───
        add("doors_unlock", R.string.cmd_doors_unlock, R.string.cmd_category_doors, "迪加车门解锁", ValueType.NONE);
        add("doors_lock", R.string.cmd_doors_lock, R.string.cmd_category_doors, "迪加车门上锁", ValueType.NONE);
        add("trunk_open", R.string.cmd_trunk_open, R.string.cmd_category_doors, "迪加开后备箱", ValueType.NONE);
        add("trunk_close", R.string.cmd_trunk_close, R.string.cmd_category_doors, "迪加关后备箱", ValueType.NONE);
        addEnum("child_lock_left", R.string.cmd_child_lock_left, R.string.cmd_category_doors, "迪加{value}",
            map("on", R.string.enum_on, "打开左童锁",
                "off", R.string.enum_off, "关闭左童锁"));
        addEnum("child_lock_right", R.string.cmd_child_lock_right, R.string.cmd_category_doors, "迪加{value}",
            map("on", R.string.enum_on, "打开右童锁",
                "off", R.string.enum_off, "关闭右童锁"));

        // ─── Lights ───
        add("hazard_on", R.string.cmd_hazard_on, R.string.cmd_category_lights, "迪加双闪", ValueType.NONE);
        add("hazard_off", R.string.cmd_hazard_off, R.string.cmd_category_lights, "迪加关双闪", ValueType.NONE);
        addEnum("turn_signal", R.string.cmd_turn_signal, R.string.cmd_category_lights, "迪加{value}",
            map("left", R.string.enum_left, "左闪灯",
                "right", R.string.enum_right, "右闪灯"));
        add("fog_on", R.string.cmd_fog_on, R.string.cmd_category_lights, "迪加打开雾灯", ValueType.NONE);
        add("fog_off", R.string.cmd_fog_off, R.string.cmd_category_lights, "迪加关闭雾灯", ValueType.NONE);
        add("drl_on", R.string.cmd_drl_on, R.string.cmd_category_lights, "迪加打开日行灯", ValueType.NONE);
        add("drl_off", R.string.cmd_drl_off, R.string.cmd_category_lights, "迪加关闭日行灯", ValueType.NONE);
        add("interior_light_on", R.string.cmd_interior_light_on, R.string.cmd_category_lights, "迪加打开车内灯", ValueType.NONE);
        add("interior_light_off", R.string.cmd_interior_light_off, R.string.cmd_category_lights, "迪加关闭车内灯", ValueType.NONE);
        add("ambilight_on", R.string.cmd_ambilight_on, R.string.cmd_category_lights, "迪加打开氛围灯", ValueType.NONE);
        add("ambilight_off", R.string.cmd_ambilight_off, R.string.cmd_category_lights, "迪加关闭氛围灯", ValueType.NONE);
        add("auto_high_beam_on", R.string.cmd_auto_high_beam_on, R.string.cmd_category_lights, "迪加打开自动远光", ValueType.NONE);
        add("auto_high_beam_off", R.string.cmd_auto_high_beam_off, R.string.cmd_category_lights, "迪加关闭自动远光", ValueType.NONE);

        // ─── Drive modes ───
        addEnum("powertrain_mode", R.string.cmd_powertrain_mode, R.string.cmd_category_modes, "迪加切换{value}",
            map("hev", R.string.enum_hev, "HEV",
                "ev", R.string.enum_ev, "EV",
                "force_ev", R.string.enum_force_ev, "强制EV"));
        addEnum("drive_mode", R.string.cmd_drive_mode, R.string.cmd_category_modes, "迪加{value}模式",
            map("eco", R.string.enum_eco, "ECO",
                "sport", R.string.enum_sport, "SPORT",
                "normal", R.string.enum_normal, "NORMAL",
                "snow", R.string.enum_snow, "雪地"));
        addEnum("charge_save", R.string.cmd_charge_save, R.string.cmd_category_modes, "迪加{value}",
            map("smart", R.string.enum_smart, "智能保电",
                "force", R.string.enum_force, "强制保电"));
        addEnum("regen", R.string.cmd_regen, R.string.cmd_category_modes, "迪加能量回馈{value}",
            map("standard", R.string.enum_standard, "标准",
                "high", R.string.enum_high, "较大"));
        addEnum("steering_assist", R.string.cmd_steering_assist, R.string.cmd_category_modes, "迪加转向助力{value}",
            map("comfort", R.string.enum_comfort, "舒适",
                "sport", R.string.enum_sport, "运动"));
        addEnum("brake_assist", R.string.cmd_brake_assist, R.string.cmd_category_modes, "迪加制动助力{value}",
            map("standard", R.string.enum_standard, "标准",
                "comfort", R.string.enum_comfort, "舒适"));
        addEnum("active_brake", R.string.cmd_active_brake, R.string.cmd_category_modes, "迪加{value}",
            map("on", R.string.enum_on, "打开主动刹车",
                "off", R.string.enum_off, "关闭主动刹车"));

        // ─── Charging ───
        add("charge_soc", R.string.cmd_charge_soc, R.string.cmd_category_charging, "迪加设置SOC{value}",
            ValueType.NUMBER, 15, 70, null, R.string.unit_percent, R.string.hint_range_0_100);
        add("charge_schedule", R.string.cmd_charge_schedule, R.string.cmd_category_charging, "迪加预约充电{value}",
            ValueType.STRING, 0, 0, null, 0, R.string.hint_schedule);

        // ─── Volume ───
        add("volume", R.string.cmd_volume, R.string.cmd_category_volume, "迪加设置音量{value}",
            ValueType.NUMBER, 0, 100, null, R.string.unit_percent, R.string.hint_range_0_100);
        add("volume_up", R.string.cmd_volume_up, R.string.cmd_category_volume, "迪加加音量", ValueType.NONE);
        add("volume_down", R.string.cmd_volume_down, R.string.cmd_category_volume, "迪加降音量", ValueType.NONE);
        add("nav_volume", R.string.cmd_nav_volume, R.string.cmd_category_volume, "迪加设置导航音量{value}",
            ValueType.NUMBER, 0, 10, null, 0, R.string.hint_nav_volume);
        add("ext_volume", R.string.cmd_ext_volume, R.string.cmd_category_volume, "迪加设置车外音量{value}",
            ValueType.NUMBER, 0, 99, null, R.string.unit_percent, R.string.hint_range_0_100);

        // ─── Sentry / dashcam ───
        addEnum("sentry", R.string.cmd_sentry, R.string.cmd_category_sentry, "迪加{value}",
            map("engine_off_on", R.string.enum_engine_off_on, "打开熄火哨兵",
                "time_lapse_on", R.string.enum_time_lapse_on, "打开缩时哨兵",
                "engine_off_off", R.string.enum_engine_off_off, "关闭熄火哨兵"));
        add("dashcam_on", R.string.cmd_dashcam_on, R.string.cmd_category_sentry, "迪加打开全景记录仪", ValueType.NONE);
        add("dashcam_off", R.string.cmd_dashcam_off, R.string.cmd_category_sentry, "迪加关闭全景记录仪", ValueType.NONE);

        // ─── Comfort / misc ───
        add("headlight_level", R.string.cmd_headlight_level, R.string.cmd_category_comfort, "迪加大灯高度{value}",
            ValueType.NUMBER, 0, 5, null, 0, R.string.hint_headlight);
        addEnum("comfort_mode", R.string.cmd_comfort_mode, R.string.cmd_category_comfort, "迪加{value}",
            map("long_trip", R.string.enum_long_trip, "长途模式",
                "city", R.string.enum_city, "城市模式",
                "sleep", R.string.enum_sleep, "一键舒睡",
                "screen_off", R.string.enum_screen_off, "屏幕关闭"));
        add("adb_on", R.string.cmd_adb_on, R.string.cmd_category_system, "迪加打开ADB", ValueType.NONE);
        addEnum("theme", R.string.cmd_theme, R.string.cmd_category_system, "迪加{value}模式",
            map("dark", R.string.enum_dark, "深色",
                "light", R.string.enum_light, "浅色"));
        addEnum("hud", R.string.cmd_hud, R.string.cmd_category_system, "迪加{value}",
            map("on", R.string.enum_on, "打开HUD",
                "off", R.string.enum_off, "关闭HUD"));
    }

    private static void add(String id, int displayNameResId, int categoryResId,
                            String template, ValueType valueType) {
        COMMANDS.add(new CommandEntry(id, displayNameResId, categoryResId, template, valueType,
            0, 0, null, null, 0));
    }

    private static void add(String id, int displayNameResId, int categoryResId,
                            String template, ValueType valueType,
                            double min, double max, Map<String, String> enumValues,
                            int valueUnitResId, int valueHintResId) {
        COMMANDS.add(new CommandEntry(id, displayNameResId, categoryResId, template, valueType,
            min, max, enumValues,
            valueUnitResId != 0 ? getStringStub(valueUnitResId) : "",
            valueHintResId));
    }

    private static void addRange(String id, int displayNameResId, int categoryResId, String template) {
        COMMANDS.add(new CommandEntry(id, displayNameResId, categoryResId, template, ValueType.RANGE,
            0, 100, null, "%", R.string.hint_range_0_100));
    }

    private static void addEnum(String id, int displayNameResId, int categoryResId,
                                String template, Map<String, String> enumValues) {
        COMMANDS.add(new CommandEntry(id, displayNameResId, categoryResId, template, ValueType.ENUM,
            0, 0, enumValues, "", 0));
    }

    private static void addEnumWithOff(String id, int displayNameResId, int categoryResId,
                                       String template, String offTemplate,
                                       Map<String, String> enumValues) {
        COMMANDS.add(new CommandEntry(id, displayNameResId, categoryResId, template, offTemplate,
            ValueType.ENUM, 0, 0, enumValues, "", 0));
    }

    /**
     * Helper to build enum value maps where every triple is:
     * value id, display-name resource id, Chinese suffix for DiPlus.
     */
    @SafeVarargs
    private static Map<String, String> map(Object... triples) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 2 < triples.length; i += 3) {
            m.put((String) triples[i], (String) triples[i + 2]);
        }
        return m;
    }

    private static String getStringStub(int resId) {
        // String resources cannot be resolved at class-init time without a Context.
        // The real unit text is resolved via Context when the UI is built.
        return "";
    }

    public static List<CommandEntry> getAll() {
        return Collections.unmodifiableList(COMMANDS);
    }

    public static CommandEntry getById(String id) {
        for (CommandEntry e : COMMANDS) {
            if (e.id.equals(id)) return e;
        }
        return null;
    }

    /**
     * Build the Chinese DiPlus command string from an entry and an optional value.
     *
     * @param entry command entry
     * @param value raw value from HA or UI; for enums this is the value-id, for numbers/ranges a number
     * @return full Chinese command, or null if the value is invalid
     */
    public static String buildCommand(CommandEntry entry, String value) {
        if (entry == null) return null;
        if (!entry.needsValue()) {
            return entry.commandTemplate;
        }
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String v = value.trim();

        switch (entry.valueType) {
            case NUMBER:
            case RANGE:
                try {
                    double d = Double.parseDouble(v.replace(',', '.'));
                    if (d < entry.minValue || d > entry.maxValue) {
                        return null;
                    }
                    // DiPlus expects integer values; any fraction throws in its
                    // Integer.parseInt and opens NaviActivity instead of applying.
                    if (d != Math.floor(d)) {
                        return null;
                    }
                    return entry.commandTemplate.replace("{value}", String.valueOf((int) d));
                } catch (NumberFormatException e) {
                    return null;
                }
            case ENUM:
                if ("off".equals(v) && entry.offTemplate != null) {
                    return entry.offTemplate;
                }
                String suffix = entry.enumValues.get(v);
                if (suffix == null) return null;
                return entry.commandTemplate.replace("{value}", suffix);
            case STRING:
            default:
                if ("charge_schedule".equals(entry.id)
                        && !CHARGE_SCHEDULE.matcher(v).matches()) {
                    return null;
                }
                return entry.commandTemplate.replace("{value}", v);
        }
    }

    /**
     * Build command by id; convenience wrapper around {@link #buildCommand(CommandEntry, String)}.
     */
    public static String buildCommand(String id, String value) {
        return buildCommand(getById(id), value);
    }
}
