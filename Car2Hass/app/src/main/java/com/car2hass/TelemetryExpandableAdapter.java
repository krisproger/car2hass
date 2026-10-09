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
    private final LayoutInflater inflater;
    private final List<Group> groups = new ArrayList<>();
    private final java.util.Set<String> expandedCategories = new java.util.HashSet<>();
    private final java.util.Map<String, String> categoryLabelCache = new java.util.HashMap<>();
    private Consumer<String> onHeaderClick;
    private Consumer<String> onCategoryClick;
    private Consumer<CANDataItem> onCheckChanged;

    public TelemetryExpandableAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    public void setOnHeaderClick(Consumer<String> listener) { this.onHeaderClick = listener; }
    public void setOnCheckChanged(Consumer<CANDataItem> listener) { this.onCheckChanged = listener; }

    public void setExpandedCategories(java.util.Set<String> cats) {
        expandedCategories.clear();
        if (cats != null) expandedCategories.addAll(cats);
        notifyDataSetChanged();
    }

    public void setOnCategoryClick(Consumer<String> listener) { this.onCategoryClick = listener; }

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
    @Override public boolean isChildSelectable(int groupPosition, int childPosition) {
        return !groups.get(groupPosition).items.get(childPosition).isHeader;
    }

    // Two child view types (0 = sensor row, 1 = category header) so the
    // recycler never hands a header layout to a row (or vice versa).
    @Override public int getChildTypeCount() { return 2; }
    @Override public int getChildType(int groupPosition, int childPosition) {
        return groups.get(groupPosition).items.get(childPosition).isHeader ? 1 : 0;
    }

    private String categoryLabel(String key) {
        String cached = categoryLabelCache.get(key);
        if (cached != null) return cached;
        int res = context.getResources().getIdentifier(
                "sensor_category_" + key, "string", context.getPackageName());
        String label = res != 0 ? context.getString(res) : key;
        categoryLabelCache.put(key, label);
        return label;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
                             View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = inflater.inflate(com.car2hass.R.layout.can_data_header, parent, false);
        }
        Group g = groups.get(groupPosition);
        TextView tv = v.findViewById(com.car2hass.R.id.rowName);
        if (tv != null) tv.setText(g.title + (isExpanded ? " ▾" : " ▸"));
        return v;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent) {
        final CANDataItem rowItem = groups.get(groupPosition).items.get(childPosition);

        if (rowItem.isHeader) {
            View hv = convertView;
            if (hv == null) {
                hv = inflater.inflate(com.car2hass.R.layout.can_data_header, parent, false);
            }
            TextView ht = hv.findViewById(com.car2hass.R.id.rowName);
            if (ht != null) {
                ht.setText(categoryLabel(rowItem.groupKey)
                        + (expandedCategories.contains(rowItem.groupKey) ? " ▾" : " ▸"));
            }
            final String groupKey = rowItem.groupKey;
            hv.setOnClickListener(v -> {
                if (onCategoryClick != null) onCategoryClick.accept(groupKey);
            });
            return hv;
        }

        View v;
        ViewHolder vh;
        if (convertView == null || !(convertView.getTag() instanceof ViewHolder)) {
            v = inflater.inflate(com.car2hass.R.layout.can_data_row, parent, false);
            vh = new ViewHolder();
            vh.checkBox = v.findViewById(com.car2hass.R.id.rowCheck);
            vh.idText = v.findViewById(com.car2hass.R.id.rowId);
            vh.nameText = v.findViewById(com.car2hass.R.id.rowName);
            vh.valueText = v.findViewById(com.car2hass.R.id.rowValue);
            vh.unitText = v.findViewById(com.car2hass.R.id.rowUnit);
            vh.updatedText = v.findViewById(com.car2hass.R.id.rowUpdated);
            vh.routeText = v.findViewById(com.car2hass.R.id.rowRoute);
            v.setTag(vh);
        } else {
            v = convertView;
            vh = (ViewHolder) v.getTag();
        }

        boolean systemKey = isSystemKey(rowItem.key);
        vh.idText.setText(rowItem.key != null && !rowItem.key.isEmpty()
                ? rowItem.key : rowItem.diplusName);
        vh.nameText.setText(rowItem.name);
        vh.valueText.setText(SignalTranslator.translateValue(rowItem.value));
        vh.unitText.setText(rowItem.unit);
        vh.updatedText.setText(TelemetryAge.format(System.currentTimeMillis() - rowItem.valueUpdatedAt));
        vh.routeText.setText(rowItem.sourceChannel != null && !rowItem.sourceChannel.isEmpty()
                ? rowItem.sourceChannel
                : (rowItem.rawData != null && !rowItem.rawData.isEmpty() ? rowItem.rawData : ""));

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
        TextView updatedText;
        TextView routeText;
    }
}