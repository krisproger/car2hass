package com.car2hass.rules;

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

    public static boolean evaluateConditionGroup(List<RuleCondition> conditions,
                                                  Function<String, String> signalLookup) {
        if (conditions == null || conditions.isEmpty()) return false;

        boolean result = false;
        for (int i = 0; i < conditions.size(); i++) {
            RuleCondition c = conditions.get(i);
            String raw = signalLookup.apply(c.sensorKey);
            if (raw == null || "---".equals(raw)) return false;

            // Geofence sensors: an empty condition value defaults to "inside"
            // so legacy rules saved without a value keep working (EQ "" behaves
            // as EQ "inside", NEQ "" as NEQ "inside").
            String expected = c.value;
            if ((expected == null || expected.isEmpty())
                    && c.sensorKey != null && c.sensorKey.startsWith("geo_")) {
                expected = "inside";
            }

            String translated = com.car2hass.SignalTranslator.translateEnumValue(c.sensorKey, raw);
            boolean condResult = c.operator.apply(translated, expected);
            if (c.negated) condResult = !condResult;

            if (i == 0) {
                result = condResult;
            } else if (c.connector == LogicalOperator.OR) {
                result = result || condResult;
            } else {
                result = result && condResult;
            }
        }
        return result;
    }

    private RuleEvaluator() {
    }
}
