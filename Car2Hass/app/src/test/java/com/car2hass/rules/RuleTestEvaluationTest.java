package com.car2hass.rules;

import java.util.ArrayList;
import java.util.List;

public class RuleTestEvaluationTest {

    public static void main(String[] args) {
        testAndGroupDetails();
        testOrGroupDetails();
        testNegatedDetails();
        testMissingDetails();
        testMissingAbortsGroup();
        testGeofenceDefaultValue();
        testEmptyGroup();
        testRestoredSuppression();

        System.out.println("All RuleTestEvaluation tests passed.");
    }

    private static void testAndGroupDetails() {
        List<RuleCondition> and = new ArrayList<>();
        and.add(new RuleCondition("speed", RuleOperator.GT, "0"));
        RuleCondition seatbelt = new RuleCondition("driver_seatbelt", RuleOperator.EQ, "buckled");
        seatbelt.connector = LogicalOperator.AND;
        and.add(seatbelt);

        RuleEvaluator.GroupEvaluation met = RuleEvaluator.evaluateConditionGroupDetails(and, key -> {
            if ("speed".equals(key)) return "10";
            if ("driver_seatbelt".equals(key)) return "buckled";
            return null;
        });
        assertTrue(met.result, "AND group true");
        assertTrue(met.conditions.get(0).met, "AND first met");
        assertTrue(met.conditions.get(1).met, "AND second met");

        RuleEvaluator.GroupEvaluation notMet = RuleEvaluator.evaluateConditionGroupDetails(and, key -> {
            if ("speed".equals(key)) return "0";
            return "buckled";
        });
        assertFalse(notMet.result, "AND group false");
        assertFalse(notMet.conditions.get(0).met, "AND first not met");
        assertTrue(notMet.conditions.get(1).met, "AND second met");
    }

    private static void testOrGroupDetails() {
        List<RuleCondition> or = new ArrayList<>();
        or.add(new RuleCondition("speed", RuleOperator.GT, "0"));
        RuleCondition seatbelt = new RuleCondition("driver_seatbelt", RuleOperator.EQ, "buckled");
        seatbelt.connector = LogicalOperator.OR;
        or.add(seatbelt);

        RuleEvaluator.GroupEvaluation met = RuleEvaluator.evaluateConditionGroupDetails(or, key -> {
            if ("speed".equals(key)) return "0";
            return "buckled";
        });
        assertTrue(met.result, "OR group true");
        assertFalse(met.conditions.get(0).met, "OR first not met");
        assertTrue(met.conditions.get(1).met, "OR second met");

        RuleEvaluator.GroupEvaluation notMet = RuleEvaluator.evaluateConditionGroupDetails(or, key -> {
            if ("speed".equals(key)) return "0";
            return "unbuckled";
        });
        assertFalse(notMet.result, "OR group false");
    }

    private static void testNegatedDetails() {
        List<RuleCondition> neg = new ArrayList<>();
        RuleCondition c = new RuleCondition("driver_seatbelt", RuleOperator.EQ, "buckled");
        c.negated = true;
        neg.add(c);

        RuleEvaluator.GroupEvaluation unbuckled =
                RuleEvaluator.evaluateConditionGroupDetails(neg, key -> "unbuckled");
        assertTrue(unbuckled.result, "negated unbuckled true");
        assertTrue(unbuckled.conditions.get(0).met, "negated condition met");
        assertTrue(unbuckled.conditions.get(0).negated, "negated flag exposed");

        RuleEvaluator.GroupEvaluation buckled =
                RuleEvaluator.evaluateConditionGroupDetails(neg, key -> "buckled");
        assertFalse(buckled.result, "negated buckled false");
        assertFalse(buckled.conditions.get(0).met, "negated condition not met");
    }

    private static void testMissingDetails() {
        List<RuleCondition> missing = new ArrayList<>();
        missing.add(new RuleCondition("missing_key", RuleOperator.EQ, "value"));

        RuleEvaluator.GroupEvaluation nullValue =
                RuleEvaluator.evaluateConditionGroupDetails(missing, key -> null);
        assertFalse(nullValue.result, "missing null -> false");
        assertTrue(nullValue.hasMissing(), "missing null flagged");
        assertEqual("missing_key", nullValue.missingSensorKey, "missing key");
        assertTrue(nullValue.conditions.get(0).missing, "condition marked missing");
        assertFalse(nullValue.conditions.get(0).met, "missing condition not met");

        RuleEvaluator.GroupEvaluation dashValue =
                RuleEvaluator.evaluateConditionGroupDetails(missing, key -> "---");
        assertFalse(dashValue.result, "dash -> false");
        assertTrue(dashValue.hasMissing(), "dash flagged");
    }

