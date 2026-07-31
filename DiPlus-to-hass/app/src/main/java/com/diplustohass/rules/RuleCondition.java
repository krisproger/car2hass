package com.diplustohass.rules;

import org.json.JSONObject;

public class RuleCondition {
    public String sensorKey;
    public RuleOperator operator;
    public String value;
    public boolean negated;
    public LogicalOperator connector;

    public RuleCondition() {}

    public RuleCondition(String sensorKey, RuleOperator operator, String value) {
        this.sensorKey = sensorKey;
        this.operator = operator;
        this.value = value;
    }

    public static RuleCondition fromJson(JSONObject o) {
        RuleCondition c = new RuleCondition();
        c.sensorKey = o.optString("sensorKey", "");
        c.operator = Rule.parseOperator(o.optString("operator", "EQ"));
        c.value = o.optString("value", "");
        c.negated = o.optBoolean("negated", false);
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
            if (connector != null) o.put("connector", connector.name());
        } catch (Exception e) { /* ignored */ }
        return o;
    }
}
