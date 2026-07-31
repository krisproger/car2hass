package com.diplustohass.rules;

import android.content.Context;
import android.content.SharedPreferences;

import com.diplustohass.CommandRegistry;
import com.diplustohass.DiPlusCommandSender;
import com.diplustohass.LogBuffer;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class RuleEngine {

    private final Context appContext;
    private final Function<String, String> signalLookup;
    private final AntiLoopGuard guard = new AntiLoopGuard();
    private ScheduledExecutorService executor;
    private volatile boolean started = false;

    // Per-rule previous condition state (keyed by rule id)
    // Thread-safe: only accessed from the single executor thread.
    private java.util.HashMap<String, Boolean> previousConditions = new java.util.HashMap<>();
    private final Set<String> firedOncePerSession = new HashSet<>();
    // Rising-edge debounce: rule id → timestamp when its condition first became
    // true in the current true-period. Used by the holdSeconds gate.
    private final java.util.HashMap<String, Long> conditionTrueSince = new java.util.HashMap<>();

    // Edge rules throttle on the transition itself; this small fixed window
    // only guards against signal bounce right at the edge. Level-triggered
    // rules keep using the configurable minIntervalSec cooldown.
    private static final long EDGE_ANTI_BOUNCE_MS = 5000;

    private static final String PREFS_NAME = "rule_engine_state";
    private static final String KEY_PREV_CONDITIONS = "previous_conditions";

    public RuleEngine(Context appContext, Function<String, String> signalLookup) {
        this.appContext = appContext;
        this.signalLookup = signalLookup;
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        loadPreviousConditions();
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
        LogBuffer.i("RuleEngine", "Engine started, 1s evaluation loop");
    }

    public synchronized void stop() {
        started = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        savePreviousConditions();
        guard.clear();
        previousConditions.clear();
        conditionTrueSince.clear();
        firedOncePerSession.clear();
        LogBuffer.i("RuleEngine", "Engine stopped");
    }

    /** Called by UI after rules are added/edited/deleted to reload state. */
    public synchronized void onRulesChanged() {
        guard.clear();
        firedOncePerSession.clear();
        conditionTrueSince.clear();
        // Prune state for deleted rules but keep the rest, so merely editing
        // a rule does not cause it to re-fire as a false rising edge.
        try {
            Set<String> ids = new HashSet<>();
            for (Rule r : RuleRegistry.load(appContext)) ids.add(r.id);
            previousConditions.keySet().retainAll(ids);
            savePreviousConditions();
        } catch (Exception e) {
            LogBuffer.w("RuleEngine", "onRulesChanged prune error: " + e.getMessage());
        }
        LogBuffer.i("RuleEngine", "Rules changed — state pruned");
    }

    private void loadPreviousConditions() {
        try {
            String json = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_PREV_CONDITIONS, null);
            if (json == null || json.isEmpty()) return;
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                previousConditions.put(k, obj.optBoolean(k, false));
            }
            LogBuffer.i("RuleEngine", "Loaded " + previousConditions.size() + " previous condition states");
        } catch (Exception e) {
            LogBuffer.w("RuleEngine", "load state error: " + e.getMessage());
        }
    }

    private void savePreviousConditions() {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, Boolean> e : previousConditions.entrySet()) {
                obj.put(e.getKey(), e.getValue());
            }
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_PREV_CONDITIONS, obj.toString()).apply();
        } catch (Exception e) {
            LogBuffer.w("RuleEngine", "save state error: " + e.getMessage());
        }
    }

    // ---- internal ----

    private void tick() {
        if (!started) return;
        try {
            List<Rule> rules = RuleRegistry.load(appContext);
            for (Rule r : rules) {
                evaluateRule(r);
            }
        } catch (Exception e) {
            LogBuffer.e("RuleEngine", "tick error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void evaluateRule(Rule rule) {
        if (rule.id == null) return;

        long now = System.currentTimeMillis();

        // Skip the rule until at least one referenced sensor has data.
        // Without this guard, the first ticks after startup run with an empty
        // signal cache, overwrite a persisted prev=true state with false, and
        // later produce a false rising edge (and wrong trigger) when data arrives.
        boolean hasSensorData = false;
        for (RuleCondition c : rule.conditions) {
            if (c.sensorKey == null || c.sensorKey.isEmpty()) continue;
            if (signalLookup.apply(c.sensorKey) != null) {
                hasSensorData = true;
                break;
            }
        }
        if (!hasSensorData) {
            LogBuffer.i("RuleEngine", "Rule '" + rule.name + "': skip (no sensor data yet)");
            return;
        }

        boolean groupResult = RuleEvaluator.evaluateConditionGroup(rule.conditions, signalLookup);

        Boolean prev = previousConditions.get(rule.id);
        boolean prevCondition = prev != null && prev;

        // Debounce (holdSeconds): for rising-edge rules the condition must stay
        // true for the configured time before the edge counts. While the hold
        // is pending, the persisted state is NOT updated, so the edge is not
        // consumed early and a flap (true→false inside the hold window) never
        // produces a fire.
        boolean holdPending = false;
        if (rule.enabled && rule.fireOnRisingEdge && rule.holdSeconds > 0) {
            if (groupResult) {
                Long since = conditionTrueSince.get(rule.id);
                if (since == null) {
                    conditionTrueSince.put(rule.id, now);
                    since = now;
                }
                if (now - since < rule.holdSeconds * 1000) {
                    holdPending = true;
                }
            } else {
                conditionTrueSince.remove(rule.id);
            }
        }

        boolean stateChanged = prev == null || prev != groupResult;
        if (!holdPending) {
            previousConditions.put(rule.id, groupResult);
            if (stateChanged) {
                savePreviousConditions();
                LogBuffer.d("RuleEngine", "Rule '" + rule.name + "': condition "
                    + prevCondition + "→" + groupResult);
            }
        }

        if (!rule.enabled) return;

        if (rule.fireOncePerSession && firedOncePerSession.contains(rule.id)) {
            return;
        }

        if (holdPending) {
            return;
        }

        boolean fireBranch;
        List<RuleAction> targetActions;

        if (groupResult) {
            fireBranch = true;
            targetActions = rule.actions;
            if (rule.fireOnRisingEdge && prevCondition) {
                return;
            }
        } else if (!rule.actionsOnFalse.isEmpty()) {
            fireBranch = false;
            targetActions = rule.actionsOnFalse;
            if (rule.fireOnRisingEdge && !prevCondition) {
                return;
            }
        } else {
            return;
        }

        // Cooldown is shared by both branches: the clock is the most recent
        // execution of either branch, exactly as when there was a single
        // lastExecutedAtMs field. Rising-edge rules throttle on the transition
        // itself, so they only get a small fixed anti-bounce window; the
        // configurable minIntervalSec applies to level-triggered rules.
        long lastFiredAtMs = Math.max(rule.lastExecutedAtMs, rule.lastExecutedFalseAtMs);
        long cooldownMs = rule.fireOnRisingEdge ? EDGE_ANTI_BOUNCE_MS : rule.minIntervalSec * 1000;
        if (now - lastFiredAtMs < cooldownMs) {
            return;
        }

        LogBuffer.i("RuleEngine", "Rule '" + rule.name + "' FIRE " + (fireBranch ? "action" : "else")
            + ": prevCondition=" + prevCondition + " groupResult=" + groupResult
            + " sensors=" + describeConditionSensors(rule));

        for (RuleAction action : targetActions) {
            if (!guard.allow(action.commandId, action.commandValue, now, rule.antiLoopWindowSec * 1000)) {
                LogBuffer.i("RuleEngine", "Rule '" + rule.name + "': action '" + action.commandId + "' blocked by anti-loop");
                continue;
            }
            String chineseCmd = CommandRegistry.buildCommand(action.commandId, action.commandValue);
            if (chineseCmd == null || chineseCmd.isEmpty()) {
                LogBuffer.w("RuleEngine", "Rule '" + rule.name + "': unknown command '" + action.commandId + "'");
                continue;
            }
            LogBuffer.i("RuleEngine", "Rule '" + rule.name + "' " + (fireBranch ? "action" : "else") + ": " + chineseCmd);
            DiPlusCommandSender.send(appContext, chineseCmd);
            guard.record(action.commandId, action.commandValue, now);
        }

        if (rule.fireOncePerSession) {
            firedOncePerSession.add(rule.id);
        }

        if (fireBranch) {
            RuleRegistry.updateLastExecuted(appContext, rule.id, now);
        } else {
            RuleRegistry.updateLastExecutedFalse(appContext, rule.id, now);
        }
    }

    /** Human-readable snapshot of the rule's condition sensor values (for fire logs). */
    private String describeConditionSensors(Rule rule) {
        StringBuilder sb = new StringBuilder("[");
        for (RuleCondition c : rule.conditions) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(c.sensorKey).append("=").append(signalLookup.apply(c.sensorKey));
        }
        return sb.append("]").toString();
    }
}
