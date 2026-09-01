package com.car2hass;

import com.car2hass.rules.RuleOperator;

public class RuleOperatorTest {

    public static void main(String[] args) {
        testEq();
        testNeq();
        testGt();
        testLt();
        testGte();
        testLte();
        testNullHandling();
        testCommaDecimal();
        testGarbageHandling();
        testCaseInsensitive();

        System.out.println("All RuleOperator tests passed.");
    }

    private static void testEq() {
        assertTrue(RuleOperator.EQ.apply("open", "open"), "eq same");
        assertTrue(RuleOperator.EQ.apply("OPEN", "open"), "eq case insensitive");
        assertFalse(RuleOperator.EQ.apply("open", "closed"), "eq different");
    }

    private static void testNeq() {
        assertFalse(RuleOperator.NEQ.apply("open", "open"), "neq same");
        assertTrue(RuleOperator.NEQ.apply("open", "closed"), "neq different");
        assertFalse(RuleOperator.NEQ.apply("OPEN", "open"), "neq case insensitive same");
    }

    private static void testGt() {
        assertTrue(RuleOperator.GT.apply("5", "3"), "gt 5>3");
        assertFalse(RuleOperator.GT.apply("3", "5"), "gt 3>5 false");
        assertFalse(RuleOperator.GT.apply("5", "5"), "gt equal");
    }

    private static void testLt() {
        assertTrue(RuleOperator.LT.apply("3", "5"), "lt 3<5");
        assertFalse(RuleOperator.LT.apply("5", "3"), "lt 5<3 false");
        assertFalse(RuleOperator.LT.apply("5", "5"), "lt equal");
    }

    private static void testGte() {
        assertTrue(RuleOperator.GTE.apply("5", "3"), "gte 5>=3");
        assertTrue(RuleOperator.GTE.apply("5", "5"), "gte 5>=5");
        assertFalse(RuleOperator.GTE.apply("3", "5"), "gte 3>=5 false");
    }

    private static void testLte() {
        assertTrue(RuleOperator.LTE.apply("3", "5"), "lte 3<=5");
        assertTrue(RuleOperator.LTE.apply("5", "5"), "lte 5<=5");
        assertFalse(RuleOperator.LTE.apply("5", "3"), "lte 5<=3 false");
    }

    private static void testNullHandling() {
        assertFalse(RuleOperator.EQ.apply(null, "test"), "eq null actual");
        assertFalse(RuleOperator.EQ.apply("test", null), "eq null expected");
        assertFalse(RuleOperator.GT.apply(null, "5"), "gt null actual");
        assertFalse(RuleOperator.GT.apply("5", null), "gt null expected");
        assertFalse(RuleOperator.GT.apply(null, null), "gt both null");
        assertFalse(RuleOperator.LT.apply(null, "5"), "lt null actual");
        assertFalse(RuleOperator.GTE.apply(null, "5"), "gte null actual");
        assertFalse(RuleOperator.LTE.apply(null, "5"), "lte null expected");
    }

    private static void testCommaDecimal() {
        assertTrue(RuleOperator.GT.apply("2,5", "1.5"), "gt comma decimal");
        assertTrue(RuleOperator.LT.apply("0,5", "1.5"), "lt comma decimal");
        assertTrue(RuleOperator.GTE.apply("1,5", "1.5"), "gte comma decimal equal");
        assertTrue(RuleOperator.LTE.apply("1,5", "1.5"), "lte comma decimal equal");
    }

    private static void testGarbageHandling() {
        assertFalse(RuleOperator.GT.apply("abc", "5"), "gt garbage actual");
        assertFalse(RuleOperator.GT.apply("5", "abc"), "gt garbage expected");
        assertFalse(RuleOperator.LT.apply("abc", "5"), "lt garbage actual");
        assertFalse(RuleOperator.GT.apply("", "5"), "gt empty actual");
        assertFalse(RuleOperator.GT.apply("5", ""), "gt empty expected");
    }

    private static void testCaseInsensitive() {
        assertTrue(RuleOperator.EQ.apply("ON", "on"), "eq all caps");
        assertTrue(RuleOperator.EQ.apply("On", "ON"), "eq mixed");
        assertFalse(RuleOperator.EQ.apply("On", "off"), "eq different");
    }

    private static void assertTrue(boolean v, String msg) {
        if (!v) throw new AssertionError(msg + " expected=true actual=false");
    }

    private static void assertFalse(boolean v, String msg) {
        if (v) throw new AssertionError(msg + " expected=false actual=true");
    }
}
