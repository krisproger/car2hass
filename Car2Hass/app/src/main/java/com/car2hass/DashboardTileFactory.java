package com.car2hass;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Factory for dashboard tiles. Centralises the list of available sensor and
 * command tiles so the dashboard and its configuration screen stay in sync.
 */
public class DashboardTileFactory {

    private static final List<String> DEFAULT_PRESET_IDS = Arrays.asList(
            "speed", "battery", "range", "charge_gun", "cabin_temp",
            "outside_temp", "doors", "tyre_pressure", "lights", "ha_status",
            "ac", "volume", "hazard", "windows"
    );

    private static final Set<String> AUXILIARY_COMMANDS = new HashSet<>(Arrays.asList(
            "volume_up", "volume_down",
            "ac_fan_up", "ac_fan_down",
            "ac_temp_up", "ac_temp_down"
    ));

    public static DashboardTile create(Context ctx, String type, String key, String value) {
        return create(ctx, type, key, value, null);
    }

    public static DashboardTile create(Context ctx, String type, String key, String value, String displayType) {
        if ("preset".equals(type)) {
            return createPresetTile(ctx, key);
        }
        if ("sensor".equals(type)) {
            DashboardTile tile = createSensorTile(ctx, key);
            if (displayType != null && !displayType.isEmpty()) {
                tile.displayType = displayType;
            }
            return tile;
        }
        return createCommandTile(ctx, key, value);
    }

    public static DashboardTile createPresetTile(Context ctx, String presetId) {
        DashboardPresetRegistry.DashboardPreset preset = DashboardPresetRegistry.getInstance(ctx).getPreset(presetId);
        DashboardTile tile = new DashboardTile(DashboardTile.Type.PRESET, presetId);
        if (preset != null) {
            tile.label = DashboardPresetRegistry.pick(ctx, preset.label, preset.labelRu);
            tile.icon = resolvePresetIcon(preset.icon);
            tile.iconName = preset.icon;
            tile.presetId = preset.id;
            tile.behavior = preset.behavior;
            tile.associatedSensorKeys = preset.sensors;
        } else {
            tile.label = presetId;
            tile.presetId = presetId;
            tile.behavior = "command";
        }
        return tile;
    }

    public static DashboardTile createSensorTile(Context ctx, String key) {
        CANDataItem item = CANDataReader.findSignalByKey(key);
        String label = item != null ? item.name : key;
        String icon = resolveSensorIcon(key);
        DashboardTile tile = new DashboardTile(DashboardTile.Type.SENSOR, key);
        tile.label = label;
        tile.icon = icon;
        return tile;
    }

    public static DashboardTile createCommandTile(Context ctx, String key, String value) {
        CommandRegistry.CommandEntry entry = CommandRegistry.getById(key);
        DashboardTile tile = new DashboardTile(DashboardTile.Type.COMMAND, key);
        tile.label = entry != null ? ctx.getString(entry.displayNameResId) : key;
        tile.icon = resolveCommandIcon(key);
        tile.commandValue = value;
        tile.setValue(value != null && !value.isEmpty() ? value : "—");
        return tile;
    }

    public static DashboardTile createEmptyCellTile(Context ctx) {
        DashboardTile tile = new DashboardTile(DashboardTile.Type.SENSOR, "__empty__");
        tile.label = "";
        tile.icon = "➕";
        tile.value = "";
        tile.unit = "";
        tile.sub = "";
        tile.isEmptyCell = true;
        return tile;
    }

    public static List<DashboardTile> defaultTiles(Context ctx) {
        DashboardPresetRegistry registry = DashboardPresetRegistry.getInstance(ctx);
        List<DashboardTile> list = new ArrayList<>();
        for (String id : DEFAULT_PRESET_IDS) {
            DashboardPresetRegistry.DashboardPreset preset = registry.getPreset(id);
            if (preset != null) {
                list.add(createPresetTile(ctx, id));
            }
        }
        return list;
    }

    public static List<DashboardTile> availableSensors(Context ctx) {
        DashboardPresetRegistry registry = DashboardPresetRegistry.getInstance(ctx);
        List<DashboardTile> list = new ArrayList<>();
        for (DashboardPresetRegistry.DashboardPreset preset : registry.getAllPresets()) {
            list.add(createPresetTile(ctx, preset.id));
        }
        // Append individual CAN signals that are not covered by any preset.
        for (CANDataItem item : CANDataReader.createSignalItems()) {
            if (registry.getPreset(item.key) == null) {
                list.add(createSensorTile(ctx, item.key));
            }
        }
        return list;
    }

    public static List<DashboardTile> availableCommands(Context ctx) {
        List<DashboardTile> list = new ArrayList<>();
        for (CommandRegistry.CommandEntry e : CommandRegistry.getAll()) {
            if (AUXILIARY_COMMANDS.contains(e.id)) continue;
            list.add(createCommandTile(ctx, e.id, defaultValueFor(ctx, e)));
        }
        return list;
    }

    private static String defaultValueFor(Context ctx, CommandRegistry.CommandEntry e) {
        if (e.valueType == CommandRegistry.ValueType.NONE) return null;
        if (e.valueType == CommandRegistry.ValueType.NUMBER) return String.valueOf((int) e.minValue);
        if (e.valueType == CommandRegistry.ValueType.RANGE) return formatNumber(e.minValue);
        if (e.valueType == CommandRegistry.ValueType.ENUM && e.enumValues != null && !e.enumValues.isEmpty())
            return e.enumValues.keySet().iterator().next();
        return null;
    }

    private static String formatNumber(double value) {
        return value == (int) value ? String.valueOf((int) value) : String.valueOf(value);
    }

