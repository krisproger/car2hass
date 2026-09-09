package com.car2hass;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Expandable telemetry list: groups (Active/Expected/…) with sensor children. */
public final class TelemetryExpandableAdapter extends BaseExpandableListAdapter {

    /** One collapsible group with its sensor rows. */
    public static final class Group {
        public final String key;
        public final String title;
        public final List<CANDataItem> items = new ArrayList<>();
        public Group(String key, String title) { this.key = key; this.title = title; }
    }

    private final Context context;
    private final List<Group> groups = new ArrayList<>();
    private Consumer<String> onHeaderClick;
    private Consumer<CANDataItem> onCheckChanged;

    public TelemetryExpandableAdapter(Context context) {
        this.context = context;
    }

    public void setOnHeaderClick(Consumer<String> listener) { this.onHeaderClick = listener; }
    public void setOnCheckChanged(Consumer<CANDataItem> listener) { this.onCheckChanged = listener; }

    public void setGroups(List<Group> data) {
        groups.clear();
        if (data != null) groups.addAll(data);
        notifyDataSetChanged();
    }

    public List<Group> getGroups() { return groups; }

    @Override public int getGroupCount() { return groups.size(); }
    @Override public long getGroupId(int groupPosition) { return groupPosition; }
    @Override public Object getGroup(int groupPosition) { return groups.get(groupPosition); }
    @Override public int getChildrenCount(int groupPosition) { return groups.get(groupPosition).items.size(); }
    @Override public Object getChild(int groupPosition, int childPosition) {
        return groups.get(groupPosition).items.get(childPosition);
    }
    @Override public long getChildId(int groupPosition, int childPosition) {
        return groupPosition * 10000L + childPosition;
    }
    @Override public boolean hasStableIds() { return false; }
    @Override public boolean isChildSelectable(int groupPosition, int childPosition) { return true; }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
                             View convertView, ViewGroup parent) {
        View v = LayoutInflater.from(context).inflate(
                com.car2hass.R.layout.can_data_header, parent, false);
        Group g = groups.get(groupPosition);
        TextView tv = v.findViewById(com.car2hass.R.id.rowName);
        if (tv != null) tv.setText(g.title + (isExpanded ? " ▾" : " ▸"));
        return v;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent) {
        View v;
        ViewHolder vh;
        if (convertView == null) {
            v = LayoutInflater.from(context).inflate(
                    com.car2hass.R.layout.can_data_row, parent, false);
            vh = new ViewHolder();
            vh.checkBox = v.findViewById(com.car2hass.R.id.rowCheck);
            vh.idText = v.findViewById(com.car2hass.R.id.rowId);
            vh.nameText = v.findViewById(com.car2hass.R.id.rowName);
            vh.valueText = v.findViewById(com.car2hass.R.id.rowValue);
            vh.unitText = v.findViewById(com.car2hass.R.id.rowUnit);
            vh.routeText = v.findViewById(com.car2hass.R.id.rowRoute);
            v.setTag(vh);
        } else {
            v = convertView;
            vh = (ViewHolder) v.getTag();
        }

        final CANDataItem rowItem = groups.get(groupPosition).items.get(childPosition);
        boolean systemKey = isSystemKey(rowItem.key);
        vh.idText.setText(rowItem.key != null && !rowItem.key.isEmpty()
                ? rowItem.key : rowItem.diplusName);
        vh.nameText.setText(rowItem.name);
        vh.valueText.setText(SignalTranslator.translateValue(rowItem.value));
        vh.unitText.setText(rowItem.unit);
        vh.routeText.setText(rowItem.rawData != null && !rowItem.rawData.isEmpty()
                ? rowItem.rawData : "");

        if (systemKey) {
            vh.valueText.setTextColor(0xFF4CAF50);
            vh.nameText.setTextColor(0xFFFFFFFF);
        } else if (rowItem.grey) {
            vh.valueText.setTextColor(0xFF808080);
            vh.nameText.setTextColor(0xFF808080);
        } else {
            boolean fresh = (System.currentTimeMillis() - rowItem.lastUpdate) < 5000;
            vh.valueText.setTextColor(fresh ? 0xFF4CAF50 : 0xFFB0B0B0);
            vh.nameText.setTextColor(0xFFFFFFFF);
        }

        vh.checkBox.setOnCheckedChangeListener(null);
        if (systemKey) {
            vh.checkBox.setVisibility(View.VISIBLE);
            vh.checkBox.setEnabled(false);
            vh.checkBox.setChecked(true);
        } else if (rowItem.unsupported) {
            vh.checkBox.setVisibility(View.GONE);
        } else {
            vh.checkBox.setVisibility(View.VISIBLE);
            vh.checkBox.setEnabled(true);
            vh.checkBox.setChecked(rowItem.enabled);
            vh.checkBox.setOnCheckedChangeListener((b, isChecked) -> {
                rowItem.enabled = isChecked;
                if (onCheckChanged != null) onCheckChanged.accept(rowItem);
            });
        }
        return v;
    }

    private static boolean isSystemKey(String key) {
        return key != null && (key.startsWith("location_")
                || "device_battery".equals(key) || "device_pressure".equals(key)
                || "media_volume".equals(key));
    }

    private static final class ViewHolder {
        CheckBox checkBox;
        TextView idText;
        TextView nameText;
        TextView valueText;
        TextView unitText;
        TextView routeText;
    }
}