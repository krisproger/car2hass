package com.car2hass.rules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure rule edge/seed state machine, decoupled from Android so it can be unit
 * tested. It guards against false triggers on startup:
 * <ul>
 *   <li>a sensor that only holds a restored last-session value blocks the rule
 *       until live data arrives;</li>
 *   <li>the first evaluation of a rule in a run records the condition state but
 *       never counts as a rising edge.</li>
 * </ul>
 */
public final class RuleEdgeLogic {

    public enum Suppress {
        NONE,
        MISSING,
        RESTORED
    }

    public static final class Suppression {
        public final Suppress reason;
        public final String sensorKey;

        Suppression(Suppress reason, String sensorKey) {
            this.reason = reason;
            this.sensorKey = sensorKey;
        }

        public boolean blocked() {
            return reason != Suppress.NONE;
        }
    }

    public static final class Outcome {
        public final boolean firstEvaluation;
        public final boolean previousCondition;
        public final boolean stateChanged;
        public final boolean risingEdge;

        Outcome(boolean firstEvaluation, boolean previousCondition,
                boolean stateChanged, boolean risingEdge) {
            this.firstEvaluation = firstEvaluation;
            this.previousCondition = previousCondition;
            this.stateChanged = stateChanged;
            this.risingEdge = risingEdge;
        }
    }

    private final Map<String, Boolean> previous = new HashMap<>();
    private final Set<String> evaluated = new HashSet<>();

    public void reset() {
        previous.clear();
        evaluated.clear();
    }

    public void retainRules(Set<String> ids) {
        previous.keySet().retainAll(ids);
        evaluated.retainAll(ids);
    }

    public void loadPrevious(String ruleId, boolean value) {
        previous.put(ruleId, value);
    }

    public Map<String, Boolean> previousConditions() {
        return previous;
    }

    /**
     * Returns the first referenced sensor that has no usable live value.
     * {@code restoredLookup} may be null when source tracking is unavailable.
     */
    public static Suppression detectSuppression(List<RuleCondition> conditions,
            Function<String, String> valueLookup,
            Predicate<String> restoredLookup) {
        if (conditions == null) return new Suppression(Suppress.NONE, null);
        for (RuleCondition c : conditions) {
            if (c == null || c.sensorKey == null || c.sensorKey.isEmpty()) continue;
            if (valueLookup.apply(c.sensorKey) == null) {
                return new Suppression(Suppress.MISSING, c.sensorKey);
            }
            if (restoredLookup != null && Boolean.TRUE.equals(restoredLookup.test(c.sensorKey))) {
                return new Suppression(Suppress.RESTORED, c.sensorKey);
            }
        }
        return new Suppression(Suppress.NONE, null);
    }

    /**
     * Records one evaluation and reports the resulting transition. The first
     * evaluation in a run always reports no edge. While a hold window is
     * pending the stored state is left untouched so the eventual edge is not
     * consumed early.
     */
    public Outcome evaluate(String ruleId, boolean conditionMet, boolean holdPending) {
        boolean first = !evaluated.contains(ruleId);
        if (first) {
            evaluated.add(ruleId);
            previous.put(ruleId, conditionMet);
            return new Outcome(true, conditionMet, false, false);
        }
        boolean prev = Boolean.TRUE.equals(previous.get(ruleId));
        boolean changed = prev != conditionMet;
        boolean rising = conditionMet && !prev;
        if (!holdPending) {
            previous.put(ruleId, conditionMet);
        }
        return new Outcome(false, prev, changed, rising);
    }
}
