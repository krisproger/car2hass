package com.car2hass.rules;

import java.util.Collections;
import java.util.function.Function;
import java.util.function.Predicate;

public class RuleStalenessTest {
    public static void main(String[] args) {
        RuleCondition c = new RuleCondition("window_fl", RuleOperator.EQ, "open");
        Function<String, String> value = k -> "open";
        Predicate<String> restored = k -> false;

        RuleEdgeLogic.Suppression fresh = RuleEdgeLogic.detectSuppression(
                Collections.singletonList(c), value, restored, k -> 1000L, 600_000L);
        if (fresh.blocked()) throw new AssertionError("fresh value must not be suppressed");

        RuleEdgeLogic.Suppression stale = RuleEdgeLogic.detectSuppression(
                Collections.singletonList(c), value, restored, k -> 700_000L, 600_000L);
        if (!stale.blocked() || stale.reason != RuleEdgeLogic.Suppress.STALE)
            throw new AssertionError("stale value must be suppressed as STALE");

        System.out.println("All RuleStaleness tests passed.");
    }
}
