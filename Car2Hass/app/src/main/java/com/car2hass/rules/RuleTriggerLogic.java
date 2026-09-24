package com.car2hass.rules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-condition transition state for rules that fire on a specific sensor
 * change. When at least one condition is flagged {@link RuleCondition#triggerOnChange}
 * the rule edges on that condition changing into a satisfying value, and the
 * remaining conditions act as guards evaluated at that moment. A guard that
 * flaps (for example the doors relocking after the rule opened them) therefore
 * cannot re-fire the rule.
 *
 * Pure Java (no Android), unit-testable. It mirrors {@link RuleEdgeLogic}'s
 * startup semantics: the first evaluation seeds without an edge, and the state
 * is not consumed while a {@code holdSeconds} window is pending, so the trigger
 * fires as soon as the hold elapses.
 */
public final class RuleTriggerLogic {

    public static final class Outcome {
        public final boolean firstEvaluation;
        /** A flagged condition changed from not-satisfied to satisfied now. */
        public final boolean transition;
        /** Guards hold and a trigger is pending, not held, not yet consumed. */
        public final boolean triggerEdge;

        Outcome(boolean firstEvaluation, boolean transition, boolean triggerEdge) {
            this.firstEvaluation = firstEvaluation;
            this.transition = transition;
            this.triggerEdge = triggerEdge;
        }
    }

    /** rule id → previous per-condition satisfaction, aligned with the conditions list. */
    private final Map<String, boolean[]> previousMets = new HashMap<>();
    /** rule ids with an unconsumed trigger transition. */
    private final Set<String> latched = new HashSet<>();

    public static boolean hasTriggerConditions(List<RuleCondition> conditions) {
        if (conditions == null) return false;
        for (RuleCondition c : conditions) {
            if (c != null && c.triggerOnChange) return true;
        }
        return false;
    }

    public void reset() {
        previousMets.clear();
        latched.clear();
    }

    public void retainRules(Set<String> ids) {
        previousMets.keySet().retainAll(ids);
        latched.retainAll(ids);
    }

    public Outcome evaluate(String ruleId, List<RuleCondition> conditions,
            RuleEvaluator.GroupEvaluation group, boolean holdPending) {
        if (ruleId == null || conditions == null || group == null
                || conditions.size() != group.conditions.size()) {
            return new Outcome(false, false, false);
        }

        boolean[] current = new boolean[conditions.size()];
        for (int i = 0; i < current.length; i++) {
            current[i] = group.conditions.get(i).met;
        }

        boolean[] previous = previousMets.get(ruleId);
        if (previous == null) {
            previousMets.put(ruleId, current);
            latched.remove(ruleId);
            return new Outcome(true, false, false);
        }

        boolean transition = false;
        for (int i = 0; i < conditions.size(); i++) {
            RuleCondition c = conditions.get(i);
            if (c == null || !c.triggerOnChange) continue;
            if (current[i] && !previous[i]) transition = true;
        }

        if (transition) latched.add(ruleId);
        // A guard failing clears any pending trigger: this is what stops a
        // relock (or any other guard edge) from re-firing the rule.
        if (!group.result) latched.remove(ruleId);

        boolean triggerEdge = group.result && latched.contains(ruleId) && !holdPending;
        if (triggerEdge) latched.remove(ruleId);
        // Mirror RuleEdgeLogic: keep the previous state frozen while a hold is
        // pending so the deferred trigger is still detected when it elapses.
        if (!holdPending) previousMets.put(ruleId, current);

        return new Outcome(false, transition, triggerEdge);
    }
}
