package com.diplustohass.rules;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Rule {

    public String id;
    public String name;
    public boolean enabled = true;
    public boolean fireOnRisingEdge = true;
    public boolean fireOncePerSession = false;
    public long minIntervalSec = 1800;
    public long antiLoopWindowSec = 60;
    public long holdSeconds = 0;
    public long lastExecutedAtMs = 0;
    public long lastExecutedFalseAtMs = 0;
    public List<RuleCondition> conditions = new ArrayList<>();
    public List<RuleAction> actions = new ArrayList<>();
    public List<RuleAction> actionsOnFalse = new ArrayList<>();

    public static Rule create(String sensorKey) {
        Rule r = new Rule();
        r.id = generateId();
        if (sensorKey != null && !sensorKey.isEmpty()) {
            r.conditions.add(new RuleCondition(sensorKey, RuleOperator.EQ, ""));
        }
        return r;
    }

    public static Rule fromJson(JSONObject o) {
        Rule r = new Rule();
        r.id = o.optString("id", generateId());
        r.name = o.optString("name", "");
        r.enabled = o.optBoolean("enabled", true);
        r.fireOnRisingEdge = o.optBoolean("fireOnRisingEdge", true);
        r.fireOncePerSession = o.optBoolean("fireOncePerSession", false);
        r.minIntervalSec = o.optLong("minIntervalSec", 1800);
        r.antiLoopWindowSec = o.optLong("antiLoopWindowSec", 60);
        r.holdSeconds = o.optLong("holdSeconds", 0);
        // The old 30-minute default anti-loop window was far too long for
        // edge rules: repeated legitimate actions (e.g. unlock on every
        // parking) were suppressed. Migrate old-default edge rules to 60 s.
        if (r.fireOnRisingEdge && r.antiLoopWindowSec == 1800) {
            r.antiLoopWindowSec = 60;
        }
        r.lastExecutedAtMs = o.optLong("lastExecutedAtMs", 0);
        r.lastExecutedFalseAtMs = o.optLong("lastExecutedFalseAtMs", 0);

        JSONArray condArr = o.optJSONArray("conditions");
        if (condArr != null) {
            for (int i = 0; i < condArr.length(); i++) {
                JSONObject co = condArr.optJSONObject(i);
                if (co != null) r.conditions.add(RuleCondition.fromJson(co));
            }
        }

        JSONArray actArr = o.optJSONArray("actions");
        if (actArr != null) {
            for (int i = 0; i < actArr.length(); i++) {
                JSONObject ao = actArr.optJSONObject(i);
                if (ao != null) r.actions.add(RuleAction.fromJson(ao));
            }
        }

        JSONArray falseArr = o.optJSONArray("actionsOnFalse");
        if (falseArr != null) {
            for (int i = 0; i < falseArr.length(); i++) {
                JSONObject ao = falseArr.optJSONObject(i);
                if (ao != null) r.actionsOnFalse.add(RuleAction.fromJson(ao));
            }
        }

        if (r.conditions.isEmpty() && o.has("sensorKey")) {
            RuleCondition c = new RuleCondition();
            c.sensorKey = o.optString("sensorKey", "");
            c.operator = parseOperator(o.optString("operator", "EQ"));
            c.value = o.optString("value", "");
            r.conditions.add(c);
        }

        if (r.actions.isEmpty() && o.has("commandId")) {
            RuleAction a = new RuleAction();
            a.commandId = o.optString("commandId", "");
            a.commandValue = o.optString("commandValue", "");
            r.actions.add(a);
        }

        if (r.conditions.isEmpty()) {
            r.conditions.add(new RuleCondition("", RuleOperator.EQ, ""));
        }

        return r;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("enabled", enabled);
            o.put("fireOnRisingEdge", fireOnRisingEdge);
            o.put("fireOncePerSession", fireOncePerSession);
            o.put("minIntervalSec", minIntervalSec);
            o.put("antiLoopWindowSec", antiLoopWindowSec);
            o.put("holdSeconds", holdSeconds);
            o.put("lastExecutedAtMs", lastExecutedAtMs);
            o.put("lastExecutedFalseAtMs", lastExecutedFalseAtMs);

            JSONArray condArr = new JSONArray();
            for (RuleCondition c : conditions) condArr.put(c.toJson());
            o.put("conditions", condArr);

            JSONArray actArr = new JSONArray();
            for (RuleAction a : actions) actArr.put(a.toJson());
            o.put("actions", actArr);

            JSONArray falseArr = new JSONArray();
            for (RuleAction a : actionsOnFalse) falseArr.put(a.toJson());
            o.put("actionsOnFalse", falseArr);
        } catch (Exception e) {
            // JSONObject.put only throws on overflow; ignored here
        }
        return o;
    }

    private static String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static RuleOperator parseOperator(String s) {
        if (s == null) return RuleOperator.EQ;
        try {
            return RuleOperator.valueOf(s);
        } catch (IllegalArgumentException e) {
            return RuleOperator.EQ;
        }
    }
}
