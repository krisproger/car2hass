package com.car2hass;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure helper for the curated phone sensor set (no Android dependency so it
 * can be unit-tested by the plain-Java runner).
 *
 * Only keys the user opted into are enabled (per-sensor prefs default off);
 * the master toggle gates all of them. Sensitive keys (location, steps,
 * activity, light, proximity, pressure) are opt-in only and never enabled
 * implicitly.
 */
public final class PhoneSensors {

    /** Curated {@code phone_*} keys, in a stable collection order. */
    public static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList(
            // Battery / charger
            "phone_battery_level",
            "phone_battery_state",
            "phone_charger_type",
            "phone_is_charging",
            "phone_battery_health",
            "phone_battery_temperature",
            "phone_battery_power",
            "phone_remaining_charge_time",
            // Connection / WiFi
            "phone_connection_type",
            "phone_wifi_ssid",
            "phone_wifi_bssid",
            "phone_wifi_frequency",
            "phone_wifi_ip",
            "phone_wifi_link_speed",
            "phone_wifi_signal",
            "phone_wifi_state",
            "phone_hotspot_state",
            "phone_transport_type",
            // Display / power
            "phone_screen_brightness",
            "phone_screen_off_timeout",
            "phone_screen_orientation",
            "phone_screen_rotation",
            "phone_doze",
            "phone_interactive",
            "phone_power_save",
            // Audio
            "phone_ringer_mode",
            "phone_audio_mode",
            "phone_is_headphones",
            "phone_is_mic_muted",
            "phone_is_speakerphone_on",
            "phone_is_music_active",
            "phone_volume_music",
            // Memory / storage / traffic
            "phone_memory_used",
            "phone_memory_free",
            "phone_storage_internal_free",
            "phone_storage_internal_total",
            "phone_storage_external_free",
            "phone_storage_external_total",
            "phone_data_tx",
            "phone_data_rx",
            // SIM / OS / time
            "phone_sim_carrier",
            "phone_sim_country",
            "phone_android_os_version",
            "phone_android_os_security_patch",
            "phone_time_zone",
            "phone_last_reboot",
            // Sensitive (opt-in only)
            "phone_steps",
            "phone_activity",
            "phone_location_lat",
            "phone_location_lon",
            "phone_light",
            "phone_proximity",
            "phone_pressure"));

    /** Sensitive keys that require an explicit user opt-in and permission. */
    public static final Set<String> SENSITIVE_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "phone_steps",
            "phone_activity",
            "phone_location_lat",
            "phone_location_lon",
            "phone_light",
            "phone_proximity",
            "phone_pressure")));

    private PhoneSensors() {}

    public static boolean isSensitive(String key) {
        return key != null && SENSITIVE_KEYS.contains(key);
    }

    /**
     * Device descriptor for the telemetry payload:
     * {@code {"class":"phone","id":<anon>,"name":<phoneName>}}.
     * Returns {@code null} for anything but a phone — an absent block is
     * treated by the integration as the car device (backward compatible).
     */
    public static String deviceBlockJson(String deviceClass, String id, String name) {
        if (!"phone".equals(deviceClass)) return null;
        try {
            JSONObject dev = new JSONObject();
            dev.put("class", "phone");
            dev.put("id", id == null ? "" : id);
            dev.put("name", name == null ? "" : name);
            return dev.toString();
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    /**
     * Effective enabled keys: empty while the master toggle is off. When it is
     * on, only keys present in {@code perKey} are enabled, emitted in
     * {@link #KEYS} order. Sensitive keys are therefore never enabled unless
     * explicitly listed by the user.
     */
    public static List<String> enabledKeys(boolean master, Set<String> perKey) {
        List<String> out = new ArrayList<>();
        if (!master || perKey == null || perKey.isEmpty()) return out;
        for (String key : KEYS) {
            if (perKey.contains(key)) out.add(key);
        }
        return out;
    }
}
