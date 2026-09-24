package com.car2hass.rules;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.car2hass.R;
import com.car2hass.SignalTranslator;
import com.car2hass.vehicle.ValueStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Diagnostic dialog that evaluates the edited rule's conditions against
 * sensor values. Values can be overridden to check a rule without the car;
 * evaluation reuses {@link RuleEvaluator} and {@link SignalTranslator} so it
 * matches the engine exactly. No commands are executed.
 */
public final class RuleTestDialog {

    private RuleTestDialog() {
    }

    private static final class Row {
        RuleCondition condition;
        EditText actual;
        TextView operator;
        TextView result;
        TextView status;
        String liveRaw;
        String liveTranslated;
        boolean liveRestored;
        boolean liveMissing;
        boolean overridden;
    }

    public static void show(Context context, List<RuleCondition> conditions,
            Map<String, String> displayNames, boolean fireOnRisingEdge) {
        final ValueStore store = ValueStore.main();

        LayoutInflater inflater = LayoutInflater.from(context);
        View root = inflater.inflate(R.layout.dialog_rule_test, null);
        LinearLayout container = root.findViewById(R.id.testRowsContainer);
        TextView summary = root.findViewById(R.id.testSummary);

        final List<Row> rows = new ArrayList<>();
        for (int i = 0; i < conditions.size(); i++) {
            RuleCondition c = conditions.get(i);
            View rowView = inflater.inflate(R.layout.rule_test_row, container, false);

            final Row r = new Row();
            r.condition = c;
            r.liveRaw = store != null ? store.get(c.sensorKey) : null;
            r.liveTranslated = r.liveRaw == null
                    ? "" : SignalTranslator.translateEnumValue(c.sensorKey, r.liveRaw);
            r.liveRestored = store != null && store.isRestored(c.sensorKey);
            r.liveMissing = r.liveRaw == null || "---".equals(r.liveRaw);

            r.actual = rowView.findViewById(R.id.testRowActual);
            r.operator = rowView.findViewById(R.id.testRowOperator);
            r.result = rowView.findViewById(R.id.testRowResult);
            r.status = rowView.findViewById(R.id.testRowStatus);

            TextView title = rowView.findViewById(R.id.testRowTitle);
            title.setText(buildTitle(context, c, displayNames, i));
            r.actual.setText(r.liveTranslated);

            Button restore = rowView.findViewById(R.id.testRowRestore);
            restore.setOnClickListener(v -> {
                r.actual.setText(r.liveTranslated);
                r.overridden = false;
                refresh(context, rows, summary, fireOnRisingEdge);
            });

            r.actual.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    r.overridden = true;
                    refresh(context, rows, summary, fireOnRisingEdge);
                }
            });

            container.addView(rowView);
            rows.add(r);
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.rules_test_title)
                .setView(root)
                .setPositiveButton(R.string.rules_test_close, null)
                .show();

        refresh(context, rows, summary, fireOnRisingEdge);
    }

    private static String buildTitle(Context context, RuleCondition c,
            Map<String, String> displayNames, int index) {
        String name = displayNames != null ? displayNames.get(c.sensorKey) : null;
        if (name == null || name.isEmpty()) name = c.sensorKey;
        StringBuilder sb = new StringBuilder();
        sb.append(index + 1).append(". ");
        if (index > 0) {
            int connectorRes = c.connector == LogicalOperator.OR
                    ? R.string.rules_connector_or : R.string.rules_connector_and;
            sb.append(context.getString(connectorRes)).append(' ');
        }
        if (c.negated) sb.append(context.getString(R.string.rules_condition_not)).append(' ');
        sb.append(name);
        if (c.triggerOnChange) {
            sb.append(" [").append(context.getString(R.string.rules_condition_trigger)).append(']');
        }
        return sb.toString();
    }

    private static void refresh(Context context, List<Row> rows, TextView summary,
            boolean fireOnRisingEdge) {
        List<RuleCondition> conditions = new ArrayList<>();
        for (Row r : rows) conditions.add(r.condition);

        Function<String, String> lookup = key -> {
            for (Row r : rows) {
                if (r.condition.sensorKey != null && r.condition.sensorKey.equals(key)) {
                    return r.overridden ? r.actual.getText().toString() : r.liveRaw;
                }
            }
            return null;
        };

        RuleEvaluator.GroupEvaluation group =
                RuleEvaluator.evaluateConditionGroupDetails(conditions, lookup);

        boolean restoredLive = false;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            RuleEvaluator.ConditionEvaluation d = group.conditions.get(i);

            String expectedLabel = d.expected == null || d.expected.isEmpty()
                    ? "\u2014" : d.expected;
            r.operator.setText(r.condition.operator.getLabel(context) + " " + expectedLabel);
            r.status.setText(statusText(context, r));

            if (d.missing) {
                r.result.setText("\u2014");
                r.result.setTextColor(context.getColor(R.color.accentYellow));
            } else if (d.met) {
                r.result.setText("\u2713");
                r.result.setTextColor(context.getColor(R.color.accentGreen));
            } else {
                r.result.setText("\u2717");
                r.result.setTextColor(context.getColor(R.color.accentRed));
            }

            if (r.liveRestored && !r.overridden) restoredLive = true;
        }

        int summaryRes;
        boolean hasTriggers = RuleTriggerLogic.hasTriggerConditions(conditions);
        if (!group.result) {
            summaryRes = R.string.rules_test_group_false;
            summary.setTextColor(context.getColor(R.color.accentRed));
        } else if (group.hasMissing() || restoredLive) {
            summaryRes = R.string.rules_test_group_true_skip;
            summary.setTextColor(context.getColor(R.color.accentYellow));
        } else if (fireOnRisingEdge && hasTriggers) {
            summaryRes = R.string.rules_test_group_true_trigger;
            summary.setTextColor(context.getColor(R.color.accentGreen));
        } else if (fireOnRisingEdge) {
            summaryRes = R.string.rules_test_group_true_rising;
            summary.setTextColor(context.getColor(R.color.accentGreen));
        } else {
            summaryRes = R.string.rules_test_group_true_immediate;
            summary.setTextColor(context.getColor(R.color.accentGreen));
        }
        summary.setText(summaryRes);
    }

    private static String statusText(Context context, Row r) {
        StringBuilder sb = new StringBuilder();
        if (r.overridden) {
            sb.append(context.getString(R.string.rules_test_status_overridden));
        }
        if (r.liveMissing) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(context.getString(R.string.rules_test_status_missing));
        } else if (r.liveRestored) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(context.getString(R.string.rules_test_status_restored));
        }
        return sb.toString();
    }
}
