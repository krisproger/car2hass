package com.car2hass.rules;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.car2hass.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.widget.Switch;

public class RulesListAdapter extends BaseAdapter {

    private static final SimpleDateFormat FIRED_FORMAT =
            new SimpleDateFormat("dd.MM HH:mm", Locale.US);

    private final Context context;
    private final List<Rule> rules;
    private final OnRuleToggleListener toggleListener;
    private final OnRuleLongClickListener longClickListener;

    public interface OnRuleToggleListener {
        void onRuleToggled(Rule rule, boolean enabled);
    }

    public interface OnRuleLongClickListener {
        boolean onRuleLongClicked(Rule rule);
    }

    public RulesListAdapter(Context context, List<Rule> rules, OnRuleToggleListener toggleListener) {
        this(context, rules, toggleListener, null);
    }

    public RulesListAdapter(Context context, List<Rule> rules, OnRuleToggleListener toggleListener, OnRuleLongClickListener longClickListener) {
        this.context = context;
        this.rules = rules;
        this.toggleListener = toggleListener;
        this.longClickListener = longClickListener;
    }

    public void updateRules(List<Rule> newRules) {
        rules.clear();
        rules.addAll(newRules);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return rules.size();
    }

    @Override
    public Rule getItem(int position) {
        return rules.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.rule_row, parent, false);
        }

        final Rule rule = getItem(position);
        if (rule == null) return convertView;

        TextView nameView = convertView.findViewById(R.id.ruleName);
        TextView summaryView = convertView.findViewById(R.id.ruleSummary);
        TextView lastFiredView = convertView.findViewById(R.id.ruleLastFired);
        Switch enabledSwitch = convertView.findViewById(R.id.ruleEnabledSwitch);

        String displayName = rule.name;
        if (displayName == null || displayName.isEmpty()) {
            if (rule.conditions != null && !rule.conditions.isEmpty()) {
                displayName = rule.conditions.get(0).sensorKey;
            }
        }
        nameView.setText(displayName != null ? displayName : "");

        RuleCondition firstCond = rule.conditions != null && !rule.conditions.isEmpty()
            ? rule.conditions.get(0) : null;
        if (firstCond != null) {
            String opSymbol = operatorSymbol(firstCond.operator);
            String prefix = firstCond.negated ? "NOT " : "";
            int actionCount = rule.actions != null ? rule.actions.size() : 0;
            summaryView.setText(prefix + firstCond.sensorKey + " " + opSymbol + " " + firstCond.value
                    + "  →  " + actionCount + " action(s)");
        } else {
            summaryView.setText("→ " + (rule.actions != null ? rule.actions.size() : 0) + " action(s)");
        }

        String lastFiredText = buildLastFiredText(rule);
        if (lastFiredText.isEmpty()) {
            lastFiredView.setVisibility(View.GONE);
        } else {
            lastFiredView.setVisibility(View.VISIBLE);
            lastFiredView.setText(lastFiredText);
        }

        // Avoid triggering listener during bind
        enabledSwitch.setOnCheckedChangeListener(null);
        enabledSwitch.setChecked(rule.enabled);
        enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            rule.enabled = isChecked;
            if (toggleListener != null) {
                toggleListener.onRuleToggled(rule, isChecked);
            }
        });

        enabledSwitch.setOnLongClickListener(v -> longClickListener != null && longClickListener.onRuleLongClicked(rule));

        return convertView;
    }

    private String buildLastFiredText(Rule rule) {
        boolean hasElse = rule.actionsOnFalse != null && !rule.actionsOnFalse.isEmpty();
        if (rule.lastExecutedAtMs <= 0 && (!hasElse || rule.lastExecutedFalseAtMs <= 0)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (rule.lastExecutedAtMs > 0) {
            sb.append(context.getString(R.string.rules_last_fired,
                    FIRED_FORMAT.format(new Date(rule.lastExecutedAtMs))));
        }
        if (hasElse && rule.lastExecutedFalseAtMs > 0) {
            if (sb.length() > 0) sb.append("  •  ");
            sb.append(context.getString(R.string.rules_last_fired_else,
                    FIRED_FORMAT.format(new Date(rule.lastExecutedFalseAtMs))));
        }
        return sb.toString();
    }

    private static String operatorSymbol(RuleOperator op) {
        if (op == null) return "?";
        switch (op) {
            case EQ: return "=";
            case NEQ: return "≠";
            case GT: return ">";
            case LT: return "<";
            case GTE: return "≥";
            case LTE: return "≤";
            default: return "?";
        }
    }
}
