package com.car2hass.rules;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RuleRegistry {

    private static final String PREFS_NAME = "rule_engine_state";
    private static final String KEY_EXAMPLES_SEEDED = "examples_seeded";

    /**
     * Seed disabled example rules on first run so new users have something
     * to start from. Runs at most once per install; if the user already has
     * rules (e.g. imported), nothing is added.
     */
    public static synchronized void seedExamplesIfFirstRun(Context ctx) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.getBoolean(KEY_EXAMPLES_SEEDED, false)) return;
            // Mark seeded regardless of outcome so we never re-seed after the
            // user deleted all examples.
            prefs.edit().putBoolean(KEY_EXAMPLES_SEEDED, true).apply();
            if (!load(ctx).isEmpty()) return;
            boolean ru = "ru".equals(Locale.getDefault().getLanguage());
            save(ctx, buildExampleRules(ru));
            com.car2hass.LogBuffer.i("RuleRegistry", "Seeded example rules (lang=" + (ru ? "ru" : "en") + ")");
        } catch (Exception e) {
            com.car2hass.LogBuffer.e("RuleRegistry", "seedExamplesIfFirstRun error: " + e.getMessage());
        }
    }

    private static List<Rule> buildExampleRules(boolean ru) {
        List<Rule> out = new ArrayList<>();

        Rule r1 = Rule.create("");
        r1.name = ru ? "Включение обдува сиденья водителя при +25" : "Driver seat vent above +25°C";
        r1.enabled = false;
        r1.minIntervalSec = 2;
        r1.antiLoopWindowSec = 15;
        r1.conditions.clear();
        r1.conditions.add(new RuleCondition("driver_seatbelt", RuleOperator.EQ, "buckled"));
        RuleCondition c12 = new RuleCondition("cabin_temp", RuleOperator.GT, "25");
        c12.connector = LogicalOperator.AND;
        r1.conditions.add(c12);
        RuleCondition c13 = new RuleCondition("outside_temp", RuleOperator.LT, "20");
        c13.connector = LogicalOperator.AND;
        c13.negated = true;
        r1.conditions.add(c13);
        r1.actions.clear();
        r1.actions.add(new RuleAction("driver_seat_vent", "high"));
        out.add(r1);

        Rule r2 = Rule.create("");
        r2.name = ru ? "Проветрить при сильном нажатии на тормоз" : "Vent windows on hard braking";
        r2.enabled = false;
        r2.minIntervalSec = 18;
        r2.conditions.clear();
        r2.conditions.add(new RuleCondition("brake_pedal", RuleOperator.GTE, "65"));
        r2.actions.clear();
        r2.actions.add(new RuleAction("windows_vent", ""));
        out.add(r2);

        Rule r3 = Rule.create("");
        r3.name = ru ? "При переключении на парковку открыть двери" : "Unlock doors when shifting to Park";
        r3.enabled = false;
        r3.conditions.clear();
        r3.conditions.add(new RuleCondition("gear", RuleOperator.EQ, "P"));
        r3.actions.clear();
        r3.actions.add(new RuleAction("doors_unlock", ""));
        out.add(r3);

        Rule r4 = Rule.create("");
        r4.name = ru ? "Включение обдува сиденья пассажира при +25" : "Passenger seat vent above +25°C";
        r4.enabled = false;
        r4.conditions.clear();
        r4.conditions.add(new RuleCondition("passenger_seatbelt", RuleOperator.EQ, "buckled"));
        RuleCondition c42 = new RuleCondition("cabin_temp", RuleOperator.GTE, "25");
        c42.connector = LogicalOperator.AND;
        r4.conditions.add(c42);
        r4.actions.clear();
        r4.actions.add(new RuleAction("passenger_seat_vent", "high"));
        out.add(r4);

        Rule r5 = Rule.create("");
        r5.name = ru ? "Подогрев сиденья водителя при −10°" : "Driver seat heat below −10°C";
        r5.enabled = false;
        r5.conditions.clear();
        r5.conditions.add(new RuleCondition("cabin_temp", RuleOperator.LT, "-10"));
        RuleCondition c52 = new RuleCondition("driver_seatbelt", RuleOperator.EQ, "buckled");
        c52.connector = LogicalOperator.AND;
        r5.conditions.add(c52);
        r5.actions.clear();
        r5.actions.add(new RuleAction("driver_seat_heat", "high"));
        out.add(r5);

        return out;
    }

    // Rule ids for which one-time load notices were already logged this
    // process — the 1s engine tick reloads rules constantly and the notices
    // must not spam the log.
    private static final java.util.Set<String> loadNoticesLogged = new java.util.HashSet<>();

    public static synchronized List<Rule> load(Context ctx) {
        String json = com.car2hass.AppConfig.getRulesJson(ctx);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        List<Rule> rules = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    Rule r = Rule.fromJson(obj);
                    if (r.id != null && loadNoticesLogged.add(r.id)) {
                        if (r.fireOnRisingEdge && r.minIntervalSec >= 1800) {
                            com.car2hass.LogBuffer.i("RuleRegistry",
                                "Rule '" + r.name + "': minInterval ignored (rising-edge rule)");
                        }
                        if (r.enabled) {
                            for (int ci = 0; ci < r.conditions.size(); ci++) {
                                RuleCondition cond = r.conditions.get(ci);
                                String v = cond.value;
                                if (v == null || v.isEmpty()) {
                                    if (cond.sensorKey != null && cond.sensorKey.startsWith("geo_")) {
                                        com.car2hass.LogBuffer.i("RuleRegistry",
                                            "Rule '" + r.name + "': condition #" + (ci + 1)
                                            + " has empty value — defaults to 'inside' for geofence sensor");
                                    } else {
                                        com.car2hass.LogBuffer.w("RuleRegistry",
                                            "Rule '" + r.name + "': condition #" + (ci + 1)
                                            + " has empty value — rule will never fire");
                                    }
                                }
                            }
                        }
                    }
                    rules.add(r);
                }
            }
        } catch (Exception e) {
            com.car2hass.LogBuffer.e("RuleRegistry", "load error: " + e.getMessage());
        }
        return rules;
    }

    public static synchronized void save(Context ctx, List<Rule> rules) {
        JSONArray arr = new JSONArray();
        if (rules != null) {
            for (Rule r : rules) {
                arr.put(r.toJson());
            }
        }
        com.car2hass.AppConfig.saveRulesJson(ctx, arr.toString());
    }

    public static synchronized void upsert(Context ctx, Rule rule) {
        if (rule == null) return;
        List<Rule> rules = load(ctx);
        boolean found = false;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).id != null && rules.get(i).id.equals(rule.id)) {
                rules.set(i, rule);
                found = true;
                break;
            }
        }
        if (!found) {
            rules.add(rule);
        }
        save(ctx, rules);
    }

    public static synchronized void delete(Context ctx, String ruleId) {
        if (ruleId == null) return;
        List<Rule> rules = load(ctx);
        rules.removeIf(r -> ruleId.equals(r.id));
        save(ctx, rules);
    }

    public static synchronized void updateLastExecuted(Context ctx, String ruleId, long nowMs) {
        if (ruleId == null) return;
        List<Rule> rules = load(ctx);
        for (Rule r : rules) {
            if (ruleId.equals(r.id)) {
                r.lastExecutedAtMs = nowMs;
                break;
            }
        }
        save(ctx, rules);
    }

    public static synchronized void updateLastExecutedFalse(Context ctx, String ruleId, long nowMs) {
        if (ruleId == null) return;
        List<Rule> rules = load(ctx);
        for (Rule r : rules) {
            if (ruleId.equals(r.id)) {
                r.lastExecutedFalseAtMs = nowMs;
                break;
            }
        }
        save(ctx, rules);
    }

    private RuleRegistry() {
    }
}