    /** A missing value aborts the group before any later OR can revive it. */
    private static void testMissingAbortsGroup() {
        List<RuleCondition> group = new ArrayList<>();
        group.add(new RuleCondition("speed", RuleOperator.GT, "0"));
        RuleCondition gap = new RuleCondition("gap_key", RuleOperator.EQ, "x");
        gap.connector = LogicalOperator.AND;
        group.add(gap);
        RuleCondition last = new RuleCondition("driver_seatbelt", RuleOperator.EQ, "buckled");
        last.connector = LogicalOperator.OR;
        group.add(last);

        RuleEvaluator.GroupEvaluation g = RuleEvaluator.evaluateConditionGroupDetails(group, key -> {
            if ("speed".equals(key)) return "10";
            if ("gap_key".equals(key)) return null;
            return "buckled";
        });
        assertFalse(g.result, "missing aborts group despite later OR true");
        assertTrue(g.hasMissing(), "group reports missing");
        assertFalse(g.conditions.get(1).met, "missing condition not met");
        assertTrue(g.conditions.get(2).met, "later condition still evaluated for display");
    }

    private static void testGeofenceDefaultValue() {
        List<RuleCondition> eq = new ArrayList<>();
        eq.add(new RuleCondition("geo_home", RuleOperator.EQ, ""));

        RuleEvaluator.GroupEvaluation inside = RuleEvaluator.evaluateConditionGroupDetails(eq, key -> "inside");
        assertTrue(inside.result, "empty geofence value defaults to inside");
        assertEqual("inside", inside.conditions.get(0).expected, "geofence expected default");

        RuleEvaluator.GroupEvaluation outside = RuleEvaluator.evaluateConditionGroupDetails(eq, key -> "outside");
        assertFalse(outside.result, "outside does not match default inside");

        List<RuleCondition> neq = new ArrayList<>();
        neq.add(new RuleCondition("geo_home", RuleOperator.NEQ, ""));
        RuleEvaluator.GroupEvaluation neqOutside =
                RuleEvaluator.evaluateConditionGroupDetails(neq, key -> "outside");
        assertTrue(neqOutside.result, "NEQ empty geofence defaults to NEQ inside");
    }

    private static void testEmptyGroup() {
        RuleEvaluator.GroupEvaluation empty =
                RuleEvaluator.evaluateConditionGroupDetails(new ArrayList<>(), key -> null);
        assertFalse(empty.result, "empty group false");
        assertFalse(empty.hasMissing(), "empty group not missing");
        assertEqual(0, empty.conditions.size(), "empty group no conditions");

        RuleEvaluator.GroupEvaluation nullList =
                RuleEvaluator.evaluateConditionGroupDetails(null, key -> null);
        assertFalse(nullList.result, "null group false");
    }

    private static void testRestoredSuppression() {
        List<RuleCondition> conditions = new ArrayList<>();
        conditions.add(new RuleCondition("gear", RuleOperator.EQ, "P"));

        RuleEdgeLogic.Suppression restored =
                RuleEdgeLogic.detectSuppression(conditions, key -> "P", key -> true);
        assertTrue(restored.blocked(), "restored blocks");
        assertEqual(RuleEdgeLogic.Suppress.RESTORED, restored.reason, "restored reason");
        assertEqual("gear", restored.sensorKey, "restored key");

        RuleEdgeLogic.Suppression live =
                RuleEdgeLogic.detectSuppression(conditions, key -> "P", key -> false);
        assertFalse(live.blocked(), "live value not blocked");
        assertEqual(RuleEdgeLogic.Suppress.NONE, live.reason, "live reason none");

        RuleEdgeLogic.Suppression missing =
                RuleEdgeLogic.detectSuppression(conditions, key -> null, key -> false);
        assertTrue(missing.blocked(), "missing blocks");
        assertEqual(RuleEdgeLogic.Suppress.MISSING, missing.reason, "missing reason");
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
