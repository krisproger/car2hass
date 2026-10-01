package com.car2hass;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PhoneSensorsTest {

    public static void main(String[] args) {
        // Channel off -> nothing, regardless of opt-ins.
        if (!PhoneSensors.effectiveKeys(false, setOf("phone_steps"), new HashSet<>()).isEmpty()) {
            throw new AssertionError("channel off -> empty");
        }

        // Channel on, no opt-ins, nothing disabled -> all non-sensitive keys.
        List<String> on = PhoneSensors.effectiveKeys(true, new HashSet<>(), new HashSet<>());
        if (!on.contains("phone_battery_level")) {
            throw new AssertionError("non-sensitive key must be enabled by default");
        }
        if (on.contains("phone_steps") || on.contains("phone_location_lat")) {
            throw new AssertionError("sensitive keys excluded unless opted in");
        }
        for (String k : on) {
            if (PhoneSensors.isSensitive(k)) {
                throw new AssertionError("sensitive key leaked: " + k);
            }
        }

        // Sensitive opt-in enables exactly that key.
        if (!PhoneSensors.effectiveKeys(true, setOf("phone_steps"), new HashSet<>()).contains("phone_steps")) {
            throw new AssertionError("sensitive opt-in");
        }
        if (!PhoneSensors.effectiveKeys(true, setOf("phone_location_lat"), new HashSet<>())
                .contains("phone_location_lat")) {
            throw new AssertionError("sensitive location opt-in");
        }

        // The global attribute filter (disabled) wins over opt-in / default.
        if (PhoneSensors.effectiveKeys(true, new HashSet<>(), setOf("phone_battery_level"))
                .contains("phone_battery_level")) {
            throw new AssertionError("disabled key must be excluded");
        }
        if (PhoneSensors.effectiveKeys(true, setOf("phone_steps"), setOf("phone_steps"))
                .contains("phone_steps")) {
            throw new AssertionError("disabled sensitive key must be excluded");
        }

        // Result preserves KEYS order (stable output).
        List<String> ordered = PhoneSensors.effectiveKeys(true,
                new HashSet<>(PhoneSensors.SENSITIVE_KEYS), new HashSet<>());
        int prev = -1;
        for (String k : ordered) {
            int idx = PhoneSensors.KEYS.indexOf(k);
            if (idx <= prev) throw new AssertionError("effectiveKeys must preserve KEYS order");
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
