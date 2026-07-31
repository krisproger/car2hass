package com.diplustohass;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Grouped picker dialog for adding a sensor or command tile to the dashboard.
 */
public class DashboardAddTileDialog {

    public interface OnTileSelectedListener {
        void onTileSelected(DashboardTile tile);
    }

    public interface ValueCallback {
        void onValue(String value);
    }

    public static AlertDialog show(Context context, Set<String> existingKeys, OnTileSelectedListener listener) {
        List<DashboardTile> sensors = DashboardTileFactory.availableSensors(context);
        List<DashboardTile> commands = DashboardTileFactory.availableCommands(context);

        List<DashboardTile> all = new ArrayList<>();
        for (DashboardTile t : sensors) {
            if (!existingKeys.contains(tileKey(t))) all.add(t);
        }
        for (DashboardTile t : commands) {
            if (!existingKeys.contains(tileKey(t))) all.add(t);
        }

        Map<String, List<DashboardTile>> groups = new LinkedHashMap<>();
        for (DashboardTile t : all) {
            String category = categoryOf(context, t);
            if (!groups.containsKey(category)) groups.put(category, new ArrayList<>());
            groups.get(category).add(t);
        }

        List<String> groupTitles = new ArrayList<>(groups.keySet());
        Collections.sort(groupTitles);
        for (List<DashboardTile> list : groups.values()) {
            Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
        }

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_tile, null);
        ExpandableListView elv = view.findViewById(R.id.elvAddTile);
        TileAdapter adapter = new TileAdapter(context, groupTitles, groups);
        elv.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        elv.setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> {
            String group = groupTitles.get(groupPosition);
            DashboardTile tile = groups.get(group).get(childPosition);
            if (tile.type == DashboardTile.Type.COMMAND) {
                CommandRegistry.CommandEntry entry = CommandRegistry.getById(tile.key);
                if (entry != null && entry.needsValue()) {
                    showCommandValueDialog(context, entry, value -> {
                        if (value != null) {
                            tile.commandValue = value;
                            tile.setValue(value);
                            listener.onTileSelected(tile);
                        }
                        dialog.dismiss();
                    });
                    return true;
                }
            }
            listener.onTileSelected(tile);
            dialog.dismiss();
            return true;
        });

        dialog.show();
        return dialog;
    }

    private static String tileKey(DashboardTile tile) {
        if (tile.type == DashboardTile.Type.PRESET && tile.presetId != null) return tile.presetId;
        if (tile.type == DashboardTile.Type.COMMAND && tile.commandValue != null && !tile.commandValue.isEmpty())
            return tile.key + "_" + tile.commandValue;
        return tile.key;
    }

    private static String categoryOf(Context context, DashboardTile tile) {
        if (tile.type == DashboardTile.Type.COMMAND) {
            CommandRegistry.CommandEntry entry = CommandRegistry.getById(tile.key);
            if (entry != null) return entry.getCategory(context);
        }
        if (tile.type == DashboardTile.Type.PRESET && tile.presetId != null) {
            DashboardPresetRegistry.DashboardPreset preset = DashboardPresetRegistry.getInstance(context).getPreset(tile.presetId);
            if (preset != null && !preset.actions.isEmpty()) {
                CommandRegistry.CommandEntry entry = CommandRegistry.getById(preset.actions.get(0).command);
                if (entry != null) return entry.getCategory(context);
            }
            if (preset != null && preset.commands != null) {
                if (!preset.commands.onId.isEmpty()) {
                    CommandRegistry.CommandEntry entry = CommandRegistry.getById(preset.commands.onId);
                    if (entry != null) return entry.getCategory(context);
                }
                if (!preset.commands.actionId.isEmpty()) {
                    CommandRegistry.CommandEntry entry = CommandRegistry.getById(preset.commands.actionId);
                    if (entry != null) return entry.getCategory(context);
                }
            }
        }
        return context.getString(R.string.dashboard_category_sensors);
    }

    private static String typeLabel(Context context, DashboardTile tile) {
        switch (tile.type) {
            case COMMAND: return context.getString(R.string.dashboard_type_command);
            case PRESET: return context.getString(R.string.dashboard_type_preset);
            default: return context.getString(R.string.dashboard_type_sensor);
        }
    }

    private static void showCommandValueDialog(Context context, CommandRegistry.CommandEntry entry,
                                               final ValueCallback callback) {
        if (entry.valueType == CommandRegistry.ValueType.NONE) {
            callback.onValue(null);
            return;
        }

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(R.string.dashboard_config_value_title);

        if (entry.valueType == CommandRegistry.ValueType.ENUM) {
            final List<String> enumKeys = new ArrayList<>(entry.enumValues.keySet());
            final String[] labels = new String[enumKeys.size()];
            for (int i = 0; i < enumKeys.size(); i++) {
                String id = enumKeys.get(i);
                int resId = MainActivity.enumValueResId(context, id);
                labels[i] = resId != 0 ? context.getString(resId) : id;
            }
            final int[] selected = {0};
            b.setSingleChoiceItems(labels, -1, (d, which) -> selected[0] = which);
            b.setPositiveButton(android.R.string.ok, (d, w) -> {
                if (selected[0] >= 0 && selected[0] < enumKeys.size()) {
                    callback.onValue(enumKeys.get(selected[0]));
                } else {
                    callback.onValue(null);
                }
            });
        } else {
            final EditText input = new EditText(context);
            int dp16 = (int) android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics());
            int dp12 = (int) android.util.TypedValue.applyDimension(
                    android.util.TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
            input.setPadding(dp16, dp12, dp16, dp12);
            if (entry.valueHintResId != 0) input.setHint(entry.valueHintResId);
            if (entry.valueType == CommandRegistry.ValueType.RANGE ||
                    entry.valueType == CommandRegistry.ValueType.NUMBER) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL |
                        InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
            if (entry.valueType == CommandRegistry.ValueType.RANGE) {
                input.setText(String.valueOf((int) entry.minValue));
            }
            b.setView(input);
            b.setPositiveButton(android.R.string.ok, (d, w) -> {
                String v = input.getText().toString().trim();
                callback.onValue(v.isEmpty() ? null : v);
            });
        }

        b.setNegativeButton(android.R.string.cancel, null);
        b.show();
    }

    private static class TileAdapter extends BaseExpandableListAdapter {
        private final Context context;
        private final List<String> groupTitles;
        private final Map<String, List<DashboardTile>> groups;

        TileAdapter(Context context, List<String> groupTitles, Map<String, List<DashboardTile>> groups) {
            this.context = context;
            this.groupTitles = groupTitles;
            this.groups = groups;
        }

        @Override public int getGroupCount() { return groupTitles.size(); }
        @Override public int getChildrenCount(int groupPosition) {
            return groups.get(groupTitles.get(groupPosition)).size();
        }
        @Override public Object getGroup(int groupPosition) { return groupTitles.get(groupPosition); }
        @Override public Object getChild(int groupPosition, int childPosition) {
            return groups.get(groupTitles.get(groupPosition)).get(childPosition);
        }
        @Override public long getGroupId(int groupPosition) { return groupPosition; }
        @Override public long getChildId(int groupPosition, int childPosition) { return groupPosition * 1000L + childPosition; }
        @Override public boolean hasStableIds() { return false; }
        @Override public boolean isChildSelectable(int groupPosition, int childPosition) { return true; }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            TextView tv = (TextView) convertView;
            if (tv == null) {
                tv = (TextView) LayoutInflater.from(context).inflate(R.layout.add_tile_group, parent, false);
            }
            tv.setText((String) getGroup(groupPosition));
            return tv;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                                 View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.add_tile_item, parent, false);
                vh = new ViewHolder();
                vh.icon = convertView.findViewById(R.id.tvItemIcon);
                vh.label = convertView.findViewById(R.id.tvItemLabel);
                vh.type = convertView.findViewById(R.id.tvItemType);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }
            DashboardTile tile = (DashboardTile) getChild(groupPosition, childPosition);
            vh.icon.setText(tile.icon);
            vh.label.setText(tile.label);
            vh.type.setText(typeLabel(context, tile));
            return convertView;
        }

        static class ViewHolder {
            TextView icon, label, type;
        }
    }
}
