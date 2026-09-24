package com.car2hass.rules;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Tests for per-condition trigger rules (issue #70): a rule may edge on a
 * specific sensor's transition instead of on the whole condition group, so a
 * guard condition flapping (e.g. doors relocking while parked) cannot re-fire
 * the rule.
 */
public class RuleTriggerLogicTest {

    private static final long T = 1_000_000_000L;

    public static void main(String[] args) {
        testFlaggedTransitionFiresOnceWhileGuardsHold();
        testUnknownThenMatchingDoesNotFire();
        testGuardsFailingAtTransitionNoFire();
        testHoldDefersTriggerUntilElapsed();
        testNegatedFlaggedCondition();
        testUnflaggedRuleKeepsGroupEdge();
        testTriggerEdgeGatesFireEvenAfterCooldown();
        testCooldownAndFireOnceInteraction();
        testJsonRoundTrip();

        System.out.println("All RuleTriggerLogic tests passed.");
    }

    /** gear shifts into P once while the doors are locked; a relock must not re-fire. */
    private static void testFlaggedTransitionFiresOnceWhileGuardsHold() {
        RuleTriggerLogic logic = new RuleTriggerLogic();
        List<RuleCondition> conds = gearAndDoors();
        Map<String, String> signals = new HashMap<>();
        signals.put("gear", "D");
        signals.put("doors_all_lock", "locked");
        Function<String, String> lookup = signals::get;

        RuleTriggerLogic.Outcome first = eval(logic, conds, lookup, false);
        assertTrue(first.firstEvaluation, "first evaluation seeds");
        assertFalse(first.triggerEdge, "seeding does not fire");

        signals.put("gear", "P");
        RuleTriggerLogic.Outcome intoPark = eval(logic, conds, lookup, false);
        assertTrue(intoPark.transition, "gear D->P is a trigger transition");
        assertTrue(intoPark.triggerEdge, "guards hold -> trigger edge");

        signals.put("gear", "D");
        eval(logic, conds, lookup, false);
        signals.put("gear", "P");
        RuleTriggerLogic.Outcome parked = eval(logic, conds, lookup, false);
        assertTrue(parked.triggerEdge, "another real D->P fires again");

        // Doors unlock (the rule action) then relock while still parked: the
        // guard flap must not produce a trigger edge.
        signals.put("doors_all_lock", "unlocked");
        RuleTriggerLogic.Outcome unlock = eval(logic, conds, lookup, false);
        assertFalse(unlock.triggerEdge, "guard going false is not a trigger");
        signals.put("doors_all_lock", "locked");
        RuleTriggerLogic.Outcome relock = eval(logic, conds, lookup, false);
        assertFalse(relock.triggerEdge, "guard relock must not re-fire the rule");
        assertFalse(relock.transition, "gear did not change again");
    }

    /** The first live value (P) is a seed, not a transition from unknown. */
    private static void testUnknownThenMatchingDoesNotFire() {
        RuleTriggerLogic logic = new RuleTriggerLogic();
        List<RuleCondition> conds = gearAndDoors();
        Map<String, String> signals = new HashMap<>();
        signals.put("gear", "P");
        signals.put("doors_all_lock", "locked");

        RuleTriggerLogic.Outcome first = eval(logic, conds, signals::get, false);
        assertTrue(first.firstEvaluation, "unknown->P seeds");
        assertFalse(first.triggerEdge, "unknown->P must not fire");

        RuleTriggerLogic.Outcome again = eval(logic, conds, signals::get, false);
        assertFalse(again.triggerEdge, "staying in P is not a new transition");
    }

    /** gear enters P while the guard fails: no fire, and no delayed fire on relock. */
    private static void testGuardsFailingAtTransitionNoFire() {
        RuleTriggerLogic logic = new RuleTriggerLogic();
        List<RuleCondition> conds = gearAndDoors();
        Map<String, String> signals = new HashMap<>();
        signals.put("gear", "D");
        signals.put("doors_all_lock", "unlocked");

        eval(logic, conds, signals::get, false);

        signals.put("gear", "P");
        RuleTriggerLogic.Outcome transition = eval(logic, conds, signals::get, false);
        assertTrue(transition.transition, "gear transitioned");
        assertFalse(transition.triggerEdge, "guard fails at transition -> no fire");

        signals.put("doors_all_lock", "locked");
        RuleTriggerLogic.Outcome relock = eval(logic, conds, signals::get, false);
        assertFalse(relock.triggerEdge, "relock after a missed transition must not fire");
    }

    private static void testHoldDefersTriggerUntilElapsed() {
        RuleTriggerLogic logic = new RuleTriggerLogic();
        List<RuleCondition> conds = gearAndDoors();
        Map<String, String> signals = new HashMap<>();
        signals.put("gear", "D");
        signals.put("doors_all_lock", "locked");
        eval(logic, conds, signals::get, false);

        signals.put("gear", "P");
        RuleTriggerLogic.Outcome held = eval(logic, conds, signals::get, true);
        assertTrue(held.transition, "transition seen during hold");
        assertFalse(held.triggerEdge, "hold defers the trigger");

        RuleTriggerLogic.Outcome released = eval(logic, conds, signals::get, false);
        assertTrue(released.triggerEdge, "trigger fires once the hold elapses");
        assertFalse(eval(logic, conds, signals::get, false).triggerEdge, "only once");
    }

    private static void testNegatedFlaggedCondition() {
        RuleTriggerLogic logic = new RuleTriggerLogic();
        List<RuleCondition> conds = new ArrayList<>();
        RuleCondition c = new RuleCondition("driver_seatbelt", RuleOperator.EQ, "buckled");
        c.negated = true;
        c.triggerOnChange = true;
        conds.add(c);
        Map<String, String> signals = new HashMap<>();
        signals.put("driver_seatbelt", "buckled");
        eval(logic, conds, signals::get, false);

        signals.put("driver_seatbelt", "unbuckled");
        assertTrue(eval(logic, conds, signals::get, false).triggerEdge,
                "negated condition now satisfied -> trigger edge");
        assertFalse(eval(logic, conds, signals::get, false).triggerEdge,
                "no repeat while still unbuckled");
    }

    private static void testUnflaggedRuleKeepsGroupEdge() {
        List<RuleCondition> conds = new ArrayList<>();
        conds.add(new RuleCondition("gear", RuleOperator.EQ, "P"));
        RuleCondition doors = new RuleCondition("doors_all_lock", RuleOperator.EQ, "locked");
        doors.connector = LogicalOperator.AND;
        conds.add(doors);
        assertFalse(RuleTriggerLogic.hasTriggerConditions(conds),
                "unflagged rule has no trigger conditions");

        RuleEvaluator.FireDecision rising = RuleEvaluator.decideFire(
                true, true, false, true, false, false, false, false, false,
                T, 0, 5000);
        assertTrue(rising.fire, "unflagged rising edge still fires");
        assertTrue(rising.fireBranch, "true branch");

        RuleEvaluator.FireDecision held = RuleEvaluator.decideFire(
                true, true, false, true, true, false, false, false, false,
                T, 0, 5000);
        assertFalse(held.fire, "already-true group is not a rising edge");
    }

    private static void testTriggerEdgeGatesFireEvenAfterCooldown() {
        // Group holds and the cooldown is long elapsed, but there is no trigger
        // edge (e.g. only a guard changed) -> must not fire.
        RuleEvaluator.FireDecision noEdge = RuleEvaluator.decideFire(
                true, true, false, true, false, true, false, false, false,
                T, 0, 5000);
        assertFalse(noEdge.fire, "group holds without a trigger edge -> no fire");

        RuleEvaluator.FireDecision edge = RuleEvaluator.decideFire(
                true, true, false, true, false, true, true, false, false,
                T, 0, 5000);
        assertTrue(edge.fire, "trigger edge with guards holding -> fire");
    }

    private static void testCooldownAndFireOnceInteraction() {
        RuleEvaluator.FireDecision cooldown = RuleEvaluator.decideFire(
                true, true, false, true, false, true, true, false, false,
                T + 1000, T, 5000);
        assertFalse(cooldown.fire, "cooldown blocks a fresh trigger");
        assertEqual(RuleEvaluator.FireReason.COOLDOWN, cooldown.reason, "cooldown reason");

        RuleEvaluator.FireDecision afterCooldown = RuleEvaluator.decideFire(
                true, true, false, true, false, true, true, false, false,
                T + 6000, T, 5000);
        assertTrue(afterCooldown.fire, "fresh trigger after cooldown fires");

        RuleEvaluator.FireDecision onceDone = RuleEvaluator.decideFire(
                true, true, false, true, false, true, true, true, false,
                T + 6000, 0, 5000);
        assertFalse(onceDone.fire, "fireOncePerSession blocks repeats");

        RuleEvaluator.FireDecision hold = RuleEvaluator.decideFire(
                true, true, false, true, false, true, true, false, true,
                T + 6000, 0, 5000);
        assertFalse(hold.fire, "pending hold blocks the trigger");

        RuleEvaluator.FireDecision disabled = RuleEvaluator.decideFire(
                false, true, false, true, false, true, true, false, false,
                T + 6000, 0, 5000);
        assertFalse(disabled.fire, "disabled rule never fires");
    }

    private static void testJsonRoundTrip() {
        RuleCondition flagged = new RuleCondition("gear", RuleOperator.EQ, "P");
        flagged.triggerOnChange = true;
        JSONObject json = flagged.toJson();
        assertTrue(json.optBoolean("triggerOnChange", false), "flag serialized");
        RuleCondition restored = RuleCondition.fromJson(json);
        assertTrue(restored.triggerOnChange, "flag round-trips");
        assertEqual("gear", restored.sensorKey, "sensorKey kept");
        assertEqual(RuleOperator.EQ, restored.operator, "operator kept");
        assertEqual("P", restored.value, "value kept");

        JSONObject old = new JSONObject();
        try {
            old.put("sensorKey", "speed");
            old.put("operator", "GT");
            old.put("value", "0");
        } catch (Exception e) {
            throw new AssertionError("json build failed: " + e.getMessage());
        }
        RuleCondition legacy = RuleCondition.fromJson(old);
        assertFalse(legacy.triggerOnChange, "absent flag defaults to false");
    }

    // ---- helpers ----

    /** gear == P (trigger) AND doors_all_lock == locked (guard). */
    private static List<RuleCondition> gearAndDoors() {
        List<RuleCondition> conds = new ArrayList<>();
        RuleCondition gear = new RuleCondition("gear", RuleOperator.EQ, "P");
        gear.triggerOnChange = true;
        conds.add(gear);
        RuleCondition doors = new RuleCondition("doors_all_lock", RuleOperator.EQ, "locked");
        doors.connector = LogicalOperator.AND;
        conds.add(doors);
        return conds;
    }

    private static RuleTriggerLogic.Outcome eval(RuleTriggerLogic logic,
            List<RuleCondition> conds, Function<String, String> lookup, boolean holdPending) {
        RuleEvaluator.GroupEvaluation group =
                RuleEvaluator.evaluateConditionGroupDetails(conds, lookup);
        return logic.evaluate("r", conds, group, holdPending);
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
