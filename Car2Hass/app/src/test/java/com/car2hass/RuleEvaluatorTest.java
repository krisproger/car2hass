package com.car2hass;

import com.car2hass.rules.LogicalOperator;
import com.car2hass.rules.RuleCondition;
import com.car2hass.rules.RuleEvaluator;
import com.car2hass.rules.RuleEvaluator.Decision;
import com.car2hass.rules.RuleEvaluator.SkipReason;
import com.car2hass.rules.RuleOperator;

import java.util.ArrayList;
import java.util.List;

public class RuleEvaluatorTest {

    // base timestamp for cooldown calculations (far enough from epoch to avoid
    // the "lastExecutedMs=0 means never" edge case)
    private static final long T = 1_000_000_000L;
    // a common minInterval used in several tests
    private static final long INTERVAL = 5000L;

    public static void main(String[] args) {
        testConditionFalse();
        testFirstTrueFires();
        testRisingEdgeBlocksRepeat();
        testFireEveryTime();
        testCooldownBlocks();
        testAfterCooldownFires();
        testDisabled();
        testAndGroupTrue();
        testAndGroupFalse();
        testOrGroupTrue();
        testOrGroupFalse();
        testNegatedCondition();
        testUnavailableSignal();
        testEmptyConditions();

        System.out.println("All RuleEvaluator tests passed.");
    }

    private static void testConditionFalse() {
        Decision d = eval().actual("off").expected("on").go();
        assertFalse(d.fire, "condition false no fire");
        assertFalse(d.conditionMet, "condition not met");
        assertEqual(SkipReason.CONDITION_FALSE, d.reason, "reason");
    }

    private static void testFirstTrueFires() {
        Decision d = eval().previousCondition(false).go();
        assertTrue(d.fire, "first true fires");
        assertTrue(d.conditionMet, "condition met");
        assertEqual(SkipReason.NONE, d.reason, "reason");
    }

    private static void testRisingEdgeBlocksRepeat() {
        // previousCondition=true means condition was already true → not a rising edge
        Decision d = eval().previousCondition(true).go();
        assertFalse(d.fire, "rising edge blocks repeat");
        assertTrue(d.conditionMet, "condition still met");
        assertEqual(SkipReason.NOT_RISING_EDGE, d.reason, "reason");
    }

    private static void testFireEveryTime() {
        // fireOnRisingEdge=false → fires whenever condition holds (subject to cooldown)
        Decision d = eval().fireOnRisingEdge(false).previousCondition(true).go();
        assertTrue(d.fire, "fire every time when fireOnRisingEdge=false");
        assertTrue(d.conditionMet, "condition met");
        assertEqual(SkipReason.NONE, d.reason, "reason");
    }

    private static void testCooldownBlocks() {
        // condition true, past rising edge (fireOnRisingEdge=false), but within cooldown
        // lastExecuted=T, now=T+1000, interval=5000 → 1000 < 5000 → COOLDOWN
        Decision d = eval()
            .fireOnRisingEdge(false)
            .previousCondition(true)
            .nowMs(T + 1000)
            .lastExecutedMs(T)
            .minIntervalMs(INTERVAL)
            .go();
        assertFalse(d.fire, "cooldown blocks");
        assertTrue(d.conditionMet, "condition met during cooldown");
        assertEqual(SkipReason.COOLDOWN, d.reason, "reason");
    }

    private static void testAfterCooldownFires() {
        // condition true, past rising edge, past cooldown
        // lastExecuted=T, now=T+10000, interval=5000 → 10000 >= 5000 → NONE
        Decision d = eval()
            .fireOnRisingEdge(false)
            .previousCondition(true)
            .nowMs(T + 10000)
            .lastExecutedMs(T)
            .minIntervalMs(INTERVAL)
            .go();
        assertTrue(d.fire, "after cooldown fires");
        assertTrue(d.conditionMet, "condition met");
        assertEqual(SkipReason.NONE, d.reason, "reason");
    }

    private static void testDisabled() {
        Decision d = eval().enabled(false).go();
        assertFalse(d.fire, "disabled no fire");
        assertTrue(d.conditionMet, "condition still computed when disabled");
        assertEqual(SkipReason.DISABLED, d.reason, "reason");
    }

    // --- fluent test builder ---

    private static EvalBuilder eval() {
        return new EvalBuilder();
    }

    private static final class EvalBuilder {
        private boolean enabled = true;
        private RuleOperator op = RuleOperator.EQ;
        private String actual = "on";
        private String expected = "on";
        private boolean fireOnRisingEdge = true;
        private boolean previousCondition = false;
        private long nowMs = T;
        private long lastExecutedMs = 0;
        private long minIntervalMs = INTERVAL;

