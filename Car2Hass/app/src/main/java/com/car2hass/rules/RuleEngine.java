package com.car2hass.rules;

import android.content.Context;
import android.content.SharedPreferences;

import com.car2hass.CommandRegistry;
import com.car2hass.DiPlusCommandSender;
import com.car2hass.LogBuffer;

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
import java.util.function.Predicate;

public class RuleEngine {

    private final Context appContext;
    private final Function<String, String> signalLookup;
    private final Predicate<String> restoredLookup;
    private final AntiLoopGuard guard = new AntiLoopGuard();
    private ScheduledExecutorService executor;
    private volatile boolean started = false;

    // Per-rule previous condition state and first-evaluation seeding.
    // Thread-safe: only accessed from the single executor thread.
    private final RuleEdgeLogic edgeLogic = new RuleEdgeLogic();
    private final Set<String> firedOncePerSession = new HashSet<>();
    // Rising-edge debounce: rule id → timestamp when its condition first became
    // true in the current true-period. Used by the holdSeconds gate.
    private final java.util.HashMap<String, Long> conditionTrueSince = new java.util.HashMap<>();

    // Edge rules throttle on the transition itself; this small fixed window
    // only guards against signal bounce right at the edge. Level-triggered
    // rules keep using the configurable minIntervalSec cooldown.
    private static final long EDGE_ANTI_BOUNCE_MS = 5000;

    // sensor key → rule ids referencing it, so a value change re-evaluates only
    // the affected rules instead of the whole set every second.
    private final java.util.HashMap<String, Set<String>> sensorRuleIndex = new java.util.HashMap<>();

    private static final String PREFS_NAME = "rule_engine_state";
    private static final String KEY_PREV_CONDITIONS = "previous_conditions";

    public RuleEngine(Context appContext, Function<String, String> signalLookup,
                      Predicate<String> restoredLookup) {
        this.appContext = appContext;
        this.signalLookup = signalLookup;
        this.restoredLookup = restoredLookup;
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        edgeLogic.reset();
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
        edgeLogic.reset();
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
            edgeLogic.retainRules(ids);
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
            int loaded = 0;
            while (keys.hasNext()) {
                String k = keys.next();
                edgeLogic.loadPrevious(k, obj.optBoolean(k, false));
                loaded++;
            }
            LogBuffer.i("RuleEngine", "Loaded " + loaded + " previous condition states");
        } catch (Exception e) {
            LogBuffer.w("RuleEngine", "load state error: " + e.getMessage());
        }
    }

    private void savePreviousConditions() {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, Boolean> e : edgeLogic.previousConditions().entrySet()) {
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
            buildIndex(rules);
            for (Rule r : rules) {
                evaluateRule(r);
            }
        } catch (Exception e) {
            LogBuffer.e("RuleEngine", "tick error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Rebuild sensorKey → rule ids for event-driven evaluation. */
    private void buildIndex(List<Rule> rules) {
        sensorRuleIndex.clear();
        for (Rule r : rules) {
            for (RuleCondition c : r.conditions) {
                if (c.sensorKey == null || c.sensorKey.isEmpty()) continue;
                sensorRuleIndex.computeIfAbsent(c.sensorKey, k -> new HashSet<>()).add(r.id);
            }
        }
    }

    /**
     * Event-driven entry point: a sensor value changed, so re-evaluate the rules
     * that reference it right away (instead of waiting for the next 1s tick).
     * The work is posted to the rule executor to keep the engine single-threaded.
     */
    public void signalChanged(String key) {
        if (!started || executor == null || executor.isShutdown() || key == null) return;
        executor.execute(() -> {
            try {
                Set<String> ids = sensorRuleIndex.get(key);
                if (ids == null || ids.isEmpty()) return;
                List<Rule> rules = RuleRegistry.load(appContext);
                for (Rule r : rules) {
                    if (ids.contains(r.id)) evaluateRule(r);
                }
            } catch (Exception e) {
                LogBuffer.e("RuleEngine", "signalChanged error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void evaluateRule(Rule rule) {
        if (rule.id == null) return;

        long now = System.currentTimeMillis();

        // A rule must not react to a value that only came back from the previous
        // session or is missing entirely: either would look like a fresh state
        // change on startup. Missing data is also skipped so a null value never
        // overwrites the persisted previous state.
        RuleEdgeLogic.Suppression suppression =
                RuleEdgeLogic.detectSuppression(rule.conditions, signalLookup, restoredLookup);
        if (suppression.blocked()) {
            // Rate-limit: log only on first occurrence per session (not every tick).
            boolean restored = suppression.reason == RuleEdgeLogic.Suppress.RESTORED;
            String tag = rule.id + (restored ? "_restored" : "_nostate");
            if (!firedOncePerSession.contains(tag)) {
                if (restored) {
                    LogBuffer.i("RuleEngine", "Rule '" + rule.name
                            + "': skip: restored value " + suppression.sensorKey);
                } else {
                    LogBuffer.i("RuleEngine", "Rule '" + rule.name + "': skip (missing sensor data)");
                }
                firedOncePerSession.add(tag);
            }
            return;
        }
        // Reset the "nostate" flag so we re-log if sensors go missing again.
        firedOncePerSession.remove(rule.id + "_nostate");

        // Evaluate once and keep the captured per-condition snapshot: reading the
        // signals a second time for the log could show a newer value and make the
        // logged conditions contradict the decision (issue #68).
        RuleEvaluator.GroupEvaluation group =
                RuleEvaluator.evaluateConditionGroupDetails(rule.conditions, signalLookup);
        boolean groupResult = group.result;

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

        RuleEdgeLogic.Outcome outcome = edgeLogic.evaluate(rule.id, groupResult, holdPending);
        if (outcome.firstEvaluation) {
            savePreviousConditions();
            LogBuffer.i("RuleEngine", "Rule '" + rule.name + "': first evaluation recorded ("
                    + groupResult + "), no edge");
            return;
        }
        boolean prevCondition = outcome.previousCondition;
        if (outcome.stateChanged && !holdPending) {
            savePreviousConditions();
            LogBuffer.d("RuleEngine", "Rule '" + rule.name + "': condition "
                + prevCondition + "→" + groupResult);
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
            + " conditions=" + describeEvaluation(group));

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
            // Route through the serial command queue (native/diplus fallback +
            // status verification) instead of a fire-and-forget DiPlus send.
            com.car2hass.CommandQueue.enqueue(appContext, action.commandId,
                    action.commandValue, com.car2hass.CommandExecutor.Source.RULE, null);
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

    /**
     * Human-readable per-condition snapshot of the evaluated group (for fire
     * logs). Each condition shows the raw store value, the translated value,
     * the operator, the expected value, the connector, the negated flag and the
     * per-condition result, so a device log is conclusive without re-reading
     * the (possibly newer) live signals.
     */
    private String describeEvaluation(RuleEvaluator.GroupEvaluation group) {
        StringBuilder sb = new StringBuilder("[");
        for (RuleEvaluator.ConditionEvaluation c : group.conditions) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(c.describe());
        }
        if (group.hasMissing()) {
            if (sb.length() > 1) sb.append(", ");
            sb.append("missing=").append(group.missingSensorKey);
        }
        return sb.append("]").toString();
    }
}
