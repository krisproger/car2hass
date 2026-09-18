package com.car2hass.rules;

import java.util.ArrayList;
import java.util.List;

public class RuleEdgeLogicTest {

    public static void main(String[] args) {
        testMissingSuppresses();
        testRestoredSuppresses();
        testLiveValueNotSuppressed();
        testFirstEvaluationNoEdge();
        testFirstEvaluationWithHoldNoEdge();
        testLiveRisingEdge();
        testHoldingTrueNoEdge();
        testFallingThenRising();
        testRuleOneTransition();

        System.out.println("All RuleEdgeLogic tests passed.");
    }

    private static void testMissingSuppresses() {
        RuleEdgeLogic.Suppression s = RuleEdgeLogic.detectSuppression(
                gearRule(), key -> null, key -> false);
        assertTrue(s.blocked(), "missing value blocks");
        assertEqual(RuleEdgeLogic.Suppress.MISSING, s.reason, "missing reason");
        assertEqual("gear", s.sensorKey, "missing key");
    }

    private static void testRestoredSuppresses() {
        RuleEdgeLogic.Suppression s = RuleEdgeLogic.detectSuppression(
                gearRule(), key -> "P", key -> true);
        assertTrue(s.blocked(), "restored value blocks");
        assertEqual(RuleEdgeLogic.Suppress.RESTORED, s.reason, "restored reason");
        assertEqual("gear", s.sensorKey, "restored key");
    }

    private static void testLiveValueNotSuppressed() {
        RuleEdgeLogic.Suppression s = RuleEdgeLogic.detectSuppression(
                gearRule(), key -> "P", key -> false);
        assertFalse(s.blocked(), "live value is not blocked");
        assertEqual(RuleEdgeLogic.Suppress.NONE, s.reason, "no suppression reason");
    }

    private static void testFirstEvaluationNoEdge() {
        RuleEdgeLogic logic = new RuleEdgeLogic();
        RuleEdgeLogic.Outcome o = logic.evaluate("r", true, false);
        assertTrue(o.firstEvaluation, "first evaluation flagged");
        assertFalse(o.risingEdge, "first evaluation never an edge");
        assertFalse(o.stateChanged, "first evaluation not a state change");
        assertTrue(o.previousCondition, "first evaluation records current state");
    }

    private static void testFirstEvaluationWithHoldNoEdge() {
        RuleEdgeLogic logic = new RuleEdgeLogic();
        RuleEdgeLogic.Outcome o = logic.evaluate("r", true, true);
        assertTrue(o.firstEvaluation, "first evaluation with hold still seeds");
        assertFalse(o.risingEdge, "hold on first evaluation never an edge");
        RuleEdgeLogic.Outcome o2 = logic.evaluate("r", true, true);
        assertFalse(o2.risingEdge, "hold keeps suppressing");
        RuleEdgeLogic.Outcome o3 = logic.evaluate("r", true, false);
        assertFalse(o3.risingEdge, "hold release does not turn init into an edge");
    }

    private static void testLiveRisingEdge() {
        RuleEdgeLogic logic = new RuleEdgeLogic();
        logic.evaluate("r", false, false);
        RuleEdgeLogic.Outcome o = logic.evaluate("r", true, false);
        assertFalse(o.firstEvaluation, "not first evaluation");
        assertTrue(o.risingEdge, "false to true is a rising edge");
        assertTrue(o.stateChanged, "false to true changes state");
        assertFalse(o.previousCondition, "previous condition was false");
    }

    private static void testHoldingTrueNoEdge() {
        RuleEdgeLogic logic = new RuleEdgeLogic();
        logic.evaluate("r", true, false);
        RuleEdgeLogic.Outcome o = logic.evaluate("r", true, false);
        assertFalse(o.risingEdge, "already true is not an edge");
        assertFalse(o.stateChanged, "already true does not change state");
    }

    private static void testFallingThenRising() {
        RuleEdgeLogic logic = new RuleEdgeLogic();
        logic.evaluate("r", true, false);
        RuleEdgeLogic.Outcome falling = logic.evaluate("r", false, false);
        assertFalse(falling.risingEdge, "true to false is not a rising edge");
        assertTrue(falling.stateChanged, "true to false changes state");
        RuleEdgeLogic.Outcome rising = logic.evaluate("r", true, false);
        assertTrue(rising.risingEdge, "false to true after falling is an edge");
    }

    /** Rule "unlock on shifting to Park", expressed as a single gear==P condition. */
    private static void testRuleOneTransition() {
        RuleEdgeLogic logic = new RuleEdgeLogic();
        String id = "7a9ba42e";

        // Startup: gear only restored from last session -> suppressed, not seeded.
        RuleEdgeLogic.Suppression s = RuleEdgeLogic.detectSuppression(
                gearRule(), key -> "P", key -> true);
        assertEqual(RuleEdgeLogic.Suppress.RESTORED, s.reason, "startup gear is restored");

        // First live value arrives: the state is recorded, no edge.
        RuleEdgeLogic.Outcome first = logic.evaluate(id, gearMet("P"), false);
        assertTrue(first.firstEvaluation, "first live evaluation seeds");
        assertFalse(first.risingEdge, "seeding must not unlock the doors");

        // Still parked: no repeated edge.
        assertFalse(logic.evaluate(id, gearMet("P"), false).risingEdge, "parked hold no edge");

        // Leave park.
        RuleEdgeLogic.Outcome leaving = logic.evaluate(id, gearMet("D"), false);
        assertFalse(leaving.risingEdge, "leaving park is not an edge");
        assertTrue(leaving.stateChanged, "leaving park changes state");

        // Shift back into Park -> the only real transition.
        RuleEdgeLogic.Outcome parking = logic.evaluate(id, gearMet("P"), false);
        assertTrue(parking.risingEdge, "shifting into Park is a rising edge");
    }

    private static List<RuleCondition> gearRule() {
        List<RuleCondition> conditions = new ArrayList<>();
        conditions.add(new RuleCondition("gear", RuleOperator.EQ, "P"));
        return conditions;
    }

    private static boolean gearMet(String gear) {
        return RuleEvaluator.evaluateConditionGroup(gearRule(), key -> gear);
    }

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