        EvalBuilder enabled(boolean v) { enabled = v; return this; }
        EvalBuilder op(RuleOperator v) { op = v; return this; }
        EvalBuilder actual(String v) { actual = v; return this; }
        EvalBuilder expected(String v) { expected = v; return this; }
        EvalBuilder fireOnRisingEdge(boolean v) { fireOnRisingEdge = v; return this; }
        EvalBuilder previousCondition(boolean v) { previousCondition = v; return this; }
        EvalBuilder nowMs(long v) { nowMs = v; return this; }
        EvalBuilder lastExecutedMs(long v) { lastExecutedMs = v; return this; }
        EvalBuilder minIntervalMs(long v) { minIntervalMs = v; return this; }

        Decision go() {
            return RuleEvaluator.evaluate(
                enabled, op, actual, expected,
                fireOnRisingEdge, previousCondition,
                nowMs, lastExecutedMs, minIntervalMs);
        }
    }

    // --- multi-condition group tests ---

    private static void testAndGroupTrue() {
        List<RuleCondition> andGroup = new ArrayList<>();
        andGroup.add(new RuleCondition("speed", RuleOperator.GT, "0"));
        RuleCondition c2 = new RuleCondition("driver_seatbelt", RuleOperator.EQ, "buckled");
        c2.connector = LogicalOperator.AND;
        andGroup.add(c2);

        assertTrue(RuleEvaluator.evaluateConditionGroup(andGroup, key -> {
            if ("speed".equals(key)) return "10";
            if ("driver_seatbelt".equals(key)) return "buckled";
            return null;
        }), "AND group true");
    }

    private static void testAndGroupFalse() {
        List<RuleCondition> andGroup = new ArrayList<>();
        andGroup.add(new RuleCondition("speed", RuleOperator.GT, "0"));
        RuleCondition c2 = new RuleCondition("driver_seatbelt", RuleOperator.EQ, "buckled");
        c2.connector = LogicalOperator.AND;
        andGroup.add(c2);

        assertFalse(RuleEvaluator.evaluateConditionGroup(andGroup, key -> {
            if ("speed".equals(key)) return "0";
            if ("driver_seatbelt".equals(key)) return "buckled";
            return null;
        }), "AND group false");
    }

    private static void testOrGroupTrue() {
        List<RuleCondition> orGroup = new ArrayList<>();
        orGroup.add(new RuleCondition("speed", RuleOperator.GT, "0"));
        RuleCondition c3 = new RuleCondition("driver_seatbelt", RuleOperator.EQ, "buckled");
        c3.connector = LogicalOperator.OR;
        orGroup.add(c3);

        assertTrue(RuleEvaluator.evaluateConditionGroup(orGroup, key -> {
            if ("speed".equals(key)) return "0";
            if ("driver_seatbelt".equals(key)) return "buckled";
            return null;
        }), "OR group true");
    }

    private static void testOrGroupFalse() {
        List<RuleCondition> orGroup = new ArrayList<>();
        orGroup.add(new RuleCondition("speed", RuleOperator.GT, "0"));
        RuleCondition c3 = new RuleCondition("driver_seatbelt", RuleOperator.EQ, "buckled");
        c3.connector = LogicalOperator.OR;
        orGroup.add(c3);

        assertFalse(RuleEvaluator.evaluateConditionGroup(orGroup, key -> {
            if ("speed".equals(key)) return "0";
            if ("driver_seatbelt".equals(key)) return "unbuckled";
            return null;
        }), "OR group false");
    }

    private static void testNegatedCondition() {
        List<RuleCondition> negGroup = new ArrayList<>();
        RuleCondition nc = new RuleCondition("driver_seatbelt", RuleOperator.EQ, "buckled");
        nc.negated = true;
        negGroup.add(nc);

        assertTrue(RuleEvaluator.evaluateConditionGroup(negGroup, key -> "unbuckled"), "NOT buckled = unbuckled -> true");
        assertFalse(RuleEvaluator.evaluateConditionGroup(negGroup, key -> "buckled"), "NOT buckled = buckled -> false");
    }

    private static void testUnavailableSignal() {
        List<RuleCondition> unavail = new ArrayList<>();
        unavail.add(new RuleCondition("missing", RuleOperator.EQ, "value"));

        assertFalse(RuleEvaluator.evaluateConditionGroup(unavail, key -> null), "unavailable signal -> false");
        assertFalse(RuleEvaluator.evaluateConditionGroup(unavail, key -> "---"), "dash signal -> false");
    }

    private static void testEmptyConditions() {
        assertFalse(RuleEvaluator.evaluateConditionGroup(new ArrayList<>(), key -> null), "empty -> false");
        assertFalse(RuleEvaluator.evaluateConditionGroup(null, key -> null), "null -> false");
    }

    // --- assertion helpers ---

    private static void assertTrue(boolean v, String msg) {
        if (!v) throw new AssertionError(msg + " expected=true actual=false");
    }

    private static void assertFalse(boolean v, String msg) {
        if (v) throw new AssertionError(msg + " expected=false actual=true");
    }

    private static void assertEqual(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + " expected=" + expected + " actual=" + actual);
        }
    }
}
