package com.car2hass;

import java.util.Arrays;
import java.util.Collections;

/**
 * Plain-Java unit tests for DashboardLogic.
 *
 * <p>These tests do not depend on the Android runtime and can be executed with
 * a standard JDK: compile both DashboardLogic.java and this class, then run
 * the main method.
 */
public class DashboardLogicTest {

    public static void main(String[] args) {
        testFormatNumber();
        testParseDoubleSafe();
        testJoinStrings();
        testIsStateTruthy();
        testIsStateTruthyNumericModes();
        testSelectOptionMatches();

        System.out.println("All DashboardLogic tests passed.");
    }

    private static void testFormatNumber() {
        assertEquals("5", DashboardLogic.formatNumber(5.0), "int-valued double");
        assertEquals("5.5", DashboardLogic.formatNumber(5.5), "fractional double");
        assertEquals("0", DashboardLogic.formatNumber(0.0), "zero");
        assertEquals("-3", DashboardLogic.formatNumber(-3.0), "negative int");
    }

    private static void testParseDoubleSafe() {
        assertTrue(DashboardLogic.parseDoubleSafe("1.5") == 1.5, "dot");
        assertTrue(DashboardLogic.parseDoubleSafe("1,5") == 1.5, "comma converted to dot");
        assertTrue(DashboardLogic.parseDoubleSafe("abc") == 0, "garbage -> 0");
        assertTrue(DashboardLogic.parseDoubleSafe(null) == 0, "null -> 0");
        assertTrue(DashboardLogic.parseDoubleSafe("") == 0, "empty -> 0");
        assertTrue(DashboardLogic.parseDoubleSafe("-12.25") == -12.25, "negative");
    }

    private static void testJoinStrings() {
        assertEquals("", DashboardLogic.joinStrings(null, ","), "null list");
        assertEquals("", DashboardLogic.joinStrings(Collections.<String>emptyList(), ","), "empty list");
        assertEquals("a", DashboardLogic.joinStrings(Arrays.asList("a"), ","), "single");
        assertEquals("a,b,c", DashboardLogic.joinStrings(Arrays.asList("a", "b", "c"), ","), "three with comma");
        assertEquals("a b", DashboardLogic.joinStrings(Arrays.asList("a", "b"), " "), "space separator");
    }

    private static void testIsStateTruthy() {
        assertTrue(DashboardLogic.isStateTruthy("on", Arrays.asList("on", "open")), "exact match");
        assertTrue(DashboardLogic.isStateTruthy("ON", Arrays.asList("on")), "case-insensitive");
        assertTrue(!DashboardLogic.isStateTruthy("off", Arrays.asList("on")), "no match");
        assertTrue(!DashboardLogic.isStateTruthy("", Arrays.asList("on")), "empty state");
        assertTrue(!DashboardLogic.isStateTruthy(null, Arrays.asList("on")), "null state");
        assertTrue(!DashboardLogic.isStateTruthy("on", null), "null truthy list");
        assertTrue(!DashboardLogic.isStateTruthy("on", Collections.<String>emptyList()), "empty truthy list");
        assertTrue(!DashboardLogic.isStateTruthy("on", Arrays.asList((String) null)), "null truthy entry");
    }

    private static void testIsStateTruthyNumericModes() {
        // numeric_gt_0
        assertTrue(DashboardLogic.isStateTruthy("42", null, "numeric_gt_0"), "gt_0: 42 true");
        assertTrue(DashboardLogic.isStateTruthy("0.5", null, "numeric_gt_0"), "gt_0: 0.5 true");
        assertTrue(!DashboardLogic.isStateTruthy("0", null, "numeric_gt_0"), "gt_0: 0 false");
        assertTrue(!DashboardLogic.isStateTruthy("-1", null, "numeric_gt_0"), "gt_0: -1 false");
        assertTrue(!DashboardLogic.isStateTruthy("abc", null, "numeric_gt_0"), "gt_0: garbage false");
        assertTrue(!DashboardLogic.isStateTruthy("", null, "numeric_gt_0"), "gt_0: empty false");
        assertTrue(!DashboardLogic.isStateTruthy(null, null, "numeric_gt_0"), "gt_0: null false");
        assertTrue(DashboardLogic.isStateTruthy("1,5", null, "numeric_gt_0"), "gt_0: comma decimal true");
        // numeric_eq_0
        assertTrue(DashboardLogic.isStateTruthy("0", null, "numeric_eq_0"), "eq_0: 0 true");
        assertTrue(DashboardLogic.isStateTruthy("0.0", null, "numeric_eq_0"), "eq_0: 0.0 true");
        assertTrue(!DashboardLogic.isStateTruthy("1", null, "numeric_eq_0"), "eq_0: 1 false");
        assertTrue(!DashboardLogic.isStateTruthy("abc", null, "numeric_eq_0"), "eq_0: garbage false");
        // list mode and unknown modes fall back to membership
        assertTrue(DashboardLogic.isStateTruthy("on", Arrays.asList("on"), "list"), "list mode membership");
        assertTrue(!DashboardLogic.isStateTruthy("off", Arrays.asList("on"), "list"), "list mode no match");
        assertTrue(DashboardLogic.isStateTruthy("on", Arrays.asList("on"), "weird_mode"), "unknown mode falls back");
    }

    private static void testSelectOptionMatches() {
        assertTrue(DashboardLogic.selectOptionMatches("face", "face"), "exact");
        assertTrue(DashboardLogic.selectOptionMatches("ECO", "eco"), "case-insensitive");
        assertTrue(DashboardLogic.selectOptionMatches("face_feet", "face+feet"), "underscore vs plus");
        assertTrue(DashboardLogic.selectOptionMatches("face_feet", "face/feet"), "underscore vs slash (Chinese 吹面/吹脚)");
        assertTrue(DashboardLogic.selectOptionMatches("face+feet", "face/feet"), "plus vs slash");
        assertTrue(DashboardLogic.selectOptionMatches("feet-defrost", "feet_defrost"), "hyphen vs underscore");
        assertTrue(DashboardLogic.selectOptionMatches("a:b", "a_b"), "colon vs underscore");
        assertTrue(!DashboardLogic.selectOptionMatches("face", "feet"), "different words");
        assertTrue(!DashboardLogic.selectOptionMatches(null, "face"), "null option");
        assertTrue(!DashboardLogic.selectOptionMatches("face", null), "null current");
        assertTrue(!DashboardLogic.selectOptionMatches("", "face"), "empty option");
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean actual, String message) {
        if (!actual) {
            throw new AssertionError(message + " expected=true actual=false");
        }
    }
}
