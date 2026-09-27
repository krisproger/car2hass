package com.car2hass.rules;

import java.util.List;

/** Compact, single-line rendering of a rule's configuration for diagnostics. */
public final class RuleDescribe {

    public static String config(Rule r) {
        if (r == null) return "rule=null";
        return "name='" + r.name + "'"
                + " enabled=" + r.enabled
                + " rising=" + r.fireOnRisingEdge
                + " hold=" + r.holdSeconds + "s"
                + " min=" + r.minIntervalSec + "s"
                + " once=" + r.fireOncePerSession
                + " antiLoop=" + r.antiLoopWindowSec + "s"
                + " conds=[" + conditions(r) + "]"
                + " actions=[" + actions(r.actions) + "]"
                + " else=[" + actions(r.actionsOnFalse) + "]";
    }

    public static String conditions(Rule r) {
        StringBuilder sb = new StringBuilder();
        List<RuleCondition> conds = r.conditions;
        for (int i = 0; conds != null && i < conds.size(); i++) {
            RuleCondition c = conds.get(i);
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.sensorKey)
              .append(' ').append(c.operator != null ? c.operator.name() : "?")
              .append(" '").append(c.value == null ? "" : c.value).append('\'')
              .append(" conn=").append(c.connector != null ? c.connector.name() : "AND")
              .append(" neg=").append(c.negated)
              .append(" trig=").append(c.triggerOnChange);
        }
        return sb.toString();
    }

    public static String actions(List<RuleAction> actions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; actions != null && i < actions.size(); i++) {
            RuleAction a = actions.get(i);
            if (sb.length() > 0) sb.append(", ");
            sb.append(a.commandId).append(':').append(a.commandValue == null ? "" : a.commandValue);
        }
        return sb.toString();
    }

    private RuleDescribe() {
    }
}
