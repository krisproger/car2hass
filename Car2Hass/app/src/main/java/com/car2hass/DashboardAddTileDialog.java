package com.car2hass;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picker dialog for adding a sensor, command, or preset tile to the dashboard.
 * Three tabs via RadioGroup: Sensors, Commands, Presets.
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
        List<DashboardPresetRegistry.DashboardPreset> presets =
                DashboardPresetRegistry.getInstance((Activity) context).getAllPresets();

        // Filter out already-added tiles.
        List<DashboardTile> sensorList = new ArrayList<>();
        for (DashboardTile t : sensors) {
            if (!existingKeys.contains(tileKey(t))) sensorList.add(t);
        }
        List<DashboardTile> commandList = new ArrayList<>();
        for (DashboardTile t : commands) {
            if (!existingKeys.contains(tileKey(t))) commandList.add(t);
        }
        List<DashboardTile> presetList = new ArrayList<>();
        for (DashboardPresetRegistry.DashboardPreset p : presets) {
            if (!existingKeys.contains(p.id)) {
                // Use the factory so picker tiles carry the same icon (PNG
                // name + emoji fallback), label, behavior and presetId as the
                // dashboard tiles themselves.
                presetList.add(DashboardTileFactory.createPresetTile(context, p.id));
            }
        }

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_tile_tabs, null);
        RadioGroup radioGroup = view.findViewById(R.id.radioGroupTabs);
        ListView listView = view.findViewById(R.id.lvAddTile);

        // Current tab state.
        final String[] currentType = {"sensor"}; // sensor | command | preset
        final List<DashboardTile>[] currentList = new List[]{sensorList};

        // Sort all lists.
        Collections.sort(sensorList, (a, b) -> a.label.compareToIgnoreCase(b.label));
        Collections.sort(commandList, (a, b) -> a.label.compareToIgnoreCase(b.label));
        Collections.sort(presetList, (a, b) -> a.label.compareToIgnoreCase(b.label));

        TileListAdapter adapter = new TileListAdapter(context, sensorList, R.string.dashboard_type_sensor);
        listView.setAdapter(adapter);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioSensors) {
                currentType[0] = "sensor";
                currentList[0] = sensorList;
            } else if (checkedId == R.id.radioCommands) {
                currentType[0] = "command";
                currentList[0] = commandList;
            } else {
                currentType[0] = "preset";
                currentList[0] = presetList;
            }
            adapter.setTiles(currentList[0], getLabelRes(currentType[0]));
            adapter.notifyDataSetChanged();
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        listView.setOnItemClickListener((parent, v, position, id) -> {
            DashboardTile tile = adapter.getItem(position);
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
                    return;
                }
            }
            // For sensor tiles, ask for display type.
            if (tile.type == DashboardTile.Type.SENSOR) {
                showSensorDisplayTypeDialog(context, tile, selectedType -> {
                    tile.displayType = selectedType;
                    listener.onTileSelected(tile);
                    dialog.dismiss();
                });
                return;
            }
            listener.onTileSelected(tile);
            dialog.dismiss();
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

    private static int getLabelRes(String type) {
        switch (type) {
            case "command": return R.string.dashboard_type_command;
            case "preset":  return R.string.dashboard_type_preset;
            default:        return R.string.dashboard_type_sensor;
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

    private static void showSensorDisplayTypeDialog(Context context, DashboardTile tile,
                                                    DisplayTypeCallback callback) {
        String[] types = context.getResources().getStringArray(R.array.sensor_display_types);
        String[] values = {"text", "gauge", "graph"};
        int initial = 0;
        if (tile.displayType != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(tile.displayType)) {
                    initial = i;
                    break;
                }
            }
        }
        final int[] selected = {initial};
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(R.string.dashboard_config_display_type_title);
        b.setSingleChoiceItems(types, initial, (d, which) -> selected[0] = which);
        b.setPositiveButton(android.R.string.ok, (d, w) -> callback.onTypeSelected(values[selected[0]]));
        b.setNegativeButton(android.R.string.cancel, null);
        b.show();
    }

    interface DisplayTypeCallback {
        void onTypeSelected(String type);
    }

    private static class TileListAdapter extends BaseAdapter {
        private final Context context;
        private List<DashboardTile> tiles;
        private int typeLabelResId;

        TileListAdapter(Context context, List<DashboardTile> tiles, int typeLabelResId) {
            this.context = context;
            this.tiles = tiles;
            this.typeLabelResId = typeLabelResId;
        }

        void setTiles(List<DashboardTile> tiles, int typeLabelResId) {
            this.tiles = tiles;
            this.typeLabelResId = typeLabelResId;
        }

        @Override public int getCount() { return tiles.size(); }
        @Override public DashboardTile getItem(int position) { return tiles.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                convertView = LayoutInflater.from(context)
                        .inflate(R.layout.dialog_add_tile_tab_item, parent, false);
                vh = new ViewHolder();
                vh.icon = convertView.findViewById(R.id.tvTileIcon);
                vh.iconImage = convertView.findViewById(R.id.tvTileIconImage);
                vh.label = convertView.findViewById(R.id.tvTileLabel);
                vh.type = convertView.findViewById(R.id.tvTileType);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }
            DashboardTile tile = getItem(position);
            // Prefer a PNG icon (filesDir override -> bundled asset); fall back
            // to the emoji text icon exactly like the dashboard grid does.
            Bitmap iconBitmap = tile.iconName != null && !tile.iconName.isEmpty()
                    ? PresetIconResolver.resolve(context, tile.iconName) : null;
            if (iconBitmap != null && vh.iconImage != null) {
                vh.iconImage.setImageBitmap(iconBitmap);
                vh.iconImage.setVisibility(View.VISIBLE);
                vh.icon.setVisibility(View.GONE);
            } else {
                if (vh.iconImage != null) vh.iconImage.setVisibility(View.GONE);
                vh.icon.setVisibility(View.VISIBLE);
            }
            vh.icon.setText(tile.icon);
            vh.label.setText(tile.label);
            vh.type.setText(context.getString(typeLabelResId));
            return convertView;
        }

        static class ViewHolder {
            TextView icon, label, type;
            ImageView iconImage;
        }
    }
}