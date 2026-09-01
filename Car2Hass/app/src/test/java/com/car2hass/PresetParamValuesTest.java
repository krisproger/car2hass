package com.car2hass;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain-Java unit tests for PresetParamValues (JSON of remembered preset
 * parameter values). No Android runtime dependency.
 */
public class PresetParamValuesTest {

    public static void main(String[] args) {
        testRoundTrip();
        testMultiplePresetsIndependent();
        testFromEmptyAndGarbage();
        testSpecialCharacters();

        System.out.println("All PresetParamValues tests passed.");
    }

    private static void testRoundTrip() {
        Map<String, Map<String, String>> all = new LinkedHashMap<>();
        Map<String, String> windows = new LinkedHashMap<>();
        windows.put("window_driver", "30");
        windows.put("window_passenger", "100");
        all.put("windows", windows);

        Map<String, Map<String, String>> parsed =
                PresetParamValues.fromJson(PresetParamValues.toJson(all));
        assertEquals(1, parsed.size(), "preset count");
        assertEquals("30", parsed.get("windows").get("window_driver"), "driver value");
        assertEquals("100", parsed.get("windows").get("window_passenger"), "passenger value");
    }

    private static void testMultiplePresetsIndependent() {
        Map<String, Map<String, String>> all = new LinkedHashMap<>();
        Map<String, String> windows = new LinkedHashMap<>();
        windows.put("window_driver", "50");
        Map<String, String> ac = new LinkedHashMap<>();
        ac.put("ac_temp", "22");
        ac.put("ac_fan", "3");
        all.put("windows", windows);
        all.put("ac", ac);

        Map<String, Map<String, String>> parsed =
                PresetParamValues.fromJson(PresetParamValues.toJson(all));
        assertEquals(2, parsed.size(), "two presets");
        assertEquals("50", parsed.get("windows").get("window_driver"), "windows kept");
        assertEquals("22", parsed.get("ac").get("ac_temp"), "ac temp");
        assertEquals("3", parsed.get("ac").get("ac_fan"), "ac fan");
    }

    private static void testFromEmptyAndGarbage() {
        assertEquals(0, PresetParamValues.fromJson(null).size(), "null");
        assertEquals(0, PresetParamValues.fromJson("").size(), "empty");
        assertEquals(0, PresetParamValues.fromJson("not json").size(), "garbage");
        assertEquals(0, PresetParamValues.fromJson("[1,2]").size(), "array is not an object");
    }

    private static void testSpecialCharacters() {
        Map<String, Map<String, String>> all = new LinkedHashMap<>();
        Map<String, String> v = new LinkedHashMap<>();
        v.put("weird\"key", "va\"l\nue");
        all.put("p", v);
        Map<String, Map<String, String>> parsed =
                PresetParamValues.fromJson(PresetParamValues.toJson(all));
        assertEquals("va\"l\nue", parsed.get("p").get("weird\"key"), "special chars survive");
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(String expected, String actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected '" + expected + "', got '" + actual + "'");
        }
    }
}
