package com.diplustohass.rules;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import com.diplustohass.AppConfig;
import com.diplustohass.BaseLocalizedActivity;
import com.diplustohass.CANDataItem;
import com.diplustohass.CANDataReader;
import com.diplustohass.CommandRegistry;
import com.diplustohass.DashboardTile;
import com.diplustohass.GeofenceZone;
import com.diplustohass.R;
import com.diplustohass.SensorValueHistory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuleEditActivity extends BaseLocalizedActivity {

    public static final String EXTRA_RULE_ID = "rule_id";

    private EditText editName;
    private Switch switchRisingEdge;
    private CheckBox checkFireOnce;
    private Switch switchEnableElse;
    private EditText editMinInterval;
    private EditText editAntiLoopWindow;
    private EditText editHoldSeconds;
    private View hintMinInterval;

    private LinearLayout conditionsContainer;
    private LinearLayout actionsContainer;
    private LinearLayout elseActionsContainer;
    private final List<View> conditionRows = new ArrayList<>();
    private final List<View> actionRows = new ArrayList<>();
    private final List<View> elseActionRows = new ArrayList<>();

    private Rule rule;
    private List<CANDataItem> signalItems;
    private List<CommandRegistry.CommandEntry> commandEntries;
    private List<String> sensorKeys;
    private List<String> commandIds;
    /** Values already used for a command in other rules/tiles, for suggestions. */
    private final Map<String, List<String>> usedValuesByCommand = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rule_edit);

        editName = findViewById(R.id.editRuleName);
        switchRisingEdge = findViewById(R.id.switchRisingEdge);
        checkFireOnce = findViewById(R.id.checkFireOnce);
        switchEnableElse = findViewById(R.id.switchEnableElse);
        editMinInterval = findViewById(R.id.editMinInterval);
        editAntiLoopWindow = findViewById(R.id.editAntiLoopWindow);
        editHoldSeconds = findViewById(R.id.editHoldSeconds);
        hintMinInterval = findViewById(R.id.hintMinInterval);
        conditionsContainer = findViewById(R.id.conditionsContainer);
        actionsContainer = findViewById(R.id.actionsContainer);
        elseActionsContainer = findViewById(R.id.elseActionsContainer);

        signalItems = CANDataReader.createSignalItems();
        commandEntries = CommandRegistry.getAll();
        sensorKeys = new ArrayList<>();
        for (CANDataItem item : signalItems) {
            if (item.key != null && !item.key.isEmpty()) {
                sensorKeys.add(item.key);
            }
        }
        for (GeofenceZone z : AppConfig.loadGeofences(this)) {
            String geoKey = "geo_" + z.id;
            if (!sensorKeys.contains(geoKey)) {
                sensorKeys.add(geoKey);
                CANDataItem geoItem = new CANDataItem(0, z.name, "", -1);
                geoItem.key = geoKey;
                signalItems.add(geoItem);
            }
        }
        commandIds = new ArrayList<>();
        for (CommandRegistry.CommandEntry entry : commandEntries) {
            commandIds.add(entry.id);
        }
        collectUsedCommandValues();

        String ruleId = getIntent().getStringExtra(EXTRA_RULE_ID);
        if (ruleId != null && !ruleId.isEmpty()) {
            List<Rule> allRules = RuleRegistry.load(this);
            for (Rule r : allRules) {
                if (ruleId.equals(r.id)) {
                    rule = r;
                    break;
                }
            }
        }

        boolean editing = rule != null;
        if (!editing) {
            setTitle(getString(R.string.rules_create_title));
            rule = Rule.create("");
        } else {
            setTitle(getString(R.string.rules_edit_title));
        }

        populateFromRule();
        updateMinIntervalState(rule.fireOnRisingEdge);
        switchRisingEdge.setOnCheckedChangeListener((v, checked) -> updateMinIntervalState(checked));

        for (RuleCondition c : rule.conditions) {
            addConditionRow(c, conditionRows.size());
        }
        if (rule.conditions.isEmpty()) addConditionRow();

        for (RuleAction a : rule.actions) {
            addActionRow(a);
        }
        if (rule.actions.isEmpty()) addActionRow();

        for (RuleAction a : rule.actionsOnFalse) {
            addElseActionRow(a);
        }

        switchEnableElse.setOnCheckedChangeListener((v, checked) -> {
            elseActionsContainer.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked && elseActionRows.isEmpty()) addElseActionRow();
        });
        elseActionsContainer.setVisibility(
                rule.actionsOnFalse.isEmpty() ? View.GONE : View.VISIBLE);
        switchEnableElse.setChecked(!rule.actionsOnFalse.isEmpty());

        findViewById(R.id.btnAddCondition).setOnClickListener(v -> addConditionRow());
        findViewById(R.id.btnAddAction).setOnClickListener(v -> addActionRow());
        findViewById(R.id.btnAddElseAction).setOnClickListener(v -> addElseActionRow());
        findViewById(R.id.btnRuleSave).setOnClickListener(v -> saveRule());
        findViewById(R.id.btnRuleCancel).setOnClickListener(v -> finish());
    }

    private View addConditionRow() {
        return addConditionRow(null, conditionRows.size());
    }

    private View addConditionRow(RuleCondition preset, int index) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View row = inflater.inflate(R.layout.condition_row, conditionsContainer, false);

        Spinner connSpinner = row.findViewById(R.id.condConnector);
        CheckBox notCheck = row.findViewById(R.id.condNot);
        Spinner sensorSpinner = row.findViewById(R.id.condSensor);
        Spinner opSpinner = row.findViewById(R.id.condOperator);
        AutoCompleteTextView valueInput = row.findViewById(R.id.condValue);
        View btnUp = row.findViewById(R.id.condUp);
        View btnDown = row.findViewById(R.id.condDown);
        View btnDelete = row.findViewById(R.id.condDelete);

        setupSensorSpinner(sensorSpinner);
        setupOperatorSpinner(opSpinner);

        valueInput.setThreshold(1);
        valueInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) valueInput.showDropDown();
        });
        valueInput.setOnClickListener(v -> valueInput.showDropDown());

        if (index == 0) {
            connSpinner.setVisibility(View.GONE);
            notCheck.setVisibility(View.GONE);
        } else {
            ArrayAdapter<CharSequence> connAdapter = ArrayAdapter.createFromResource(this,
                    R.array.rules_connector_options, android.R.layout.simple_spinner_item);
            connAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            connSpinner.setAdapter(connAdapter);
            if (preset != null && preset.connector != null) {
                connSpinner.setSelection(preset.connector == LogicalOperator.AND ? 0 : 1);
            }
            if (preset != null && preset.negated) {
                notCheck.setChecked(true);
            }
        }

        if (preset != null) {
            selectSensor(sensorSpinner, preset.sensorKey);
            opSpinner.setSelection(preset.operator.ordinal());
            valueInput.setText(preset.value);
            updateValueSuggestions(preset.sensorKey, valueInput);
        }

        btnDelete.setOnClickListener(v -> {
            conditionsContainer.removeView(row);
            conditionRows.remove(row);
            refreshConditionConnectors();
        });
        btnUp.setOnClickListener(v -> moveConditionRow(row, -1));
        btnDown.setOnClickListener(v -> moveConditionRow(row, 1));

        sensorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                updateValueSuggestions(getSensorKeyAt(pos), valueInput);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        conditionsContainer.addView(row, index);
        conditionRows.add(index, row);
        refreshConditionConnectors();
        return row;
    }

    private View addActionRow() {
        return addActionRow(null);
    }

    private View addActionRow(RuleAction preset) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View row = inflater.inflate(R.layout.action_row, actionsContainer, false);

        Spinner cmdSpinner = row.findViewById(R.id.actCommand);
        AutoCompleteTextView valueInput = row.findViewById(R.id.actValue);
        View btnUp = row.findViewById(R.id.actUp);
        View btnDown = row.findViewById(R.id.actDown);
        View btnDelete = row.findViewById(R.id.actDelete);

        setupActionValueInput(valueInput);
        setupCommandSpinner(cmdSpinner);
        cmdSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                updateActionValueInput(getCommandIdAt(pos), valueInput);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        if (preset != null) {
            selectCommand(cmdSpinner, preset.commandId);
            valueInput.setText(actionValueDisplayText(preset.commandId, preset.commandValue));
        }
        updateActionValueInput(getCommandIdAt(cmdSpinner.getSelectedItemPosition()), valueInput);

        btnDelete.setOnClickListener(v -> {
            actionsContainer.removeView(row);
            actionRows.remove(row);
        });
        btnUp.setOnClickListener(v -> moveActionRow(row, -1));
        btnDown.setOnClickListener(v -> moveActionRow(row, 1));

        actionsContainer.addView(row);
        actionRows.add(row);
        return row;
    }

    private View addElseActionRow() {
        return addElseActionRow(null);
    }

    private View addElseActionRow(RuleAction preset) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View row = inflater.inflate(R.layout.action_row, elseActionsContainer, false);

        Spinner cmdSpinner = row.findViewById(R.id.actCommand);
        AutoCompleteTextView valueInput = row.findViewById(R.id.actValue);
        View btnUp = row.findViewById(R.id.actUp);
        View btnDown = row.findViewById(R.id.actDown);
        View btnDelete = row.findViewById(R.id.actDelete);

        setupActionValueInput(valueInput);
        setupCommandSpinner(cmdSpinner);
        cmdSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                updateActionValueInput(getCommandIdAt(pos), valueInput);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        if (preset != null) {
            selectCommand(cmdSpinner, preset.commandId);
            valueInput.setText(actionValueDisplayText(preset.commandId, preset.commandValue));
        }
        updateActionValueInput(getCommandIdAt(cmdSpinner.getSelectedItemPosition()), valueInput);

        btnDelete.setOnClickListener(v -> {
            elseActionsContainer.removeView(row);
            elseActionRows.remove(row);
        });
        btnUp.setOnClickListener(v -> moveElseActionRow(row, -1));
        btnDown.setOnClickListener(v -> moveElseActionRow(row, 1));

        elseActionsContainer.addView(row);
        elseActionRows.add(row);
        return row;
    }

    private void moveElseActionRow(View row, int direction) {
        int index = elseActionRows.indexOf(row);
        int newIndex = index + direction;
        if (newIndex < 0 || newIndex >= elseActionRows.size()) return;
        elseActionsContainer.removeView(row);
        elseActionRows.remove(index);
        elseActionsContainer.addView(row, newIndex);
        elseActionRows.add(newIndex, row);
    }

    private void moveConditionRow(View row, int direction) {
        int index = conditionRows.indexOf(row);
        int newIndex = index + direction;
        if (newIndex < 0 || newIndex >= conditionRows.size()) return;
        conditionsContainer.removeView(row);
        conditionRows.remove(index);
        conditionsContainer.addView(row, newIndex);
        conditionRows.add(newIndex, row);
        refreshConditionConnectors();
    }

    private void moveActionRow(View row, int direction) {
        int index = actionRows.indexOf(row);
        int newIndex = index + direction;
        if (newIndex < 0 || newIndex >= actionRows.size()) return;
        actionsContainer.removeView(row);
        actionRows.remove(index);
        actionsContainer.addView(row, newIndex);
        actionRows.add(newIndex, row);
    }

    private void refreshConditionConnectors() {
        for (int i = 0; i < conditionRows.size(); i++) {
            View row = conditionRows.get(i);
            Spinner connSpinner = row.findViewById(R.id.condConnector);
            CheckBox notCheck = row.findViewById(R.id.condNot);
            if (i == 0) {
                connSpinner.setVisibility(View.GONE);
                notCheck.setVisibility(View.GONE);
            } else {
                connSpinner.setVisibility(View.VISIBLE);
                notCheck.setVisibility(View.VISIBLE);
            }
        }
    }

    private void populateFromRule() {
        editName.setText(rule.name != null ? rule.name : "");
        switchRisingEdge.setChecked(rule.fireOnRisingEdge);
        checkFireOnce.setChecked(rule.fireOncePerSession);
        editMinInterval.setText(String.valueOf(rule.minIntervalSec));
        editAntiLoopWindow.setText(String.valueOf(rule.antiLoopWindowSec));
        editHoldSeconds.setText(String.valueOf(rule.holdSeconds));
    }

    /** Rising-edge rules throttle on the transition itself: minInterval is
     * ignored by the engine, so the field is greyed out with an explanation. */
    private void updateMinIntervalState(boolean risingEdge) {
        editMinInterval.setEnabled(!risingEdge);
        editMinInterval.setAlpha(risingEdge ? 0.45f : 1.0f);
        hintMinInterval.setVisibility(risingEdge ? View.VISIBLE : View.GONE);
    }

    private void saveRule() {
        rule.name = editName.getText().toString().trim();
        rule.fireOnRisingEdge = switchRisingEdge.isChecked();
        rule.fireOncePerSession = checkFireOnce.isChecked();

        rule.conditions.clear();
        for (int i = 0; i < conditionRows.size(); i++) {
            View row = conditionRows.get(i);
            RuleCondition c = new RuleCondition();

            if (i > 0) {
                Spinner connSpinner = row.findViewById(R.id.condConnector);
                c.connector = connSpinner.getSelectedItemPosition() == 0
                    ? LogicalOperator.AND : LogicalOperator.OR;
                CheckBox notCheck = row.findViewById(R.id.condNot);
                c.negated = notCheck.isChecked();
            }

            Spinner sensorSpinner = row.findViewById(R.id.condSensor);
            c.sensorKey = getSensorKeyAt(sensorSpinner.getSelectedItemPosition());

            Spinner opSpinner = row.findViewById(R.id.condOperator);
            c.operator = RuleOperator.values()[opSpinner.getSelectedItemPosition()];

            AutoCompleteTextView valueInput = row.findViewById(R.id.condValue);
            c.value = valueInput.getText().toString().trim();

            rule.conditions.add(c);
        }

        if (rule.conditions.isEmpty()) {
            Toast.makeText(this, getString(R.string.rules_need_condition), Toast.LENGTH_SHORT).show();
            return;
        }

        // A condition without a value can never fire meaningfully (and for
        // geo_* sensors it silently breaks the rule) — reject with a hint.
        for (int i = 0; i < rule.conditions.size(); i++) {
            RuleCondition c = rule.conditions.get(i);
            if (c.value == null || c.value.isEmpty()) {
                Toast.makeText(this, getString(R.string.rules_empty_condition_value, i + 1),
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        rule.actions.clear();
        for (View row : actionRows) {
            Spinner cmdSpinner = row.findViewById(R.id.actCommand);
            AutoCompleteTextView valueInput = row.findViewById(R.id.actValue);

            RuleAction a = new RuleAction();
            a.commandId = getCommandIdAt(cmdSpinner.getSelectedItemPosition());
            a.commandValue = resolveActionValue(a.commandId,
                    valueInput.getText().toString().trim());
            rule.actions.add(a);
        }

        rule.actionsOnFalse.clear();
        for (View row : elseActionRows) {
            Spinner cmdSpinner = row.findViewById(R.id.actCommand);
            AutoCompleteTextView valueInput = row.findViewById(R.id.actValue);

            RuleAction a = new RuleAction();
            a.commandId = getCommandIdAt(cmdSpinner.getSelectedItemPosition());
            a.commandValue = resolveActionValue(a.commandId,
                    valueInput.getText().toString().trim());
            rule.actionsOnFalse.add(a);
        }

        if (rule.actions.isEmpty()) {
            Toast.makeText(this, getString(R.string.rules_need_action), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String intervalStr = editMinInterval.getText().toString().trim();
            rule.minIntervalSec = intervalStr.isEmpty() ? 1800 : Long.parseLong(intervalStr);
        } catch (NumberFormatException e) {
            rule.minIntervalSec = 1800;
        }

        try {
            String windowStr = editAntiLoopWindow.getText().toString().trim();
            rule.antiLoopWindowSec = windowStr.isEmpty() ? 60 : Long.parseLong(windowStr);
        } catch (NumberFormatException e) {
            rule.antiLoopWindowSec = 60;
        }

        try {
            String holdStr = editHoldSeconds.getText().toString().trim();
            rule.holdSeconds = holdStr.isEmpty() ? 0 : Long.parseLong(holdStr);
        } catch (NumberFormatException e) {
            rule.holdSeconds = 0;
        }

        if (rule.name.isEmpty() && !rule.conditions.isEmpty()) {
            rule.name = rule.conditions.get(0).sensorKey;
        }

        RuleRegistry.upsert(this, rule);
        notifyEngineChanged();
        finish();
    }

    private void notifyEngineChanged() {
        try {
            com.diplustohass.service.TelemetryService ts =
                    com.diplustohass.MainActivity.getTelemetryService();
            if (ts != null && ts.getRuleEngine() != null) {
                ts.getRuleEngine().onRulesChanged();
            }
        } catch (Exception e) {
            // Service may not be bound — ignore
        }
    }

    // ---- Spinner helpers ----

    private void setupSensorSpinner(Spinner spinner) {
        List<String> labels = new ArrayList<>();
        for (CANDataItem item : signalItems) {
            if (item.key != null && !item.key.isEmpty()) {
                labels.add(item.getDisplayName(this));
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void setupOperatorSpinner(Spinner spinner) {
        List<String> labels = new ArrayList<>();
        for (RuleOperator op : RuleOperator.values()) {
            labels.add(op.getLabel(this));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void setupCommandSpinner(Spinner spinner) {
        List<String> labels = new ArrayList<>();
        for (CommandRegistry.CommandEntry entry : commandEntries) {
            String displayName;
            try {
                displayName = getString(entry.displayNameResId);
            } catch (Exception e) {
                displayName = entry.id;
            }
            String label = entry.id;
            if (displayName != null && !displayName.isEmpty() && !displayName.equals(entry.id)) {
                label = displayName + " (" + entry.id + ")";
            }
            labels.add(label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void selectSensor(Spinner spinner, String key) {
        int idx = sensorKeys.indexOf(key);
        if (idx >= 0) spinner.setSelection(idx);
    }

    private void selectCommand(Spinner spinner, String id) {
        int idx = commandIds.indexOf(id);
        if (idx >= 0) spinner.setSelection(idx);
    }

    private String getSensorKeyAt(int position) {
        return position >= 0 && position < sensorKeys.size() ? sensorKeys.get(position) : "";
    }

    private String getCommandIdAt(int position) {
        return position >= 0 && position < commandIds.size() ? commandIds.get(position) : "";
    }

    private void updateValueSuggestions(String sensorKey, AutoCompleteTextView valueInput) {
        try {
            // Virtual geofence sensors have no CAN history; their value space
            // is fixed: inside / outside.
            if (sensorKey != null && sensorKey.startsWith("geo_")) {
                ArrayAdapter<String> geoAdapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_dropdown_item_1line,
                        new String[]{"inside", "outside"});
                valueInput.setAdapter(geoAdapter);
                return;
            }
            SensorValueHistory.ensureLoaded(AppConfig.getSensorValueHistoryJson(this));
            List<String> values = SensorValueHistory.getValues(sensorKey);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, values);
            valueInput.setAdapter(adapter);
} catch (Exception e) {
            // Suggestions are best-effort; never break the editor.
        }
    }

    // ---- Action value input ----

    private void setupActionValueInput(AutoCompleteTextView valueInput) {
        valueInput.setThreshold(1);
        valueInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) valueInput.showDropDown();
        });
        valueInput.setOnClickListener(v -> valueInput.showDropDown());
    }

    /**
     * Adjust the value field to the selected command: disabled for NONE,
     * localized enum labels for ENUM, min..max hint for NUMBER/RANGE.
     * Never touches the current text so presets and user input survive.
     */
    private void updateActionValueInput(String commandId, AutoCompleteTextView valueInput) {
        CommandRegistry.CommandEntry entry = CommandRegistry.getById(commandId);
        if (entry == null || entry.valueType == CommandRegistry.ValueType.NONE) {
            valueInput.setEnabled(false);
            valueInput.setHint(null);
            setActionValueAdapter(valueInput, new ArrayList<>());
            return;
        }
        valueInput.setEnabled(true);
        List<String> used = usedValuesByCommand.get(commandId);
        switch (entry.valueType) {
            case ENUM: {
                valueInput.setHint(null);
                List<String> suggestions = new ArrayList<>();
                for (String key : entry.enumValues.keySet()) {
                    suggestions.add(enumValueLabel(key) + " (" + key + ")");
                }
                setActionValueAdapter(valueInput, suggestions);
                break;
            }
            case NUMBER:
            case RANGE: {
                valueInput.setHint(formatBound(entry.minValue) + ".." + formatBound(entry.maxValue));
                List<String> suggestions = new ArrayList<>();
                suggestions.add(formatBound(entry.minValue));
                suggestions.add(formatBound(entry.maxValue));
                if (used != null) {
                    for (String v : used) {
                        if (!suggestions.contains(v)) suggestions.add(v);
                    }
                }
                setActionValueAdapter(valueInput, suggestions);
                break;
            }
            default: { // STRING
                valueInput.setHint(entry.getValueHint(this));
                setActionValueAdapter(valueInput,
                        used != null ? used : new ArrayList<>());
                break;
            }
        }
    }

    private void setActionValueAdapter(AutoCompleteTextView valueInput, List<String> suggestions) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, suggestions);
        valueInput.setAdapter(adapter);
    }

    /** Display text for a stored action value: "label (key)" for enum keys. */
    private String actionValueDisplayText(String commandId, String value) {
        if (value == null) return "";
        CommandRegistry.CommandEntry entry = CommandRegistry.getById(commandId);
        if (entry != null && entry.valueType == CommandRegistry.ValueType.ENUM
                && entry.enumValues.containsKey(value)) {
            return enumValueLabel(value) + " (" + value + ")";
        }
        return value;
    }

    /** Map the text in the value field back to the value stored in the rule. */
    private String resolveActionValue(String commandId, String rawValue) {
        CommandRegistry.CommandEntry entry = CommandRegistry.getById(commandId);
        if (entry == null || entry.valueType != CommandRegistry.ValueType.ENUM) {
            return rawValue;
        }
        if (rawValue.isEmpty() || entry.enumValues.containsKey(rawValue)) {
            return rawValue;
        }
        // Suggestions are shown as "label (key)" — prefer the parenthesised key.
        int open = rawValue.lastIndexOf(" (");
        if (open >= 0 && rawValue.endsWith(")")) {
            String candidate = rawValue.substring(open + 2, rawValue.length() - 1);
            if (entry.enumValues.containsKey(candidate)) return candidate;
        }
        // Fall back to matching a localized label.
        for (String key : entry.enumValues.keySet()) {
            if (enumValueLabel(key).equals(rawValue)) return key;
        }
        return rawValue;
    }

    private String enumValueLabel(String key) {
        // Mirrors MainActivity.enumValueResId (package-private there):
        // stable enum value ids map to string resources such as enum_on.
        int resId = getResources().getIdentifier("enum_" + key, "string", getPackageName());
        return resId != 0 ? getString(resId) : key;
    }

    private static String formatBound(double value) {
        return value == (int) value ? String.valueOf((int) value) : String.valueOf(value);
    }

    /** Collect values already used for each command in other rules and dashboard tiles. */
    private void collectUsedCommandValues() {
        try {
            for (Rule r : RuleRegistry.load(this)) {
                if (r == null) continue;
                collectUsedCommandValues(r.actions);
                collectUsedCommandValues(r.actionsOnFalse);
            }
        } catch (Exception e) {
            // Suggestions are best-effort; never break the editor.
        }
        try {
            List<DashboardTile> tiles = AppConfig.loadDashboardTiles(this);
            if (tiles != null) {
                for (DashboardTile t : tiles) {
                    if (t != null && t.isCommand() && t.key != null
                            && t.commandValue != null && !t.commandValue.isEmpty()) {
                        addUsedCommandValue(t.key, t.commandValue);
                    }
                }
            }
        } catch (Exception e) {
            // Suggestions are best-effort; never break the editor.
        }
    }

    private void collectUsedCommandValues(List<RuleAction> actions) {
        if (actions == null) return;
        for (RuleAction a : actions) {
            if (a != null && a.commandId != null && a.commandValue != null
                    && !a.commandValue.isEmpty()) {
                addUsedCommandValue(a.commandId, a.commandValue);
            }
        }
    }

    private void addUsedCommandValue(String commandId, String value) {
        List<String> values = usedValuesByCommand.get(commandId);
        if (values == null) {
            values = new ArrayList<>();
            usedValuesByCommand.put(commandId, values);
        }
        if (!values.contains(value)) values.add(value);
    }

}