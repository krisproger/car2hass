package com.diplustohass.rules;

import org.json.JSONObject;

public class NewModelTest {
    public static void main(String[] args) {
        // RuleCondition JSON roundtrip
        JSONObject cJson = new JSONObject();
        try {
            cJson.put("sensorKey", "driver_seatbelt");
            cJson.put("operator", "EQ");
            cJson.put("value", "buckled");
            cJson.put("negated", true);
            cJson.put("connector", "AND");
        } catch (Exception e) {
            System.err.println("Failed to build condition JSON: " + e.getMessage());
            System.exit(1);
        }
        RuleCondition c = RuleCondition.fromJson(cJson);
        assert "driver_seatbelt".equals(c.sensorKey) : "sensorKey";
        assert c.operator == RuleOperator.EQ : "operator";
        assert "buckled".equals(c.value) : "value";
        assert c.negated : "negated";
        assert c.connector == LogicalOperator.AND : "connector";
        JSONObject out = c.toJson();
        assert "driver_seatbelt".equals(out.optString("sensorKey")) : "toJson sensorKey";
        assert out.optBoolean("negated") : "toJson negated";

        // RuleAction JSON roundtrip
        JSONObject aJson = new JSONObject();
        try {
            aJson.put("commandId", "driver_seat_vent");
            aJson.put("commandValue", "high");
        } catch (Exception e) {
            System.err.println("Failed to build action JSON: " + e.getMessage());
            System.exit(1);
        }
        RuleAction a = RuleAction.fromJson(aJson);
        assert "driver_seat_vent".equals(a.commandId) : "commandId";
        assert "high".equals(a.commandValue) : "commandValue";
        JSONObject aOut = a.toJson();
        assert "high".equals(aOut.optString("commandValue")) : "toJson commandValue";

        // RuleCondition without connector
        JSONObject c2Json = new JSONObject();
        try {
            c2Json.put("sensorKey", "speed");
            c2Json.put("operator", "GT");
            c2Json.put("value", "0");
        } catch (Exception e) {
            System.err.println("Failed: " + e.getMessage());
            System.exit(1);
        }
        RuleCondition c2 = RuleCondition.fromJson(c2Json);
        assert c2.connector == null : "no connector for first condition";

        // LogicalOperator values
        assert LogicalOperator.valueOf("AND") == LogicalOperator.AND;
        assert LogicalOperator.valueOf("OR") == LogicalOperator.OR;

        System.out.println("All NewModel tests passed.");
    }
}
