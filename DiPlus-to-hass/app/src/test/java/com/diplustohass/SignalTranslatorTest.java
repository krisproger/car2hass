package com.diplustohass;

/**
 * Plain-Java unit tests for SignalTranslator.
 *
 * <p>These tests do not depend on the Android runtime and can be executed with
 * a standard JDK: compile both SignalTranslator.java and this class, then run
 * the main method.
 */
public class SignalTranslatorTest {

    public static void main(String[] args) {
        int failures = 0;

        failures += testChineseToEnglish();
        failures += testNumericEnumMapping();
        failures += testUnknownValuePassthrough();
        failures += testNumericSentinels();
        failures += testOffStateDetection();

        if (failures > 0) {
            System.err.println("FAILURES: " + failures);
            System.exit(1);
        }
        System.out.println("All SignalTranslator tests passed.");
    }

    private static int testChineseToEnglish() {
        assertEquals("on", SignalTranslator.translateValue("开"), "Chinese '开' should translate to 'on'");
        assertEquals("off", SignalTranslator.translateValue("关"), "Chinese '关' should translate to 'off'");
        assertEquals("offline", SignalTranslator.translateValue("离线"), "Chinese '离线' should translate to 'offline'");
        assertEquals("locked", SignalTranslator.translateValue("锁定"), "Chinese '锁定' should translate to 'locked'");
        assertEquals("buckled", SignalTranslator.translateValue("已系"), "Chinese '已系' should translate to 'buckled'");
        assertEquals("unbuckled", SignalTranslator.translateValue("未系"), "Chinese '未系' should translate to 'unbuckled'");
        return 0;
    }

    private static int testNumericEnumMapping() {
        assertEquals("off", SignalTranslator.translateEnumValue("power_state", "0"), "power_state 0 -> off");
        assertEquals("on", SignalTranslator.translateEnumValue("power_state", "1"), "power_state 1 -> on");
        assertEquals("driving", SignalTranslator.translateEnumValue("power_state", "2"), "power_state 2 -> driving");
        assertEquals("open", SignalTranslator.translateEnumValue("driver_door", "1"), "driver_door 1 -> open");
        assertEquals("closed", SignalTranslator.translateEnumValue("driver_door", "0"), "driver_door 0 -> closed");
        assertEquals("P", SignalTranslator.translateEnumValue("gear", "1"), "gear 1 -> P");
        return 0;
    }

    private static int testUnknownValuePassthrough() {
        assertEquals("custom_value", SignalTranslator.translateValue("custom_value"), "Unknown values pass through");
        assertEquals("custom_value", SignalTranslator.translateEnumValue("unknown_key", "custom_value"), "Unknown key passes through");
        assertEquals("99", SignalTranslator.translateEnumValue("power_state", "99"), "Unknown enum index passes through");
        return 0;
    }

    private static int testNumericSentinels() {
        assertEquals("∞", SignalTranslator.translateEnumValue("energy_per_100km", "∞"), "Infinity sentinel passes through");
        assertEquals("NaN", SignalTranslator.translateEnumValue("energy_per_100km", "NaN"), "NaN sentinel passes through");
        assertEquals("—", SignalTranslator.translateEnumValue("energy_per_100km", "—"), "Em-dash sentinel passes through");
        return 0;
    }

    private static int testOffStateDetection() {
        assertTrue(SignalTranslator.isOffState("off"), "off is off state");
        assertTrue(SignalTranslator.isOffState("Offline"), "Offline is off state");
        assertTrue(SignalTranslator.isOffState("Inactive"), "Inactive is off state");
        assertTrue(SignalTranslator.isOffState("disabled"), "disabled is off state");
        assertTrue(SignalTranslator.isOffState("Stopped"), "Stopped is off state");
        assertFalse(SignalTranslator.isOffState("on"), "on is not off state");
        assertFalse(SignalTranslator.isOffState("driving"), "driving is not off state");
        assertFalse(SignalTranslator.isOffState(null), "null is not off state");
        return 0;
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean actual, String message) {
        if (!actual) {
            throw new AssertionError(message + " expected=true actual=false");
        }
    }

    private static void assertFalse(boolean actual, String message) {
        if (actual) {
            throw new AssertionError(message + " expected=false actual=true");
        }
    }
}
