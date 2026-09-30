package com.car2hass;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PhoneSensorsTest {

    public static void main(String[] args) {
        // Master off -> nothing enabled, regardless of the per-key opt-ins.
        if (!PhoneSensors.enabledKeys(false, new HashSet<>()).isEmpty()) {
            throw new AssertionError("master off -> empty");
        }
        if (!PhoneSensors.enabledKeys(false, setOf("phone_steps", "phone_battery_level")).isEmpty()) {
            throw new AssertionError("master off -> empty even with per-key opt-ins");
        }

        // Master on, nothing opted in -> still empty (per-sensor prefs default off).
        if (!PhoneSensors.enabledKeys(true, new HashSet<>()).isEmpty()) {
            throw new AssertionError("master on, no opt-ins -> empty");
        }

        // Master on + an opted-in non-sensitive key -> that key is enabled.
        Set<String> perKey = setOf("phone_battery_level", "phone_wifi_ssid");
        List<String> on = PhoneSensors.enabledKeys(true, perKey);
        if (!on.contains("phone_battery_level")) {
            throw new AssertionError("master on + non-sensitive key -> key enabled");
        }
        if (!on.contains("phone_wifi_ssid")) {
            throw new AssertionError("master on + second non-sensitive key -> key enabled");
        }

        // Sensitive keys are excluded unless explicitly added.
        if (on.contains("phone_steps")) {
            throw new AssertionError("sensitive key excluded when not opted in");
        }
        if (PhoneSensors.enabledKeys(true, new HashSet<>()).contains("phone_steps")) {
            throw new AssertionError("sensitive key excluded by default");
        }
        if (!PhoneSensors.enabledKeys(true, setOf("phone_steps")).contains("phone_steps")) {
            throw new AssertionError("sensitive key included when explicitly added");
        }
        if (!PhoneSensors.enabledKeys(true, setOf("phone_location_lat")).contains("phone_location_lat")) {
            throw new AssertionError("sensitive location opt-in");
        }

        // Unknown / non-phone keys in the opt-in set are ignored.
        if (PhoneSensors.enabledKeys(true, setOf("bogus_key")).contains("bogus_key")) {
            throw new AssertionError("unknown key ignored");
        }

        // Result preserves KEYS order (stable output).
        List<String> ordered = PhoneSensors.enabledKeys(true, new HashSet<>(PhoneSensors.KEYS));
        int prev = -1;
        for (String k : ordered) {
            int idx = PhoneSensors.KEYS.indexOf(k);
            if (idx <= prev) throw new AssertionError("enabledKeys must preserve KEYS order");
            prev = idx;
        }

        if (!PhoneSensors.isSensitive("phone_location_lat")) {
            throw new AssertionError("phone_location_lat is sensitive");
        }
        if (!PhoneSensors.isSensitive("phone_steps")) {
            throw new AssertionError("phone_steps is sensitive");
        }
        if (PhoneSensors.isSensitive("phone_battery_level")) {
            throw new AssertionError("phone_battery_level is not sensitive");
        }
        if (PhoneSensors.isSensitive("bogus_key")) {
            throw new AssertionError("unknown key is not sensitive");
        }

        // The core set the spec curates must be present and unique.
        if (!PhoneSensors.KEYS.contains("phone_battery_level")
                || !PhoneSensors.KEYS.contains("phone_android_os_version")
                || !PhoneSensors.KEYS.contains("phone_pressure")) {
            throw new AssertionError("curated keys missing");
        }
        if (new HashSet<>(PhoneSensors.KEYS).size() != PhoneSensors.KEYS.size()) {
            throw new AssertionError("KEYS must not contain duplicates");
        }
        for (String k : PhoneSensors.KEYS) {
            if (!k.startsWith("phone_")) throw new AssertionError("key without phone_ prefix: " + k);
        }
        for (String k : PhoneSensors.SENSITIVE_KEYS) {
            if (!PhoneSensors.KEYS.contains(k)) {
                throw new AssertionError("sensitive key not in KEYS: " + k);
            }
        }

        testDeviceBlock();

        System.out.println("All PhoneSensors tests passed.");
    }

    private static void testDeviceBlock() {
        // Car (or unknown) has no device descriptor: absent block means "car".
        if (PhoneSensors.deviceBlockJson("car", "abc123", "My Phone") != null) {
            throw new AssertionError("car -> no dev block");
        }
        if (PhoneSensors.deviceBlockJson(null, "abc123", "My Phone") != null) {
            throw new AssertionError("null class -> no dev block");
        }
        if (PhoneSensors.deviceBlockJson("", "abc123", "My Phone") != null) {
            throw new AssertionError("empty class -> no dev block");
        }

        // Phone gets a single block with class/id/name.
        String json = PhoneSensors.deviceBlockJson("phone", "abc123", "My Phone");
        if (json == null) throw new AssertionError("phone -> dev block");
        try {
            org.json.JSONObject dev = new org.json.JSONObject(json);
            if (!"phone".equals(dev.getString("class"))) {
                throw new AssertionError("dev.class must be phone");
            }
            if (!"abc123".equals(dev.getString("id"))) {
                throw new AssertionError("dev.id must round-trip");
            }
            if (!"My Phone".equals(dev.getString("name"))) {
                throw new AssertionError("dev.name must round-trip");
            }
        } catch (org.json.JSONException e) {
            throw new AssertionError("dev block must be valid JSON: " + e.getMessage());
        }

        // Names with quotes/backslashes must survive JSON encoding.
        String tricky = "O\"Brien \\ phone";
        String trickyJson = PhoneSensors.deviceBlockJson("phone", "id\"\\x", tricky);
        try {
            org.json.JSONObject dev = new org.json.JSONObject(trickyJson);
            if (!tricky.equals(dev.getString("name"))) {
                throw new AssertionError("dev.name escaping broken");
            }
            if (!"id\"\\x".equals(dev.getString("id"))) {
                throw new AssertionError("dev.id escaping broken");
            }
        } catch (org.json.JSONException e) {
            throw new AssertionError("escaped dev block must be valid JSON: " + e.getMessage());
        }
    }

    private static Set<String> setOf(String... keys) {
        return new HashSet<>(Arrays.asList(keys));
    }
}
