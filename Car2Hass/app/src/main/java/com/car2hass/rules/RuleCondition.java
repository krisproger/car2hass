package com.car2hass.rules;

import org.json.JSONObject;

public class RuleCondition {
    public String sensorKey;
    public RuleOperator operator;
    public String value;
    public boolean negated;
    public LogicalOperator connector;
    /** Rule edges on this condition's transition into a satisfying value. */
    public boolean triggerOnChange;

    public RuleCondition() {}

    public RuleCondition(String sensorKey, RuleOperator operator, String value) {
        this.sensorKey = sensorKey;
        this.operator = operator;
        this.value = value;
    }

    /**
     * Maps the connector spinner selection to a logical operator. Only the
     * explicit OR item (position 1) is OR; everything else — including
     * {@code AdapterView.INVALID_POSITION} from a row whose adapter was never
     * attached — defaults to AND.
     */
    public static LogicalOperator connectorForSpinnerPosition(int position) {
        return position == 1 ? LogicalOperator.OR : LogicalOperator.AND;
    }

    public static RuleCondition fromJson(JSONObject o) {
        RuleCondition c = new RuleCondition();
        c.sensorKey = o.optString("sensorKey", "");
        c.operator = Rule.parseOperator(o.optString("operator", "EQ"));
        c.value = o.optString("value", "");
        c.negated = o.optBoolean("negated", false);
        c.triggerOnChange = o.optBoolean("triggerOnChange", false);
        String conn = o.optString("connector", null);
        c.connector = (conn != null && !conn.isEmpty()) ? LogicalOperator.valueOf(conn) : null;
        return c;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("sensorKey", sensorKey);
            o.put("operator", operator != null ? operator.name() : "EQ");
            o.put("value", value != null ? value : "");
            o.put("negated", negated);
            o.put("triggerOnChange", triggerOnChange);
            if (connector != null) o.put("connector", connector.name());
        } catch (Exception e) { /* ignored */ }
        return o;
    }
}
