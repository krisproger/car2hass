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

    /** Per-condition outcome of a group evaluation, for diagnostics/tests. */
    public static final class ConditionEvaluation {
        public final String sensorKey;
        public final String expected;
        public final String actual;
        public final boolean missing;
        public final boolean negated;
        public final boolean met;

        ConditionEvaluation(String sensorKey, String expected, String actual,
                boolean missing, boolean negated, boolean met) {
            this.sensorKey = sensorKey;
            this.expected = expected;
            this.actual = actual;
            this.missing = missing;
            this.negated = negated;
            this.met = met;
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

            details.add(new ConditionEvaluation(c.sensorKey, expected, translated,
                    missing, c.negated, missing ? false : condResult));
        }
        return new GroupEvaluation(result, missingKey, details);
    }

    private RuleEvaluator() {
    }
}
