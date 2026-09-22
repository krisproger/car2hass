package com.car2hass.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Regression tests for the rule editor's connector handling (issue #68) and
 * for the AND/OR/negated/geofence semantics of the shared evaluator.
 *
 * The editor maps the connector spinner through
 * {@link RuleCondition#connectorForSpinnerPosition(int)}. A condition row that
 * was created first has no connector adapter, so after the user reorders it
 * below another condition Android's Spinner reports
 * {@code AdapterView.INVALID_POSITION} (-1). The mapping must treat anything
 * other than an explicit OR selection as AND, otherwise a rule silently turns
 * into an OR and its second condition stops constraining the result.
 */
public class RuleConnectorEditorTest {

    public static void main(String[] args) {
        testConnectorPositionMapping();
        testExactLogScenarioWithReorderedRow();
        testAndGroupFalseWhenSecondConditionFalse();
        testOrGroupTrueWhenSecondConditionFalse();
        testNegatedSecondCondition();
        testSingleCondition();
        testGeofenceDefaultValue();
        testMissingValue();
        testDiagnosticsFields();

        System.out.println("All RuleConnectorEditor tests passed.");
    }

    /** Issue #68: a reordered first row yields INVALID_POSITION, not AND. */
    private static void testConnectorPositionMapping() {
        assertEqual(LogicalOperator.AND,
                RuleCondition.connectorForSpinnerPosition(0), "position 0 is AND");
        assertEqual(LogicalOperator.OR,
                RuleCondition.connectorForSpinnerPosition(1), "position 1 is OR");
        assertEqual(LogicalOperator.AND,
                RuleCondition.connectorForSpinnerPosition(-1), "invalid position defaults to AND");
    }

    /**
     * The exact device log scenario, built the way the editor builds it after
     * the condition rows were reordered: speed>=100 AND windows_all_state!=closed
     * with windows_all_state == "closed". The group must be false.
     */
    private static void testExactLogScenarioWithReorderedRow() {
        List<RuleCondition> conditions = new ArrayList<>();
        conditions.add(new RuleCondition("speed", RuleOperator.GTE, "100"));
        RuleCondition windows = new RuleCondition("windows_all_state", RuleOperator.NEQ, "closed");
        windows.connector = RuleCondition.connectorForSpinnerPosition(-1);
        conditions.add(windows);

        RuleEvaluator.GroupEvaluation g =
                RuleEvaluator.evaluateConditionGroupDetails(conditions, key -> {
                    if ("speed".equals(key)) return "104";
                    if ("windows_all_state".equals(key)) return "closed";
                    return null;
                });

        assertEqual(LogicalOperator.AND, windows.connector, "reordered row must resolve to AND");
        assertTrue(g.conditions.get(0).met, "speed 104 >= 100");
        assertFalse(g.conditions.get(1).met, "windows closed != closed is false");
        assertFalse(g.result, "AND group must be false when windows are closed");
    }

    private static void testAndGroupFalseWhenSecondConditionFalse() {
        List<RuleCondition> conditions = new ArrayList<>();
        conditions.add(new RuleCondition("speed", RuleOperator.GTE, "100"));
        RuleCondition windows = new RuleCondition("windows_all_state", RuleOperator.NEQ, "closed");
        windows.connector = LogicalOperator.AND;
        conditions.add(windows);

        assertFalse(RuleEvaluator.evaluateConditionGroup(conditions,
                key -> "speed".equals(key) ? "104" : "closed"), "AND stays false");
    }

    private static void testOrGroupTrueWhenSecondConditionFalse() {
        List<RuleCondition> conditions = new ArrayList<>();
        conditions.add(new RuleCondition("speed", RuleOperator.GTE, "100"));
        RuleCondition windows = new RuleCondition("windows_all_state", RuleOperator.NEQ, "closed");
        windows.connector = LogicalOperator.OR;
        conditions.add(windows);

        assertTrue(RuleEvaluator.evaluateConditionGroup(conditions,
                key -> "speed".equals(key) ? "104" : "closed"), "OR is true");
    }

    private static void testNegatedSecondCondition() {
        List<RuleCondition> conditions = new ArrayList<>();
        conditions.add(new RuleCondition("gear", RuleOperator.EQ, "P"));
        RuleCondition locked = new RuleCondition("doors_all_lock", RuleOperator.EQ, "locked");
        locked.connector = LogicalOperator.AND;
        locked.negated = true;
        conditions.add(locked);

        RuleEvaluator.GroupEvaluation unparked = RuleEvaluator.evaluateConditionGroupDetails(conditions,
                key -> "gear".equals(key) ? "D" : "locked");
        assertFalse(unparked.result, "AND with false first condition is false");

        RuleEvaluator.GroupEvaluation parkedUnlocked = RuleEvaluator.evaluateConditionGroupDetails(conditions,
                key -> "gear".equals(key) ? "P" : "unlocked");
        assertTrue(parkedUnlocked.result, "gear=P AND NOT locked=true when unlocked");
        assertTrue(parkedUnlocked.conditions.get(1).met, "negated condition met");

        RuleEvaluator.GroupEvaluation parkedLocked = RuleEvaluator.evaluateConditionGroupDetails(conditions,
                key -> "gear".equals(key) ? "P" : "locked");
        assertFalse(parkedLocked.result, "gear=P AND NOT locked=false when locked");
    }

    private static void testSingleCondition() {
        List<RuleCondition> conditions = new ArrayList<>();
        conditions.add(new RuleCondition("gear", RuleOperator.EQ, "P"));
        assertTrue(RuleEvaluator.evaluateConditionGroup(conditions, key -> "P"), "single met");
        assertFalse(RuleEvaluator.evaluateConditionGroup(conditions, key -> "D"), "single not met");
    }

    private static void testGeofenceDefaultValue() {
        List<RuleCondition> conditions = new ArrayList<>();
        conditions.add(new RuleCondition("geo_home", RuleOperator.EQ, ""));
        assertTrue(RuleEvaluator.evaluateConditionGroup(conditions, key -> "inside"),
                "empty geofence defaults to inside");
        assertFalse(RuleEvaluator.evaluateConditionGroup(conditions, key -> "outside"),
                "outside does not match inside");
    }

    private static void testMissingValue() {
        List<RuleCondition> conditions = new ArrayList<>();
        conditions.add(new RuleCondition("speed", RuleOperator.GTE, "100"));
        conditions.add(new RuleCondition("windows_all_state", RuleOperator.NEQ, "closed"));
        RuleEvaluator.GroupEvaluation g =
                RuleEvaluator.evaluateConditionGroupDetails(conditions, key -> null);
        assertFalse(g.result, "missing value makes the group false");
        assertTrue(g.hasMissing(), "missing flagged");
    }

    /** Issue #68: the fire log must expose everything needed to be conclusive. */
    private static void testDiagnosticsFields() {
        List<RuleCondition> conditions = new ArrayList<>();
        conditions.add(new RuleCondition("speed", RuleOperator.GTE, "100"));
        RuleCondition windows = new RuleCondition("windows_all_state", RuleOperator.NEQ, "closed");
        windows.connector = LogicalOperator.AND;
        conditions.add(windows);

        RuleEvaluator.GroupEvaluation g =
                RuleEvaluator.evaluateConditionGroupDetails(conditions, key -> {
                    if ("speed".equals(key)) return "104";
                    if ("windows_all_state".equals(key)) return "closed";
                    return null;
                });
        RuleEvaluator.ConditionEvaluation speed = g.conditions.get(0);
        RuleEvaluator.ConditionEvaluation win = g.conditions.get(1);
        assertEqual("104", speed.rawValue, "raw value captured");
        assertEqual("104", speed.actual, "translated value captured");
        assertEqual("GTE", speed.operatorName, "operator captured");
        assertEqual("100", speed.expected, "expected value captured");
        assertEqual("AND", win.connectorName(), "connector captured");
        assertFalse(win.met, "windows met captured");
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
