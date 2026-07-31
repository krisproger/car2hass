package com.diplustohass.rules;

import org.json.JSONObject;

public class RuleAction {
    public String commandId;
    public String commandValue;

    public RuleAction() {}

    public RuleAction(String commandId, String commandValue) {
        this.commandId = commandId;
        this.commandValue = commandValue;
    }

    public static RuleAction fromJson(JSONObject o) {
        RuleAction a = new RuleAction();
        a.commandId = o.optString("commandId", "");
        a.commandValue = o.optString("commandValue", "");
        return a;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("commandId", commandId);
            o.put("commandValue", commandValue);
        } catch (Exception e) { /* ignored */ }
        return o;
    }
}
