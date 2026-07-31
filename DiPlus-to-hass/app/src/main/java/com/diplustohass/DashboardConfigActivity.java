package com.diplustohass;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets the user pick which sensor tiles and quick-command tiles appear on the
 * dashboard. Sensors and commands are shown in two separate sections. Checking
 * a sensor reveals linked command chips that can add the corresponding command
 * tiles in one tap.
 */
public class DashboardConfigActivity extends BaseLocalizedActivity {

    private interface ValueCallback {
        void onValue(String value);
    }

    private enum SortMode {
        LABEL, KEY, CATEGORY
    }

    private final List<SensorRow> sensorRows = new ArrayList<>();
    private final List<CommandRow> commandRows = new ArrayList<>();
    private final Map<String, CommandRow> commandRowMap = new LinkedHashMap<>();
    private SensorAdapter sensorAdapter;
    private CommandAdapter commandAdapter;
    private SensorCommandRegistry sensorCommandRegistry;

    private SortMode sensorSortMode = SortMode.LABEL;
    private boolean sensorAscending = true;
    private SortMode commandSortMode = SortMode.LABEL;
    private boolean commandAscending = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard_config);

        sensorCommandRegistry = SensorCommandRegistry.getInstance(this);

        List<DashboardTile> currentTiles = AppConfig.loadDashboardTiles(this);
        if (currentTiles == null) {
            currentTiles = DashboardTileFactory.defaultTiles(this);
        }

        Map<String, DashboardTile> currentMap = new LinkedHashMap<>();
        for (DashboardTile tile : currentTiles) {
            currentMap.put(tile.key, tile);
        }

        loadSensorRows(currentMap);
        loadCommandRows(currentMap);

        ListView lvSensors = findViewById(R.id.lvSensors);
        sensorAdapter = new SensorAdapter(this, sensorRows);
        lvSensors.setAdapter(sensorAdapter);

        ListView lvCommands = findViewById(R.id.lvCommands);
        commandAdapter = new CommandAdapter(this, commandRows);
        lvCommands.setAdapter(commandAdapter);

        CheckBox cbSelectAllSensors = findViewById(R.id.cbSelectAllSensors);
        cbSelectAllSensors.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (SensorRow row : sensorRows) {
                row.checked = isChecked;
            }
            sensorAdapter.notifyDataSetChanged();
        });

        CheckBox cbSelectAllCommands = findViewById(R.id.cbSelectAllCommands);
        cbSelectAllCommands.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (CommandRow row : commandRows) {
                row.checked = isChecked;
            }
            commandAdapter.notifyDataSetChanged();
        });

        setupSortHeaders();

        Button btnDone = findViewById(R.id.btnDone);
        btnDone.setOnClickListener(v -> saveAndFinish());
    }

    private void setupSortHeaders() {
        findViewById(R.id.headerSensorName).setOnClickListener(v -> sortSensorRows(SortMode.LABEL));
        findViewById(R.id.headerSensorKey).setOnClickListener(v -> sortSensorRows(SortMode.KEY));

        findViewById(R.id.headerCommandName).setOnClickListener(v -> sortCommandRows(SortMode.LABEL));
        findViewById(R.id.headerCommandKey).setOnClickListener(v -> sortCommandRows(SortMode.KEY));
        findViewById(R.id.headerCommandCategory).setOnClickListener(v -> sortCommandRows(SortMode.CATEGORY));
    }

    private void loadSensorRows(Map<String, DashboardTile> currentMap) {
        sensorRows.clear();
        for (DashboardTile tile : DashboardTileFactory.availableSensors(this)) {
            sensorRows.add(new SensorRow(tile, currentMap.containsKey(tile.key)));
        }
        applySensorSort();
    }

    private void loadCommandRows(Map<String, DashboardTile> currentMap) {
        commandRows.clear();
        commandRowMap.clear();
        for (DashboardTile tile : DashboardTileFactory.availableCommands(this)) {
            DashboardTile current = currentMap.get(tile.key);
            String value = current != null ? current.commandValue : tile.commandValue;
            CommandRow row = new CommandRow(tile, current != null, value);
            commandRows.add(row);
            commandRowMap.put(tile.key, row);
        }
        applyCommandSort();
    }

    private void sortSensorRows(SortMode mode) {
        if (sensorSortMode == mode) {
            sensorAscending = !sensorAscending;
        } else {
            sensorSortMode = mode;
            sensorAscending = true;
        }
        applySensorSort();
        sensorAdapter.notifyDataSetChanged();
    }

    private void sortCommandRows(SortMode mode) {
        if (commandSortMode == mode) {
            commandAscending = !commandAscending;
        } else {
            commandSortMode = mode;
            commandAscending = true;
        }
        applyCommandSort();
        commandAdapter.notifyDataSetChanged();
    }

    private void applySensorSort() {
        Collections.sort(sensorRows, (a, b) -> compareTiles(a.tile, b.tile, sensorSortMode));
        if (!sensorAscending) {
            Collections.reverse(sensorRows);
        }
    }

    private void applyCommandSort() {
        Collections.sort(commandRows, (a, b) -> compareTiles(a.tile, b.tile, commandSortMode));
        if (!commandAscending) {
            Collections.reverse(commandRows);
        }
    }

    private int compareTiles(DashboardTile a, DashboardTile b, SortMode mode) {
        if (mode == SortMode.CATEGORY) {
            String ca = categoryOf(a);
            String cb = categoryOf(b);
            int c = safeCompare(ca, cb);
            if (c != 0) return c;
        }
        if (mode == SortMode.LABEL || mode == SortMode.CATEGORY) {
            int c = safeCompare(a.label, b.label);
            if (c != 0) return c;
        }
        return safeCompare(a.key, b.key);
    }

    private String categoryOf(DashboardTile tile) {
        if (tile.isCommand()) {
            CommandRegistry.CommandEntry e = CommandRegistry.getById(tile.key);
            if (e != null) return e.getCategory(this);
        }
        return "";
    }

    private static int safeCompare(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        return a.compareToIgnoreCase(b);
    }

    private void saveAndFinish() {
        List<DashboardTile> selected = new ArrayList<>();
        for (SensorRow row : sensorRows) {
            if (row.checked) {
                selected.add(row.tile);
            }
        }
        for (CommandRow row : commandRows) {
            if (row.checked) {
                row.tile.commandValue = row.commandValue;
                selected.add(row.tile);
            }
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.dashboard_config_none_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        AppConfig.saveDashboardTiles(this, selected);
        Toast.makeText(this, R.string.dashboard_config_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onLinkedCommandSelected(SensorCommandRegistry.LinkedCommand linked,
                                          CommandRegistry.CommandEntry entry) {
        if (linked.needsParameter) {
            showCommandValueDialog(entry, null, value -> {
                if (value == null) return;
                setCommandSelected(entry.id, value);
            }, null);
        } else {
            setCommandSelected(entry.id, linked.value);
        }
    }

    private void setCommandSelected(String commandId, String value) {
        CommandRow row = commandRowMap.get(commandId);
        if (row == null) return;
        row.checked = true;
        row.commandValue = value;
        commandAdapter.notifyDataSetChanged();
    }

    private void showCommandValueDialog(CommandRegistry.CommandEntry entry, ValueCallback onValueSet) {
        showCommandValueDialog(entry, null, onValueSet, null);
    }

    private void showCommandValueDialog(CommandRegistry.CommandEntry entry, String currentValue,
                                        ValueCallback onValueSet, Runnable onCancel) {
        if (entry.valueType == CommandRegistry.ValueType.NONE) {
            onValueSet.onValue(null);
            return;
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.dashboard_config_value_title);

        if (entry.valueType == CommandRegistry.ValueType.ENUM) {
            final List<String> enumKeys = new ArrayList<>(entry.enumValues.keySet());
            final String[] labels = new String[enumKeys.size()];
            for (int i = 0; i < enumKeys.size(); i++) {
                String id = enumKeys.get(i);
                int resId = MainActivity.enumValueResId(this, id);
                labels[i] = resId != 0 ? getString(resId) : id;
            }
            int checked = -1;
            if (currentValue != null) {
                for (int i = 0; i < enumKeys.size(); i++) {
                    if (currentValue.equals(enumKeys.get(i))) {
                        checked = i;
                        break;
                    }
                }
            }
            final int[] selected = {checked >= 0 ? checked : 0};
            b.setSingleChoiceItems(labels, checked, (d, which) -> selected[0] = which);
            b.setPositiveButton(android.R.string.ok, (d, w) -> {
                if (selected[0] >= 0 && selected[0] < enumKeys.size()) {
                    onValueSet.onValue(enumKeys.get(selected[0]));
                } else {
                    onValueSet.onValue(null);
                }
            });
        } else {
            final EditText input = new EditText(this);
            input.setPadding(dp(16), dp(12), dp(16), dp(12));
            if (entry.valueHintResId != 0) input.setHint(entry.valueHintResId);
            if (currentValue != null) input.setText(currentValue);

            if (entry.valueType == CommandRegistry.ValueType.RANGE ||
                    entry.valueType == CommandRegistry.ValueType.NUMBER) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL |
                        InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
            if (entry.valueType == CommandRegistry.ValueType.RANGE && TextUtils.isEmpty(currentValue)) {
                input.setText(String.valueOf((int) entry.minValue));
            }
            b.setView(input);
            b.setPositiveButton(android.R.string.ok, (d, w) -> {
                String v = input.getText().toString().trim();
                onValueSet.onValue(v.isEmpty() ? null : v);
            });
        }

        b.setNegativeButton(android.R.string.cancel, (d, w) -> {
            if (onCancel != null) onCancel.run();
        });
        b.setOnCancelListener(d -> {
            if (onCancel != null) onCancel.run();
        });
        b.show();
    }

    private List<SensorCommandRegistry.LinkedCommand> getLinkedCommands(DashboardTile tile) {
        if (tile.type == DashboardTile.Type.PRESET && tile.presetId != null) {
            // For preset tiles, derive linked commands from the preset definition.
            DashboardPresetRegistry.DashboardPreset preset = DashboardPresetRegistry.getInstance(this).getPreset(tile.presetId);
            if (preset == null) return Collections.emptyList();
            List<SensorCommandRegistry.LinkedCommand> linked = new ArrayList<>();
            // Add all preset actions as linked commands.
            for (DashboardPresetRegistry.PresetAction a : preset.actions) {
                linked.add(new SensorCommandRegistry.LinkedCommand(a.command, a.value, false, null));
            }
            // If it's a toggle, show both on and off commands.
            if ("toggle".equals(preset.behavior) && preset.commands != null) {
                if (!preset.commands.onId.isEmpty()) {
                    linked.add(new SensorCommandRegistry.LinkedCommand(preset.commands.onId, preset.commands.onValue, false, null));
                }
                if (!preset.commands.offId.isEmpty()) {
                    linked.add(new SensorCommandRegistry.LinkedCommand(preset.commands.offId, preset.commands.offValue, false, null));
                }
            }
            // Add parameter commands as linked commands with needsParameter=true.
            for (DashboardPresetRegistry.PresetParam p : preset.params) {
                linked.add(new SensorCommandRegistry.LinkedCommand(p.command, null, true, null));
            }
            // For select behavior, add all options as linked commands.
            for (DashboardPresetRegistry.PresetOption o : preset.options) {
                linked.add(new SensorCommandRegistry.LinkedCommand(o.command, o.commandValue, false, o.label));
            }
            return linked;
        }
        // Fallback to sensor_command_map.json for legacy sensor tiles.
        if (sensorCommandRegistry == null) return Collections.emptyList();
        return sensorCommandRegistry.getCommandsForSensor(tile.key);
    }

    private TextView createChip(String label, View.OnClickListener listener) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTextColor(getResources().getColor(R.color.onPrimary));
        chip.setBackgroundResource(R.drawable.chip_background);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(lp);
        chip.setClickable(true);
        chip.setOnClickListener(listener);
        return chip;
    }

    private int dp(float px) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, px, getResources().getDisplayMetrics());
    }

    private static class SensorRow {
        final DashboardTile tile;
        boolean checked;

        SensorRow(DashboardTile tile, boolean checked) {
            this.tile = tile;
            this.checked = checked;
        }
    }

    private static class CommandRow {
        final DashboardTile tile;
        boolean checked;
        String commandValue;

        CommandRow(DashboardTile tile, boolean checked, String commandValue) {
            this.tile = tile;
            this.checked = checked;
            this.commandValue = commandValue;
        }
    }

    private class SensorAdapter extends ArrayAdapter<SensorRow> {
        SensorAdapter(Context context, List<SensorRow> rows) {
            super(context, 0, rows);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.dashboard_config_row, parent, false);
                vh = new ViewHolder();
                vh.check = convertView.findViewById(R.id.checkTile);
                vh.label = convertView.findViewById(R.id.textTileLabel);
                vh.key = convertView.findViewById(R.id.textTileKey);
                vh.category = convertView.findViewById(R.id.textTileCategory);
                vh.linkedLabel = convertView.findViewById(R.id.textLinkedCommands);
                vh.chipContainer = convertView.findViewById(R.id.chipContainer);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }

            SensorRow row = getItem(position);
            if (row == null) return convertView;

            vh.check.setOnCheckedChangeListener(null);
            vh.check.setChecked(row.checked);
            vh.check.setOnCheckedChangeListener((buttonView, isChecked) -> row.checked = isChecked);
            vh.label.setText(row.tile.label);
            if (vh.key != null) {
                vh.key.setVisibility(View.VISIBLE);
                vh.key.setText(row.tile.key);
            }
            if (vh.category != null) vh.category.setVisibility(View.GONE);

            List<SensorCommandRegistry.LinkedCommand> linked = getLinkedCommands(row.tile);
            if (row.checked && !linked.isEmpty()) {
                vh.linkedLabel.setVisibility(View.VISIBLE);
                vh.chipContainer.setVisibility(View.VISIBLE);
                vh.chipContainer.removeAllViews();
                for (SensorCommandRegistry.LinkedCommand lc : linked) {
                    CommandRegistry.CommandEntry entry = CommandRegistry.getById(lc.commandId);
                    if (entry == null) continue;
                    String chipLabel = lc.labelKey != null && !lc.labelKey.isEmpty() ? lc.labelKey : entry.getDisplayName(DashboardConfigActivity.this);
                    TextView chip = createChip(chipLabel,
                            v -> onLinkedCommandSelected(lc, entry));
                    vh.chipContainer.addView(chip);
                }
            } else {
                vh.linkedLabel.setVisibility(View.GONE);
                vh.chipContainer.setVisibility(View.GONE);
                vh.chipContainer.removeAllViews();
            }

            return convertView;
        }

        class ViewHolder {
            CheckBox check;
            TextView label;
            TextView key;
            TextView category;
            TextView linkedLabel;
            LinearLayout chipContainer;
        }
    }

    private class CommandAdapter extends ArrayAdapter<CommandRow> {
        CommandAdapter(Context context, List<CommandRow> rows) {
            super(context, 0, rows);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.dashboard_config_row, parent, false);
                vh = new ViewHolder();
                vh.check = convertView.findViewById(R.id.checkTile);
                vh.label = convertView.findViewById(R.id.textTileLabel);
                vh.key = convertView.findViewById(R.id.textTileKey);
                vh.category = convertView.findViewById(R.id.textTileCategory);
                vh.linkedLabel = convertView.findViewById(R.id.textLinkedCommands);
                vh.chipContainer = convertView.findViewById(R.id.chipContainer);
                vh.linkedLabel.setVisibility(View.GONE);
                vh.chipContainer.setVisibility(View.GONE);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }

            CommandRow row = getItem(position);
            if (row == null) return convertView;

            CommandRegistry.CommandEntry entry = CommandRegistry.getById(row.tile.key);

            vh.check.setOnCheckedChangeListener(null);
            vh.check.setChecked(row.checked);
            vh.check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                row.checked = isChecked;
                if (isChecked && entry != null && entry.needsValue() && TextUtils.isEmpty(row.commandValue)) {
                    showCommandValueDialog(entry, row.commandValue, value -> {
                        row.commandValue = value;
                        notifyDataSetChanged();
                    }, () -> {
                        row.checked = false;
                        notifyDataSetChanged();
                    });
                }
            });

            String label = row.tile.label;
            if (!TextUtils.isEmpty(row.commandValue)) {
                label = label + " (" + row.commandValue + ")";
            }
            vh.label.setText(label);
            if (vh.key != null) {
                vh.key.setVisibility(View.VISIBLE);
                vh.key.setText(row.tile.key);
            }
            if (vh.category != null) {
                vh.category.setVisibility(View.VISIBLE);
                vh.category.setText(entry != null ? entry.getCategory(DashboardConfigActivity.this) : "");
            }

            return convertView;
        }

        class ViewHolder {
            CheckBox check;
            TextView label;
            TextView key;
            TextView category;
            TextView linkedLabel;
            LinearLayout chipContainer;
        }
    }
}
