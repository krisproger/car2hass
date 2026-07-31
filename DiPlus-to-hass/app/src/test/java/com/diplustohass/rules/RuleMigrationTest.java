package com.diplustohass.rules;

import org.json.JSONArray;
import org.json.JSONObject;

public class RuleMigrationTest {
    public static void main(String[] args) throws Exception {
        JSONObject newJson = new JSONObject();
        newJson.put("id", "test123");
        newJson.put("name", "My Rule");
        newJson.put("enabled", true);
        JSONArray conds = new JSONArray();
        JSONObject c1 = new JSONObject();
        c1.put("sensorKey", "speed");
        c1.put("operator", "GT");
        c1.put("value", "0");
        c1.put("negated", false);
        conds.put(c1);
        JSONObject c2 = new JSONObject();
        c2.put("sensorKey", "driver_seatbelt");
        c2.put("operator", "EQ");
        c2.put("value", "buckled");
        c2.put("negated", false);
        c2.put("connector", "AND");
        conds.put(c2);
        newJson.put("conditions", conds);
        JSONArray acts = new JSONArray();
        JSONObject a1 = new JSONObject();
        a1.put("commandId", "driver_seat_vent");
        a1.put("commandValue", "high");
        acts.put(a1);
        newJson.put("actions", acts);
        newJson.put("lastExecutedAtMs", 111L);
        newJson.put("lastExecutedFalseAtMs", 222L);

        Rule r = Rule.fromJson(newJson);
        assert r.conditions.size() == 2 : "two conditions loaded";
        assert r.actions.size() == 1 : "one action loaded";
        assert "speed".equals(r.conditions.get(0).sensorKey) : "first condition sensorKey";
        assert r.conditions.get(0).connector == null : "first has no connector";
        assert r.conditions.get(1).connector == LogicalOperator.AND : "second has AND connector";
        assert r.lastExecutedAtMs == 111L : "lastExecutedAtMs loaded";
        assert r.lastExecutedFalseAtMs == 222L : "lastExecutedFalseAtMs loaded";

        JSONObject out = r.toJson();
        assert out.has("conditions") : "has conditions";
        assert out.has("actions") : "has actions";
        assert out.optJSONArray("conditions").length() == 2 : "conditions count";
        assert out.optLong("lastExecutedAtMs") == 111L : "lastExecutedAtMs serialized";
        assert out.optLong("lastExecutedFalseAtMs") == 222L : "lastExecutedFalseAtMs serialized";

        JSONObject oldJson = new JSONObject();
        oldJson.put("id", "old123");
        oldJson.put("name", "Old Rule");
        oldJson.put("sensorKey", "driver_seatbelt");
        oldJson.put("operator", "EQ");
        oldJson.put("value", "buckled");
        oldJson.put("commandId", "driver_seat_vent");
        oldJson.put("commandValue", "high");

        Rule oldR = Rule.fromJson(oldJson);
        assert oldR.conditions.size() == 1 : "old -> 1 condition";
        assert oldR.actions.size() == 1 : "old -> 1 action";
        assert "driver_seatbelt".equals(oldR.conditions.get(0).sensorKey) : "old sensorKey";
        assert "driver_seat_vent".equals(oldR.actions.get(0).commandId) : "old commandId";
        assert oldR.lastExecutedAtMs == 0 : "old -> default lastExecutedAtMs";
        assert oldR.lastExecutedFalseAtMs == 0 : "old -> default lastExecutedFalseAtMs";

        JSONObject emptyJson = new JSONObject();
        emptyJson.put("id", "empty");
        Rule emptyR = Rule.fromJson(emptyJson);
        assert emptyR.conditions.size() == 1 : "empty -> 1 default condition";
        assert emptyR.actions.isEmpty() : "empty -> 0 actions";

        Rule created = Rule.create("speed");
        assert created.conditions.size() == 1 : "create has condition";
        assert "speed".equals(created.conditions.get(0).sensorKey) : "create sensorKey";

        System.out.println("All RuleMigration tests passed.");
    }
}