    private static int syntheticLabelRes(String key) {
        if (key == null) return 0;
        switch (key) {
            case "battery": return R.string.dash_battery;
            case "charge_gun": return R.string.dash_charge_gun;
            case "doors": return R.string.dash_doors;
            case "tyre_pressure": return R.string.dash_tyre_pressure;
            case "lights": return R.string.dash_lights;
            case "ha_status": return R.string.dash_ha_status;
            default: return 0;
        }
    }

    private static String resolvePresetIcon(String iconName) {
        if (iconName == null || iconName.isEmpty()) return "";
        switch (iconName) {
            case "car-door": return "🚪";
            case "lock": case "lock-open": return "🔒";
            case "window-open": return "▭";
            case "window-closed": return "■";
            case "window-open-variant": return "□";
            case "air-conditioner": return "❄";
            case "fan": return "🌀";
            case "air-recirculator": return "🔄";
            case "alert": return "⚠";
            case "lightbulb": case "lightbulb-on": case "lightbulb-off": return "💡";
            case "car-light-high": case "car-light-dim": return "💡";
            case "volume-high": return "🔊";
            case "minus": return "➖";
            case "plus": return "➕";
            case "car-settings": return "⚙";
            case "car-engine": return "🔧";
            case "battery": return "🔋";
            case "ev-plug": return "🔌";
            case "thermometer": return "🌡";
            case "speedometer": return "🚗";
            case "map-marker-distance": return "📏";
            case "tire": return "◎";
            case "car-hatchback": return "🚗";
            case "home-assistant": return "🏠";
            case "steering": return "🔄";
            case "mirror": return "🪞";
            case "car-windshield": return "▭";
            case "camera": return "📹";
            case "car-sentry": return "📹";
            case "led-strip": return "💡";
            case "car-child-seat": return "👶";
            default: return iconName;
        }
    }

    private static String resolveSensorIcon(String key) {
        if (key == null) return "";
        switch (key) {
            case "speed": return "🚗";
            case "soc": case "battery": case "battery_capacity":
            case "battery_charge": case "device_battery": return "🔋";
            case "range": return "📏";
            case "power_state": case "energy_per_100_km": case "total_energy": return "⚡";
            case "charge_gun_state": case "charging_state": return "🔌";
            case "cabin_temp": case "outside_temp":
            case "engine_coolant_temp": return "🌡";
            case "engine_rpm": case "steering_rate": return "🌀";
            case "gear": case "drive_mode": case "powertrain_mode": return "⚙️";
            case "brake_pedal": return "🛑";
            case "accel_pedal": return "⚡";
            case "doors": case "driver_door": case "passenger_door":
            case "rear_left_door": case "rear_right_door": return "🚪";
            case "bonnet": return "🚘";
            case "trunk": return "📦";
            case "window_fl": case "window_fr": case "window_rl": case "window_rr":
            case "sunroof": case "sunshade": return "🪟";
            case "driver_door_lock": case "passenger_door_lock":
            case "rear_left_door_lock": case "rear_right_door_lock":
            case "trunk_lock": case "remote_lock_state": return "🔒";
            case "tyre_pressure": case "tyre_pressure_fl": case "tyre_pressure_fr":
            case "tyre_pressure_rl": case "tyre_pressure_rr": return "◎";
            case "lights": case "sidelights": case "low_beam": case "high_beam":
            case "front_fog": case "rear_fog": case "footwell_light": case "drl":
            case "hazard": case "turn_signal": return "💡";
            case "front_wiper_speed": case "wiper_mode": case "rain_amount": return "💧";
            case "driver_seatbelt": case "passenger_seatbelt":
            case "seatbelt_2nd_left": case "seatbelt_2nd_right":
            case "seatbelt_2nd_center": case "passenger_seatbelt_warning": return "🪢";
            case "fuel_level": case "total_fuel": return "⛽";
            case "ac_state": case "fan_speed": case "ac_recirculation":
            case "ac_airflow_mode": case "ac_set_temp": case "auto_hold": return "❄";
            case "steering_angle": case "slope": return "🎡";
            case "location_lat": case "location_lon": case "location_speed":
            case "location_bearing": case "location_altitude":
            case "location_accuracy": case "location_provider": return "📍";
            case "media_volume": case "navigation_volume": case "bluetooth_signal": return "🔊";
            case "sentry_state": case "dashcam_state": case "parked_recording_switch":
            case "parked_sentry_alarm": return "📹";
            case "wifi_state": return "📶";
            case "bluetooth_state": return "🔵";
            case "ha_status": return "🏠";
            default: return "";
        }
    }

    private static String resolveCommandIcon(String key) {
        if (key == null) return "";
        if (key.startsWith("ac_")) return "❄";
        if (key.startsWith("doors_")) return "🔒";
        if (key.startsWith("window_") || key.equals("windows_close_all") || key.equals("windows_vent"))
            return "🪟";
        if (key.startsWith("hazard_")) return "⚠";
        if (key.startsWith("lights_") || key.equals("turn_signal") || key.equals("fog_on") || key.equals("fog_off")
                || key.equals("drl_on") || key.equals("drl_off") || key.equals("interior_light_on")
                || key.equals("interior_light_off") || key.equals("ambilight_on") || key.equals("ambilight_off")
                || key.equals("auto_high_beam_on") || key.equals("auto_high_beam_off"))
            return "💡";
        if (key.startsWith("trunk_")) return "🚗";
        if (key.startsWith("charge_")) return "🔋";
        if (key.startsWith("volume") || key.equals("nav_volume") || key.equals("ext_volume")) return "🔊";
        if (key.equals("sentry") || key.startsWith("dashcam_")) return "📹";
        if (key.startsWith("steering_heat_") || key.startsWith("mirror_heat_") || key.startsWith("front_defrost_"))
            return "❄";
        return "";
    }
}
