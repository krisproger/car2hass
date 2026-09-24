package com.car2hass.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class RuleEvaluator {

    public enum SkipReason {
        NONE,
        DISABLED,
        CONDITION_FALSE,
        NOT_RISING_EDGE,
        COOLDOWN
    }

    /** Why the full rule fire decision was skipped. */
    public enum FireReason {
        NONE,
        DISABLED,
        FIRE_ONCE_DONE,
        HOLD_PENDING,
        CONDITION_FALSE,
        NOT_EDGE,
        COOLDOWN
    }

    public static final class FireDecision {
        public final boolean fire;
        /** true → actions, false → actionsOnFalse (only meaningful when firing). */
        public final boolean fireBranch;
        public final FireReason reason;

        FireDecision(boolean fire, boolean fireBranch, FireReason reason) {
            this.fire = fire;
            this.fireBranch = fireBranch;
            this.reason = reason;
        }
    }

    public static final class Decision {
        public final boolean fire;
        public final boolean conditionMet;
        public final SkipReason reason;

        public Decision(boolean fire, boolean conditionMet, SkipReason reason) {
            this.fire = fire;
            this.conditionMet = conditionMet;
            this.reason = reason;
        }
    }

    public static Decision evaluate(
            boolean enabled, RuleOperator op, String actual, String expected,
            boolean fireOnRisingEdge, boolean previousCondition,
            long nowMs, long lastExecutedMs, long minIntervalMs) {

        boolean conditionMet = op.apply(actual, expected);

        if (!enabled) {
            return new Decision(false, conditionMet, SkipReason.DISABLED);
        }

        if (!conditionMet) {
            return new Decision(false, false, SkipReason.CONDITION_FALSE);
        }

        if (fireOnRisingEdge && previousCondition) {
            return new Decision(false, true, SkipReason.NOT_RISING_EDGE);
        }

        if (nowMs - lastExecutedMs < minIntervalMs) {
            return new Decision(false, true, SkipReason.COOLDOWN);
        }

        return new Decision(true, true, SkipReason.NONE);
    }

    /**
     * Pure full-rule fire decision shared by the engine and tests. For rules
     * with {@code triggerOnChange} conditions the true branch edges on the
     * per-condition {@code triggerEdge}; otherwise it keeps the classic group
     * rising edge ({@code previousCondition}).
     */
    public static FireDecision decideFire(
            boolean enabled,
            boolean groupResult,
            boolean hasActionsOnFalse,
            boolean fireOnRisingEdge,
            boolean previousCondition,
            boolean hasTriggers,
            boolean triggerEdge,
            boolean fireOnceDone,
            boolean holdPending,
            long nowMs,
            long lastExecutedMs,
            long cooldownMs) {

        if (!enabled) {
            return new FireDecision(false, false, FireReason.DISABLED);
        }
        if (fireOnceDone) {
            return new FireDecision(false, false, FireReason.FIRE_ONCE_DONE);
        }
        if (holdPending) {
            return new FireDecision(false, false, FireReason.HOLD_PENDING);
        }

        boolean fireBranch;
        boolean edgeOk;
        if (groupResult) {
            fireBranch = true;
            boolean rising = hasTriggers ? triggerEdge : !previousCondition;
            edgeOk = !fireOnRisingEdge || rising;
        } else if (hasActionsOnFalse) {
            fireBranch = false;
            edgeOk = !fireOnRisingEdge || !previousCondition;
        } else {
            return new FireDecision(false, false, FireReason.CONDITION_FALSE);
        }

        if (!edgeOk) {
            return new FireDecision(false, fireBranch, FireReason.NOT_EDGE);
        }
        if (nowMs - lastExecutedMs < cooldownMs) {
            return new FireDecision(false, fireBranch, FireReason.COOLDOWN);
        }
        return new FireDecision(true, fireBranch, FireReason.NONE);
    }

    /** Per-condition outcome of a group evaluation, for diagnostics/tests. */
    public static final class ConditionEvaluation {
        public final String sensorKey;
        /** Value exactly as read from the signal store (untranslated). */
        public final String rawValue;
        /** Value after {@link com.car2hass.SignalTranslator} translation. */
        public final String actual;
        public final String expected;
        public final String operatorName;
        public final LogicalOperator connector;
        public final boolean missing;
        public final boolean negated;
        public final boolean met;

        ConditionEvaluation(String sensorKey, String rawValue, String expected, String actual,
                String operatorName, LogicalOperator connector,
                boolean missing, boolean negated, boolean met) {
            this.sensorKey = sensorKey;
            this.rawValue = rawValue;
            this.expected = expected;
            this.actual = actual;
            this.operatorName = operatorName;
            this.connector = connector;
            this.missing = missing;
            this.negated = negated;
            this.met = met;
        }

        public String connectorName() {
            return connector != null ? connector.name() : "AND";
        }

        /** One compact "key raw='x'->'y' OP expected='z' conn=AND neg=false met=true" chunk. */
        public String describe() {
            return sensorKey + " raw='" + (rawValue == null ? "---" : rawValue)
                    + "'->'" + (actual == null ? "---" : actual) + "' "
                    + (operatorName != null ? operatorName : "?")
                    + " expected='" + (expected == null ? "" : expected) + "'"
                    + " conn=" + connectorName()
                    + " neg=" + negated
                    + " met=" + met;
        }
    }

    /** Full result of an AND/OR condition group with per-condition details. */
    public static final class GroupEvaluation {
        public final boolean result;
        public final String missingSensorKey;
        public final List<ConditionEvaluation> conditions;

        GroupEvaluation(boolean result, String missingSensorKey,
                List<ConditionEvaluation> conditions) {
            this.result = result;
            this.missingSensorKey = missingSensorKey;
            this.conditions = conditions;
        }

        public boolean hasMissing() {
            return missingSensorKey != null;
        }
    }

    public static boolean evaluateConditionGroup(List<RuleCondition> conditions,
                                                  Function<String, String> signalLookup) {
        return evaluateConditionGroupDetails(conditions, signalLookup).result;
    }

    public static GroupEvaluation evaluateConditionGroupDetails(
            List<RuleCondition> conditions, Function<String, String> signalLookup) {
        List<ConditionEvaluation> details = new ArrayList<>();
        if (conditions == null || conditions.isEmpty()) {
            return new GroupEvaluation(false, null, details);
        }

        boolean result = false;
        boolean aborted = false;
        String missingKey = null;
        for (int i = 0; i < conditions.size(); i++) {
            RuleCondition c = conditions.get(i);
            String raw = signalLookup.apply(c.sensorKey);
            boolean missing = raw == null || "---".equals(raw);

            // Geofence sensors: an empty condition value defaults to "inside"
            // so legacy rules saved without a value keep working (EQ "" behaves
            // as EQ "inside", NEQ "" as NEQ "inside").
            String expected = c.value;
            if ((expected == null || expected.isEmpty())
                    && c.sensorKey != null && c.sensorKey.startsWith("geo_")) {
                expected = "inside";
            }

            String translated = missing ? null
                    : com.car2hass.SignalTranslator.translateEnumValue(c.sensorKey, raw);
            boolean condResult = !missing && c.operator.apply(translated, expected);
            if (c.negated) condResult = !condResult;

            // The engine aborts the whole group at the first missing value;
            // later conditions are still shown, but cannot change the result.
            if (!aborted) {
                if (missing) {
                    result = false;
                    aborted = true;
                    missingKey = c.sensorKey;
                } else if (i == 0) {
                    result = condResult;
                } else if (c.connector == LogicalOperator.OR) {
                    result = result || condResult;
                } else {
                    result = result && condResult;
                }
            }

            details.add(new ConditionEvaluation(c.sensorKey, raw, expected, translated,
                    c.operator != null ? c.operator.name() : null,
                    i == 0 ? null : c.connector,
                    missing, c.negated, missing ? false : condResult));
        }
        return new GroupEvaluation(result, missingKey, details);
    }

    private RuleEvaluator() {
    }
}
